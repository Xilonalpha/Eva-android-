# EVA-1 2.4.1 — microphone foreground service

## Changes

- Declares `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE`.
- Registers `EvaWakeWordForegroundService` with `android:foregroundServiceType="microphone"`.
- Starts the service from the explicit user action that enables Hands-Free, while the app activity is visible and after `RECORD_AUDIO` has been granted.
- Promotes the service immediately and displays an ongoing, low-importance notification while local Hey Siri detection is enabled.
- Stops the service when Hands-Free is disabled or the ViewModel is cleared.
- Uses `START_NOT_STICKY`: Android will not silently restart microphone capture after the process is killed.
- Keeps Sherpa KWS and SpeechRecognizer mutually exclusive; the service is the Android lifecycle/foreground anchor, while the existing Sherpa detector owns AudioRecord.

## Android restrictions

- Android 14+ requires both `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE` and checks microphone foreground-service eligibility at start.
- Start is intentionally initiated from a visible user interaction, not from boot or a background timer.
- Android 15+ does not permit starting a microphone FGS from `BOOT_COMPLETED`; this implementation does not attempt that.
- Notification permission is requested by the existing activity flow on Android 13+. If denied, Android may still show the FGS notice in its active-app/task-manager UI rather than the notification drawer.
- The user can stop listening in EVA's Hands-Free control; Android may also stop the service through system controls.

## Validation status

Manifest XML parsed successfully and source-level structural checks passed. An Android APK build was not run in this environment because the archive has no Gradle wrapper JAR and no system Gradle/Android SDK is installed. Device validation is still required for Android 14/15 microphone permission behavior and OEM battery-management policies.
