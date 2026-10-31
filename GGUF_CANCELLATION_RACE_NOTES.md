# GGUF JNI cancellation race correction

The previous native implementation reset the shared cancellation flag inside infer(), which could erase a cancellation received just after the JNI call became active. It also allowed a late cancellation when no inference was active to contaminate the next request.

This revision clears stale cancellation immediately before marking inference active, only accepts cancel() while a native request is active, and clears the flag after completion. The inference mutex continues to serialize native generate/release operations.

Limitations: cancellation is still a single shared flag, not a per-request ID; cancellation of a queued Kotlin coroutine can still target a different active request. This needs a request-specific native handle/API for strict concurrency correctness. No Android NDK compilation or runtime validation was performed.
