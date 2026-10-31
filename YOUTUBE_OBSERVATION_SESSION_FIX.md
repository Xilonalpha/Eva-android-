# EVA v2.4.1 — YouTube observation session fix

- Added a monotonically increasing request sequence to invalidate observation results from older playback requests.
- Every UI observation checks the sequence before publishing a result; cancelled older checks cannot overwrite the newer request's status.
- Reads both contentDescription and visible text of accessibility nodes for the pause-control hint.
- Does not claim that a pause control proves requested video identity or audible playback.
- Preserved existing WhatsApp, microphone, games, multimedia, GGUF code.
- `python tools/verify_source.py`: PASS. This is static only; no APK build or Android runtime test performed.

Remaining: actual YouTube media-session verification, Android device validation, WhatsApp delivery verification, game runtime testing, multimedia provider credentials and runtime tests, and GGUF native build.
