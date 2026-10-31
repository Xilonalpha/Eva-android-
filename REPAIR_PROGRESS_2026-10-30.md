# EVA — etapă de reparare, 30 octombrie 2026

## Modificat
- Eliminată configurarea host/port/protocol Ollama/llama.cpp din strategul jocurilor.
- Eliminat panoul UI pentru server, ping și dialogul Host/Port.
- Testul de disponibilitate solicită acum o inferență scurtă motorului OfflineLlmEngine, în loc să întoarcă permanent false.
- Păstrate mecanismele de decizie și control deja existente; în lipsa inferenței, se folosește WAIT.

## Limite importante
- NU este integrată biblioteca nativă llama.cpp/JNI. GGUF nu este funcțional doar prin această modificare.
- Funcțiile multimedia, voce, WhatsApp și YouTube nu au fost validate sau complet reparate în această etapă.
- Nu s-a compilat APK-ul; testul static existent eșuează pe o aserțiune pentru textul «KWS low-power».
- Comunicațiile Mesh și domeniile localhost folosite în alte subsisteme nu au fost eliminate arbitrar.
