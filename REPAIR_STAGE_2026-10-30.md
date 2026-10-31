# EVA v2.4.1 — actualizare verificabilă

Modificări aplicate:
- Șabloane explicite MISTRAL_INST și PHI3_INST în GgufOnDeviceEngine.kt; catalogul Mistral Nemo și Phi-3.5 folosește șabloanele respective.
- În eva_gguf_jni.cpp, schimbarea modelului încarcă întâi modelul nou, iar la eșec îl păstrează pe cel anterior.
- Verificatorul tools/verify_source.py: PASS.

Limitări / restanțe:
- Nu s-a compilat codul JNI: sursele llama.cpp lipsesc din arhivă și mediul nu poate accesa github.com; Android NDK/Gradle nu sunt instalate.
- Șabloanele manuale nu garantează compatibilitatea tuturor versiunilor GGUF; ideal se va folosi șablonul din metadatele modelului și llama_chat_apply_template.
- Nu s-au modificat în această etapă microfonul, WhatsApp, YouTube, jocurile sau multimedia; aceste funcții NU sunt declarate finalizate.
- Aplicația nu a fost testată pe telefon.
