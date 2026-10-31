# EVA-1 2.3.0 — voce Piper, wake-word și verificare jocuri

## Modificări aplicate peste proiectul 2.2.0

### Piper / Sherpa-ONNX TTS
- `EvaTtsEngine` detectează serviciul Android TTS upstream cu package id `com.k2fsa.sherpa.onnx.tts.engine`.
- Dacă motorul Sherpa-ONNX este instalat, EVA încearcă să-l folosească pentru vocea românească Piper; dacă inițializarea eșuează, revine la motorul Android existent.
- Setările EVA afișează instrucțiunea pentru instalarea pachetului oficial `ro_RO-mihai-medium` și deschid pagina upstream cu APK-urile TTS Engine.
- Aceasta este integrare prin Android TTS provider. Sherpa JNI și modelul vocal nu sunt incluse în APK-ul EVA; motorul Piper se instalează separat. Upstream oferă APK ARM64 dedicat pentru română.

### Hey Siri / wake-word
- Expresia Hey Siri rămâne activă.
- Statusul nu mai prezintă SpeechRecognizer drept detector low-power. Aplicația comunică explicit că actualul fallback este ascultare continuă Android SpeechRecognizer și că preferința offline poate fi ignorată de provider.
- Detectorul permanent Sherpa KWS nu a putut fi livrat ca motor încorporat: mediul acestei sesiuni nu a putut descărca arhiva upstream cu JNI/ONNX pentru Android (DNS GitHub indisponibil). Fără `libsherpa-onnx-jni.so`, `libonnxruntime.so`, API-ul Kotlin/Java compatibil și modelul KWS, nu este corect să declarăm că Hey Siri funcționează prin keyword spotting low-power.
- Android SoundTrigger nu este substitut universal: modelul hotword trebuie furnizat/enrolled de sistem/OEM și nu poate fi înregistrat liber de o aplicație obișnuită.

### Control jocuri
- `EvaAccessibilityService` raportează distinct acceptarea gestului de către Android, callback-ul de completare, primirea unui cadru nou și schimbarea vizuală observată.
- Verificarea nu pretinde că schimbarea cadrului înseamnă mutare corectă sau victorie. Fără MediaProjection, acțiunea rămâne `UNVERIFIED`.
- `OsOrchestratorEngine` acordă timp pentru cadrul post-acțiune și jurnalizează starea `POST-ACTION`.
- Adaptorul Tetris limitează vocabularul la MOVE_LEFT / MOVE_RIGHT / ROTATE / DROP / WAIT și cere grilă/piesă vizibile înainte de acțiune.
- Adaptorul Klondike interzice coordonatele speculative din LLM și lasă mutările motorului Solitaire dedicat deja existent.
- Când nu există motor LLM local, nu se mai aplică gesturi generice FIRE/ATTACK jocurilor Tetris sau Solitaire.

## Build
- Versiune: 2.3.0, versionCode 6.
- Gradle Wrapper JAR și Android SDK lipsesc din mediul de execuție curent; distribuția Gradle nu a putut fi descărcată din cauza rezolvării DNS.
- Prin urmare, build-ul Android nu a fost executat și APK-ul nu este certificat.

## Surse upstream
- Sherpa-ONNX TTS Engine: https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html
- Model românesc Piper Mihai: https://k2-fsa.github.io/sherpa/onnx/tts/all/Romanian/vits-piper-ro_RO-mihai-medium.html
- Sherpa-ONNX KWS Android: https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxKws
- Build JNI Android: https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html
