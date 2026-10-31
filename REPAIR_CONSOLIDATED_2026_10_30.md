# EVA v2.4.1 — Consolidated source fixes

Base: EVA-v2.4.1-whatsapp-observable-status.zip.

## Changes
- EvaViewModel.kt: removed fabricated default WhatsApp message in natural-language command parsing. Explicit recipient and message are required before launching the WhatsApp composer.
- OsScreen.kt: WhatsApp confirmation now snapshots the recipient and message shown to the user. Later changes to text fields cannot silently alter the approved payload.
- OsScreen.kt: empty recipient/message cannot open the confirmation dialog.
- OsOrchestratorEngine.kt: standalone 11-character YouTube video identifiers can open directly.
- Existing guarded Accessibility WhatsApp send and status flow are retained.
- Existing voice, game, image, music, video and GGUF engines are retained.

## Verification
- `python tools/verify_source.py`: PASS (static checks only).
- No Android SDK/NDK or runnable device in this environment; APK compilation, media playback confirmation, WhatsApp delivery, voice lifecycle and gameplay runtime remain unverified.
- YouTube `ACTION_MEDIA_PLAY_FROM_SEARCH` is not guaranteed to start playback in the YouTube app; successful intent dispatch is not playback confirmation.
- WhatsApp Accessibility button click is not evidence of server acceptance or delivery.

## Remaining blockers
- Runtime build and instrumentation/device verification of WhatsApp, YouTube, microphone, games and multimedia.
- End-to-end verified automatic playback by title depends on supported YouTube APIs/UI and app behavior; no fake confirmation has been introduced.
- GGUF native dependency and build are intentionally deferred.
