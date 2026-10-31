# EVA-1 2.2.0 — integrare locală AI și corectarea controlului jocurilor

## Ce a fost integrat în sursă

### 1. Runtime AI real: LiteRT-LM
- Adăugat runtime-ul Android oficial `com.google.ai.edge.litertlm:litertlm-android`.
- Creat `LiteRtLocalLlmEngine.kt` cu descărcare, progres, verificare minimă de dimensiune, stocare în `filesDir`, inițializare în fundal, conversație persistentă și streaming de tokenuri.
- Backend GPU este încercat primul; la eșec se încearcă CPU.
- Modele disponibile separat de catalogul GGUF:
  - Qwen3 0.6B LiteRT-LM (aprox. 586 MB; text).
  - Gemma 4 E2B LiteRT-LM (aprox. 2.58 GB; intrare multimodală imagine + text pe versiunea de model compatibilă).
- Interfața Offline LLM include descărcarea și încărcarea explicită a acestor modele. Fișierele nu sunt incluse în APK sau în ZIP.
- `OfflineLlmEngine` direcționează prompturile către LiteRT-LM când este selectat un model `litert:`.
- Modelele GGUF rămân un format separat și continuă să necesite un runtime llama.cpp compatibil.

Documentație: https://developers.google.com/edge/litert-lm/android

### 2. Corectarea răspunsurilor AI simulate
- Catalogul de 11 modele existente a fost corectat pentru a nu declara capabilități Vision, video, WhatsApp, jocuri sau generare media pentru modele text-only.
- Aliasurile false EVA Omni / Flux-Wan au fost înlocuite cu modelele GGUF reale Qwen 2.5 7B și Qwen 2.5 3B.
- Pe traseul GGUF/WebLLM, lipsa runtime-ului sau timeout-ul nu mai produce automat un răspuns șablon prezentat drept inferență AI.
- Statusul și rata de tokenuri nu mai pretind că un model este încărcat doar pentru că s-a inițializat interfața.

### 3. Viziune reală pentru jocuri
- Dacă este selectat Gemma 4 E2B LiteRT-LM, cadrul capturat de pe ecran este comprimat JPEG și transmis modelului prin intrare multimodală reală `ImageBytes`.
- Promptul cere acțiune, coordonate, motiv și încredere.
- Orice recomandare sub 0.75 încredere sau fără încredere explicită devine `WAIT_OBSERVE`; nu se execută o atingere ghicită.
- Gesturile sunt limitate la durate rezonabile.
- Jurnalul diferențiază acum acceptarea gestului de către Android de succesul efectiv în joc. Un `dispatchGesture=true` nu dovedește că jocul a reacționat.
- Eliminată afirmația falsă că bucla poate garanta 30 FPS / reacții end-to-end de 35 ms. Captura Android și jocurile au latențe proprii.

Limită: nu există un agent universal care să joace corect orice joc. Pentru jocuri externe încă sunt necesare adaptoare specializate pe gen/joc, capturi proaspete și verificarea schimbării stării după fiecare acțiune. Pentru jocurile multiplayer trebuie respectate regulile jocului și anti-cheat-ul.

### 4. Voce și Hey Siri
- Păstrată expresia de activare Hey Siri / Hei Siri.
- Android SpeechRecognizer solicită acum, best-effort, recunoaștere offline (`EXTRA_PREFER_OFFLINE`). Furnizorul Android poate ignora această preferință.
- Îmbunătățită segmentarea TTS pentru a nu rupe cuvintele în mijlocul propoziției.
- TTS existent folosește în continuare Android TextToSpeech și preferă o voce românească locală dacă există.

Limită importantă: nu este integrat încă un detector low-power permanent Hey Siri și nici un motor neural Piper/Sherpa-ONNX încorporat. Pentru acestea trebuie incluse bibliotecile native Android pentru ABI-urile țintă și modelul ONNX vocal + fișierele auxiliare. Nu am etichetat Android TTS drept Piper sau voce neurală.

### 5. Build Android
- Versiune sursă: `2.2.0`, versionCode `5`.
- Adăugat un debug keystore standard, necesar configurației existente `debugConfig`.
- Adăugat workflow GitHub Actions `.github/workflows/android-build.yml` care instalează JDK/Gradle/Android SDK și construiește `:app:assembleDebug`, apoi publică APK-ul ca artifact.

## Verificări executate în această sesiune
- ZIP-ul original a fost extras și modificările au fost aplicate în proiectul său complet.
- Verificare statică a modificărilor și consistenței identificatorilor.
- Kotlin compiler a fost folosit pentru verificare sintactică preliminară; compilarea izolată nu poate rezolva dependențele Android/LiteRT fără Gradle/SDK.

## Ce NU a putut fi confirmat
- Nu a fost posibilă compilarea Android aici: mediul nu are Android SDK, Gradle sau Gradle Wrapper JAR, iar rezolvarea DNS pentru descărcarea distribuției Gradle a eșuat.
- Prin urmare, nu există un APK 2.2.0 compilat/verificat în această sesiune. Workflow-ul GitHub Actions este inclus pentru a efectua build-ul real după încărcarea proiectului într-un repository.
- Nu au fost incluse în arhivă fișierele model de 586 MB / 2.58 GB și nici modelul vocal ONNX.

## Referințe tehnologice
- LiteRT-LM Android: https://developers.google.com/edge/litert-lm/android
- LiteRT-LM overview și modele: https://developers.google.com/edge/litert-lm/overview
- Sherpa-ONNX Android / native libraries: https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html
- Sherpa-ONNX Romanian Piper Mihai: https://k2-fsa.github.io/sherpa/onnx/tts/all/Romanian/vits-piper-ro_RO-mihai-medium.html
- Piper Romanian voices și licențe: https://github.com/rhasspy/piper/blob/master/VOICES.md
