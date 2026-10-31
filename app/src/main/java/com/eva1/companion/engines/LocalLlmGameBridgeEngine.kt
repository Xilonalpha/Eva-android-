package com.eva1.companion.engines

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Locale
import java.util.regex.Pattern

/**
 * Frecvența de intervenție a LLM-ului local în deciziile de joc:
 */
enum class GameBridgeTacticalFrequency(val displayName: String, val description: String) {
    ON_STALL_OR_STATIC("La Blocaje & Tranziții", "Intervine doar când ecranul este nemișcat de 2+ cicluri"),
    HYBRID_EVERY_3_STEPS("Hibrid Activ (Fiecare 3 pași)", "Supervizează constant jocul și dictează tactica la fiecare 3 pași"),
    VISION_DIRECTED_ONLY("Condus de Computer Vision", "Intervine doar când CV detectează inamici sau opțiuni multiple")
}

/** Setări ale strategiei locale, fără endpoint de rețea. */
data class LocalLlmEndpointConfig(
    val modelName: String = "model-offline-selectat",
    val isEnabled: Boolean = true,
    val tacticalFrequency: GameBridgeTacticalFrequency = GameBridgeTacticalFrequency.HYBRID_EVERY_3_STEPS
)

/**
 * Rezultatul unei decizii de joc generate de LLM-ul local:
 */
data class LocalGameDecisionResult(
    val actionType: String,             // ex: FIRE, RELOAD, HEAL, ATTACK, MENU_TAP, SWIPE_LEFT, SWIPE_RIGHT, WAIT
    val normalizedX: Float? = null,     // 0.0f .. 1.0f
    val normalizedY: Float? = null,     // 0.0f .. 1.0f
    val targetPixelX: Float? = null,
    val targetPixelY: Float? = null,
    val reasoning: String = "",
    val rawText: String = "",
    val latencyMs: Long = 0L,
    val engineSource: String = "",
    val isFallback: Boolean = false,
    val matchedLearnedGesture: LearnedUserGesture? = null
)

/**
 * Starea disponibilității motorului LLM local:
 */
sealed class LocalBridgeStatus {
    data class Connected(val latencyMs: Long, val message: String, val endpoint: String) : LocalBridgeStatus()
    data class OfflineStandby(val reason: String, val fallbackModel: String) : LocalBridgeStatus()
    object Checking : LocalBridgeStatus()
    object Disabled : LocalBridgeStatus()
}

/** Strategul de joc folosește exclusiv inferența motorului Android offline. */
class LocalLlmGameBridgeEngine(
    private val context: Context,
    private val offlineLlmEngine: OfflineLlmEngine?
) {
    companion object { private const val TAG = "EVA_GAME_DECISION" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Configurație curentă
    private val _config = MutableStateFlow(LocalLlmEndpointConfig())
    val config: StateFlow<LocalLlmEndpointConfig> = _config.asStateFlow()

    // Stare conexiune locală
    private val _status = MutableStateFlow<LocalBridgeStatus>(
        LocalBridgeStatus.OfflineStandby(
            "Fără server: deciziile sunt procesate de motorul offline disponibil",
            offlineLlmEngine?.currentModel?.value ?: "Model Intern"
        )
    )
    val status: StateFlow<LocalBridgeStatus> = _status.asStateFlow()

    // Ultima decizie tactică generată
    private val _latestDecision = MutableStateFlow<LocalGameDecisionResult?>(null)
    val latestDecision: StateFlow<LocalGameDecisionResult?> = _latestDecision.asStateFlow()

    init {
        // Verifică în fundal disponibilitatea motorului offline la pornire
        testConnectionAsync()
    }

    fun updateConfig(
        modelName: String = _config.value.modelName,
        isEnabled: Boolean = _config.value.isEnabled,
        frequency: GameBridgeTacticalFrequency = _config.value.tacticalFrequency
    ) {
        _config.value = LocalLlmEndpointConfig(modelName, isEnabled, frequency)
        testConnectionAsync()
    }

    fun testConnectionAsync() {
        if (!_config.value.isEnabled) {
            _status.value = LocalBridgeStatus.Disabled
            return
        }
        scope.launch {
            _status.value = LocalBridgeStatus.Checking
            val result = testConnectionSync()
            _status.value = if (result.first) {
                LocalBridgeStatus.Connected(result.second, "Motor offline disponibil", "on-device")
            } else {
                LocalBridgeStatus.OfflineStandby("Motor offline indisponibil sau neîncărcat", _config.value.modelName)
            }
        }
    }

    /** Disponibilitatea motorului nu se deduce dintr-un ping sau din catalogul modelelor. */
    suspend fun testConnectionSync(): Pair<Boolean, Long> {
        val engine = offlineLlmEngine ?: return false to 0L
        val start = System.currentTimeMillis()
        val result = runCatching {
            engine.generateOffline("Răspunde numai cu OK.", "Test scurt de inferență locală.")
        }.getOrNull()
        return (result?.getOrNull()?.isNotBlank() == true) to (System.currentTimeMillis() - start)
    }

    /**
     * Evaluează dacă pe pasul curent al jocului trebuie consultat LLM-ul local
     */
    fun shouldConsultOnStep(step: Int, staticStreak: Int): Boolean {
        if (!_config.value.isEnabled) return false
        return when (_config.value.tacticalFrequency) {
            GameBridgeTacticalFrequency.ON_STALL_OR_STATIC -> staticStreak >= 2
            GameBridgeTacticalFrequency.HYBRID_EVERY_3_STEPS -> (step % 3 == 0) || (staticStreak >= 1)
            GameBridgeTacticalFrequency.VISION_DIRECTED_ONLY -> staticStreak >= 2
        }
    }

    /**
     * Cere o decizie tactică motorului LLM offline
     * FĂRĂ NICIUN APEL CLOUD SAU EXTERN.
     */
    suspend fun requestGameTacticalDecision(
        gameTitle: String,
        screenSummary: String,
        detectedElements: List<IdentifiedGameElement>,
        playerHp: Float,
        staticStreak: Int,
        screenWidth: Float,
        screenHeight: Float,
        learnedGestures: List<LearnedUserGesture>
    ): LocalGameDecisionResult = withContext(Dispatchers.IO) {
        val cfg = _config.value
        val startTime = System.currentTimeMillis()

        val prompt = buildTacticalPrompt(
            gameTitle = gameTitle,
            screenSummary = screenSummary,
            elements = detectedElements,
            hp = playerHp,
            staticStreak = staticStreak,
            gestures = learnedGestures
        )

        // Încercare 2 (Fallback 100% Offline): Folosire motor intern OfflineLlmEngine (Gemma-2, DeepSeek, etc.)
        val fallbackDecision = queryInternalOfflineEngine(
            gameTitle = gameTitle,
            prompt = prompt,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            learnedGestures = learnedGestures,
            startTime = startTime
        )
        _latestDecision.value = fallbackDecision
        Log.i(TAG, "🧠 [INTERNAL OFFLINE LLM DECISION] ${fallbackDecision.actionType} | Sursă: ${fallbackDecision.engineSource} | Motiv: ${fallbackDecision.reasoning}")
        return@withContext fallbackDecision
    }

    private fun buildTacticalPrompt(
        gameTitle: String,
        screenSummary: String,
        elements: List<IdentifiedGameElement>,
        hp: Float,
        staticStreak: Int,
        gestures: List<LearnedUserGesture>
    ): String {
        val elementsStr = if (elements.isEmpty()) {
            "grafică canvas/joc fără butoane clasice"
        } else {
            elements.take(6).joinToString("; ") { "${it.label} la (${it.x.toInt()}, ${it.y.toInt()})" }
        }

        val gesturesStr = if (gestures.isEmpty()) {
            "Fără gesturi pre-clonate"
        } else {
            gestures.joinToString(", ") { "${it.actionTypeKey} [${(it.targetXFrac * 100).toInt()}%, ${(it.targetYFrac * 100).toInt()}%]" }
        }

        val normalizedGame = gameTitle.lowercase(Locale.ROOT)
        val gameSpecificRules = when {
            normalizedGame.contains("tetris") -> """
                ADAPTOR TETRIS: identifică mai întâi tabla ca grilă 10x20, piesa activă și celulele ocupate.
                Nu inventa poziția piesei, rotația sau controalele. Dacă nu poți distinge clar piesa activă,
                tabla ori ecranul GAME OVER/PAUSE, alege WAIT. Acțiuni permise: MOVE_LEFT, MOVE_RIGHT,
                ROTATE, DROP, WAIT. După o singură acțiune, cere observarea unui cadru nou înainte de alta.
            """.trimIndent()
            normalizedGame.contains("solitaire") || normalizedGame.contains("solitario") -> """
                ADAPTOR KLONDIKE SOLITAIRE: respectă regulile Klondike: fundații crescător pe suită,
                tableau descrescător cu culori alternante, doar Regele pe coloană goală. Nu ghici rangul
                sau suita cărților ascunse. Dacă rangul/suita nu sunt lizibile, alege WAIT. Folosește
                motorul specializat Solitaire al aplicației pentru mutări; nu emite coordonate speculative.
            """.trimIndent()
            else -> "Joc necunoscut: nu presupune controale sau reguli; dacă starea nu este clară, alege WAIT."
        }

        return """
            Joc: "$gameTitle".
            $gameSpecificRules
            Stare: $screenSummary (static: $staticStreak cicluri).
            Viață jucător: ${hp.toInt()}%.
            Elemente detectate pe ecran: $elementsStr.
            Gesturi clonate disponibile: $gesturesStr.
            
            Alege o acțiune tactică imediată pentru a continua jocul. Răspunde strict în acest format:
            ACTION: <FIRE | RELOAD | HEAL | ATTACK | MENU_TAP | SWIPE_LEFT | SWIPE_RIGHT | MOVE_LEFT | MOVE_RIGHT | ROTATE | DROP | WAIT>
            COORDS: <xFrac, yFrac> (valori între 0.0 și 1.0)
            REASON: <motivație scurtă max 15 cuvinte>
        """.trimIndent()
    }

    /**
     * Fallback folosind motorul intern offline (cele 11 modele locale integrate)
     */
    private suspend fun queryInternalOfflineEngine(
        gameTitle: String,
        prompt: String,
        screenWidth: Float,
        screenHeight: Float,
        learnedGestures: List<LearnedUserGesture>,
        startTime: Long
    ): LocalGameDecisionResult {
        val internalLlm = offlineLlmEngine
        val currentModelId = internalLlm?.currentModel?.value ?: "Niciun model încărcat"

        if (internalLlm == null) {
            // Fără motor AI disponibil nu executăm gesturi generice în jocuri specializate.
            return LocalGameDecisionResult(
                actionType = "WAIT",
                normalizedX = null,
                normalizedY = null,
                targetPixelX = null,
                targetPixelY = null,
                reasoning = "Nu există model activ; nu se execută gesturi speculative.",
                rawText = "ACTION: WAIT",
                latencyMs = System.currentTimeMillis() - startTime,
                engineSource = "Euristică Locală (Offline)",
                isFallback = true,
                matchedLearnedGesture = null
            )
        }

        return try {
            val res = internalLlm.generateOffline(
                prompt = prompt,
                systemPrompt = "Ești Strategul Tactic AI Local. Răspunde strict: ACTION: <TIP> COORDS: <x, y> REASON: <motiv>. Dacă nu ești sigur de coordonată sau jocul e stabil, răspunde cu ACTION: WAIT."
            )
            val replyText = res.getOrNull().orEmpty()
            val latency = System.currentTimeMillis() - startTime

            parseTacticalResponse(
                rawReply = replyText,
                width = screenWidth,
                height = screenHeight,
                latencyMs = latency,
                engineName = "Model Intern: $currentModelId",
                isFallback = true,
                learnedGestures = learnedGestures,
                gameTitle = gameTitle
            )
        } catch (_: Exception) {
            LocalGameDecisionResult(
                actionType = "WAIT",
                normalizedX = null,
                normalizedY = null,
                targetPixelX = null,
                targetPixelY = null,
                reasoning = "Observare pasivă (evitare atingeri oarbe pe ecran).",
                rawText = "ACTION: WAIT",
                latencyMs = System.currentTimeMillis() - startTime,
                engineSource = "Model Intern: $currentModelId",
                isFallback = true
            )
        }
    }

    /**
     * Parsează răspunsul text într-o directivă clară cu coordonate și potrivire de gesturi clonate
     */
    private fun parseTacticalResponse(
        rawReply: String,
        width: Float,
        height: Float,
        latencyMs: Long,
        engineName: String,
        isFallback: Boolean,
        learnedGestures: List<LearnedUserGesture>,
        gameTitle: String = ""
    ): LocalGameDecisionResult {
        var actionType = "WAIT"
        var normX: Float? = null
        var normY: Float? = null
        var reason = ""

        val lines = rawReply.lines()
        for (line in lines) {
            val upper = line.uppercase().trim()
            if (upper.contains("ACTION:")) {
                actionType = line.substringAfter("ACTION:").substringAfter("action:").trim().split(" ", "\n", ";").firstOrNull()?.uppercase() ?: "WAIT"
            }
            if (upper.contains("COORDS:")) {
                val coordsPart = line.substringAfter("COORDS:").substringAfter("coords:").trim()
                val parts = coordsPart.replace("(", "").replace(")", "").split(",", " ")
                    .filter { it.isNotBlank() }
                if (parts.size >= 2) {
                    val x = parts[0].toFloatOrNull()
                    val y = parts[1].toFloatOrNull()
                    if (x != null && y != null) {
                        if (x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) {
                            normX = x
                            normY = y
                        }
                    }
                }
            }
            if (upper.contains("REASON:")) {
                reason = line.substringAfter("REASON:").substringAfter("reason:").trim().take(90)
            }
        }

        // Răspunsurile fără directivă ACTION validă nu autorizează gesturi.
        val allowedActions = setOf("FIRE", "RELOAD", "HEAL", "ATTACK", "MENU_TAP", "SWIPE_LEFT", "SWIPE_RIGHT", "MOVE_LEFT", "MOVE_RIGHT", "ROTATE", "DROP", "WAIT")
        if (actionType !in allowedActions) actionType = "WAIT"

        // Adaptoare de siguranță: nu lăsăm LLM-ul să trimită acțiuni din alt gen de joc.
        val gameKey = gameTitle.lowercase(Locale.ROOT)
        if (gameKey.contains("tetris")) {
            val allowed = setOf("MOVE_LEFT", "MOVE_RIGHT", "ROTATE", "DROP", "WAIT")
            if (actionType !in allowed) {
                actionType = "WAIT"
                normX = null
                normY = null
                reason = "Acțiunea propusă nu aparține adaptorului Tetris; reobservare sigură."
            }
        } else if (gameKey.contains("solitaire") || gameKey.contains("solitario")) {
            // Mutările Solitaire sunt gestionate de solverul Klondike dedicat din OsOrchestrator.
            actionType = "WAIT"
            normX = null
            normY = null
            reason = "Adaptorul Klondike lasă mutarea motorului Solitaire specializat; evităm coordonate speculative."
        }

        if (reason.isBlank()) {
            reason = rawReply.lines().firstOrNull { it.isNotBlank() }?.take(80) ?: "Decizie autonomă LLM"
        }

        // Potrivim cu profilul de gesturi clonate ale utilizatorului pentru acest tip de acțiune
        val matchedGesture = learnedGestures.firstOrNull { it.actionTypeKey.equals(actionType, ignoreCase = true) }

        val finalNormX = if (actionType == "WAIT") null else (normX ?: matchedGesture?.targetXFrac)
        val finalNormY = if (actionType == "WAIT") null else (normY ?: matchedGesture?.targetYFrac)

        val pixelX = finalNormX?.let { it * width }
        val pixelY = finalNormY?.let { it * height }

        return LocalGameDecisionResult(
            actionType = actionType,
            normalizedX = finalNormX,
            normalizedY = finalNormY,
            targetPixelX = pixelX,
            targetPixelY = pixelY,
            reasoning = reason,
            rawText = rawReply.take(150),
            latencyMs = latencyMs,
            engineSource = engineName,
            isFallback = isFallback,
            matchedLearnedGesture = matchedGesture
        )
    }
}
