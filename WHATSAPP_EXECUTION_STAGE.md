# EVA v2.4.1 — WhatsApp execution safety

- Added `EvaAccessibilityService.clickExactResourceInForeground`: strictly checks foreground package, exact resource ID, enabled/visible/clickable node. No fuzzy text matching or coordinate fallback.
- Added `OsOrchestratorEngine.confirmAndPressWhatsAppSend(userConfirmedRecipientAndText)`: explicit, separately callable action requiring a true confirmation from UI. It does **not** run automatically after opening a chat. Must be wired to a visible confirmation UI before production use.
- Contact lookup now prefers Android `NORMALIZED_NUMBER` when available; no guessing national prefixes.
- Existing model and multimedia integrations unchanged; GGUF untouched.
- `tools/verify_source.py`: PASS. No Android build or device execution performed.
- Remaining: wire confirmation UI, YouTube playback observation, full voice/game/media runtime tests, Android build, GGUF native validation.
