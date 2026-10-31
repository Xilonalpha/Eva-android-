package com.eva1.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.evaDataStore: DataStore<Preferences> by preferencesDataStore(name = "eva1_v2_datastore")

class EvaRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    companion object {
        private val KEY_MEMORIES = stringPreferencesKey("memories_v2")
        private val KEY_SESSIONS = stringPreferencesKey("sessions_v2")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt_v2")
        private val KEY_EVOLUTION_COUNT = intPreferencesKey("evolution_count_v2")
        private val KEY_SELECTED_ENGINE = stringPreferencesKey("selected_engine_v2")
        private val KEY_EXTENDED_THINKING = booleanPreferencesKey("extended_thinking_v2")
        private val KEY_WEB_SEARCH = booleanPreferencesKey("web_search_v2")
        private val KEY_AUTO_GENESIS = booleanPreferencesKey("auto_genesis_v2")
        private val KEY_API_KEYS = stringPreferencesKey("api_keys_v2")
        private val KEY_CODE_PROJECTS = stringPreferencesKey("code_projects_v2")
        private val KEY_GENERATED_MEDIA = stringPreferencesKey("generated_media_v2")
    }

    val memoriesFlow: Flow<List<MemoryEntry>> = context.evaDataStore.data.map { prefs ->
        val raw = prefs[KEY_MEMORIES] ?: return@map defaultMemories()
        runCatching { json.decodeFromString<List<MemoryEntry>>(raw) }.getOrDefault(defaultMemories())
    }

    val historyFlow: Flow<List<ConversationSession>> = context.evaDataStore.data.map { prefs ->
        val raw = prefs[KEY_SESSIONS] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<ConversationSession>>(raw) }.getOrDefault(emptyList())
    }

    val systemPromptFlow: Flow<String> = context.evaDataStore.data.map { prefs ->
        prefs[KEY_SYSTEM_PROMPT] ?: EvaUiState.DEFAULT_EVA_SYSTEM_PROMPT
    }

    val evolutionCountFlow: Flow<Int> = context.evaDataStore.data.map { prefs ->
        prefs[KEY_EVOLUTION_COUNT] ?: 0
    }

    val engineFlow: Flow<AiEngine> = context.evaDataStore.data.map { prefs ->
        runCatching { AiEngine.valueOf(prefs[KEY_SELECTED_ENGINE] ?: "LOCAL_LLM") }.getOrDefault(AiEngine.LOCAL_LLM)
    }

    val thinkingFlow: Flow<Boolean> = context.evaDataStore.data.map { prefs ->
        prefs[KEY_EXTENDED_THINKING] ?: false
    }

    val webSearchFlow: Flow<Boolean> = context.evaDataStore.data.map { prefs ->
        prefs[KEY_WEB_SEARCH] ?: true
    }

    val autoGenesisFlow: Flow<Boolean> = context.evaDataStore.data.map { prefs ->
        prefs[KEY_AUTO_GENESIS] ?: true
    }

    val apiKeysFlow: Flow<StoredApiKeys> = context.evaDataStore.data.map { prefs ->
        val raw = prefs[KEY_API_KEYS]
        if (raw.isNullOrBlank()) {
            defaultApiKeys()
        } else {
            runCatching {
                val saved = json.decodeFromString<StoredApiKeys>(raw)
                val fallback = defaultApiKeys()
                StoredApiKeys(
                    anthropicKey = saved.anthropicKey.ifBlank { fallback.anthropicKey },
                    mistralKey = saved.mistralKey.ifBlank { fallback.mistralKey },
                    jdoodleClientId = saved.jdoodleClientId.ifBlank { fallback.jdoodleClientId },
                    jdoodleClientSecret = saved.jdoodleClientSecret.ifBlank { fallback.jdoodleClientSecret },
                    elevenLabsKey = saved.elevenLabsKey.ifBlank { fallback.elevenLabsKey },
                    replicateKey = saved.replicateKey.ifBlank { fallback.replicateKey },
                    stabilityKey = saved.stabilityKey.ifBlank { fallback.stabilityKey },
                    runwayKey = saved.runwayKey.ifBlank { fallback.runwayKey },
                    sunoKey = saved.sunoKey.ifBlank { fallback.sunoKey },
                    geminiKey = saved.geminiKey.ifBlank { fallback.geminiKey }
                )
            }.getOrDefault(defaultApiKeys())
        }
    }

    val codeProjectsFlow: Flow<List<CodeProject>> = context.evaDataStore.data.map { prefs ->
        val raw = prefs[KEY_CODE_PROJECTS] ?: return@map defaultCodeProjects()
        runCatching { json.decodeFromString<List<CodeProject>>(raw) }.getOrDefault(defaultCodeProjects())
    }

    val generatedMediaFlow: Flow<List<GeneratedMedia>> = context.evaDataStore.data.map { prefs ->
        val raw = prefs[KEY_GENERATED_MEDIA] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<GeneratedMedia>>(raw) }.getOrDefault(emptyList())
    }

    private fun defaultApiKeys(): StoredApiKeys {
        fun clean(v: String): String =
            if (v.isBlank() || v == "DUMMY_API_KEY" || v.startsWith("YOUR_") || v.startsWith("MY_")) "" else v

        return StoredApiKeys(
            anthropicKey = clean(com.example.BuildConfig.ANTHROPIC_API_KEY),
            mistralKey = clean(com.example.BuildConfig.MISTRAL_API_KEY),
            jdoodleClientId = clean(com.example.BuildConfig.JDOODLE_CLIENT_ID),
            jdoodleClientSecret = clean(com.example.BuildConfig.JDOODLE_CLIENT_SECRET),
            elevenLabsKey = clean(com.example.BuildConfig.ELEVENLABS_API_KEY),
            replicateKey = clean(com.example.BuildConfig.REPLICATE_API_KEY),
            stabilityKey = clean(com.example.BuildConfig.STABILITY_API_KEY),
            runwayKey = clean(com.example.BuildConfig.RUNWAY_API_KEY),
            sunoKey = clean(com.example.BuildConfig.SUNO_API_KEY),
            geminiKey = clean(com.example.BuildConfig.GEMINI_API_KEY)
        )
    }

    private fun defaultMemories(): List<MemoryEntry> = listOf(
        MemoryEntry(
            key = "arhitectura_eva1",
            value = "EVA-1 v2.1.0 integrează 9 sisteme AI autonome: Chat Multimodal, Agent ReAct, Code Studio, Multimedia Studio, Swarm Mesh 5-Faze, Quantum Context Zero-UI, Offline LLM WebGPU, OS Orchestrator și Auto-Genesis.",
            category = MemoryCategory.FACT,
            importance = 0.95f
        ),
        MemoryEntry(
            key = "limba_preferata",
            value = "Română — explicații tehnice clare, structurate și exemple de cod complete.",
            category = MemoryCategory.USER_PREF,
            importance = 0.9f
        )
    )

    private fun defaultCodeProjects(): List<CodeProject> = listOf(
        CodeProject(
            title = "Swarm Load Balancer Benchmark",
            language = CodeLanguage.KOTLIN,
            code = CodeLanguage.KOTLIN.sampleCode
        ),
        CodeProject(
            title = "Fibonacci Generator",
            language = CodeLanguage.PYTHON,
            code = CodeLanguage.PYTHON.sampleCode
        )
    )

    suspend fun addMemory(entry: MemoryEntry) {
        context.evaDataStore.edit { prefs ->
            val current = runCatching {
                json.decodeFromString<List<MemoryEntry>>(prefs[KEY_MEMORIES] ?: "")
            }.getOrDefault(defaultMemories())
            val filtered = current.filterNot { it.key.equals(entry.key, ignoreCase = true) }
            val updated = (listOf(entry) + filtered).take(100)
            prefs[KEY_MEMORIES] = json.encodeToString(updated)
        }
    }

    suspend fun deleteMemory(id: String) {
        context.evaDataStore.edit { prefs ->
            val current = runCatching {
                json.decodeFromString<List<MemoryEntry>>(prefs[KEY_MEMORIES] ?: "")
            }.getOrDefault(defaultMemories())
            prefs[KEY_MEMORIES] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    suspend fun saveSession(session: ConversationSession) {
        context.evaDataStore.edit { prefs ->
            val current = runCatching {
                json.decodeFromString<List<ConversationSession>>(prefs[KEY_SESSIONS] ?: "")
            }.getOrDefault(emptyList())
            val filtered = current.filterNot { it.id == session.id }
            val updated = (listOf(session) + filtered).take(50)
            prefs[KEY_SESSIONS] = json.encodeToString(updated)
        }
    }

    suspend fun deleteSession(id: String) {
        context.evaDataStore.edit { prefs ->
            val current = runCatching {
                json.decodeFromString<List<ConversationSession>>(prefs[KEY_SESSIONS] ?: "")
            }.getOrDefault(emptyList())
            prefs[KEY_SESSIONS] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    suspend fun saveSystemPrompt(prompt: String) {
        context.evaDataStore.edit { prefs -> prefs[KEY_SYSTEM_PROMPT] = prompt }
    }

    suspend fun incrementEvolution(): Int {
        var next = 1
        context.evaDataStore.edit { prefs ->
            next = (prefs[KEY_EVOLUTION_COUNT] ?: 0) + 1
            prefs[KEY_EVOLUTION_COUNT] = next
        }
        return next
    }

    suspend fun saveEngine(engine: AiEngine) {
        context.evaDataStore.edit { prefs -> prefs[KEY_SELECTED_ENGINE] = engine.name }
    }

    suspend fun saveThinking(enabled: Boolean) {
        context.evaDataStore.edit { prefs -> prefs[KEY_EXTENDED_THINKING] = enabled }
    }

    suspend fun saveWebSearch(enabled: Boolean) {
        context.evaDataStore.edit { prefs -> prefs[KEY_WEB_SEARCH] = enabled }
    }

    suspend fun saveAutoGenesis(enabled: Boolean) {
        context.evaDataStore.edit { prefs -> prefs[KEY_AUTO_GENESIS] = enabled }
    }

    suspend fun saveApiKeys(keys: StoredApiKeys) {
        context.evaDataStore.edit { prefs ->
            prefs[KEY_API_KEYS] = json.encodeToString(keys)
        }
    }

    suspend fun saveCodeProjects(projects: List<CodeProject>) {
        context.evaDataStore.edit { prefs ->
            prefs[KEY_CODE_PROJECTS] = json.encodeToString(projects.take(30))
        }
    }

    suspend fun saveGeneratedMedia(mediaList: List<GeneratedMedia>) {
        context.evaDataStore.edit { prefs ->
            // Prevenim blocarea Preferences DataStore și OutOfMemoryError eliminând blob-urile multi-cadru Base64
            // din fișierul protobuf (imaginile și cadrele rămân salvate pe disc în localPath / preview_path / frame_paths).
            val compactList = mediaList.take(40).map { item ->
                if (item.localPath.isNotBlank() && item.metadata.isNotEmpty()) {
                    val cleanedMeta = item.metadata.toMutableMap().apply {
                        remove("anim_frames_b64")
                        val previewB64 = this["base64_preview"]
                        if (previewB64 != null && previewB64.length > 24_000) {
                            remove("base64_preview")
                        }
                    }
                    item.copy(metadata = cleanedMeta)
                } else {
                    item
                }
            }
            prefs[KEY_GENERATED_MEDIA] = json.encodeToString(compactList)
        }
    }
}
