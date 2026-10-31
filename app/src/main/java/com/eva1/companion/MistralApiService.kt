package com.eva1.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MistralApiService {

    companion object {
        var API_KEY: String = ""
        private const val MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions"

        fun isConfigured(): Boolean =
            API_KEY.isNotBlank() && !API_KEY.startsWith("YOUR_") && !API_KEY.startsWith("MY_")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val anthropicFallback = AnthropicApiService()

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(
        messages: List<Message>,
        systemPrompt: String,
        memories: List<MemoryEntry> = emptyList(),
        useWebSearch: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val lastUserPrompt = messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val webContext = if (useWebSearch && lastUserPrompt.isNotBlank()) {
            anthropicFallback.fetchWebSearchContext(lastUserPrompt)
        } else ""

        val temporalBlock = "\n\n" + EvaTemporalContext.systemTemporalDirective()

        if (!isConfigured()) {
            val autoWebContext = if (webContext.isNotBlank()) {
                webContext
            } else if (lastUserPrompt.length > 6) {
                anthropicFallback.fetchWebSearchContext(lastUserPrompt)
            } else ""
            return@withContext Result.success(
                anthropicFallback.generateLocalIntelligentFallback(
                    userPrompt = lastUserPrompt,
                    systemPrompt = systemPrompt + temporalBlock,
                    memories = memories,
                    useThinking = false,
                    webContext = autoWebContext,
                    conversationHistory = messages
                )
            )
        }

        try {
            val memoryBlock = if (memories.isNotEmpty()) {
                "\n\n[MEMORIE EVA-1]\n" + memories.take(15).joinToString("\n") { "- ${it.key}: ${it.value}" }
            } else ""

            val webBlock = if (webContext.isNotBlank()) {
                "\n\n[CĂUTARE WEB LIVE 2026]\n$webContext"
            } else ""

            val msgArray = buildJsonArray {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt + temporalBlock + memoryBlock + webBlock)
                }
                messages.takeLast(20).forEach { m ->
                    if (m.role != MessageRole.SYSTEM) {
                        addJsonObject {
                            put("role", if (m.role == MessageRole.USER) "user" else "assistant")
                            put("content", m.content)
                        }
                    }
                }
            }

            val bodyJson = buildJsonObject {
                put("model", AiEngine.MISTRAL.modelId)
                put("messages", msgArray)
                put("temperature", 0.7)
                put("max_tokens", 2048)
            }.toString()

            val req = Request.Builder()
                .url(MISTRAL_URL)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val raw = resp.body?.string() ?: ""

            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Mistral HTTP ${resp.code}: $raw"))
            }

            val root = json.parseToJsonElement(raw).jsonObject
            val reply = root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?: "Răspuns gol de la Mistral."

            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
