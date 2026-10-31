# EVA GGUF JNI — next repair

Changes: prompt evaluation now uses chunks bounded by n_batch (512), rather than passing up to 4095 prompt tokens in a single batch. Added null JNI input checks, finite temperature validation, greedy sampler for zero temperature, and capped generated tokens to the context window. Updated static verifier to inspect the new invariants.

Not verified: llama.cpp API/NDK compilation, actual model loading, model switching, latency and memory. The native dependency is not bundled; `tools/prepare_llama_cpp.sh` must fetch it. The C++ backend currently loads and frees the model for every request; caching and chat templates remain future work. No server is needed for the GGUF path.
