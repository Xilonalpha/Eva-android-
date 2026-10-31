# EVA v2.4.1 — JNI Unicode output fix

## Fixed
The native inference bridge previously used `NewStringUTF(result.c_str())` on arbitrary UTF-8 output from llama.cpp. JNI `NewStringUTF` requires **modified UTF-8**, which is not identical to ordinary UTF-8 (notably supplementary Unicode characters). The bridge now validates/decodes UTF-8 into UTF-16 and constructs a Java string via `NewString`.

## Verified
`python tools/verify_source.py` passes. No Ollama/llama.cpp HTTP server was introduced.

## Not verified
Native compilation against the pinned llama.cpp revision is blocked because the vendored source is absent and github.com cannot be resolved in this environment. Android NDK/Gradle are not installed here. The current build configuration still requires `tools/prepare_llama_cpp.sh` to fetch the dependency. This is not a validated APK.

## Next priority
Obtain the pinned llama.cpp source and Android NDK, build the JNI library, then fix actual compiler/API errors. Afterward test Unicode output, model switching, cancellation and prompt formats.
