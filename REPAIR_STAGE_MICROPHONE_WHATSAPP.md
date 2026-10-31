# EVA v2.4.1 — verificare incrementală

Modificări efectuate:
- `EvaWakeWordForegroundService.stop`: `stopService` în loc de `startService` cu ACTION_STOP, evitând pornirea unui serviciu din fundal doar pentru oprire.
- Notificarea foreground nu mai afirmă că detectorul KWS rulează atunci când serviciul este doar un anchor al ciclului de viață.
- `OsOrchestratorEngine.extractPhoneNumber`: respinge numele cu cifre și intrările care nu arată ca numere de telefon; păstrează regula RO pentru `07...`.

Validare: `python tools/verify_source.py` — PASS. Aceasta este verificare statică, nu compilare Android.

Neîncheiate: compilare Android/NDK, inferență GGUF nativă, confirmare efectivă WhatsApp/YouTube, sincronizarea completă a capturii audio, validare jocuri și generare neurală locală multimedia.
