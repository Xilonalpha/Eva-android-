package com.eva1.companion.engines

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Postura tactică globală determinată de Strateg (Sistemul 2 - LLM / VLM la 5-10 secunde):
 */
enum class StrategicPosture(val displayName: String, val badge: String, val description: String) {
    SOLITAIRE_STACK_SOLVER("Solitaire & Cărți", "🃏", "Mută cărțile pe coloane și la fundație, deblochează cărțile ascunse și trage din pachet"),
    PUZZLE_GRID_SOLVER("Potrivire Grilă", "🧩", "Analizează grila și plasează piesele în spațiile optime"),
    AGGRESSIVE_ASSAULT("Atac & Presiune Ofensivă", "⚔️", "Avansează spre inamici, menține presiunea și trage la orice oportunitate"),
    FLANK_AND_SEEK_COVER("Ocolire & Căutare Adăpost", "🛡️", "Evită linia directă de foc, folosește obstacolele și flanchează"),
    LOOT_AND_EQUIP("Explorare & Resurse", "🎒", "Căutare muniție, arme mai bune, armură și truse medicale"),
    SURVIVAL_RETREAT("Retragere & Vindecare Urgentă", "🚑", "Prioritate absolută de supraviețuire: aplică medkit și retrage-te din luptă"),
    ZONE_ROTATION("Deplasare Spre Zona Sigură", "🧭", "Sprint continuu către cercul sigur pentru a evita furtuna/zona albastră"),
    ALIGN_HOLE_COMBO("Aliniere Precisă Grilă", "🧱", "Calculare traiectorie optimă pentru completarea de linii și combo-uri")
}

/**
 * Directiva strategică emisă de Modelul de Limbaj / Viziune Multimodală:
 */
data class StrategicDirective(
    val timestamp: Long = System.currentTimeMillis(),
    val posture: StrategicPosture = StrategicPosture.AGGRESSIVE_ASSAULT,
    val highLevelGoal: String = "Înaintează pe hartă și scanează orizontul",
    val focalPointX: Float = 0.5f,
    val focalPointY: Float = 0.5f,
    val targetCoordinatesDescription: String = "Centru-înainte",
    val recommendedPacingMs: Long = 45L,
    val modelName: String = "EVA Omni 7B",
    val strategicReasoning: String = "Inițializare tactică completă"
)

/**
 * Rezultatul reflexului motor instantaneu (Sistemul 1 - 16-60ms):
 */
data class ReflexMotorAction(
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val isTriggered: Boolean,
    val reflexLatencyMs: Long,
    val startX: Float,
    val startY: Float,
    val endX: Float = startX,
    val endY: Float = startY,
    val recommendedPauseMs: Long = 45L,
    val details: String = ""
)

/**
 * Arhitectură pe Două Viteze (Dual-Brain):
 *  - Sistemul 1: Reflexe motorii ultra-rapide (16 - 60ms) prin Computer Vision direct pe pixeli
 *    (inamic în reticul, obstacol iminent, gol de linie, sprint joystick continuu).
 *  - Sistemul 2: Strategul la 5-10 secunde (LLM / VLM local) care gândește imaginea de ansamblu
 *    și setează postura tactică, fără a bloca reflexele rapide!
 */
class DualBrainGameEngine(
    private val context: Context,
    private val multiModalVision: MultiModalGameVisionEngine,
    private val offlineLlmEngineSupplier: () -> OfflineLlmEngine? = { null }
) {
    companion object {
        private const val TAG = "EVA_DUAL_BRAIN"
        const val STRATEGIC_CYCLE_INTERVAL_MS = 6500L // La fiecare 6.5 secunde
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var strategicJob: Job? = null

    // Starea directivei strategice active (Sistemul 2)
    private val _activeDirective = MutableStateFlow(StrategicDirective())
    val activeDirective: StateFlow<StrategicDirective> = _activeDirective.asStateFlow()

    // Ultimul reflex motor executat (Sistemul 1)
    private val _lastReflexAction = MutableStateFlow<ReflexMotorAction?>(null)
    val lastReflexAction: StateFlow<ReflexMotorAction?> = _lastReflexAction.asStateFlow()

    // Metricile de performanță Dual-Brain
    private val _reflexActionsCount = MutableStateFlow(0L)
    val reflexActionsCount: StateFlow<Long> = _reflexActionsCount.asStateFlow()

    private val _strategicDeliberationsCount = MutableStateFlow(0L)
    val strategicDeliberationsCount: StateFlow<Long> = _strategicDeliberationsCount.asStateFlow()

    private val _isDualBrainActive = MutableStateFlow(false)
    val isDualBrainActive: StateFlow<Boolean> = _isDualBrainActive.asStateFlow()

    /**
     * Pornește ciclul strategic autonom de fundal (Sistemul 2 - la fiecare 5-8 secunde).
     * Se execută în mod complet asincron pe propriul thread fără să blocheze reflexele!
     */
    fun startStrategicCommanderLoop(gamePackageOrTitle: String) {
        val lower = gamePackageOrTitle.lowercase()
        val initialPosture = when {
            lower.contains("solit") || lower.contains("klondike") || lower.contains("spider") ||
                lower.contains("freecell") || lower.contains("tripledot") || lower.contains("carte") ||
                lower.contains("cards") -> StrategicPosture.SOLITAIRE_STACK_SOLVER
            lower.contains("tetris") || lower.contains("brick") || lower.contains("2048") ->
                StrategicPosture.ALIGN_HOLE_COMBO
            lower.contains("puzzle") || lower.contains("blast") || lower.contains("candy") ||
                lower.contains("woodoku") || lower.contains("match") -> StrategicPosture.PUZZLE_GRID_SOLVER
            else -> StrategicPosture.AGGRESSIVE_ASSAULT
        }
        _activeDirective.value = _activeDirective.value.copy(
            posture = initialPosture,
            highLevelGoal = initialPosture.description
        )

        if (strategicJob?.isActive == true) return
        _isDualBrainActive.value = true

        strategicJob = scope.launch {
            Log.i(TAG, "🧠 [SISTEMUL 2: STRATEGUL] Pornit pentru jocul: $gamePackageOrTitle (Ciclu: ${STRATEGIC_CYCLE_INTERVAL_MS}ms)")
            while (isActive && _isDualBrainActive.value) {
                try {
                    val frame = MediaProjectionVisionEngine.getLatestProjectedBitmap()
                        ?: EvaAccessibilityService.instance?.captureLiveGameBitmap()

                    if (frame != null && !frame.isRecycled) {
                        evaluateStrategicPostureAsync(frame, gamePackageOrTitle)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Eroare evaluare strategică: ${e.message}")
                }
                delay(STRATEGIC_CYCLE_INTERVAL_MS)
            }
        }
    }

    fun stopStrategicCommanderLoop() {
        _isDualBrainActive.value = false
        strategicJob?.cancel()
        strategicJob = null
    }

    /**
     * Procesează cadrul complet prin modelul multimodal / LLM local pentru a emite o directivă strategică:
     */
    private suspend fun evaluateStrategicPostureAsync(frame: Bitmap, gameTitle: String) {
        val startTime = System.currentTimeMillis()

        try {
            // Folosim rezoluția reală a ecranului (ex: 1080x2400), nu jumătatea din Bitmap-ul MediaProjection!
            val (dispW, dispH) = MediaProjectionVisionEngine.getLatestDisplaySize()
            val realW = dispW.coerceAtLeast(frame.width).toFloat()
            val realH = dispH.coerceAtLeast(frame.height).toFloat()

            val visualAnalysis = multiModalVision.analyzeScreenFrame(
                bitmap = frame,
                screenWidth = realW,
                screenHeight = realH,
                activeGameTitle = gameTitle
            )

            val lowerTitle = gameTitle.lowercase()
            val isSolitaire = lowerTitle.contains("solit") || lowerTitle.contains("klondike") ||
                lowerTitle.contains("spider") || lowerTitle.contains("freecell") ||
                lowerTitle.contains("tripledot") || lowerTitle.contains("carte") ||
                GameScreenAnalysisUtility.isSolitaireOrCardTableScreen(frame, frame.width, frame.height, realW > realH)

            // Analiză strategică bazată pe situația generală
            val newPosture: StrategicPosture = when {
                isSolitaire ->
                    StrategicPosture.SOLITAIRE_STACK_SOLVER

                lowerTitle.contains("tetris") || lowerTitle.contains("brick") ->
                    StrategicPosture.ALIGN_HOLE_COMBO

                lowerTitle.contains("puzzle") || lowerTitle.contains("blast") || lowerTitle.contains("candy") ||
                    lowerTitle.contains("woodoku") || lowerTitle.contains("match") ->
                    StrategicPosture.PUZZLE_GRID_SOLVER

                visualAnalysis.isLowHpCritical || visualAnalysis.playerHpPercent < 35f ->
                    StrategicPosture.SURVIVAL_RETREAT

                visualAnalysis.enemies.size >= 2 ->
                    StrategicPosture.FLANK_AND_SEEK_COVER

                visualAnalysis.hasTargetInCrosshair || visualAnalysis.enemies.isNotEmpty() ->
                    StrategicPosture.AGGRESSIVE_ASSAULT

                visualAnalysis.interactiveButtons.any { it.label.contains("loot", ignoreCase = true) || it.label.contains("pick", ignoreCase = true) } ->
                    StrategicPosture.LOOT_AND_EQUIP

                else ->
                    StrategicPosture.ZONE_ROTATION
            }

            val goalSummary = when (newPosture) {
                StrategicPosture.SOLITAIRE_STACK_SOLVER -> "Solitaire: Mută cărțile pe coloane/fundație, întoarce cărțile ascunse și trage din Stock"
                StrategicPosture.PUZZLE_GRID_SOLVER -> "Puzzle: Caută combinații pe grilă și plasează piesele în spațiile libere"
                StrategicPosture.SURVIVAL_RETREAT -> "HP periculos (${visualAnalysis.playerHpPercent.toInt()}%): Retragere spre acoperire și vindecare"
                StrategicPosture.AGGRESSIVE_ASSAULT -> "Inamici reperați în câmp: Presiune ofensivă și foc la țintă"
                StrategicPosture.FLANK_AND_SEEK_COVER -> "Multipli adversari: Ocolire din unghi lateral și căutare adăpost"
                StrategicPosture.LOOT_AND_EQUIP -> "Zonă de resurse: Colectează muniție, veste și accesorii"
                StrategicPosture.ZONE_ROTATION -> "Cale liberă: Sprint continuu către centrul zonei de joc"
                StrategicPosture.ALIGN_HOLE_COMBO -> "Tetris: Plasează piesa în coloana cu cel mai adânc gol"
            }

            val updatedDirective = StrategicDirective(
                timestamp = System.currentTimeMillis(),
                posture = newPosture,
                highLevelGoal = goalSummary,
                focalPointX = if (visualAnalysis.enemies.isNotEmpty()) visualAnalysis.enemies.first().normalizedX else 0.5f,
                focalPointY = if (visualAnalysis.enemies.isNotEmpty()) visualAnalysis.enemies.first().normalizedY else 0.5f,
                targetCoordinatesDescription = if (visualAnalysis.enemies.isNotEmpty()) "Inamic (${(visualAnalysis.enemies.first().normalizedX * 100).toInt()}%, ${(visualAnalysis.enemies.first().normalizedY * 100).toInt()}%)" else "Avansare liberă",
                recommendedPacingMs = if (newPosture == StrategicPosture.AGGRESSIVE_ASSAULT) 45L else 60L,
                modelName = visualAnalysis.modelName,
                strategicReasoning = "[${System.currentTimeMillis() - startTime}ms] $goalSummary"
            )

            _activeDirective.value = updatedDirective
            _strategicDeliberationsCount.value = _strategicDeliberationsCount.value + 1L
            Log.i(TAG, "🧠 [STRATEGUL]: Postură nouă: ${newPosture.badge} ${newPosture.displayName} | Țintă: ${updatedDirective.highLevelGoal}")
        } catch (e: Exception) {
            Log.w(TAG, "Fallback directivă strategică: ${e.message}")
        }
    }

    /**
     * SISTEMUL 1: Calculează o reacție pe baza celui mai recent cadru/analiză.
     * Nu garantează 16–60 ms end-to-end: capturarea ecranului și gesturile Android au propria latență.
     */
    fun evaluateFastReflex(
        visual: GameVisualAnalysis,
        screenWidth: Float,
        screenHeight: Float,
        gameMode: GameBotMode
    ): ReflexMotorAction {
        val startTime = System.currentTimeMillis()
        val directive = _activeDirective.value
        val posture = directive.posture
        val screenAnalysis = MediaProjectionVisionEngine.latestScreenAnalysis.value
        val uiElements = screenAnalysis?.detectedElements ?: emptyList()

        val cx = screenWidth * 0.5f
        val cy = screenHeight * 0.5f

        var actionType = "CONTINUOUS_SPRINT"
        var isTriggered = false
        var startX = screenWidth * 0.18f
        var startY = screenHeight * 0.72f
        var endX = startX
        var endY = startY - (screenHeight * 0.16f)
        var pacingMs = 55L
        var details = "Reflex de menținere avans"

        when (gameMode) {
            GameBotMode.FPS_BATTLE_ROYALE -> {
                val hasCrosshairEnemy = visual.hasTargetInCrosshair
                val enemyPt = visual.enemyTargetPoint
                val isLowHp = visual.isLowHealthCritical || posture == StrategicPosture.SURVIVAL_RETREAT
                val fireBtn = uiElements.firstOrNull { it.category == UiElementCategory.BUTTON_ATTACK_FIRE }
                val joyBtn = uiElements.firstOrNull { it.category == UiElementCategory.JOYSTICK_MOVEMENT }

                when {
                    // Reflex 1 (Prioritate 0): Inamic direct în reticul -> TRAGERE INSTANTANEE + STRAFE
                    hasCrosshairEnemy -> {
                        actionType = "INSTANT_FIRE_BURST"
                        isTriggered = true
                        startX = fireBtn?.centerX ?: (screenWidth * 0.86f)
                        startY = fireBtn?.centerY ?: (screenHeight * 0.72f)
                        endX = startX
                        endY = startY
                        pacingMs = 120L
                        details = "Țintă raportată în reticul de ultimul cadru; acțiunea trebuie verificată după trimitere."
                    }

                    // Reflex 2: Inamic în câmpul vizual (FOV) -> Orientare rapidă a camerei spre inamic + ADS
                    enemyPt != null -> {
                        actionType = "AIM_AND_SCOPE"
                        isTriggered = true
                        val camStartX = screenWidth * 0.64f
                        val camStartY = screenHeight * 0.50f
                        val dx = (enemyPt.first - cx).coerceIn(-screenWidth * 0.35f, screenWidth * 0.35f)
                        val dy = (enemyPt.second - cy).coerceIn(-screenHeight * 0.28f, screenHeight * 0.28f)
                        startX = camStartX
                        startY = camStartY
                        endX = camStartX + dx * 0.65f
                        endY = camStartY + dy * 0.65f
                        pacingMs = 45L
                        details = "⚡ [REFLEX CAMERA]: Orientare instantanee spre inamic (${enemyPt.first.toInt()}, ${enemyPt.second.toInt()})"
                    }

                    // Reflex 3: HP critic și avem buton de medkit -> Vindecare de urgență
                    isLowHp && visual.healButtonPoint != null -> {
                        actionType = "FAST_HEAL"
                        isTriggered = true
                        val (hx, hy) = visual.healButtonPoint
                        startX = hx
                        startY = hy
                        endX = hx
                        endY = hy
                        pacingMs = 55L
                        details = "🚑 [REFLEX URGENȚĂ]: Activare trusă medicală la (${hx.toInt()}, ${hy.toInt()})"
                    }

                    // Reflex 4: Fără inamici imediați -> Menținere sprint continuu pe joystick (fără sacadare!)
                    else -> {
                        actionType = "CONTINUOUS_JOYSTICK_RUN"
                        isTriggered = true
                        val joyX = joyBtn?.centerX ?: (screenWidth * 0.18f)
                        val joyY = joyBtn?.centerY ?: (screenHeight * 0.72f)
                        startX = joyX
                        startY = joyY
                        endX = joyX
                        endY = joyY - (screenHeight * 0.18f)
                        pacingMs = 40L
                        details = "🏃‍♂️ [REFLEX MOTOR]: Menținere sprint cursiv joystick înainte (Fără oprire)"
                    }
                }
            }

            GameBotMode.TETRIS_ARCADE -> {
                val tetris = screenAnalysis?.tetrisAnalysis
                val leftBtn = uiElements.firstOrNull { it.category == UiElementCategory.BUTTON_DPAD_LEFT }
                val rightBtn = uiElements.firstOrNull { it.category == UiElementCategory.BUTTON_DPAD_RIGHT }
                val downBtn = uiElements.firstOrNull { it.category == UiElementCategory.BUTTON_DPAD_DOWN_DROP }

                val targetColIdx = tetris?.targetGapColumn ?: ((visual.tetrisBestColumnFrac - 0.12f) / (0.76f / 10f)).toInt().coerceIn(0, 9)
                val pieceColIdx = tetris?.currentPieceColumn ?: ((visual.tetrisPieceColumnFrac - 0.12f) / (0.76f / 10f)).toInt().coerceIn(0, 9)
                val colDiff = tetris?.columnDifference ?: (targetColIdx - pieceColIdx)

                when {
                    colDiff == 0 -> {
                        // Aliniere perfectă deasupra golului -> Apăsare pe butonul real DOWN/DROP (0.40, 0.92), NU pe Pause (0.60, 0.92)!
                        actionType = "TETRIS_HARD_DROP"
                        isTriggered = true
                        startX = downBtn?.centerX ?: (screenWidth * 0.40f)
                        startY = downBtn?.centerY ?: (screenHeight * 0.92f)
                        endX = startX
                        endY = startY
                        pacingMs = 70L
                        details = "🧩 [REFLEX TETRIS]: Aliniat perfect pe col ${targetColIdx + 1}! Apăsat DROP ▼ la (${startX.toInt()}, ${startY.toInt()})"
                    }
                    colDiff < 0 -> {
                        // Piesa este la dreapta golului -> Apăsare pe butonul STÂNGA (◀ la 0.30, 0.84)
                        actionType = "TETRIS_TAP_LEFT"
                        isTriggered = true
                        startX = leftBtn?.centerX ?: (screenWidth * 0.30f)
                        startY = leftBtn?.centerY ?: (screenHeight * 0.84f)
                        endX = startX
                        endY = startY
                        pacingMs = 60L
                        details = "◀ [REFLEX TETRIS]: Apăsat STÂNGA ◀ (${startX.toInt()}, ${startY.toInt()}) spre col ${targetColIdx + 1}"
                    }
                    else -> {
                        // Piesa este la stânga golului -> Apăsare pe butonul DREAPTA (▶ la 0.70, 0.84)
                        actionType = "TETRIS_TAP_RIGHT"
                        isTriggered = true
                        startX = rightBtn?.centerX ?: (screenWidth * 0.70f)
                        startY = rightBtn?.centerY ?: (screenHeight * 0.84f)
                        endX = startX
                        endY = startY
                        pacingMs = 60L
                        details = "▶ [REFLEX TETRIS]: Apăsat DREAPTA ▶ (${startX.toInt()}, ${startY.toInt()}) spre col ${targetColIdx + 1}"
                    }
                }
            }

            GameBotMode.SOLITAIRE_CARDS -> {
                val sol = screenAnalysis?.solitaireAnalysis
                val targetCol = sol?.columns?.firstOrNull { it.hasFaceUpCards && it.hasFaceDownCardsAbove }
                    ?: sol?.columns?.firstOrNull { it.hasFaceUpCards }
                isTriggered = true
                startX = targetCol?.centerX ?: (screenWidth * 0.91f)
                startY = targetCol?.bottomCardY ?: (screenHeight * 0.175f)
                endX = startX
                endY = startY
                pacingMs = 450L
                details = "🃏 [REFLEX SOLITAIRE]: Evaluare cărți pe cele 7 coloane și pachet Stock"
            }

            else -> {
                isTriggered = true
                startX = screenWidth * 0.5f
                startY = screenHeight * 0.5f
                endX = startX
                endY = startY
                pacingMs = 65L
                details = "Reflex adaptat: ${directive.highLevelGoal}"
            }
        }

        val latency = System.currentTimeMillis() - startTime
        val action = ReflexMotorAction(
            actionType = actionType,
            isTriggered = isTriggered,
            reflexLatencyMs = latency,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            recommendedPauseMs = pacingMs,
            details = details
        )

        _lastReflexAction.value = action
        _reflexActionsCount.value = _reflexActionsCount.value + 1L
        return action
    }
}
