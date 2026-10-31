# EVA v2.4.1 — execution fixes

Modified `app/src/main/java/com/eva1/companion/engines/OsOrchestratorEngine.kt`:
- WhatsApp no longer silently falls back to a browser when its installed app cannot open. A public ACTION_VIEW intent prepares, but does not send or verify delivery of, a WhatsApp message.
- Directly entered phone numbers require an explicit international prefix; removed erroneous implicit Romanian conversion.
- YouTube explicit watch, Shorts, Live, Embed and youtu.be links resolve to validated video IDs and open in installed YouTube app.
- YouTube search and media-play intent report their actual limitations rather than claiming playback verification.

Preserved all other AI engines, models, game, voice and multimedia source code. GGUF untouched.

`python tools/verify_source.py`: PASS (static checks only).

NOT COMPLETE: WhatsApp auto-send/delivery confirmation, actual YouTube playback confirmation, Android mic and game runtime tests, and cloud media API provisioning. Android build was not executed. Do not label this as a production-ready build.
