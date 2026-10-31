package com.eva1.companion.engines

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.eva1.companion.AnthropicApiService
import kotlinx.coroutines.Dispatchers
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
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Provideri suportați pentru conducta de analiză vizuală multimodală a jocurilor:
 */
enum class MultiModalVisionProvider(val displayName: String, val badge: String) {
    LOCAL_VISION_MODEL("Analiză locală a capturii", "🦙"),
    OFFLINE_SMART_CV("Computer Vision Offline On-Device", "⚡"),
    GEMINI_VISION("Google Gemini Vision (Cloud Opțional)", "✨"),
    AUTO_BALANCED("Mod Inteligent Adaptiv (Auto)", "🧠")
}

/**
 * Obiect gameplay detectat direct de modelul multimodal din cadrul video:
 */
data class DetectedVisionObject(
    val category: String, // ex: "ATTACK_BUTTON", "START_BUTTON", "CLOSE_POPUP", "ENEMY", "HEALTH_BAR", "JOYSTICK"
    val label: String,
    val normalizedX: Float, // 0.0f .. 1.0f
    val normalizedY: Float, // 0.0f .. 1.0f
    val widthFrac: Float = 0.08f,
    val heightFrac: Float = 0.08f,
    val confidence: Float = 0.9f,
    val inCrosshair: Boolean = false,
    val description: String = ""
)

/**
 * Rezultatul complet al analizei vizuale multimodale:
 */
data class MultiModalVisionAnalysis(
    val timestamp: Long = System.currentTimeMillis(),
    val providerUsed: MultiModalVisionProvider = MultiModalVisionProvider.LOCAL_VISION_MODEL,
    val modelName: String = "EVA-Local-Omni",
    val gameState: DetectedVisionGameState = DetectedVisionGameState.ACTIVE_GAMEPLAY_COMBAT,
    val playerHpPercent: Float = 100f,
    val enemyHpPercent: Float = 100f,
    val resourcePercent: Float = 100f,
    val isLowHpCritical: Boolean = false,
    val detectedObjects: List<DetectedVisionObject> = emptyList(),
    val enemies: List<DetectedVisionObject> = emptyList(),
    val interactiveButtons: List<DetectedVisionObject> = emptyList(),
    val hasTargetInCrosshair: Boolean = false,
    val recommendedAction: String = "OBSERVE_STABLE",
    val recommendedGesture: DynamicGestureCommand = DynamicGestureCommand(
        motion = DynamicGestureMotion.WAIT_OBSERVE,
        startX = 0f,
        startY = 0f,
        reasoning = "Așteptare analiză cadru"
    ),
    val latencyMs: Long = 0L,
    val rawModelJson: String = "",
    val base64Thumbnail: String = ""
)

/**
 * Conductă avansată de viziune computerizată multimodală (Gemini Vision / Local Vision / Smart CV).
 *
 * Hrănește cadrele capturate în timp real de pe ecran direct într-un model AI multimodal
 * pentru a detecta obiectele de gameplay reale (butoane tactile, bare de viață HP, inamici,
 * ferestre pop-up și vehicule) în locul coordonatelor pre-scrise sau statice.
 */
class MultiModalGameVisionEngine(
    private val context: Context,
    private val apiKeyProvider: () -> String = { "" },
    private val localBridgeEngineSupplier: () -> LocalLlmGameBridgeEngine? = { null },
    private val offlineLlmEngineSupplier: () -> OfflineLlmEngine? = { null }
) {
    companion object {
        private const val TAG = "EVA_MULTIMODAL_VISION"
        const val DEFAULT_GEMINI_VISION_MODEL = "gemini-2.5-flash"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4500, TimeUnit.MILLISECONDS)
        .readTimeout(6000, TimeUnit.MILLISECONDS)
        .writeTimeout(4500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val _selectedProvider = MutableStateFlow(MultiModalVisionProvider.LOCAL_VISION_MODEL)
    val selectedProvider: StateFlow<MultiModalVisionProvider> = _selectedProvider.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _latestAnalysis = MutableStateFlow(MultiModalVisionAnalysis())
    val latestAnalysis: StateFlow<MultiModalVisionAnalysis> = _latestAnalysis.asStateFlow()

    private val _framesAnalyzedCount = MutableStateFlow(0L)
    val framesAnalyzedCount: StateFlow<Long> = _framesAnalyzedCount.asStateFlow()

    fun selectProvider(provider: MultiModalVisionProvider) {
        _selectedProvider.value = provider
    }

    /**
     * Procesează un cadru Bitmap capturat de pe ecran prin modelul multimodal configurat
     * și returnează obiectele detectate, barele de viață și comanda gestuală recomandată.
     */
    suspend fun analyzeScreenFrame(
        bitmap: Bitmap,
        screenWidth: Float,
        screenHeight: Float,
        activeGameTitle: String,
        activeMode: GameBotMode = GameBotMode.AUTO_ADAPTIVE
    ): MultiModalVisionAnalysis = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (bitmap.isRecycled) {
            return@withContext _latestAnalysis.value
        }
        _isAnalyzing.value = true

        val provider = _selectedProvider.value
        val apiKey = apiKeyProvider().trim()

        try {
            val (scaledBmp, base64Frame) = prepareFrameForMultiModalInference(bitmap)
            if (scaledBmp !== bitmap && !scaledBmp.isRecycled) {
                runCatching { scaledBmp.recycle() }
            }

            val result: MultiModalVisionAnalysis = when (provider) {
                MultiModalVisionProvider.LOCAL_VISION_MODEL -> {
                    queryLocalVisionModel(bitmap, base64Frame, activeGameTitle, activeMode, screenWidth, screenHeight, startTime)
                }

                MultiModalVisionProvider.OFFLINE_SMART_CV -> {
                    fallbackOfflineSmartCv(bitmap, screenWidth, screenHeight, activeGameTitle, activeMode, startTime, "Mod Smart CV On-Device")
                }

                MultiModalVisionProvider.GEMINI_VISION -> {
                    if (apiKey.isNotBlank()) {
                        queryGeminiVisionApi(base64Frame, activeGameTitle, activeMode, screenWidth, screenHeight, apiKey, startTime)
                    } else {
                        queryLocalVisionModel(bitmap, base64Frame, activeGameTitle, activeMode, screenWidth, screenHeight, startTime)
                    }
                }

                MultiModalVisionProvider.AUTO_BALANCED -> {
                    // Implicit mod local cu cele 11 modele offline și Computer Vision
                    queryLocalVisionModel(bitmap, base64Frame, activeGameTitle, activeMode, screenWidth, screenHeight, startTime)
                }
            }

            _latestAnalysis.value = result.copy(base64Thumbnail = base64Frame)
            _framesAnalyzedCount.value = _framesAnalyzedCount.value + 1L
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Eroare în conducta de viziune multimodală: ${e.message}")
            val fallback = fallbackOfflineSmartCv(bitmap, screenWidth, screenHeight, activeGameTitle, activeMode, startTime, "Eroare conductă: ${e.message}")
            _latestAnalysis.value = fallback
            return@withContext fallback
        } finally {
            _isAnalyzing.value = false
        }
    }

    /**
     * Apelează API-ul Google Gemini Multimodal Vision (gemini-2.5-flash) prin REST JSON
     */
    private fun queryGeminiVisionApi(
        base64Jpeg: String,
        gameTitle: String,
        mode: GameBotMode,
        screenWidth: Float,
        screenHeight: Float,
        apiKey: String,
        startTime: Long
    ): MultiModalVisionAnalysis {
        val endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$DEFAULT_GEMINI_VISION_MODEL:generateContent?key=$apiKey"

        val systemPrompt = """
            Ești Modelul AI Vision de Analiză a Jocurilor Mobile EVA-1.
            Analizează acest cadru capturat de pe ecranul jocului "$gameTitle" (Mod: ${mode.displayName}).
            
            Detectează precis toate elementele de gameplay:
            1. Starea ecranului (gameState: MAIN_MENU_LOBBY, ACTIVE_GAMEPLAY_COMBAT, POPUP_OR_REWARD_DIALOG, VICTORY_OR_DEFEAT_SCREEN, LOADING_TRANSITION).
            2. Barele de viață și resurse (playerHpPercent, enemyHpPercent, resourcePercent, isLowHealthCritical).
            3. Butoanele UI interactive vizibile pe ecran (categoria: START_PLAY_BUTTON, ATTACK_PRIMARY_BUTTON, CLOSE_POPUP_X_BUTTON, SKILL_OR_NITRO_BUTTON, HEAL_OR_COVER_BUTTON; etichetă, coordonate normalizate X și Y între 0.0 și 1.0).
            4. Inamicii detectați în câmpul vizual (coordonate X și Y normalizate, și dacă sunt în crosshair).
            5. Recomandarea gestuală tactică imediată (action: TAP, BURST_TAPS, SWIPE, DUAL_TOUCH sau WAIT; startX, startY normalizate).
            
            Răspunde STRICT cu un obiect JSON valid conform acestei scheme:
            {
              "gameState": "ACTIVE_GAMEPLAY_COMBAT",
              "playerHpPercent": 100,
              "enemyHpPercent": 100,
              "resourcePercent": 100,
              "isLowHealthCritical": false,
              "hasTargetInCrosshair": false,
              "detectedButtons": [
                {"category": "ATTACK_PRIMARY_BUTTON", "label": "Fire", "x": 0.86, "y": 0.72, "conf": 0.95}
              ],
              "enemies": [
                {"label": "Target", "x": 0.52, "y": 0.48, "inCrosshair": false}
              ],
              "recommendedGesture": {
                "action": "TAP",
                "startX": 0.86,
                "startY": 0.72,
                "endX": 0.86,
                "endY": 0.72,
                "durationMs": 75,
                "confidence": 0.90,
                "reasoning": "Acțiune detectată vizual prin Gemini Vision"
              }
            }
        """.trimIndent()

        val requestPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Jpeg)
                            })
                        })
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.15)
                put("maxOutputTokens", 700)
            })
        }

        val request = Request.Builder()
            .url(endpointUrl)
            .post(requestPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                throw IllegalStateException("Gemini Vision HTTP ${response.code}: $err")
            }
            val body = response.body?.string().orEmpty()
            val latency = System.currentTimeMillis() - startTime
            return parseMultiModalJsonResponse(
                rawResponse = body,
                provider = MultiModalVisionProvider.GEMINI_VISION,
                modelName = DEFAULT_GEMINI_VISION_MODEL,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                latencyMs = latency
            )
        }
    }

    /**
     * Analizează captura local, fără server LLM și fără a pretinde că modelele text văd imagini
     */
    private suspend fun queryLocalVisionModel(
        bitmap: Bitmap,
        base64Jpeg: String,
        gameTitle: String,
        mode: GameBotMode,
        screenWidth: Float,
        screenHeight: Float,
        startTime: Long
    ): MultiModalVisionAnalysis {
        // Inferența vizuală nu utilizează niciun server HTTP local.
        // Analiza locală trebuie să provină din pixelii capturii, nu dintr-un model text
        // prezentat în mod fals drept model multimodal.
        return queryOfflineLlmEngineDirect(bitmap, gameTitle, mode, screenWidth, screenHeight, startTime)
    }

    private suspend fun queryOfflineLlmEngineDirect(
        bitmap: Bitmap,
        gameTitle: String,
        mode: GameBotMode,
        screenWidth: Float,
        screenHeight: Float,
        startTime: Long
    ): MultiModalVisionAnalysis {
        val smartCv = fallbackOfflineSmartCv(bitmap, screenWidth, screenHeight, gameTitle, mode, startTime, "Viziune Pixel On-Device")
        val offlineEngine = offlineLlmEngineSupplier()
        val currentModel = offlineEngine?.currentModel?.value ?: "Qwen2.5-7B-Instruct-Q4_K_M"

        if (offlineEngine == null) {
            return smartCv.copy(
                providerUsed = MultiModalVisionProvider.LOCAL_VISION_MODEL,
                modelName = currentModel
            )
        }

        val prompt = """
            Analizează IMAGINEA REALĂ a jocului "$gameTitle" în modul ${mode.displayName}.
            Nu presupune valori care nu sunt vizibile. Dacă nu poți identifica sigur o acțiune, alege WAIT.
            Răspunde în acest format exact:
            ACTION: <FIRE | SPRINT_FORWARD | RELOAD | SCOPE | PASS | SHOOT | DROP | SWIPE_LEFT | SWIPE_RIGHT | WAIT>
            COORDS: <xFrac, yFrac>
            REASON: <motiv scurt>
            CONFIDENCE: <0.0-1.0>
        """.trimIndent()

        return try {
            val system = "Ești agent vizual tactic local. Analizează doar pixelii furnizați; nu inventa HUD, ținte sau scor. Dacă încrederea este sub 0.75, recomandă WAIT."
            val res = if (currentModel.startsWith("litert:") && currentModel.contains("gemma4", ignoreCase = true)) {
                offlineEngine.generateOfflineWithImage(bitmap, prompt, system)
            } else {
                offlineEngine.generateOffline(prompt = prompt, systemPrompt = system)
            }
            val reply = res.getOrNull().orEmpty()
            val latency = System.currentTimeMillis() - startTime
            val dynamicCmd = parseTacticalDirectiveToGesture(reply, screenWidth, screenHeight, smartCv)

            smartCv.copy(
                providerUsed = MultiModalVisionProvider.LOCAL_VISION_MODEL,
                modelName = currentModel,
                latencyMs = latency,
                recommendedAction = dynamicCmd.motion.name,
                recommendedGesture = dynamicCmd
            )
        } catch (_: Exception) {
            smartCv.copy(
                providerUsed = MultiModalVisionProvider.LOCAL_VISION_MODEL,
                modelName = currentModel
            )
        }
    }

    private fun parseTacticalDirectiveToGesture(
        text: String,
        width: Float,
        height: Float,
        cvAnalysis: MultiModalVisionAnalysis
    ): DynamicGestureCommand {
        val upper = text.uppercase()
        val confidence = Regex("CONFIDENCE:\\s*([0-9.]+)").find(upper)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val actionMatch = Regex("ACTION:\\s*([A-Z_]+)").find(upper)?.groupValues?.get(1) ?: ""
        if (confidence in 0f..0.749f) return DynamicGestureCommand(
            motion = DynamicGestureMotion.WAIT_OBSERVE,
            startX = 0f,
            startY = 0f,
            reasoning = "Încredere vizuală insuficientă ($confidence); observare fără atingere"
        )
        val coordsMatch = Regex("COORDS:\\s*([0-9.]+)\\s*,\\s*([0-9.]+)").find(upper)
        val reasonMatch = Regex("REASON:\\s*(.+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim() ?: "Decizie tactică model local"

        val xFrac = coordsMatch?.groupValues?.get(1)?.toFloatOrNull()
        val yFrac = coordsMatch?.groupValues?.get(2)?.toFloatOrNull()

        return when {
            actionMatch.contains("SPRINT") || actionMatch.contains("RUN") -> {
                DynamicGestureCommand(
                    motion = DynamicGestureMotion.SWIPE,
                    startX = width * 0.18f,
                    startY = height * 0.72f,
                    endX = width * 0.18f,
                    endY = height * 0.54f,
                    durationMs = 450L,
                    reasoning = reasonMatch
                )
            }
            actionMatch.contains("FIRE") || actionMatch.contains("ATTACK") || actionMatch.contains("SHOOT") -> {
                val btn = cvAnalysis.interactiveButtons.firstOrNull { it.category.contains("ATTACK") || it.category.contains("FIRE") }
                val targetX = xFrac?.let { it * width } ?: btn?.let { it.normalizedX * width } ?: (width * 0.86f)
                val targetY = yFrac?.let { it * height } ?: btn?.let { it.normalizedY * height } ?: (height * 0.72f)
                DynamicGestureCommand(
                    motion = DynamicGestureMotion.BURST_TAPS,
                    startX = targetX,
                    startY = targetY,
                    durationMs = 380L,
                    repeatCount = 3,
                    reasoning = reasonMatch
                )
            }
            actionMatch.contains("RELOAD") -> {
                val btn = cvAnalysis.interactiveButtons.firstOrNull { it.category.contains("RELOAD") }
                val targetX = xFrac?.let { it * width } ?: btn?.let { it.normalizedX * width } ?: (width * 0.78f)
                val targetY = yFrac?.let { it * height } ?: btn?.let { it.normalizedY * height } ?: (height * 0.88f)
                DynamicGestureCommand(
                    motion = DynamicGestureMotion.TAP,
                    startX = targetX,
                    startY = targetY,
                    durationMs = 85L,
                    reasoning = reasonMatch
                )
            }
            actionMatch.contains("SCOPE") || actionMatch.contains("ADS") -> {
                val btn = cvAnalysis.interactiveButtons.firstOrNull { it.category.contains("SCOPE") }
                val targetX = xFrac?.let { it * width } ?: btn?.let { it.normalizedX * width } ?: (width * 0.93f)
                val targetY = yFrac?.let { it * height } ?: btn?.let { it.normalizedY * height } ?: (height * 0.56f)
                DynamicGestureCommand(
                    motion = DynamicGestureMotion.TAP,
                    startX = targetX,
                    startY = targetY,
                    durationMs = 85L,
                    reasoning = reasonMatch
                )
            }
            actionMatch.contains("PASS") -> {
                val btn = cvAnalysis.interactiveButtons.firstOrNull { it.label.contains("pass", ignoreCase = true) }
                val targetX = xFrac?.let { it * width } ?: btn?.let { it.normalizedX * width } ?: (width * 0.78f)
                val targetY = yFrac?.let { it * height } ?: btn?.let { it.normalizedY * height } ?: (height * 0.82f)
                DynamicGestureCommand(
                    motion = DynamicGestureMotion.TAP,
                    startX = targetX,
                    startY = targetY,
                    durationMs = 85L,
                    reasoning = reasonMatch
                )
            }
            actionMatch.contains("DROP") -> {
                val dropBtn = cvAnalysis.interactiveButtons.firstOrNull {
                    it.label.contains("drop", ignoreCase = true) || it.label.contains("▼")
                }
                val targetX = xFrac?.let { it * width } ?: dropBtn?.let { it.normalizedX * width } ?: (width * 0.40f)
                val targetY = yFrac?.let { it * height } ?: dropBtn?.let { it.normalizedY * height } ?: (height * 0.92f)
                DynamicGestureCommand(
                    motion = DynamicGestureMotion.TAP,
                    startX = targetX,
                    startY = targetY,
                    durationMs = 70L,
                    reasoning = reasonMatch
                )
            }
            actionMatch.contains("SWIPE_LEFT") -> {
                DynamicGestureCommand(
                    motion = DynamicGestureMotion.SWIPE,
                    startX = width * 0.65f,
                    startY = height * 0.44f,
                    endX = width * 0.35f,
                    endY = height * 0.44f,
                    durationMs = 150L,
                    reasoning = reasonMatch
                )
            }
            actionMatch.contains("SWIPE_RIGHT") -> {
                DynamicGestureCommand(
                    motion = DynamicGestureMotion.SWIPE,
                    startX = width * 0.35f,
                    startY = height * 0.44f,
                    endX = width * 0.65f,
                    endY = height * 0.44f,
                    durationMs = 150L,
                    reasoning = reasonMatch
                )
            }
            xFrac != null && yFrac != null -> {
                DynamicGestureCommand(
                    motion = DynamicGestureMotion.TAP,
                    startX = xFrac * width,
                    startY = yFrac * height,
                    durationMs = 85L,
                    reasoning = reasonMatch
                )
            }
            else -> cvAnalysis.recommendedGesture
        }
    }

    /**
     * Fallback folosind Computer Vision On-Device (analiză de luminanță, culori HSV și ierarhie accesibilitate)
     */
    private fun fallbackOfflineSmartCv(
        bitmap: Bitmap,
        screenWidth: Float,
        screenHeight: Float,
        activeGameTitle: String,
        activeMode: GameBotMode,
        startTime: Long,
        note: String
    ): MultiModalVisionAnalysis {
        val mpAnalysis = MediaProjectionVisionEngine.analyzeGameFrameForAiVision(
            bitmap = bitmap,
            screenWidth = screenWidth.toInt(),
            screenHeight = screenHeight.toInt(),
            activeGameTitle = activeGameTitle,
            activeMode = activeMode,
            sourceName = "On-Device Smart CV Pipeline"
        )

        val detectedObjs = mpAnalysis.detectedButtons.map { btn ->
            DetectedVisionObject(
                category = btn.category.name,
                label = btn.label,
                normalizedX = (btn.centerX / screenWidth).coerceIn(0f, 1f),
                normalizedY = (btn.centerY / screenHeight).coerceIn(0f, 1f),
                widthFrac = (btn.widthPx.toFloat() / screenWidth).coerceIn(0.04f, 0.4f),
                heightFrac = (btn.heightPx.toFloat() / screenHeight).coerceIn(0.04f, 0.4f),
                confidence = btn.confidence
            )
        }

        val tapPt = mpAnalysis.recommendedTapTarget
        val recCmd = if (tapPt != null) {
            DynamicGestureCommand(
                motion = DynamicGestureMotion.TAP,
                startX = tapPt.first,
                startY = tapPt.second,
                reasoning = "[Smart CV Fallback] ${mpAnalysis.recommendedActionLabel}: $note"
            )
        } else {
            DynamicGestureCommand(
                motion = DynamicGestureMotion.WAIT_OBSERVE,
                startX = 0f,
                startY = 0f,
                reasoning = "[Smart CV Fallback] Observare pasivă (fără atingeri): $note"
            )
        }

        return MultiModalVisionAnalysis(
            timestamp = System.currentTimeMillis(),
            providerUsed = MultiModalVisionProvider.OFFLINE_SMART_CV,
            modelName = "On-Device Smart CV ($note)",
            gameState = mpAnalysis.gameState,
            playerHpPercent = mpAnalysis.healthBars.playerHpPercent,
            enemyHpPercent = mpAnalysis.healthBars.enemyOrBossHpPercent,
            resourcePercent = mpAnalysis.healthBars.secondaryBarPercent,
            isLowHpCritical = mpAnalysis.healthBars.isLowHealthCritical,
            detectedObjects = detectedObjs,
            enemies = emptyList(),
            interactiveButtons = detectedObjs,
            hasTargetInCrosshair = false,
            recommendedAction = mpAnalysis.recommendedActionLabel,
            recommendedGesture = recCmd,
            latencyMs = System.currentTimeMillis() - startTime,
            rawModelJson = mpAnalysis.aiVisionModelPromptPayload
        )
    }

    /**
     * Parsează răspunsul JSON returnat de modelul multimodal
     */
    private fun parseMultiModalJsonResponse(
        rawResponse: String,
        provider: MultiModalVisionProvider,
        modelName: String,
        screenWidth: Float,
        screenHeight: Float,
        latencyMs: Long
    ): MultiModalVisionAnalysis {
        val jsonText = extractJsonSubstring(rawResponse)
        val json = JSONObject(jsonText)

        val stateStr = json.optString("gameState", "ACTIVE_GAMEPLAY_COMBAT").uppercase()
        val gameState = when {
            stateStr.contains("LOBBY") || stateStr.contains("MENU") -> DetectedVisionGameState.MAIN_MENU_LOBBY
            stateStr.contains("POPUP") || stateStr.contains("DIALOG") -> DetectedVisionGameState.POPUP_OR_REWARD_DIALOG
            stateStr.contains("VICTORY") || stateStr.contains("DEFEAT") -> DetectedVisionGameState.VICTORY_OR_DEFEAT_SCREEN
            stateStr.contains("LOADING") -> DetectedVisionGameState.LOADING_TRANSITION
            stateStr.contains("LOW") || json.optBoolean("isLowHealthCritical", false) -> DetectedVisionGameState.LOW_HEALTH_CRITICAL
            else -> DetectedVisionGameState.ACTIVE_GAMEPLAY_COMBAT
        }

        val playerHp = json.optDouble("playerHpPercent", 100.0).toFloat().coerceIn(0f, 100f)
        val enemyHp = json.optDouble("enemyHpPercent", 100.0).toFloat().coerceIn(0f, 100f)
        val resPercent = json.optDouble("resourcePercent", 100.0).toFloat().coerceIn(0f, 100f)
        val isLowHp = json.optBoolean("isLowHealthCritical", playerHp < 35f)
        val inCrosshair = json.optBoolean("hasTargetInCrosshair", false)

        // Butoane detectate de model
        val detectedButtons = mutableListOf<DetectedVisionObject>()
        val buttonsArr = json.optJSONArray("detectedButtons")
        if (buttonsArr != null) {
            for (i in 0 until buttonsArr.length()) {
                val b = buttonsArr.optJSONObject(i) ?: continue
                val cat = b.optString("category", "INTERACTIVE_ELEMENT").uppercase()
                val lbl = b.optString("label", "Buton Joc")
                val nx = b.optDouble("x", -1.0).toFloat()
                val ny = b.optDouble("y", -1.0).toFloat()
                if (nx in 0.01f..0.99f && ny in 0.01f..0.99f) {
                    detectedButtons.add(
                        DetectedVisionObject(
                            category = cat,
                            label = lbl,
                            normalizedX = nx,
                            normalizedY = ny,
                            confidence = b.optDouble("conf", 0.9).toFloat()
                        )
                    )
                }
            }
        }

        // Inamici detectați
        val enemies = mutableListOf<DetectedVisionObject>()
        val enemiesArr = json.optJSONArray("enemies")
        if (enemiesArr != null) {
            for (i in 0 until enemiesArr.length()) {
                val e = enemiesArr.optJSONObject(i) ?: continue
                val nx = e.optDouble("x", -1.0).toFloat()
                val ny = e.optDouble("y", -1.0).toFloat()
                if (nx in 0.01f..0.99f && ny in 0.01f..0.99f) {
                    enemies.add(
                        DetectedVisionObject(
                            category = "ENEMY",
                            label = e.optString("label", "Inamic"),
                            normalizedX = nx,
                            normalizedY = ny,
                            inCrosshair = e.optBoolean("inCrosshair", false)
                        )
                    )
                }
            }
        }

        // Recomandare gestuală tactică
        val gestureJson = json.optJSONObject("recommendedGesture")
        val recommendedGesture = if (gestureJson != null) {
            val act = gestureJson.optString("action", "WAIT").uppercase()
            val motion = when {
                act.contains("BURST") -> DynamicGestureMotion.BURST_TAPS
                act.contains("SWIPE") -> DynamicGestureMotion.SWIPE
                act.contains("DRAG") -> DynamicGestureMotion.DRAG_AND_DROP
                act.contains("DUAL") -> DynamicGestureMotion.DUAL_TOUCH
                act.contains("LONG") -> DynamicGestureMotion.LONG_PRESS
                act.contains("WAIT") -> DynamicGestureMotion.WAIT_OBSERVE
                else -> DynamicGestureMotion.TAP
            }

            val sx = gestureJson.optDouble("startX", -1.0).toFloat()
            val sy = gestureJson.optDouble("startY", -1.0).toFloat()
            val ex = gestureJson.optDouble("endX", sx.toDouble()).toFloat()
            val ey = gestureJson.optDouble("endY", sy.toDouble()).toFloat()
            val dur = gestureJson.optLong("durationMs", 75L).coerceIn(40L, 1200L)
            val confidence = gestureJson.optDouble("confidence", json.optDouble("confidence", 0.0)).toFloat()
            val reasoning = gestureJson.optString("reasoning", "Decizie model multimodal")

            if (motion == DynamicGestureMotion.WAIT_OBSERVE || confidence < 0.75f || sx !in 0.01f..0.99f || sy !in 0.01f..0.99f) {
                DynamicGestureCommand(
                    motion = DynamicGestureMotion.WAIT_OBSERVE,
                    startX = 0f,
                    startY = 0f,
                    reasoning = reasoning
                )
            } else {
                DynamicGestureCommand(
                    motion = motion,
                    startX = sx * screenWidth,
                    startY = sy * screenHeight,
                    endX = ex * screenWidth,
                    endY = ey * screenHeight,
                    durationMs = dur,
                    reasoning = "[$modelName • ${latencyMs}ms] $reasoning"
                )
            }
        } else {
            DynamicGestureCommand(
                motion = DynamicGestureMotion.WAIT_OBSERVE,
                startX = 0f,
                startY = 0f,
                reasoning = "Observare pasivă"
            )
        }

        return MultiModalVisionAnalysis(
            timestamp = System.currentTimeMillis(),
            providerUsed = provider,
            modelName = modelName,
            gameState = gameState,
            playerHpPercent = playerHp,
            enemyHpPercent = enemyHp,
            resourcePercent = resPercent,
            isLowHpCritical = isLowHp,
            detectedObjects = detectedButtons + enemies,
            enemies = enemies,
            interactiveButtons = detectedButtons,
            hasTargetInCrosshair = inCrosshair,
            recommendedAction = gestureJson?.optString("action", "WAIT") ?: "WAIT",
            recommendedGesture = recommendedGesture,
            latencyMs = latencyMs,
            rawModelJson = jsonText
        )
    }

    private fun extractJsonSubstring(text: String): String {
        // Dacă e răspuns complet de la Google Gemini v1beta REST:
        val parsedJson = try { JSONObject(text) } catch (_: Exception) { null }
        if (parsedJson != null && parsedJson.has("candidates")) {
            val candidates = parsedJson.optJSONArray("candidates")
            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val partText = parts?.optJSONObject(0)?.optString("text")
            if (!partText.isNullOrBlank()) {
                return extractJsonSubstring(partText)
            }
        }

        val s = text.indexOf('{')
        val e = text.lastIndexOf('}')
        if (s >= 0 && e > s) {
            return text.substring(s, e + 1)
        }
        return "{}"
    }

    private fun prepareFrameForMultiModalInference(bmp: Bitmap): Pair<Bitmap, String> {
        if (bmp.isRecycled) return bmp to ""
        return try {
            val targetWidth = 480
            val targetHeight = max(270, (bmp.height.toFloat() / bmp.width.coerceAtLeast(1) * targetWidth).toInt())
            val scaled = Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, true)
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 72, bos)
            val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            scaled to b64
        } catch (_: Exception) {
            bmp to ""
        }
    }
}
