# EVA-1 2.4.1 — Sherpa-ONNX native wake-word integration

## What is integrated

- Sherpa-ONNX `v2.0.1` Kotlin API and Android JNI runtime, pinned to upstream.
- English GigaSpeech Zipformer KWS model `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`.
- `AudioRecord` capture at 16 kHz, mono, PCM16 in 100 ms blocks.
- CPU provider with one inference thread to keep background work bounded.
- Custom keyword stream `HEY SIRI`, with a short retrigger guard.
- Exclusive microphone handoff: KWS listens for the wake phrase; Android SpeechRecognizer listens for the Romanian command; both are stopped while EVA speaks.
- Automatic SpeechRecognizer fallback if the native runtime/model is unavailable.
- The runtime and model are fetched from official upstream release assets by `tools/prepare_sherpa_kws.sh`; no placeholder model or fake JNI library is created.

## Build

The Gradle `preBuild` task invokes `tools/prepare_sherpa_kws.sh`. It downloads the pinned upstream Kotlin API, Android arm64 JNI libraries and KWS model, then validates every required file. GitHub Actions runs the same preparation step before `:app:assembleDebug`.

For manual builds, run from the project root:

```bash
bash tools/prepare_sherpa_kws.sh
gradle --no-daemon :app:assembleDebug
```

The first build requires internet access. The generated API sources, JNI libraries and model assets are intentionally git-ignored and reproducibly fetched at build time.

## Important operational limits

- The wake-word detector is local/offline after assets are installed.
- The current integration is active while the EVA app process is running and has microphone access. It does **not** claim guaranteed always-on operation after Android kills the app or while the device is in deep background. That requires a dedicated microphone foreground service, a persistent notification, Android 14+ foreground-service permissions, and device-specific battery-policy testing.
- The selected acoustic model is English GigaSpeech. It can recognize the English phrase `HEY SIRI`, but it is not a Romanian-trained acoustic model. Romanian accents, pronunciation variants, television audio and false-trigger rate must be measured on the target phone. The fallback Android recognizer remains available.
- CPU use, battery use and detection latency are not certified until tested on a physical Android device.

## Upstream

- https://github.com/k2-fsa/sherpa-onnx
- https://k2-fsa.github.io/sherpa/onnx/kws/index.html
- https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html

Sherpa-ONNX is distributed under Apache-2.0. Preserve upstream license notices when redistributing its source/runtime.
