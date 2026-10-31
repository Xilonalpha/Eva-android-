# EVA v2.4.1 — JNI coroutine cancellation

- The GGUF coroutine now installs a cancellation callback before the blocking JNI generate call.
- Cancellation signals the native atomic flag; the native loop checks it between prompt batches and generated tokens.
- The callback is disposed after JNI returns; coroutine cancellation is rechecked before publishing the reply.
- `release()` remains an explicit JNI API; it is **not** called automatically because another inference may be active or a cached model may be reused.
- Known limitation: JNI cannot interrupt an ongoing `llama_decode` call immediately. This change does not establish that native llama.cpp compiles or works on a device.
- No inference HTTP server was added; model downloads still require network access.

Verification: `python tools/verify_source.py` passed (static checks only).
