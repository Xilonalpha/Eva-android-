# YouTube UI observation reliability

Changed `OsOrchestratorEngine.kt`:
- Requires two distinct, consecutive YouTube snapshots containing a Pause label before reporting the UI indicator.
- Discards stale/duplicate snapshot timestamps.
- Stops observing after two consecutive snapshots of another foreground app once YouTube was seen.
- Keeps playback/audio identity explicitly unverified.

`python tools/verify_source.py`: PASS. No Android build or device test executed. GGUF unchanged.
