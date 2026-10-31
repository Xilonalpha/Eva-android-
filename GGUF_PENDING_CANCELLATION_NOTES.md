# EVA v2.4.1 — queued JNI cancellation

- Added a pending cancellation registry protected by `pending_mutex`.
- Cancellation requests received while another inference owns `inference_mutex` are remembered and checked before a waiting request starts.
- Activation and cancellation synchronize through the same mutex to avoid losing a cancellation at startup.
- Fixed early JNI string acquisition failures to clear `active_request_id`.
- Static checks: `python tools/verify_source.py` PASS.

## Remaining limitations
- Native llama.cpp dependency is not vendored; native compilation and inference remain unverified.
- The pending cancellation registry can retain IDs when a request never enters native generation.
- The active request still uses one global cancellation flag; only one native inference runs at once.
- No Android device testing performed.
