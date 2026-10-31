# EVA v2.4.1 — Android consolidation / pre-build gate

Base: EVA-v2.4.1-multimedia-download-repair.zip.

## Changes in this stage
- Added `tools/preflight_android.py`: parses the Android manifest, checks essential permissions, the microphone foreground-service declaration, accessibility service permission, Kotlin sources, and native/build preparation inputs. Uses only Python standard library.
- Updated `.github/workflows/android-build.yml`: executes both source consistency and Android preflight checks before downloading native runtimes; runs `:app:testDebugUnitTest` after `:app:assembleDebug` and before publishing the APK artifact.
- Renamed CI artifact to explicitly identify a **compiled** debug APK, not the historical binary bundled in `binaries/`.

## Checks executed here
- `python tools/verify_source.py`: PASS.
- `python tools/preflight_android.py`: PASS (57 Kotlin files; valid manifest).
- `bash -n tools/prepare_llama_cpp.sh tools/prepare_sherpa_kws.sh`: PASS.
- XML parse of `app/src/main/AndroidManifest.xml`: PASS.

## Not executed / unresolved
- No Android SDK, NDK, Gradle executable or network dependency resolution available in this environment; no new APK was compiled.
- CI has not yet been run on GitHub. Native llama.cpp / Sherpa downloads, Android compilation, and JVM unit tests can still fail; a CI green build is required before calling this release build-verified.
- WhatsApp, YouTube, microphone, games, and AI multimedia require device-level acceptance tests; source checks do not verify real-world app behavior.
- `binaries/EVA1-v2.0-2026.apk` is an old artifact and **must not** be treated as a build of v2.4.1.
