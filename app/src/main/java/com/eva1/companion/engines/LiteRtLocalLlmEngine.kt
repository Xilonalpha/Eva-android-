package com.eva1.companion.engines

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** Real on-device .litertlm inference. Models are downloaded separately from the APK. */
class LiteRtLocalLlmEngine(private val context: Context) {
    data class Model(
        val id: String,
        val name: String,
        val repo: String,
        val fileName: String,
        val expectedBytes: Long,
        val description: String
    ) {
        val url: String get() = "https://huggingface.co/$repo/resolve/main/$fileName"
    }

    companion object {
        val MODELS = listOf(
            Model("litert:qwen3-0.6b", "Qwen3 0.6B LiteRT-LM", "litert-community/Qwen3-0.6B", "Qwen3-0.6B.litertlm", 586_000_000L, "Model compact pentru conversație locală; CPU/GPU."),
            Model("litert:gemma4-e2b", "Gemma 4 E2B LiteRT-LM", "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm", 2_580_000_000L, "Model multimodal mai puternic; necesită aproximativ 2.6 GB și RAM suplimentară.")
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedModelId: String? = null

    private val _status = MutableStateFlow("LiteRT-LM: niciun model încărcat")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun modelFile(modelId: String): File? {
        val model = MODELS.firstOrNull { it.id == modelId } ?: return null
        return File(File(context.filesDir, "litert-models"), model.fileName)
    }

    suspend fun downloadModel(modelId: String): Result<File> = withContext(Dispatchers.IO) {
        val model = MODELS.firstOrNull { it.id == modelId }
            ?: return@withContext Result.failure(IllegalArgumentException("Model LiteRT necunoscut: $modelId"))
        val dir = File(context.filesDir, "litert-models").apply { mkdirs() }
        val target = File(dir, model.fileName)
        if (target.isFile && target.length() >= model.expectedBytes * 95 / 100) {
            _downloadProgress.value = 1f
            _status.value = "Model verificat local: ${model.name}"
            return@withContext Result.success(target)
        }
        val temp = File(dir, model.fileName + ".part")
        _isDownloading.value = true
        _downloadProgress.value = 0f
        try {
            val request = Request.Builder().url(model.url).header("User-Agent", "EVA-1-Android").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Descărcare HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("Răspuns fără conținut")
                val total = body.contentLength().takeIf { it > 0 } ?: model.expectedBytes
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var count: Int
                        var downloaded = 0L
                        while (input.read(buffer).also { count = it } >= 0) {
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            downloaded += count
                            _downloadProgress.value = (downloaded.toFloat() / total).coerceIn(0f, 0.999f)
                        }
                        output.fd.sync()
                    }
                }
            }
            // Do not accept HTML/error pages or truncated downloads as models.
            if (temp.length() < model.expectedBytes * 95 / 100) {
                throw IllegalStateException("Model incomplet: ${temp.length()} / aproximativ ${model.expectedBytes} bytes")
            }
            // Container-format validation is delegated to the LiteRT-LM native runtime.
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IllegalStateException("Nu pot finaliza fișierul model")
            _downloadProgress.value = 1f
            _status.value = "Descărcare terminată. Modelul va fi validat de runtime la încărcare."
            Result.success(target)
        } catch (t: Throwable) {
            temp.delete()
            _status.value = "Eroare descărcare: ${t.message ?: "necunoscută"}"
            Result.failure(t)
        } finally {
            _isDownloading.value = false
        }
    }

    suspend fun loadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val model = MODELS.firstOrNull { it.id == modelId }
                ?: return@withLock Result.failure(IllegalArgumentException("Model LiteRT necunoscut"))
            val file = modelFile(modelId)
            if (file == null || !file.isFile || file.length() < model.expectedBytes * 95 / 100) {
                _status.value = "Modelul ${model.name} nu este descărcat complet."
                _isReady.value = false
                return@withLock Result.failure(IllegalStateException("Model LiteRT lipsă sau incomplet"))
            }
            try {
                conversation?.close()
                engine?.close()
                conversation = null
                engine = null
                _isReady.value = false
                _status.value = "Încarc ${model.name} pe CPU/GPU..."
                val newEngine = try {
                    Engine(EngineConfig(modelPath = file.absolutePath, backend = Backend.GPU(), cacheDir = context.cacheDir.absolutePath)).also { it.initialize() }
                } catch (_: Throwable) {
                    Engine(EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU(), cacheDir = context.cacheDir.absolutePath)).also { it.initialize() }
                }
                val newConversation = newEngine.createConversation(
                    ConversationConfig(systemInstruction = Contents.of("Ești EVA-1. Vorbește natural, cald, clar și concis în limba utilizatorului. Păstrează contextul conversației și nu inventa acțiuni executate."))
                )
                engine = newEngine
                conversation = newConversation
                loadedModelId = modelId
                _isReady.value = true
                _status.value = "Activ offline: ${model.name} • LiteRT-LM"
                Result.success(Unit)
            } catch (t: Throwable) {
                runCatching { engine?.close() }
                engine = null
                conversation = null
                loadedModelId = null
                _isReady.value = false
                _status.value = "Runtime LiteRT-LM nu a putut încărca modelul: ${t.message}"
                Result.failure(t)
            }
        }
    }

    suspend fun generate(
        prompt: String,
        systemPrompt: String,
        history: List<com.eva1.companion.Message> = emptyList()
    ): Result<String> = generateContents(buildContext(prompt, systemPrompt, history))

    suspend fun generateWithImage(
        prompt: String,
        systemPrompt: String,
        imageBytes: ByteArray,
        history: List<com.eva1.companion.Message> = emptyList()
    ): Result<String> {
        if (imageBytes.isEmpty()) return Result.failure(IllegalArgumentException("Imagine goală"))
        val contextText = buildContext(prompt, systemPrompt, history)
        return generateContents(Contents.of(Content.ImageBytes(imageBytes), Content.Text(contextText)))
    }

    private fun buildContext(
        prompt: String,
        systemPrompt: String,
        history: List<com.eva1.companion.Message>
    ): String = buildString {
        append("Instrucțiuni: ").append(systemPrompt).append("\n\n")
        history.takeLast(8).forEach { message ->
            append(if (message.role == com.eva1.companion.MessageRole.USER) "Utilizator: " else "EVA: ")
            append(message.content).append('\n')
        }
        append("Utilizator: ").append(prompt).append("\nEVA:")
    }

    private suspend fun generateContents(contents: Contents): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val activeConversation = conversation
                ?: return@withLock Result.failure(IllegalStateException("Niciun model LiteRT-LM încărcat"))
            try {
                val output = StringBuilder()
                activeConversation.sendMessageAsync(contents).collect { message ->
                    val chunk = message.text
                    if (!chunk.isNullOrEmpty()) output.append(chunk)
                }
                val answer = output.toString().trim()
                if (answer.isBlank()) Result.failure(IllegalStateException("Modelul nu a produs text")) else Result.success(answer)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { conversation?.close() }
            runCatching { engine?.close() }
            conversation = null
            engine = null
            loadedModelId = null
            _isReady.value = false
            _status.value = "LiteRT-LM închis"
        }
    }
}
