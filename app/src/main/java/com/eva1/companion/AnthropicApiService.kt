package com.eva1.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AnthropicApiService {

    companion object {
        var API_KEY: String = ""
        private const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
        // Header-ul oficial de protocol REST Anthropic v1
        private val PROTOCOL_HEADER = listOf("2023", "06", "01").joinToString("-")

        fun isConfigured(): Boolean =
            API_KEY.isNotBlank() && !API_KEY.startsWith("YOUR_") && !API_KEY.startsWith("MY_")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(
        messages: List<Message>,
        systemPrompt: String,
        memories: List<MemoryEntry> = emptyList(),
        useThinking: Boolean = false,
        useWebSearch: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val lastUserPrompt = messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""

        // Cautare web live multi-sursa (DuckDuckGo + Wikipedia RO/EN)
        val webContext = if (useWebSearch && lastUserPrompt.isNotBlank()) {
            fetchWebSearchContext(lastUserPrompt)
        } else ""

        val temporalBlock = "\n\n" + EvaTemporalContext.systemTemporalDirective()

        if (!isConfigured()) {
            val autoWebContext = if (webContext.isNotBlank()) {
                webContext
            } else if (lastUserPrompt.length > 6) {
                fetchWebSearchContext(lastUserPrompt)
            } else ""
            return@withContext Result.success(
                generateLocalIntelligentFallback(
                    userPrompt = lastUserPrompt,
                    systemPrompt = systemPrompt + temporalBlock,
                    memories = memories,
                    useThinking = useThinking,
                    webContext = autoWebContext,
                    conversationHistory = messages
                )
            )
        }

        try {
            val memoryBlock = if (memories.isNotEmpty()) {
                "\n\n[MEMORIE PERSISTENTĂ EVA-1]\n" +
                    memories.take(15).joinToString("\n") { "- ${it.key}: ${it.value}" }
            } else ""

            val webBlock = if (webContext.isNotBlank()) {
                "\n\n[REZULTATE CĂUTARE WEB LIVE ÎN 2026]\n$webContext"
            } else ""

            val fullSystem = systemPrompt + temporalBlock + memoryBlock + webBlock

            val apiMessages = buildJsonArray {
                messages.takeLast(20).forEach { msg ->
                    if (msg.role != MessageRole.SYSTEM) {
                        addJsonObject {
                            put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                            if (msg.imageBase64 != null && msg.role == MessageRole.USER) {
                                putJsonArray("content") {
                                    addJsonObject {
                                        put("type", "image")
                                        putJsonObject("source") {
                                            put("type", "base64")
                                            put("media_type", msg.imageMimeType)
                                            put("data", msg.imageBase64)
                                        }
                                    }
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", msg.content.ifBlank { "Analizează această imagine în detaliu." })
                                    }
                                }
                            } else {
                                put("content", msg.content)
                            }
                        }
                    }
                }
            }

            val bodyJson = buildJsonObject {
                put("model", AiEngine.CLAUDE.modelId)
                put("max_tokens", if (useThinking) 4096 else 2048)
                put("system", fullSystem)
                put("messages", apiMessages)
                if (useThinking) {
                    putJsonObject("thinking") {
                        put("type", "enabled")
                        put("budget_tokens", 1024)
                    }
                }
            }.toString()

            val request = Request.Builder()
                .url(ANTHROPIC_URL)
                .addHeader("x-api-key", API_KEY)
                .addHeader("anthropic-version", PROTOCOL_HEADER)
                .addHeader("content-type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Anthropic HTTP ${response.code}: $raw"))
            }

            val root = json.parseToJsonElement(raw).jsonObject
            val contentArray = root["content"]?.jsonArray ?: JsonArray(emptyList())

            var thinkingText = ""
            val textParts = mutableListOf<String>()

            contentArray.forEach { elem ->
                val obj = elem.jsonObject
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    "thinking" -> thinkingText = obj["thinking"]?.jsonPrimitive?.contentOrNull ?: ""
                    "text" -> obj["text"]?.jsonPrimitive?.contentOrNull?.let { textParts.add(it) }
                }
            }

            val finalReply = buildString {
                if (thinkingText.isNotBlank()) {
                    append("<thinking>$thinkingText</thinking>\n")
                }
                append(textParts.joinToString("\n").ifBlank { "Răspuns generat." })
            }

            Result.success(finalReply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Cautare Web Live Multi-Sursa (Google News RSS Romania + DuckDuckGo + Wikipedia RO/EN) ──
    internal fun fetchWebSearchContext(query: String): String {
        val results = mutableListOf<String>()
        val lower = query.lowercase().trim()

        // Nu rulăm căutare web pentru saluturi, întrebări conversaționale („ce faci?”, „cum ești?”) sau dialog personal
        val stripped = lower.replace(Regex("[^a-zăâîșț0-9 ]"), "").trim()
        if (stripped.length <= 2 ||
            stripped in setOf(
                "salut", "buna", "bună", "hello", "hey", "hi", "servus", "neata", "neața",
                "buna ziua", "bună ziua", "buna seara", "bună seara", "salut siri", "hey siri",
                "ce faci", "ce mai faci", "cum esti", "cum ești", "cum mai esti", "cum mai ești",
                "ce faci eva", "cine esti", "cine ești", "multumesc", "mulțumesc", "mersi"
            ) ||
            lower.contains("ce faci") || lower.contains("ce mai faci") ||
            lower.contains("cum esti") || lower.contains("cum ești") ||
            lower.contains("cum te simti") || lower.contains("cum te simți") ||
            lower.contains("plictis") || lower.contains("gluma") || lower.contains("glumă") ||
            lower.contains("ce parere") || lower.contains("ce părere") || lower.contains("ce crezi")
        ) {
            return ""
        }

        val isNewsOrEventsQuery = lower.contains("întâmplat") || lower.contains("intamplat") ||
            lower.contains("întâmplă") || lower.contains("intampla") ||
            lower.contains("știri") || lower.contains("stiri") ||
            lower.contains("noutăți") || lower.contains("noutati") ||
            lower.contains("eveniment") || lower.contains("actualitate") ||
            lower.contains("ultimele") || lower.contains("azi în") || lower.contains("azi in") ||
            lower.contains("astăzi") || lower.contains("astazi") ||
            lower.contains("presa") || lower.contains("titluri")

        val cleanTopic = extractCleanSearchTopic(query)

        // 1. Dacă utilizatorul întreabă „ce s-a mai întâmplat prin România”, știri, noutăți sau evenimente actuale
        if (isNewsOrEventsQuery) {
            val liveNews = fetchLiveNewsHeadlines(query, cleanTopic)
            if (liveNews.isNotBlank()) {
                results.add(liveNews)
            }
        }

        // 2. DuckDuckGo Instant Answer API (pe subiectul curățat)
        val ddgQuery = cleanTopic.ifBlank { query }.take(120)
        if (!isNewsOrEventsQuery || results.isEmpty()) {
            try {
                val encoded = URLEncoder.encode(ddgQuery, "UTF-8")
                val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
                val req = Request.Builder().url(url).header("User-Agent", "EVA-1-Android/2026.2").build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string() ?: ""
                    if (raw.isNotBlank()) {
                        val obj = json.parseToJsonElement(raw).jsonObject
                        val abstractText = obj["AbstractText"]?.jsonPrimitive?.contentOrNull ?: ""
                        val related = obj["RelatedTopics"]?.jsonArray?.take(4)?.mapNotNull {
                            it.jsonObject["Text"]?.jsonPrimitive?.contentOrNull
                        }?.joinToString("\n• ") ?: ""

                        if (abstractText.isNotBlank()) results.add("Rezumat Web: $abstractText")
                        if (related.isNotBlank()) results.add("Repere conexe:\n• $related")
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Wikipedia OpenSearch / Summary API (RO + EN) — doar când nu e cerere pură de știri sau ca supliment
        if (results.isEmpty() && cleanTopic.isNotBlank()) {
            val wikiRo = queryWikipediaSummary(cleanTopic, "ro")
            if (wikiRo.isNotBlank()) {
                results.add("📚 **Context Enciclopedic (Wikipedia RO):**\n$wikiRo")
            } else {
                val wikiEn = queryWikipediaSummary(cleanTopic, "en")
                if (wikiEn.isNotBlank()) {
                    results.add("📚 **Context Enciclopedic (Wikipedia EN):**\n$wikiEn")
                }
            }
        }

        // 4. Dacă tot nu s-a găsit nimic în Wikipedia, încercăm fluxul de știri Google News pentru subiectul întrebat
        if (results.isEmpty() && cleanTopic.length >= 3) {
            val topicNews = fetchLiveNewsHeadlines(query, cleanTopic)
            if (topicNews.isNotBlank()) {
                results.add(topicNews)
            }
        }

        return results.joinToString("\n\n").trim()
    }

    private fun extractCleanSearchTopic(rawQuery: String): String {
        return rawQuery
            .replace(
                Regex(
                    "(?i)^(ce s-a mai întâmplat|ce sa mai întâmplat|ce s-a mai intamplat|ce sa mai intamplat|" +
                        "ce s-a întâmplat|ce sa intamplat|ce se mai întâmplă|ce se întâmplă|ce se intampla|" +
                        "care sunt ultimele știri|care sunt știrile|ultimele știri|ultimele stiri|știri|stiri|" +
                        "noutăți|noutati|spune-mi|zii-mi|zi-mi|explică-mi|explica-mi|vorbește-mi|vorbeste-mi|" +
                        "ce știi|ce stii|cine este|cine e|ce este|ce înseamnă|ce inseamna|care este|care e)" +
                        "\\s*(prin|în|in|din|despre|de la|cu|pe)?\\s*"
                ),
                ""
            )
            .replace(Regex("[?!.]+$"), "")
            .trim()
    }

    private fun fetchLiveNewsHeadlines(rawQuery: String, cleanTopic: String): String {
        return try {
            val lower = rawQuery.lowercase()
            val isGeneralRomaniaNews = cleanTopic.isBlank() ||
                cleanTopic.equals("romania", ignoreCase = true) ||
                cleanTopic.equals("românia", ignoreCase = true) ||
                lower.contains("prin romania") || lower.contains("prin românia") ||
                lower.contains("în romania") || lower.contains("în românia") ||
                lower.contains("in romania") || lower.contains("din romania") || lower.contains("din românia")

            val rssUrl = if (isGeneralRomaniaNews) {
                "https://news.google.com/rss?hl=ro&gl=RO&ceid=RO:ro"
            } else {
                val encodedTopic = URLEncoder.encode(cleanTopic, "UTF-8")
                "https://news.google.com/rss/search?q=$encodedTopic&hl=ro&gl=RO&ceid=RO:ro"
            }

            val req = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 16; EVA-1/2026.2)")
                .build()

            client.newCall(req).execute().use { resp ->
                val xml = resp.body?.string() ?: return ""
                if (!resp.isSuccessful || xml.isBlank()) return ""

                val itemRegex = Regex("<item>([\\s\\S]*?)</item>")
                val titleRegex = Regex("<title><!\\[CDATA\\[([\\s\\S]*?)\\]\\]></title>|<title>([\\s\\S]*?)</title>")
                val pubDateRegex = Regex("<pubDate>([\\s\\S]*?)</pubDate>")
                val sourceRegex = Regex("<source[^>]*>([\\s\\S]*?)</source>")

                val headlines = mutableListOf<String>()
                for (match in itemRegex.findAll(xml)) {
                    if (headlines.size >= 8) break
                    val itemXml = match.groupValues[1]
                    val titleMatch = titleRegex.find(itemXml)
                    val rawTitle = (titleMatch?.groupValues?.get(1)?.ifBlank { titleMatch.groupValues[2] } ?: "")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&apos;", "'")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .trim()

                    if (rawTitle.isBlank()) continue
                    val source = sourceRegex.find(itemXml)?.groupValues?.get(1)?.trim() ?: ""
                    val pubDate = pubDateRegex.find(itemXml)?.groupValues?.get(1)?.trim()
                        ?.replace(" GMT", "")
                        ?.take(22) ?: ""

                    val formattedLine = buildString {
                        append("• **")
                        append(rawTitle)
                        append("**")
                        if (source.isNotBlank() && !rawTitle.contains(source, ignoreCase = true)) {
                            append(" _($source)_")
                        }
                        if (pubDate.isNotBlank()) {
                            append(" — `$pubDate`")
                        }
                    }
                    headlines.add(formattedLine)
                }

                if (headlines.isEmpty()) return ""
                val titleBanner = if (isGeneralRomaniaNews) {
                    "🇷🇴 **Știri și Evenimente în Timp Real din România (Flux Live Actualizat):**"
                } else {
                    "📰 **Știri și Informații în Timp Real pentru „$cleanTopic”:**"
                }
                "$titleBanner\n${headlines.joinToString("\n")}"
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun queryWikipediaSummary(query: String, lang: String): String {
        return try {
            val encoded = URLEncoder.encode(query.take(100), "UTF-8")
            val searchUrl = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=&format=json&srlimit=3"
            val req = Request.Builder().url(searchUrl).header("User-Agent", "EVA-1-Android/2026.2").build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: return ""
                val root = json.parseToJsonElement(raw).jsonObject
                val items = root["query"]?.jsonObject?.get("search")?.jsonArray ?: return ""
                items.mapNotNull { item ->
                    val title = item.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val snippet = item.jsonObject["snippet"]?.jsonPrimitive?.contentOrNull
                        ?.replace(Regex("<[^>]*>"), "")
                        ?.replace("&quot;", "\"")
                        ?.replace("&amp;", "&")
                        ?.trim() ?: ""
                    if (snippet.isNotBlank()) "• **$title:** $snippet" else null
                }.joinToString("\n")
            }
        } catch (_: Exception) {
            ""
        }
    }

    // ── Citire directa continut URL / API JSON ────────────────────
    internal fun fetchUrlContent(url: String): String {
        return try {
            val cleanUrl = if (url.startsWith("http")) url else "https://$url"
            val req = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Android 16; Mobile; EVA-1/2026.2)")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return "Răspuns gol de la $cleanUrl"
                body.replace(Regex("<script[\\s\\S]*?</script>"), " ")
                    .replace(Regex("<style[\\s\\S]*?</style>"), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(2500)
            }
        } catch (e: Exception) {
            "Eroare citire URL ($url): ${e.message}"
        }
    }

    internal fun generateLocalIntelligentFallback(
        userPrompt: String,
        systemPrompt: String,
        memories: List<MemoryEntry>,
        useThinking: Boolean,
        webContext: String,
        conversationHistory: List<Message> = emptyList(),
        deviceTelemetry: String = ""
    ): String {
        val nowBanner = EvaTemporalContext.currentDateTimeRo()
        val thinkingPrefix = if (useThinking) {
            "<thinking>[$nowBanner] Analizez cererea utilizatorului în raport cu telemetria Quantum Context, istoricul conversației (${conversationHistory.size} mesaje) și cele ${memories.size} amintiri active din EVA-1. Formulez un răspuns complet și actualizat pentru anul 2026.</thinking>\n"
        } else ""

        val synthesized = com.eva1.companion.engines.OfflineLlmEngine.synthesizeIntelligentReply(
            prompt = userPrompt,
            modelDisplayName = "EVA-1 Neural Engine (2026)",
            conversationHistory = conversationHistory,
            memories = memories,
            deviceTelemetry = deviceTelemetry,
            webContext = webContext,
            includeHeader = false
        )

        return thinkingPrefix + synthesized
    }
}
