# EVA v2.4.1 — game gesture feedback repair

Base: EVA-v2.4.1-voice-lifecycle-repair.zip.

Changes in EvaAccessibilityService.kt:
- Each dispatched game gesture receives a monotonic feedback sequence; callbacks and delayed visual observations from older gestures cannot overwrite the latest gesture status.
- If the Android screenshot callback never arrives, the floating EVA HUD is restored after the screenshot timeout.

Existing game vision, decision, gesture implementations, AI models, voice, WhatsApp, YouTube and GGUF are preserved.

Verification: `python tools/verify_source.py` passes. This is static verification only. Android build and physical gameplay tests have not been run. A changed frame is only a visual signal, not proof that the move succeeded.
