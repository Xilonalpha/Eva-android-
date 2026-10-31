# GGUF JNI tokenizer fix — 2026-10-30

Corrected a real bug in `eva_gguf_jni.cpp`: the code assumed `llama_tokenize(..., nullptr, 0, ...)` must return a negative required token count. The llama.cpp API can return a positive count when the provided output capacity is sufficient, and behavior should not be misclassified as an error. The code now accepts either sign and allocates accordingly, while rejecting zero and oversized inputs.

Also preserved a pending cancellation request at the start of `infer` instead of silently clearing it. This does not solve the broader cross-request cancellation race: the global cancel flag may still affect a queued request. A per-request cancellation token is preferable before concurrent production use.

Verification: `python tools/verify_source.py` passes. Native compilation remains **unverified**, because llama.cpp sources are not bundled and github.com could not be resolved from this environment. No claims of a working APK or completed inference are made.
