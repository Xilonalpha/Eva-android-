# EVA 2.4.1 — GGUF tokenization context correction

The JNI tokenizer now uses an explicitly allocated 4096-token buffer and checks the actual returned count. This avoids relying on ambiguous zero-capacity probing behavior in different llama.cpp revisions. Prompts that fill or exceed the configured context are rejected before decoding.

Static source verification: PASS (`python tools/verify_source.py`).

Not verified: native C++ build, Gradle APK build, model execution, Android device behavior. llama.cpp sources are not bundled; `tools/prepare_llama_cpp.sh` requires network access to fetch the pinned upstream tag. No HTTP inference server was introduced. Mesh network code remains untouched.
