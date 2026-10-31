# GGUF pending cancellation cleanup

This revision bounds the native cancellation tombstone queue to 1024 IDs and expires records after 10 minutes. Records are consumed when the corresponding generate request enters JNI, including after waiting for the inference mutex. The active request still uses its separate cancellation flag. This prevents indefinite growth when Kotlin cancels before JNI is entered.

Limitation: when more than 1024 unique cancelled requests accumulate without reaching JNI, oldest tombstones are evicted, so an extremely delayed request may not observe its cancellation. This is a defensive memory bound, not a durable queue. No Android/llama.cpp native compilation was performed. Static checks passed.
