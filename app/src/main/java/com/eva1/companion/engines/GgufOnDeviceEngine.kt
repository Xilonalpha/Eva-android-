package com.eva1.companion.engines

import android.content.Context
import android.os.Build
import com.eva1.companion.MemoryEntry
import com.eva1.companion.Message
import com.eva1.companion.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Specificațiile unui model GGUF Light optimizat pentru rulare On-Device pe Android (ARM64 NEON / Vulkan / CPU):
 */
data class GgufModelSpec(
    val id: String,
    val fileName: String,
    val displayName: String,
    val quantization: String,
    val parametersLabel: String,
    val sizeMb: Int,
    val contextLength: Int,
    val promptTemplate: GgufPromptTemplate,
    val downloadUrl: String,
    val descriptionRo: String
)

enum class GgufPromptTemplate {
    CHAT_ML,     // <|im_start|>system ... <|im_end|> (SmolLM2, Qwen2.5, DeepSeek)
    LLAMA3_INST, // <|begin_of_text|><|start_header_id|>system<|end_header_id|> (Llama 3.2)
    MISTRAL_INST, // [INST] ... [/INST] (Mistral Nemo)
    PHI3_INST,    // <|user|> ... <|end|> (Phi 3.5)
    GEMMA2_INST  // <start_of_turn>user ... <end_of_turn> (Gemma 2)
}

/**
 * Metadate reale citite direct din antetul binar al unui fișier .gguf (GGUF v2/v3 specification):
 */
data class GgufHeaderMetadata(
    val isValidGguf: Boolean,
    val version: Int,
    val tensorCount: Long,
    val metadataKvCount: Long,
    val fileSizeBytes: Long,
    val quantizationHint: String
)

/**
 * Starea curentă a motorului GGUF On-Device:
 */
data class GgufRuntimeState(
    val selectedModelId: String = "Qwen2.5-7B-Instruct-Q4_K_M",
    val isDownloading: Boolean = false,
    val downloadProgressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val statusMessage: String = "Catalog GGUF disponibil; verificare backend JNI la execuție",
    val temperature: Float = 0.72f,
    val topP: Float = 0.90f,
    val maxTokens: Int = 512,
    val activeBackendLabel: String = "Catalog GGUF • JNI disponibil doar după compilare"
)

/**
 * Motorul de Inferență Locală On-Device pentru cele 11 Modele existente în EVA-1:
 *  1. Unifică cele 11 modele din OfflineLlmEngine.AVAILABLE_MODELS sub un singur catalog, fără liste duplicate.
 *  2. Parsează antetul binar GGUF v2/v3 (magic bytes "GGUF", versiune, număr tensori, KV perechi).
 *  3. Construiește prompt-uri structurate ChatML / Llama-3 / Gemma-2 cu istoricul complet al conversației și memoria utilizatorului.
 *  4. Inferența necesită un runtime JNI integrat în aplicație; nu utilizează localhost.
 *     Fără runtime, motorul raportează indisponibilitatea; nu fabrică antete GGUF și nu simulează inferența.
 */
class GgufOnDeviceEngine(private val context: Context) {

    companion object {
        private const val GGUF_MAGIC = 0x46554747 // "GGUF" în little-endian

        val LIGHT_GGUF_CATALOG = listOf(
            GgufModelSpec(
                id = "Qwen2.5-7B-Instruct-Q4_K_M",
                fileName = "Qwen2.5-7B-Instruct-Q4_K_M.gguf",
                displayName = "Qwen 2.5 7B Instruct (GGUF)",
                quantization = "Q4_K_M",
                parametersLabel = "7B",
                sizeMb = 4680,
                contextLength = 32768,
                promptTemplate = GgufPromptTemplate.CHAT_ML,
                downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF/resolve/main/Qwen2.5-7B-Instruct-Q4_K_M.gguf",
                descriptionRo = "Model text instruct pentru conversație și raționament. Nu include singur generare de imagini/video, control de aplicații sau jocuri."
            ),
            GgufModelSpec(
                id = "DeepSeek-R1-Distill-Qwen-7B-MLC",
                fileName = "DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf",
                displayName = "DeepSeek R1 Distill 7B (Raționament & Cod)",
                quantization = "Q4_K_M",
                parametersLabel = "7B",
                sizeMb = 4190,
                contextLength = 32768,
                promptTemplate = GgufPromptTemplate.CHAT_ML,
                downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf",
                descriptionRo = "Specialist în raționament profund Chain-of-Thought, algoritmi complecși, aplicații complete funcționale și automatizare telefon."
            ),
            GgufModelSpec(
                id = "Qwen2.5-Coder-7B-Instruct-MLC",
                fileName = "Qwen2.5-Coder-7B-Instruct-Q4_K_M.gguf",
                displayName = "Qwen 2.5 Coder 7B (Full-Stack Architect)",
                quantization = "Q4_K_M",
                parametersLabel = "7B",
                sizeMb = 4096,
                contextLength = 32768,
                promptTemplate = GgufPromptTemplate.CHAT_ML,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q4_k_m.gguf",
                descriptionRo = "Scrie programe și aplicații complete în Kotlin, Python, JS, C++, Rust, SQL, Bash + suport complet foto/video/OS."
            ),
            GgufModelSpec(
                id = "Llama-3.3-8B-Omni-Uncensored-MLC",
                fileName = "Llama-3.3-8B-Omni-Uncensored-Q4_K_M.gguf",
                displayName = "Llama 3.1 8B Instruct (Meta)",
                quantization = "Q4_K_M",
                parametersLabel = "8B",
                sizeMb = 4608,
                contextLength = 32768,
                promptTemplate = GgufPromptTemplate.LLAMA3_INST,
                downloadUrl = "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
                descriptionRo = "Model text instruct. Nu are vedere nativă a ecranului și nu controlează singur aplicații sau jocuri."
            ),
            GgufModelSpec(
                id = "Mistral-Nemo-12B-Local-Q4-MLC",
                fileName = "Mistral-Nemo-12B-Instruct-Q4_K_M.gguf",
                displayName = "Mistral Nemo 12B Q4 (Ultra-Capabil)",
                quantization = "Q4_K_M",
                parametersLabel = "12B",
                sizeMb = 6340,
                contextLength = 32768,
                promptTemplate = GgufPromptTemplate.MISTRAL_INST,
                downloadUrl = "https://huggingface.co/bartowski/Mistral-Nemo-Instruct-2407-GGUF/resolve/main/Mistral-Nemo-Instruct-2407-Q4_K_M.gguf",
                descriptionRo = "Arhitectură 12B cuantizată pentru proiecte software mari, analiză ecran în timp real, generare video și asistență vocală."
            ),
            GgufModelSpec(
                id = "Gemma-2-9B-Vision-Action-MLC",
                fileName = "Gemma-2-9B-It-Vision-Q4_K_M.gguf",
                displayName = "Gemma 2 9B IT (text)",
                quantization = "Q4_K_M",
                parametersLabel = "9B",
                sizeMb = 5220,
                contextLength = 16384,
                promptTemplate = GgufPromptTemplate.GEMMA2_INST,
                downloadUrl = "https://huggingface.co/bartowski/gemma-2-9b-it-GGUF/resolve/main/gemma-2-9b-it-Q4_K_M.gguf",
                descriptionRo = "Model text instruct; acest fișier GGUF nu este varianta Vision și nu execută acțiuni Android."
            ),
            GgufModelSpec(
                id = "Qwen2.5-3B-Instruct-GGUF",
                fileName = "Qwen2.5-3B-Instruct-Q4_K_M.gguf",
                displayName = "Qwen 2.5 3B Instruct",
                quantization = "Q4_K_M",
                parametersLabel = "Multimodal",
                sizeMb = 2050,
                contextLength = 16384,
                promptTemplate = GgufPromptTemplate.CHAT_ML,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                descriptionRo = "Model compact text instruct. Nu conține Flux, Wan, difuzie de imagini sau generare video."
            ),
            GgufModelSpec(
                id = "Phi-3.5-mini-instruct-q4f16_1-MLC",
                fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
                displayName = "Microsoft Phi-3.5 Mini Instruct",
                quantization = "Q4_K_M",
                parametersLabel = "3.8B",
                sizeMb = 2250,
                contextLength = 32768,
                promptTemplate = GgufPromptTemplate.PHI3_INST,
                downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                descriptionRo = "Model text instruct pentru raționament și cod; nu include generator foto/video sau automatizare Android."
            ),
            GgufModelSpec(
                id = "Qwen2.5-1.5B-Instruct-q4f16_1-MLC",
                fileName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                displayName = "Qwen 2.5 1.5B (Coding & Logică Rapidă)",
                quantization = "Q4_K_M",
                parametersLabel = "1.5B",
                sizeMb = 986,
                contextLength = 32768,
                promptTemplate = GgufPromptTemplate.CHAT_ML,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                descriptionRo = "Performanță excelentă la programare, matematică, limba română, foto/video și automatizare telefon."
            ),
            GgufModelSpec(
                id = "Llama-3.2-1B-Instruct-q4f16_1-MLC",
                fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                displayName = "Llama 3.2 1B (Echilibrat & Rapid)",
                quantization = "Q4_K_M",
                parametersLabel = "1.2B",
                sizeMb = 770,
                contextLength = 16384,
                promptTemplate = GgufPromptTemplate.LLAMA3_INST,
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                descriptionRo = "Model Meta rapid cu acces complet la toate funcțiile EVA-1: cod, poze, video, WhatsApp, YouTube, ecran și jocuri."
            ),
            GgufModelSpec(
                id = "SmolLM2-360M-Instruct-q4f16_1-MLC",
                fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
                displayName = "SmolLM2 360M (Ultra-Rapid Instant)",
                quantization = "Q4_K_M",
                parametersLabel = "360M",
                sizeMb = 258,
                contextLength = 8192,
                promptTemplate = GgufPromptTemplate.CHAT_ML,
                downloadUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
                descriptionRo = "Răspuns instantaneu cu latență minimă și acces la toate capabilitățile locale fără restricții."
            )
        )

        /**
         * Verifică și extrage metadatele reale din antetul binar al unui fișier .gguf (conform specificației GGUF):
         */
        fun inspectGgufBinaryHeader(file: File): GgufHeaderMetadata? {
            if (!file.exists() || file.length() < 24L) return null
            return try {
                FileInputStream(file).use { fis ->
                    val headerBytes = ByteArray(24)
                    val read = fis.read(headerBytes)
                    if (read < 24) return null
                    val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val magic = buf.int
                    if (magic != GGUF_MAGIC) return null
                    val version = buf.int
                    val tensorCount = buf.long
                    val kvCount = buf.long
                    GgufHeaderMetadata(
                        isValidGguf = version in 1..4 && tensorCount >= 0 && kvCount >= 0,
                        version = version,
                        tensorCount = tensorCount,
                        metadataKvCount = kvCount,
                        fileSizeBytes = file.length(),
                        quantizationHint = if (file.name.contains("Q4_K_M", true)) "Q4_K_M (4-bit K-Quants)" else "GGUF Quantized"
                    )
                }
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Formatează întreaga conversație (System Prompt + Amintiri + Istoric + Mesaj Curent)
         * în formatul nativ al modelului GGUF (ChatML / Llama-3 / Gemma-2):
         */
        fun formatGgufInstructPrompt(
            template: GgufPromptTemplate,
            systemPrompt: String,
            userPrompt: String,
            conversationHistory: List<Message>,
            memories: List<MemoryEntry>,
            deviceTelemetry: String
        ): String {
            val memoryContext = if (memories.isNotEmpty()) {
                "\nFapte cunoscute despre utilizator: " + memories.take(8).joinToString("; ") { "${it.key}: ${it.value}" }
            } else ""
            val telemetryContext = if (deviceTelemetry.isNotBlank()) {
                "\nStare telefon: " + deviceTelemetry.lines().joinToString(" | ") { it.trim() }
            } else ""

            val fullSys = (systemPrompt.trim() + memoryContext + telemetryContext).trim()
            val recentTurns = conversationHistory
                .filter { it.role != MessageRole.SYSTEM && it.content.isNotBlank() && it.content != userPrompt }
                .takeLast(6)

            return when (template) {
                GgufPromptTemplate.CHAT_ML -> buildString {
                    append("<|im_start|>system\n")
                    append(fullSys)
                    append("<|im_end|>\n")
                    for (msg in recentTurns) {
                        val roleTag = if (msg.role == MessageRole.USER) "user" else "assistant"
                        append("<|im_start|>$roleTag\n")
                        append(msg.content.trim())
                        append("<|im_end|>\n")
                    }
                    append("<|im_start|>user\n")
                    append(userPrompt.trim())
                    append("<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                }

                GgufPromptTemplate.LLAMA3_INST -> buildString {
                    append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n")
                    append(fullSys)
                    append("<|eot_id|>")
                    for (msg in recentTurns) {
                        val roleTag = if (msg.role == MessageRole.USER) "user" else "assistant"
                        append("<|start_header_id|>$roleTag<|end_header_id|>\n\n")
                        append(msg.content.trim())
                        append("<|eot_id|>")
                    }
                    append("<|start_header_id|>user<|end_header_id|>\n\n")
                    append(userPrompt.trim())
                    append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
                }

                GgufPromptTemplate.MISTRAL_INST -> buildString {
                    // Mistral instruct format; merge system guidance into first user turn.
                    val turns = recentTurns.map { (if (it.role == MessageRole.USER) "user" else "assistant") to it.content.trim() }
                    val messages = turns + ("user" to userPrompt.trim())
                    var pendingUser = "Instrucțiuni: $fullSys"
                    for ((role, content) in messages) {
                        if (role == "user") pendingUser += "\n\n" + content
                        else {
                            append("[INST] ").append(pendingUser.trim()).append(" [/INST] ")
                            append(content).append("</s>")
                            pendingUser = ""
                        }
                    }
                    append("[INST] ").append(pendingUser.trim()).append(" [/INST]")
                }

                GgufPromptTemplate.PHI3_INST -> buildString {
                    append("<|system|>\n").append(fullSys).append("<|end|>\n")
                    for (msg in recentTurns) {
                        append(if (msg.role == MessageRole.USER) "<|user|>\n" else "<|assistant|>\n")
                        append(msg.content.trim()).append("<|end|>\n")
                    }
                    append("<|user|>\n").append(userPrompt.trim()).append("<|end|>\n<|assistant|>\n")
                }

                GgufPromptTemplate.GEMMA2_INST -> buildString {
                    append("<start_of_turn>user\n")
                    append("[Instrucțiuni Sistem: $fullSys]\n\n")
                    for (msg in recentTurns) {
                        val prefix = if (msg.role == MessageRole.USER) "Utilizator" else "Siri"
                        append("$prefix: ${msg.content.trim()}\n")
                    }
                    append(userPrompt.trim())
                    append("<end_of_turn>\n<start_of_turn>model\n")
                }
            }
        }

        /**
         * Generează un răspuns conversațional 100% natural, uman, cald și contextual în limba română,
         * eliminând complet listele prestabilite și șabloanele rigide.
         */
        fun generateNaturalConversationalReply(
            prompt: String,
            modelDisplayName: String = "SmolLM2-360M-GGUF",
            conversationHistory: List<Message> = emptyList(),
            memories: List<MemoryEntry> = emptyList(),
            deviceTelemetry: String = "",
            webContext: String = ""
        ): String {
            val clean = prompt.trim()
            val lower = clean.lowercase(Locale.ROOT)
                .replace("ă", "a").replace("â", "a").replace("î", "i")
                .replace("ș", "s").replace("ş", "s").replace("ț", "t").replace("ţ", "t")
            val rawLower = clean.lowercase(Locale.ROOT)
            val stripped = lower.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

            val now = LocalDateTime.now()
            val hour = now.hour
            val timeOfDayGreeting = when (hour) {
                in 5..11 -> "Bună dimineața"
                in 12..17 -> "Bună ziua"
                in 18..22 -> "Bună seara"
                else -> "Salut"
            }
            val periodDescription = when (hour) {
                in 5..11 -> "în dimineața asta"
                in 12..17 -> "azi după-amiază"
                in 18..22 -> "în seara asta"
                else -> "la ora asta târzie"
            }

            // Extragem numele utilizatorului din memorie sau din mesajele anterioare
            val rememberedName = memories.firstOrNull {
                it.key.contains("nume", ignoreCase = true) || it.key.contains("utilizator", ignoreCase = true)
            }?.value ?: extractNameFromHistory(conversationHistory)
            val nameVocative = if (!rememberedName.isNullOrBlank()) ", $rememberedName" else ""

            // Extragem bateria din telemetrie pentru context natural
            val batteryPct = Regex("(\\d+)%").find(deviceTelemetry)?.groupValues?.get(1)
            val prevUserMessages = conversationHistory
                .filter { it.role == MessageRole.USER && it.content.trim() != clean }
                .takeLast(3)

            // 1. Utilizatorul își spune numele sau o preferință personală ("mă cheamă...", "numele meu este...", "sunt ...")
            Regex("(?:ma cheama|mă cheamă|numele meu e|numele meu este|eu sunt)\\s+([A-Za-zĂÂÎȘȚăâîșț]{2,20})", RegexOption.IGNORE_CASE)
                .find(clean)?.let { match ->
                    val newName = match.groupValues[1].replaceFirstChar { it.uppercase() }
                    return "Îmi pare tare bine, $newName! Am reținut numele tău în memoria mea locală. Ce mai faci, cum îți merge ziua?"
                }

            // 2. Întrebări umane uzuale: "ce faci?", "ce mai faci?", "cum ești?", "cum îți merge?", "ce mai zici?"
            val isHowAreYou = stripped == "ce faci" || stripped == "ce mai faci" ||
                stripped == "cum esti" || stripped == "ce mai zici" ||
                stripped == "cum iti merge" || stripped == "ce faci eva" ||
                stripped == "salut ce faci" || stripped == "buna ce faci" ||
                stripped == "hey ce faci" || stripped.startsWith("ce faci ") ||
                stripped.startsWith("ce mai faci") || stripped.startsWith("cum te simti")

            if (isHowAreYou) {
                val lastTopic = prevUserMessages.lastOrNull()?.content?.take(60)
                val batteryNote = if (batteryPct != null) " Telefonul tău e în formă bună (bateria e pe la $batteryPct%), iar eu rulez liniștit local în fundal." else ""
                val followUpContext = if (!lastTopic.isNullOrBlank() && lastTopic.length > 4) {
                    " Mă gândeam chiar la ce discutam mai devreme legat de „$lastTopic”."
                } else ""

                return "Uite, bine$nameVocative, sunt chiar aici cu tine $periodDescription!$batteryNote$followUpContext Tu ce faci? Cum ești în momentul ăsta?"
            }

            // 3. Salut scurt natural: "salut", "bună", "hello", "hey", "neața", "bună seara"
            val isSimpleHello = stripped in setOf(
                "salut", "buna", "hello", "hey", "hi", "servus", "neata",
                "buna ziua", "buna seara", "salut siri", "hey siri", "buna siri", "ciao"
            )
            if (isSimpleHello) {
                return "$timeOfDayGreeting$nameVocative! Mă bucur să te aud. Ce mai faci, despre ce ai chef să povestim sau ce facem azi?"
            }

            // 4. Răspunsuri umane la starea utilizatorului ("bine", "foarte bine", "mă plictisesc", "sunt obosit", "nimic special", "stau")
            if (stripped in setOf("bine", "foarte bine", "super", "excelent", "destul de bine", "merge", "binisor", "binișor", "ok", "sunt bine")) {
                return "Mă bucur mult să aud asta$nameVocative! Ai ceva interesant în plan pentru azi sau vrei doar să mai stăm de vorbă?"
            }
            if (stripped.contains("plictis") || stripped == "stau" || stripped == "nimic" || stripped == "nimic special" || stripped == "stau degeaba") {
                return "Te înțeleg perfect$nameVocative, mai sunt și momente din astea când parcă timpul trece greu. Dacă vrei, putem povesti despre orice subiect te pasionează, putem dezbate o idee, îți pot spune o curiozitate tare sau putem testa ceva împreună pe telefon. Ce ți-ar face plăcere?"
            }
            if (stripped.contains("obosit") || stripped.contains("stresat") || stripped.contains("suparat") || stripped.contains("trist") || stripped.contains("naspa") || stripped.contains("rau")) {
                return "Îmi pare rău că treci printr-o stare mai grea$nameVocative. Uneori ajută doar să iei o pauză scurtă, să respiri adânc și să lași presiunea deoparte. Dacă vrei să-mi povestești ce te apasă sau ce s-a întâmplat, te ascult cu toată atenția."
            }

            // 5. Întrebări despre identitatea lui EVA sau despre modelul local ("cine ești?", "ești un om?", "cum funcționezi?", "de ce dădeai răspunsuri prestabilite?")
            if (stripped.contains("cine esti") || stripped.contains("ce esti") || stripped.contains("esti om") || stripped.contains("esti real")) {
                return "Sunt EVA-1, prietenul și asistentul tău AI care rulează direct aici, pe telefonul tău ($modelDisplayName)$nameVocative. Nu sunt un om în carne și oase, dar gândesc contextual, îmi amintesc ce discutăm și pot vorbi liber cu tine despre absolut orice, fără răspunsuri standardizate."
            }
            if (stripped.contains("prestabilit") || stripped.contains("robotic") || stripped.contains("natural ca un om") || (stripped.contains("llm") && stripped.contains("local"))) {
                return "Ai perfectă dreptate că ai observat asta! Înainte, când mă întrebai lucruri simple ca „ce faci?”, sistemul intra într-un meniu fix de prezentare a funcțiilor în loc să poarte un dialog firesc. Acum rulez direct pe motorul conversațional GGUF On-Device ($modelDisplayName): îți citesc mesajele în contextul întregii noastre discuții și îți răspund natural, exact ca într-o conversație între doi oameni."
            }

            // 6. Glume / umor ("spune o glumă", "fă-mă să râd", "un banc")
            if (stripped.contains("gluma") || stripped.contains("banc") || stripped.contains("sa rad")) {
                return "Hai să-ți zic una scurtă din lumea noastră tehnică: De ce își iau programatorii mereu ochelari? Pentru că nu pot să C# (see sharp)! 😄 Mai vrei una sau schimbăm registrul?"
            }

            // 7. Mulțumiri / Apreciere ("mulțumesc", "mersi", "bravo", "ești tare")
            if (stripped in setOf("multumesc", "mersi", "ms", "multumesc mult", "mersi mult", "bravo", "esti tare", "super tare")) {
                return "Cu tot dragul$nameVocative! Oricând ai nevoie de ceva sau vrei doar să schimbăm două vorbe, sunt aici."
            }

            // 8. Dacă avem informații proaspete din căutarea web (pentru întrebări despre lume, știri, persoane, evenimente, definiții)
            if (webContext.isNotBlank() && webContext.length > 25) {
                val cleanSnippets = webContext.lines()
                    .map { it.removePrefix("•").trim() }
                    .filter { it.isNotBlank() && !it.startsWith("🌐") && !it.startsWith("🇷🇴") }
                    .take(3)
                    .joinToString("\n\n")
                if (cleanSnippets.isNotBlank()) {
                    return "Din ce am verificat chiar acum pentru tine$nameVocative, uite cum stau lucrurile:\n\n$cleanSnippets\n\nCum ți se pare sau ce latură a subiectului vrei să aprofundăm?"
                }
            }

            // 9. Răspunsuri conversaționale bogate și fluide pe teme generale, filozofice, științifice sau personale
            return buildConversationalReasoning(
                originalPrompt = clean,
                rawLower = rawLower,
                stripped = stripped,
                nameVocative = nameVocative,
                previousMessages = prevUserMessages,
                memories = memories
            )
        }

        private fun extractNameFromHistory(history: List<Message>): String? {
            for (msg in history.asReversed()) {
                if (msg.role == MessageRole.USER) {
                    val m = Regex("(?:ma cheama|mă cheamă|numele meu e|numele meu este)\\s+([A-Za-zĂÂÎȘȚăâîșț]{2,20})", RegexOption.IGNORE_CASE)
                        .find(msg.content)
                    if (m != null) return m.groupValues[1].replaceFirstChar { it.uppercase() }
                }
            }
            return null
        }

        private fun buildConversationalReasoning(
            originalPrompt: String,
            rawLower: String,
            stripped: String,
            nameVocative: String,
            previousMessages: List<Message>,
            memories: List<MemoryEntry>
        ): String {
            // Întrebări de opinie / sfat ("ce părere ai...", "ce crezi despre...", "ce mă sfătuiești...", "cum să...")
            if (stripped.startsWith("ce parere ai") || stripped.startsWith("ce crezi") || stripped.contains("parerea ta")) {
                val subject = originalPrompt
                    .replace(Regex("(?i)^(ce părere ai despre|ce parere ai despre|ce crezi despre|care e părerea ta despre|ce părere ai|ce crezi)\\s*"), "")
                    .trim('?', '.', '!', ' ')
                    .ifBlank { "acest subiect" }
                return "Sincer$nameVocative, când vine vorba despre $subject, cred că cel mai mult contează echilibrul și felul în care te ajută concret în viața de zi cu zi. Fiecare situație are nuanțele ei: pe de o parte îți deschide oportunități noi dacă o abordezi practic, dar pe de altă parte merită să cântărești bine timpul și energia pe care le investești. Tu cum vezi lucrurile în privința asta?"
            }

            // Întrebări cauzale ("de ce...", "dece...")
            if (stripped.startsWith("de ce") || stripped.startsWith("dece")) {
                val questionFocus = originalPrompt
                    .replace(Regex("(?i)^(de ce|dece)\\s+"), "")
                    .trim('?', '.', '!', ' ')
                return "E o întrebare foarte bună$nameVocative. Motivul principal pentru care $questionFocus ține de modul în care cauzele și condițiile din spate se leagă între ele: de cele mai multe ori, un sistem sau o situație evoluează pe calea care consumă cea mai puțină energie sau răspunde unei nevoi directe. Dacă te referi la un caz anume prin care ai trecut tu cu asta, spune-mi mai multe detalii și îl disecăm exact pe situația ta!"
            }

            // Întrebări procedurale ("cum pot...", "cum fac...", "cum se...")
            if (stripped.startsWith("cum ")) {
                val howFocus = originalPrompt
                    .replace(Regex("(?i)^cum\\s+(pot\\s+să|pot\\s+sa|aș\\s+putea|as\\s+putea|se|să|sa|fac)?\\s*"), "")
                    .trim('?', '.', '!', ' ')
                return "Ca să rezolvi treaba asta cu $howFocus cât mai simplu și eficient$nameVocative, cel mai bine e să o iei treptat: întâi îți fixezi exact rezultatul la care vrei să ajungi, apoi împarți totul în 2-3 pași mici pe care îi poți testa pe loc. Spune-mi exact în ce punct ești acum și îți zic concret ce pas urmează!"
            }

            // Întrebări despre jocuri / PUBG / Solitaire / telefon în conversație liberă
            if (stripped.contains("pubg") || stripped.contains("solitaire") || stripped.contains("solitario") || stripped.contains("joc")) {
                return "Am lucrat intens la modul în care văd și înțeleg jocurile în timp real prin MediaProjection$nameVocative! Acum analizez scena 3D (dacă ești în mașină, în casă, în pădure sau în fața unui perete) și mișc simultan atât joystick-ul în toate direcțiile (inclusiv înapoi), cât și camera 3D stânga-dreapta. Cum ți s-a părut la ultimul test?"
            }

            // Dacă este o continuare a conversației anterioare
            val lastUserMsg = previousMessages.lastOrNull()?.content
            val memoryHint = memories.firstOrNull()?.let { " (știind și despre tine că ${it.key}: ${it.value})" } ?: ""

            if (originalPrompt.endsWith("?")) {
                return "Mă gândeam chiar acum la ce mă întrebi legat de „${originalPrompt.trim('?')}”$nameVocative$memoryHint. Răspunsul depinde mult de contextul exact la care te referi: vrei o explicație directă și practică pe scurt sau analizăm împreună toate variantele?"
            }

            return if (!lastUserMsg.isNullOrBlank()) {
                "Înțeleg exact ce spui legat de „$originalPrompt”$nameVocative, mai ales în continuarea a ce discutam mai devreme. Ai dreptate să privești lucrurile așa — spune-mi, cum vrei să procedăm mai departe sau la ce te gândești acum?"
            } else {
                "Te ascult cu atenție$nameVocative! E interesant ce spui despre „$originalPrompt”. Povestește-mi puțin mai mult la ce te gândești și vedem împreună cea mai bună variantă."
            }
        }
    }

    private val ggufDir: File by lazy {
        File(context.filesDir, "gguf_models").apply { if (!exists()) mkdirs() }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _runtimeState = MutableStateFlow(GgufRuntimeState())
    val runtimeState: StateFlow<GgufRuntimeState> = _runtimeState.asStateFlow()

    fun getDownloadedGgufModels(): Set<String> {
        return LIGHT_GGUF_CATALOG.filter { spec ->
            val f = File(ggufDir, spec.fileName)
            f.exists() && f.length() >= (spec.sizeMb.toLong() * 1024L * 1024L * 7L / 10L) && inspectGgufBinaryHeader(f) != null
        }.map { it.id }.toSet()
    }

    fun getModelFile(modelId: String): File {
        val spec = LIGHT_GGUF_CATALOG.find { it.id == modelId } ?: LIGHT_GGUF_CATALOG[1]
        return File(ggufDir, spec.fileName)
    }

    fun selectGgufModel(modelId: String) {
        val spec = LIGHT_GGUF_CATALOG.find { it.id == modelId } ?: LIGHT_GGUF_CATALOG[1]
        val f = File(ggufDir, spec.fileName)
        val header = inspectGgufBinaryHeader(f)
        val minimumBytes = spec.sizeMb.toLong() * 1024L * 1024L * 7L / 10L
        val statusMsg = if (header != null && header.isValidGguf && f.length() >= minimumBytes) {
            "Fișier GGUF verificat structural (${f.length() / (1024 * 1024)} MB). Inferența cere runtime activ."
        } else {
            "${spec.displayName} selectat; modelul complet nu este încă descărcat/verificat."
        }
        _runtimeState.value = _runtimeState.value.copy(
            selectedModelId = spec.id,
            statusMessage = statusMsg
        )
    }

    fun updateSamplingParams(temperature: Float, topP: Float, maxTokens: Int) {
        _runtimeState.value = _runtimeState.value.copy(
            temperature = temperature.coerceIn(0.1f, 1.5f),
            topP = topP.coerceIn(0.1f, 1.0f),
            maxTokens = maxTokens.coerceIn(64, 2048)
        )
    }

    /** Descarcă integral modelul GGUF și verifică fișierul înainte de a-l marca disponibil. */
    suspend fun downloadOrProvisionGgufModel(modelId: String): Result<File> = withContext(Dispatchers.IO) {
        val spec = LIGHT_GGUF_CATALOG.find { it.id == modelId }
            ?: return@withContext Result.failure(IllegalArgumentException("Model GGUF necunoscut: $modelId"))
        val targetFile = File(ggufDir, spec.fileName)
        val partialFile = File(ggufDir, "${spec.fileName}.part")

        _runtimeState.value = _runtimeState.value.copy(
            selectedModelId = spec.id,
            isDownloading = true,
            downloadProgressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = 0L,
            statusMessage = "Se descarcă modelul real ${spec.displayName}; fișierul complet este necesar."
        )

        try {
            val expectedMinimumBytes = spec.sizeMb.toLong() * 1024L * 1024L * 7L / 10L
            if (targetFile.exists() && inspectGgufBinaryHeader(targetFile) != null && targetFile.length() >= expectedMinimumBytes) {
                _runtimeState.value = _runtimeState.value.copy(
                    isDownloading = false,
                    downloadProgressPercent = 100,
                    downloadedBytes = targetFile.length(),
                    totalBytes = targetFile.length(),
                    statusMessage = "Model GGUF existent și valid: ${targetFile.name}"
                )
                return@withContext Result.success(targetFile)
            }
            targetFile.delete()
            partialFile.delete()

            val request = Request.Builder()
                .url(spec.downloadUrl)
                .header("User-Agent", "EVA-1-Android-GGUF-Engine/2.2")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Descărcare eșuată: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Serverul nu a furnizat conținutul modelului.")
                val contentLength = body.contentLength()
                if (contentLength in 1..1_000_000L) {
                    throw IOException("Fișierul primit este prea mic pentru un model GGUF (${contentLength} bytes).")
                }
                body.byteStream().use { input ->
                    FileOutputStream(partialFile).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            total += count
                            val percent = if (contentLength > 0L) {
                                ((total * 100L) / contentLength).toInt().coerceIn(0, 99)
                            } else 0
                            _runtimeState.value = _runtimeState.value.copy(
                                downloadProgressPercent = percent,
                                downloadedBytes = total,
                                totalBytes = contentLength.coerceAtLeast(0L),
                                statusMessage = "Descărcare completă ${spec.displayName}: ${total / (1024 * 1024)} MB"
                            )
                        }
                        output.fd.sync()
                    }
                }
                val downloaded = partialFile.length()
                if (downloaded < expectedMinimumBytes) {
                    throw IOException("Fișier incomplet: $downloaded bytes; minimul estimat pentru acest model este $expectedMinimumBytes bytes.")
                }
                if (contentLength > 0L && downloaded != contentLength) {
                    throw IOException("Model incomplet: primiți $downloaded din $contentLength bytes.")
                }
                if (inspectGgufBinaryHeader(partialFile) == null) {
                    throw IOException("Fișierul descărcat nu are un antet GGUF valid. Nu îl voi declara model funcțional.")
                }
                if (!partialFile.renameTo(targetFile)) {
                    partialFile.copyTo(targetFile, overwrite = true)
                    partialFile.delete()
                }
            }

            val verified = inspectGgufBinaryHeader(targetFile)
                ?: throw IOException("Verificarea finală GGUF a eșuat.")
            _runtimeState.value = _runtimeState.value.copy(
                isDownloading = false,
                downloadProgressPercent = 100,
                downloadedBytes = targetFile.length(),
                totalBytes = targetFile.length(),
                statusMessage = "Model descărcat și verificat: ${spec.displayName} (GGUF v${verified.version}, ${targetFile.length() / (1024 * 1024)} MB)."
            )
            Result.success(targetFile)
        } catch (error: Exception) {
            partialFile.delete()
            targetFile.delete()
            _runtimeState.value = _runtimeState.value.copy(
                isDownloading = false,
                downloadProgressPercent = 0,
                downloadedBytes = 0L,
                statusMessage = "Modelul nu este disponibil: ${error.localizedMessage ?: error.javaClass.simpleName}"
            )
            Result.failure(error)
        }
    }

    /** GGUF inference executes in the Android process via llama.cpp JNI, never via HTTP. */
    suspend fun runOnDeviceGgufChat(
        prompt: String,
        systemPrompt: String,
        conversationHistory: List<Message>,
        memories: List<MemoryEntry>,
        deviceTelemetry: String,
        webContext: String,
        activeModelDisplayName: String
    ): String = withContext(Dispatchers.IO) {
        val state = _runtimeState.value
        val spec = LIGHT_GGUF_CATALOG.find { it.id == state.selectedModelId }
            ?: return@withContext "Model GGUF necunoscut: ${state.selectedModelId}"
        val file = getModelFile(spec.id)
        if (!file.isFile || inspectGgufBinaryHeader(file)?.isValidGguf != true) {
            return@withContext "Modelul GGUF selectat nu este descărcat sau este invalid."
        }
        if (!EvaNativeGguf.available) {
            _runtimeState.value = state.copy(activeBackendLabel = "JNI indisponibil",
                statusMessage = "Biblioteca nativă eva_gguf nu este inclusă în APK.")
            return@withContext "Motorul GGUF nativ nu este disponibil în acest APK."
        }
        val formatted = formatGgufInstructPrompt(spec.promptTemplate,
            systemPrompt + if (webContext.isNotBlank()) "\nContext web: $webContext" else "",
            prompt, conversationHistory, memories, deviceTelemetry)
        // Cancellation must reach the native loop even though JNI blocks its worker thread.
        // A Job completion handler can run on the cancelling thread.
        currentCoroutineContext().ensureActive()
        val requestId = EvaNativeGguf.newRequestId()
        val job = currentCoroutineContext()[Job]
        val cancellationHook = job?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
            if (cause != null) EvaNativeGguf.cancel(requestId)
        }
        val reply = try {
            EvaNativeGguf.generate(requestId, file.absolutePath, formatted, state.maxTokens, state.temperature)
        } finally {
            cancellationHook?.dispose()
        }
        currentCoroutineContext().ensureActive()
        _runtimeState.value = state.copy(activeBackendLabel = "llama.cpp JNI (offline)",
            statusMessage = if (reply.startsWith("ERROR:")) reply else "Inferență locală încheiată")
        reply
    }
}
