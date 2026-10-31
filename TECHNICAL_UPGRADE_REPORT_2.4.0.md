# EVA-1 2.4.0 — Sherpa-ONNX KWS integration report

## Implemented in source

1. Added `SherpaKwsWakeWordDetector` using the official Sherpa-ONNX Kotlin API.
2. Added 16 kHz mono PCM16 `AudioRecord` streaming, 100 ms frame blocks and CPU inference with one thread.
3. Configured the official English GigaSpeech Zipformer KWS model and custom `HEY SIRI` keyword stream.
4. Added a two-second retrigger guard and deterministic stream/native cleanup.
5. Updated `VoiceAssistantEngine`: native KWS is preferred for wake-word detection; after detection it stops KWS and hands the microphone to the existing Romanian SpeechRecognizer command path. The microphone is paused while EVA speaks and KWS resumes after the utterance.
6. Added a safe SpeechRecognizer fallback when permission, model files, native runtime or KWS initialization is unavailable.
7. Added an idempotent preparation script that fetches pinned Sherpa-ONNX v2.0.1 Kotlin sources, arm64-v8a JNI libraries and the official KWS model. It validates all files and fails the build rather than creating fake placeholders.
8. Added Gradle `preBuild` dependency and GitHub Actions preparation step. Updated app version to 2.4.0 / versionCode 7.

## Important limitations

- The KWS acoustic model is English GigaSpeech, not a Romanian-trained wake-word model. The target phrase is English `HEY SIRI`; performance with Romanian pronunciation/accent requires real handset testing and threshold tuning.
- This in-process microphone detector is active while EVA's process is alive and microphone access is permitted. It is not a guaranteed always-on service after Android kills the app or while the app is deep in background. A microphone foreground service and OEM battery-policy testing remain a separate step.
- This execution environment has no DNS/network access, so the pinned Sherpa assets could not be downloaded here and the Android build could not be run. GitHub Actions downloads them before compiling. No APK build success is claimed.

## Verification performed here

- Shell syntax validation of `tools/prepare_sherpa_kws.sh`.
- Structural delimiter checks for modified Kotlin files.
- Reviewed integration against upstream Sherpa KWS Android sample/API and model documentation.
- ZIP integrity check performed after packaging.

## Official upstream references

- https://github.com/k2-fsa/sherpa-onnx
- https://k2-fsa.github.io/sherpa/onnx/kws/index.html
- https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html
- https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html
