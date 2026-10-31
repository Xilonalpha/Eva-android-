# EVA v2.4.1 — YouTube observation reliability

Changes to `OsOrchestratorEngine.kt`:
- `youtubeRequestSequence` is now an `AtomicLong`, avoiding data races between the UI and coroutine observation.
- The Accessibility service has a 3-second startup grace period rather than immediate failure.
- A transient snapshot exception no longer aborts the entire observation; the next sample is attempted.
- Search fallback cancels playback observation and explicitly labels results as a search, not playback.

Validation: `python tools/verify_source.py` (static PASS). No Android compilation, actual YouTube playback confirmation, or device testing performed. GGUF unchanged.
