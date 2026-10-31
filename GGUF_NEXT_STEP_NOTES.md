# EVA v2.4.1 — JNI model lifetime update

- Native GGUF model is cached by file path across generation requests, avoiding reload on every prompt.
- Changing model path frees the old model before loading the new one.
- Per-request llama_context and sampler are still created/freed per request, preventing stale conversation KV state.
- llama_backend_init is now process-lifetime rather than called for each inference.
- Generation no longer decodes the final token unnecessarily.
- `python tools/verify_source.py` passes (static checks only).

## Important remaining limitations
- `llama.cpp` sources are NOT bundled in this archive; run `tools/prepare_llama_cpp.sh` before attempting an Android build. JNI ABI/API compatibility is not compile-verified.
- Cached model memory remains allocated until a different model is selected or process exit. Explicit unload, cancellation, streaming, model chat-template support and native build validation are pending.
- This does not resolve microphone, WhatsApp, YouTube, media generation or game end-to-end testing.
- No HTTP Ollama / llama.cpp inference server has been added.
