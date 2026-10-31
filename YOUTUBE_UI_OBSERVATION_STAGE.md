# YouTube UI observation stage

- Added a 12-second asynchronous observation window after successfully launching YouTube video or media-search intent.
- Reads the current foreground package and accessible control descriptions through existing EvaAccessibilityService.
- Reports a Pause control as a UI hint only, not as proof of audible playback, decoding, or completion.
- Reports missing Accessibility, absence of YouTube foreground, and no detectable UI playback cue.
- Exposes observation through StateFlow in OsScreen, preserving existing engines and GGUF untouched.
- Search fallback does not claim to start playback.

Verification: `python tools/verify_source.py` PASS (static only). No Android SDK/NDK build or device test performed. Further work required for reliable YouTube playback, WhatsApp delivery confirmation, voice, games, and multimedia end-to-end testing.
