#include <jni.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <string_view>
#include <deque>
#include <cmath>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

namespace {
// Java UTF-16 -> strict UTF-8. GetStringUTFChars returns Modified UTF-8,
// which is not suitable for GGUF tokenization of supplementary Unicode characters.
std::string java_to_utf8(JNIEnv *env, jstring value) {
    if (!value) return {};
    const jsize length = env->GetStringLength(value);
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    std::string out;
    out.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF) {
            if (i + 1 < length && chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
                cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[++i] - 0xDC00);
            } else cp = 0xFFFD;
        } else if (cp >= 0xDC00 && cp <= 0xDFFF) cp = 0xFFFD;
        if (cp < 0x80) out.push_back(static_cast<char>(cp));
        else if (cp < 0x800) {
            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return out;
}
std::mutex inference_mutex;
std::atomic<bool> cancel_requested{false};
std::atomic<jlong> active_request_id{0};
std::mutex pending_mutex;
using Clock = std::chrono::steady_clock;
struct PendingCancellation { jlong id; Clock::time_point expires; };
std::deque<PendingCancellation> cancelled_pending;
constexpr size_t kMaxPendingCancellations = 1024;
constexpr auto kCancellationTtl = std::chrono::minutes(10);
// Called only while pending_mutex is held. Bounds stale cancellation state.
void prune_pending() {
    const auto now = Clock::now();
    cancelled_pending.erase(std::remove_if(cancelled_pending.begin(), cancelled_pending.end(),
        [now](const PendingCancellation &entry) { return entry.expires <= now; }), cancelled_pending.end());
    while (cancelled_pending.size() > kMaxPendingCancellations) cancelled_pending.pop_front();
}
bool consume_pending(jlong id) {
    prune_pending();
    auto it = std::find_if(cancelled_pending.begin(), cancelled_pending.end(),
        [id](const PendingCancellation &entry) { return entry.id == id; });
    if (it == cancelled_pending.end()) return false;
    cancelled_pending.erase(it);
    return true;
}
constexpr int kContext = 4096;
constexpr int kBatch = 512;
constexpr int kMaxOutputTokens = 2048;
// Leave room for at least one generated token and its decode.
constexpr int kMaxPromptTokens = kContext - 1;
// The model is retained between requests; context is reset per request.
llama_model *cached_model = nullptr;
std::string cached_path;
struct BackendLifetime {
    BackendLifetime() { llama_backend_init(); }
    ~BackendLifetime() { if (cached_model) llama_model_free(cached_model); llama_backend_free(); }
};
BackendLifetime backend_lifetime;


std::string infer(const std::string &path, const std::string &prompt, int max_tokens, float temperature) {
    if (path.empty() || prompt.empty()) return "ERROR: model path or prompt is empty";
    if (max_tokens < 1 || max_tokens > kMaxOutputTokens) return "ERROR: invalid output token limit";
    if (!std::isfinite(temperature) || temperature < 0.0f || temperature > 5.0f)
        return "ERROR: invalid temperature";

    if (!cached_model || cached_path != path) {
        // Load the replacement first. If loading fails, preserve the previous usable model.
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        llama_model *replacement = llama_model_load_from_file(path.c_str(), mp);
        if (!replacement) return "ERROR: GGUF model load failed";
        if (cached_model) llama_model_free(cached_model);
        cached_model = replacement;
        cached_path = path;
    }
    llama_model *model = cached_model;
    auto cp = llama_context_default_params();
    cp.n_ctx = kContext;
    cp.n_batch = kBatch;
    cp.n_ubatch = kBatch;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) return "ERROR: llama context initialization failed";

    llama_sampler *sampler = nullptr;
    std::string output;
    const llama_vocab *vocab = llama_model_get_vocab(model);
    // Allocate no more than the usable context; a positive count is valid.
    // Avoid depending on how a particular llama.cpp revision handles a null output buffer.
    std::vector<llama_token> tokens(static_cast<size_t>(kContext));
    int count = llama_tokenize(vocab, prompt.c_str(), static_cast<int>(prompt.size()),
                               tokens.data(), static_cast<int>(tokens.size()), true, true);
    if (count <= 0 || count > kMaxPromptTokens) {
        output = "ERROR: tokenization failed or prompt exceeds context";
    } else {
        tokens.resize(static_cast<size_t>(count));
        {
            bool decoded = true;
            // llama_batch_get_one is limited by n_batch. Feed the prompt in chunks,
            // retaining logits from the final token for the first sampling step.
            for (int offset = 0; offset < count; offset += kBatch) {
                if (cancel_requested.load()) { decoded = false; break; }
                int size = std::min(kBatch, count - offset);
                llama_batch batch = llama_batch_get_one(tokens.data() + offset, size);
                if (llama_decode(ctx, batch) != 0) { decoded = false; break; }
            }
            if (!decoded) {
                output = cancel_requested.load() ? "ERROR: generation cancelled" : "ERROR: prompt evaluation failed";
            } else {
                auto sp = llama_sampler_chain_default_params();
                sampler = llama_sampler_chain_init(sp);
                if (!sampler) {
                    output = "ERROR: sampler initialization failed";
                } else {
                    if (temperature == 0.0f) {
                        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
                    } else {
                        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
                        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
                    }
                    for (int i = 0; i < max_tokens && count + i < kContext; ++i) {
                        if (cancel_requested.load()) { output = "ERROR: generation cancelled"; break; }
                        llama_token token = llama_sampler_sample(sampler, ctx, -1);
                        if (llama_vocab_is_eog(vocab, token)) break;
                        int needed = llama_token_to_piece(vocab, token, nullptr, 0, 0, true);
                        if (needed < 0) {
                            std::vector<char> piece(static_cast<size_t>(-needed));
                            int n = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, true);
                            if (n > 0) output.append(piece.data(), static_cast<size_t>(n));
                            else { output = "ERROR: token decoding failed"; break; }
                        } else if (needed > 0) {
                            // Some llama.cpp revisions return required bytes as positive.
                            std::vector<char> piece(static_cast<size_t>(needed));
                            int n = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, true);
                            if (n > 0) output.append(piece.data(), static_cast<size_t>(n));
                            else { output = "ERROR: token decoding failed"; break; }
                        }
                        // The final sampled token needs no decode: there is no next sampling step.
                        if (i + 1 < max_tokens && count + i + 1 < kContext) {
                            llama_batch next = llama_batch_get_one(&token, 1);
                            if (llama_decode(ctx, next) != 0) { output += "\n[ERROR: generation decode failed]"; break; }
                        }
                    }
                }
            }
        }
    }
    if (sampler) llama_sampler_free(sampler);
    llama_free(ctx);
    return output;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_eva1_companion_engines_EvaNativeGguf_generate(JNIEnv *env, jobject,
    jlong request_id, jstring model_path, jstring prompt_j, jint max_tokens, jfloat temperature) {
    if (request_id <= 0 || !model_path || !prompt_j) return env->NewStringUTF("ERROR: invalid request input");
    {
        std::lock_guard<std::mutex> pending_guard(pending_mutex);
        if (consume_pending(request_id)) {
            return env->NewStringUTF("ERROR: generation cancelled");
        }
    }
    std::lock_guard<std::mutex> guard(inference_mutex);
    {
        std::lock_guard<std::mutex> pending_guard(pending_mutex);
        if (consume_pending(request_id)) {
            return env->NewStringUTF("ERROR: generation cancelled");
        }
        // Activate under the same lock as queued cancellation to avoid a lost signal.
        cancel_requested.store(false);
        active_request_id.store(request_id);
    }
    std::string result;
    try {
        const std::string path = java_to_utf8(env, model_path);
        const std::string prompt = java_to_utf8(env, prompt_j);
        if (env->ExceptionCheck()) result = "ERROR: JNI string conversion failed";
        else result = infer(path, prompt, max_tokens, temperature);
    } catch (...) { result = "ERROR: native inference exception"; }
    {
        std::lock_guard<std::mutex> pending_guard(pending_mutex);
        active_request_id.store(0);
        cancel_requested.store(false);
    }
    // NewStringUTF expects modified UTF-8; generated model bytes can contain ordinary UTF-8
    // (including 4-byte code points). Construct a Java String from UTF-16 instead.
    std::u16string utf16;
    for (size_t i = 0; i < result.size();) {
        const unsigned char c = static_cast<unsigned char>(result[i]);
        uint32_t cp = 0; size_t n = 0;
        if (c < 0x80) { cp = c; n = 1; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; n = 2; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; n = 3; }
        else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; n = 4; }
        if (!n || i + n > result.size()) { utf16.push_back(0xFFFD); ++i; continue; }
        bool valid = true;
        for (size_t j = 1; j < n; ++j) {
            const unsigned char b = static_cast<unsigned char>(result[i+j]);
            if ((b & 0xC0) != 0x80) { valid = false; break; }
            cp = (cp << 6) | (b & 0x3F);
        }
        if (!valid || (n == 2 && cp < 0x80) || (n == 3 && cp < 0x800) ||
            (n == 4 && cp < 0x10000) || cp > 0x10FFFF ||
            (cp >= 0xD800 && cp <= 0xDFFF)) { utf16.push_back(0xFFFD); ++i; continue; }
        if (cp <= 0xFFFF) utf16.push_back(static_cast<char16_t>(cp));
        else { cp -= 0x10000; utf16.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
               utf16.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF))); }
        i += n;
    }
    return env->NewString(reinterpret_cast<const jchar *>(utf16.data()), static_cast<jsize>(utf16.size()));
}

extern "C" JNIEXPORT void JNICALL
Java_com_eva1_companion_engines_EvaNativeGguf_cancel(JNIEnv *, jobject, jlong request_id) {
    // Non-blocking: generation owns inference_mutex and observes this flag.
    if (request_id <= 0) return;
    std::lock_guard<std::mutex> pending_guard(pending_mutex);
    if (active_request_id.load() == request_id) {
        cancel_requested.store(true);
    } else {
        // Pending IDs expire and are bounded even if generate() is never invoked.
        prune_pending();
        auto it = std::find_if(cancelled_pending.begin(), cancelled_pending.end(),
            [request_id](const PendingCancellation &entry) { return entry.id == request_id; });
        if (it == cancelled_pending.end()) {
            if (cancelled_pending.size() >= kMaxPendingCancellations) cancelled_pending.pop_front();
            cancelled_pending.push_back({request_id, Clock::now() + kCancellationTtl});
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_eva1_companion_engines_EvaNativeGguf_release(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> guard(inference_mutex);
    if (cached_model) {
        llama_model_free(cached_model);
        cached_model = nullptr;
        cached_path.clear();
    }
}
