# EVA-1 2.4.1 — microphone foreground service integration

This release adds the Android foreground-service lifecycle required for the opt-in local Hey Siri wake-word listener.

## Implemented

1. Added `EvaWakeWordForegroundService` with a persistent, low-importance notification.
2. Declared `android.permission.FOREGROUND_SERVICE_MICROPHONE` and the service's `microphone` foreground type.
3. Starts the service only after the user enables Hands-Free from the visible app and `RECORD_AUDIO` is granted.
4. Stops the service when Hands-Free is disabled and when the ViewModel is cleared.
5. Uses `START_NOT_STICKY` to avoid silently restarting microphone capture after process death or reboot.
6. Bumped app version to 2.4.1 / versionCode 8 and updated the GitHub Actions artifact name.

## Important architecture detail

The service is the Android foreground/lifecycle anchor; `SherpaKwsWakeWordDetector` continues to own the native Sherpa KWS instance and `AudioRecord`. It pauses when SpeechRecognizer or TTS takes the microphone. The service does not claim that Android permits invisible, unrestricted, always-on microphone access.

## Validation

- AndroidManifest.xml parsed successfully.
- Kotlin source structural checks (balanced braces/parentheses) passed.
- APK compilation and device execution were not available in this environment. Do not treat this as a successful Android build until GitHub Actions or a local Android SDK build completes.
