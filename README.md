# EVA-1 2.2.0

Proiect Android EVA-1. Include arhitectura originală, actualizată cu runtime-ul LiteRT-LM real pentru modele `.litertlm`, viziune multimodală Gemma 4 pentru cadre de joc, verificare conservatoare a acțiunilor și raportare corectă a stării runtime-urilor.

## Build

Deschide proiectul în Android Studio / Google AI Studio sau folosește workflow-ul GitHub Actions:

`.github/workflows/android-build.yml`

Acesta construiește `:app:assembleDebug` și publică APK-ul ca artifact.

## Modele locale

Din ecranul Offline LLM poți descărca separat:
- Qwen3 0.6B LiteRT-LM (aprox. 586 MB)
- Gemma 4 E2B LiteRT-LM (aprox. 2.58 GB)

Modelele sunt descărcate în spațiul privat al aplicației și nu sunt incluse în APK/ZIP. GGUF rămâne separat și cere llama.cpp.

## Voce și automatizare jocuri

Hey Siri este un prefix de comandă verificat prin Android SpeechRecognizer, cu preferință best-effort pentru recunoaștere offline; nu este încă un detector low-power permanent. Vocea este redată prin Android TextToSpeech și folosește o voce românească offline dacă este instalată.

Gemma 4 poate primi cadrul real al jocului; EVA nu execută gesturi cu încredere insuficientă. Un gest acceptat de Android nu este echivalent cu succesul în joc.

Vezi `TECHNICAL_UPGRADE_REPORT.md` pentru schimbări, limite și verificări.


## EVA-1 2.3.0 — voice/game integration notes

- Optional neural Romanian TTS: install the official Sherpa-ONNX Android TTS Engine build for `ro_RO-mihai-medium` from https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html. EVA detects the provider on next process start and prefers it; otherwise it uses Android TTS.
- Hey Siri currently uses Android SpeechRecognizer continuous listening, not low-power Sherpa KWS. This is explicitly reported in the voice status.
- Game actions expose post-gesture feedback. A changed frame is not proof that the move was correct; semantic verification is still game-adapter-specific.
- See `TECHNICAL_UPGRADE_REPORT_2.3.0.md` for limitations and build status.

## EVA-1 2.4.0 — Sherpa-ONNX Hey Siri wake-word engine

The hands-free path now prefers a native Sherpa-ONNX KWS engine instead of continuously running Android SpeechRecognizer to detect the wake phrase. The KWS runtime/model are fetched from pinned official upstream releases during `preBuild` by `tools/prepare_sherpa_kws.sh`. Once the phrase is detected, EVA releases the KWS microphone capture and starts the existing Romanian command-recognition path. If native assets cannot be loaded, Android SpeechRecognizer remains the fallback.

See [SHERPA_KWS_INTEGRATION.md](SHERPA_KWS_INTEGRATION.md) for build requirements, upstream licenses and limitations. The first build needs internet access to fetch the pinned native runtime, Kotlin API and model. This version targets the arm64-v8a ABI for the phone build.


## EVA-1 2.4.1 — microphone foreground service

Hands-Free now starts a microphone-type Android foreground service from the visible app after microphone permission is granted. A persistent notification indicates that Hey Siri listening is active. Disabling Hands-Free stops the service. The service uses `START_NOT_STICKY`; it does not silently restart microphone access after process death or device reboot. See `FOREGROUND_MICROPHONE_SERVICE_2.4.1.md` for Android 14/15 restrictions and validation status.
