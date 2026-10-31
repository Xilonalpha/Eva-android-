# EVA v2.4.1 — JNI UTF-16 input conversion

Changed `app/src/main/cpp/eva_gguf_jni.cpp`:
- Convert Java UTF-16 prompt and model path into standard UTF-8 instead of JNI Modified UTF-8.
- Properly combine surrogate pairs (emoji and other non-BMP characters); replace malformed lone surrogates with U+FFFD.
- Release Java UTF-16 buffers and handle JNI conversion exceptions.
- Synchronize native completion state with the pending-cancellation mutex to avoid a completion/cancel race.

Validation: `python tools/verify_source.py` PASS (static only).
No Android NDK / pinned llama.cpp checkout available here; no C++ compilation or GGUF inference claimed.
This patch does not change Mesh transport and does not add an HTTP inference server.
