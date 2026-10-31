# EVA v2.4.1 — Voice lifecycle repair

Source: `app/src/main/java/com/eva1/companion/engines/VoiceAssistantEngine.kt`.

- SpeechRecognizer state becomes LISTENING only after Android invokes `onReadyForSpeech`; before that the UI reports initialization.
- Missing RECORD_AUDIO permission and unavailable recognition service stop the logical assistant, reset state to OFF and prevent misleading active-listening indicators.
- `startListening()` exceptions now report the real error and stop the logical session.
- Permanent recognition errors and repeated failures stop the retry loop, reset the recognizer and require user reactivation.
- Existing Sherpa wake-word and TTS pause paths preserved. GGUF unchanged.

Validation: `python tools/verify_source.py` PASS (static only). No APK compilation or device test was performed. WhatsApp/YouTube/game/multimedia device verification is still outstanding.
