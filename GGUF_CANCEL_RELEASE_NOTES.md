# GGUF JNI – incremental maintenance (2026-10-30)

Changes: JNI cancel() uses a nonblocking atomic cancellation flag, polled during prompt chunk decoding and token generation; release() frees the cached model under the inference mutex; token-to-piece handles both positive and negative buffer-size conventions.

Caveats: cancel() is cooperative, not an interrupt of a single long llama_decode call. A new generate() resets cancellation; concurrent requests are serialized by inference_mutex. No automatic coroutine cancellation wiring has been added. No native build or Android runtime inference test was performed. llama.cpp sources are fetched at build preparation time and are not vendored in this archive. The version-pinned llama.cpp API compatibility remains unverified.

Static check: python tools/verify_source.py passed. No HTTP inference server was added.
