# EVA v2.4.1 — verificare 2026-10-30

## Schimbare în această etapă
- Am explicitat limita maximă de tokenuri a promptului (`kContext - 1`) în JNI, păstrând loc pentru generare.
- Am păstrat restul surselor și configurația fără server HTTP LLM.

## Verificări executate
- `python tools/verify_source.py`: PASS (verificări statice, nu compilare).
- `git ls-remote https://github.com/ggml-org/llama.cpp.git HEAD`: nu a putut rezolva domeniul GitHub în mediul curent.
- Android SDK/NDK și sursele llama.cpp nu sunt prezente în mediul de execuție; compilarea JNI nu a fost efectuată.

## Blocaje reale rămase
- Compilarea și compatibilitatea API llama.cpp pinned b8182.
- Validarea șabloanelor de chat din metadatele GGUF, nu numai a celor hardcodate.
- Testarea reală a comutării modelelor, a consumului de memorie și a inferenței.
- Verificarea comportamentelor microfonului, WhatsApp, YouTube, jocurilor și multimedia pe Android.

Nu este un APK final validat. Nu declarați funcționalități neprobate drept finalizate.
