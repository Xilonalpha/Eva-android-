package com.eva1.companion.engines

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tipul jocului analizat:
 *  - INTERNAL_SIMPLE_TEST: Mini-jocul simplificat intern (stare internă în memorie deterministă, fără GPU/OpenGL necunoscut).
 *  - EXTERNAL_COMPLEX_GAME: Jocuri mobile reale externe (Clash of Clans, PUBG, FC Mobile, Block Blast, etc.
 *                           cu elemente dinamice, motoare grafice Unity/Unreal/Vulkan, ferestre pop-up și latență de randare).
 */
enum class GameDecisionType(val displayName: String) {
    INTERNAL_SIMPLE_TEST("Test Simplu Intern (Cyber Runner)"),
    EXTERNAL_COMPLEX_GAME("Joc Complex Extern (Clash / PUBG / Touch)")
}

data class IdentifiedGameElement(
    val category: String,
    val label: String,
    val x: Float,
    val y: Float,
    val confidencePercent: Int = 100
)

/**
 * Înregistrare completă a procesului de decizie AI:
 *  - Ce elemente a identificat pe ecran
 *  - Ce acțiune tactică a ales
 *  - De ce a ales această acțiune
 *  - Rezultatul gestului și potențiale cauze de eșec
 */
data class GameDecisionLogEntry(
    val id: Long = System.currentTimeMillis(),
    val step: Int,
    val timestampFormatted: String,
    val gameType: GameDecisionType,
    val gameTitle: String,
    val packageName: String,
    val displayMetrics: String,
    val identifiedElements: List<IdentifiedGameElement>,
    val visionState: String,
    val healthSummary: String,
    val chosenAction: String,
    val actionCategory: String,
    val coordinatesTarget: String,
    val gestureSuccess: Boolean,
    val reasoning: String,
    val whyFailedOrSucceededNote: String = ""
)

/**
 * Sistem avansat de jurnalizare și diagnoză a deciziilor AI în Logcat (Tag: EVA_GAME_DECISION).
 * Permite dezvoltatorilor și utilizatorilor să analizeze de ce AI-ul reușește pe mini-jocul simplu
 * de test versus jocurile mobile complexe instalate pe telefon.
 */
object EvaGameDecisionLogger {

    const val TAG = "EVA_GAME_DECISION"
    private const val MAX_IN_MEMORY_LOGS = 60

    private val _recentLogs = MutableStateFlow<List<GameDecisionLogEntry>>(emptyList())
    val recentLogs: StateFlow<List<GameDecisionLogEntry>> = _recentLogs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    @Synchronized
    private fun formatNow(): String = timeFormat.format(Date())

    /**
     * Înregistrează în Logcat decizia tactică pentru un JOC COMPLEX EXTERN
     * (Clash of Clans, PUBG, FC Mobile, Block Blast, etc.)
     */
    fun logExternalComplexDecision(
        step: Int,
        gameTitle: String,
        packageName: String,
        displayWidth: Int,
        displayHeight: Int,
        isLandscape: Boolean,
        visionState: String,
        frameDiff: Float,
        staticStreak: Int,
        playerHp: Float,
        enemyHp: Float,
        secondaryResource: Float,
        identifiedButtons: List<DetectedGameUiButton>,
        menuTarget: Pair<Float, Float>?,
        menuLabel: String,
        chosenActionDescription: String,
        actionCategory: String,
        targetCoordinates: String,
        gestureResult: Boolean,
        reasoning: String,
        whyFailurePotential: String = ""
    ) {
        val now = formatNow()
        val elements = identifiedButtons.map {
            IdentifiedGameElement(
                category = it.category.name,
                label = it.label,
                x = it.centerX,
                y = it.centerY,
                confidencePercent = (it.confidence * 100).toInt()
            )
        }

        val entry = GameDecisionLogEntry(
            step = step,
            timestampFormatted = now,
            gameType = GameDecisionType.EXTERNAL_COMPLEX_GAME,
            gameTitle = gameTitle,
            packageName = packageName,
            displayMetrics = "${displayWidth}x${displayHeight} (${if (isLandscape) "Landscape" else "Portrait"})",
            identifiedElements = elements,
            visionState = "$visionState (diff=${"%.4f".format(frameDiff)}, staticStreak=$staticStreak)",
            healthSummary = "HP Jucător: ${playerHp.toInt()}% | Inamic: ${enemyHp.toInt()}% | Resursă/Scut: ${secondaryResource.toInt()}%",
            chosenAction = chosenActionDescription,
            actionCategory = actionCategory,
            coordinatesTarget = targetCoordinates,
            gestureSuccess = gestureResult,
            reasoning = reasoning,
            whyFailedOrSucceededNote = if (gestureResult) {
                listOf("Android a acceptat trimiterea gestului; rezultatul în joc nu a fost verificat.", whyFailurePotential).filter { it.isNotBlank() }.joinToString(" ")
            } else whyFailurePotential
        )

        addLogEntry(entry)

        // Jurnalizare structurată în Logcat Android (filtrabilă cu `adb logcat -s EVA_GAME_DECISION`)
        val sb = StringBuilder()
        sb.appendLine("══════════════════ [EVA-1 DECISION STEP #$step • COMPLEX GAME] ══════════════════")
        sb.appendLine("[CONTEXT] Game: $gameTitle | Pkg: $packageName | Screen: ${displayWidth}x${displayHeight} (${if (isLandscape) "Landscape" else "Portrait"})")
        sb.appendLine("[VISION STATE] $visionState | Frame DiffScore: ${"%.4f".format(frameDiff)} | Static Streak: $staticStreak")
        sb.appendLine("[VITALS] Player HP: ${playerHp.toInt()}% | Enemy/Boss: ${enemyHp.toInt()}% | Shield/Elixir: ${secondaryResource.toInt()}%")
        sb.appendLine("[IDENTIFIED ELEMENTS] Total ${identifiedButtons.size} elemente interactive / butoane detectate:")
        if (identifiedButtons.isEmpty()) {
            sb.appendLine("  • (Niciun buton de înaltă încredere detectat pe ecran — analiză de câmp / pixeli)")
        } else {
            identifiedButtons.forEach { btn ->
                sb.appendLine("  • [${btn.category.name}] \"${btn.label}\" la (${btn.centerX.toInt()}, ${btn.centerY.toInt()}) [conf: ${(btn.confidence * 100).toInt()}%]")
            }
        }
        if (menuTarget != null) {
            sb.appendLine("[RECOMMENDED MENU TARGET] \"$menuLabel\" la (${menuTarget.first.toInt()}, ${menuTarget.second.toInt()})")
        }
        sb.appendLine("[DECISION & ACTION] Category: $actionCategory")
        sb.appendLine("  • Acțiune: $chosenActionDescription")
        sb.appendLine("  • Țintă Coordonate: $targetCoordinates")
        sb.appendLine("  • Rezultat API: ${if (gestureResult) "GEST ACCEPTAT DE ANDROID (nu confirmă succesul în joc)" else "GEST RESPINS / NEDISPONIBIL"}")
        sb.appendLine("[REASONING] $reasoning")
        if (whyFailurePotential.isNotBlank()) {
            sb.appendLine("[DIAGNOSTIC / CAUZĂ POTENȚIALĂ EȘEC] $whyFailurePotential")
        }
        sb.appendLine("═════════════════════════════════════════════════════════════════════════════════")

        Log.i(TAG, sb.toString())
    }

    /** Înregistrează rezultatul observației de după gest, fără să-l confunde cu victoria/mutarea corectă. */
    fun logPostActionVerification(
        packageName: String,
        gameTitle: String,
        feedback: GestureVerificationFeedback
    ) {
        val level = when {
            !feedback.androidAccepted || !feedback.callbackCompleted -> "GEST_FAILED"
            !feedback.freshFrameObserved -> "UNVERIFIED"
            feedback.frameChanged == true -> "FRAME_CHANGED_NOT_SEMANTIC_SUCCESS"
            else -> "NO_VISIBLE_CHANGE"
        }
        Log.i(
            TAG,
            "[POST-ACTION][$level] game=$gameTitle package=$packageName action=${feedback.action} " +
                "freshFrame=${feedback.freshFrameObserved} changed=${feedback.frameChanged} note=${feedback.note}"
        )
    }

    /**
     * Înregistrează în Logcat decizia pentru MINI-JOCUL SIMPLIFICAT DE TEST
     */
    fun logSimpleTestGameDecision(
        step: Int,
        playerLane: Int,
        dangerObsLane: Int?,
        dangerObsY: Float?,
        bonusCoinLane: Int?,
        decisionSummary: String,
        actionType: String,
        laneChosen: Int,
        jumped: Boolean
    ) {
        val now = formatNow()
        val elements = mutableListOf<IdentifiedGameElement>()
        if (dangerObsLane != null && dangerObsY != null) {
            elements.add(IdentifiedGameElement("OBSTACLE", "Obstacol pe banda ${dangerObsLane + 1}", dangerObsLane.toFloat(), dangerObsY))
        }
        if (bonusCoinLane != null) {
            elements.add(IdentifiedGameElement("COIN", "Monedă pe banda ${bonusCoinLane + 1}", bonusCoinLane.toFloat(), 0.6f))
        }

        val entry = GameDecisionLogEntry(
            step = step,
            timestampFormatted = now,
            gameType = GameDecisionType.INTERNAL_SIMPLE_TEST,
            gameTitle = "EVA-1 Cyber Runner (Demo)",
            packageName = "internal.demo.arena",
            displayMetrics = "Arena Virtuală 3 Benzi",
            identifiedElements = elements,
            visionState = "INTERNAL_GAMEPLAY_LOOP",
            healthSummary = "Combo Activ",
            chosenAction = decisionSummary,
            actionCategory = actionType,
            coordinatesTarget = "Banda ${laneChosen + 1} ${if (jumped) "(+SALT)" else ""}",
            gestureSuccess = true,
            reasoning = if (dangerObsLane != null) "Pericol detectat pe banda curentă ($playerLane). Comutat pe banda liberă $laneChosen." else "Culoar liber, colectare resurse.",
            whyFailedOrSucceededNote = "Test determinist: datele obstacolelor vin direct din memoria RAM (fără procesare de cadre GPU sau întârzieri de randare)."
        )

        addLogEntry(entry)

        val sb = StringBuilder()
        sb.appendLine("══════════════════ [EVA-1 DECISION STEP #$step • SIMPLE TEST GAME] ══════════════════")
        sb.appendLine("[GAME] EVA-1 Cyber Runner (Demo Intern Simplu)")
        sb.appendLine("[IDENTIFIED] Banda Jucător: ${playerLane + 1} | Pericol: ${if (dangerObsLane != null) "Banda ${dangerObsLane + 1} (Y=${"%.2f".format(dangerObsY)})" else "Niciunul"} | Monedă: ${bonusCoinLane?.let { "Banda ${it + 1}" } ?: "Niciuna"}")
        sb.appendLine("[DECISION] Acțiune: $actionType | Bandă Țintă: ${laneChosen + 1} | Salt: $jumped")
        sb.appendLine("[REASONING] $decisionSummary")
        sb.appendLine("[COMPARAȚIE DIAGNOSTICĂ] Mini-jocul intern reușește 100% deoarece are stare în memorie exactă. Jocurile externe necesită recunoaștere vizuală, adaptare la pop-up-uri și timpi de răspuns ai serverului.")
        sb.appendLine("════════════════════════════════════════════════════════════════════════════════════")

        Log.i(TAG, sb.toString())
    }

    private fun addLogEntry(entry: GameDecisionLogEntry) {
        val current = _recentLogs.value.toMutableList()
        current.add(0, entry)
        if (current.size > MAX_IN_MEMORY_LOGS) {
            _recentLogs.value = current.take(MAX_IN_MEMORY_LOGS)
        } else {
            _recentLogs.value = current
        }
    }

    fun clearLogs() {
        _recentLogs.value = emptyList()
    }

    fun exportFormattedLogcatText(): String {
        val logs = _recentLogs.value
        if (logs.isEmpty()) return "Niciun jurnal de decizie înregistrat încă. Pornește Autopilotul pe un joc pentru a genera logcat."

        val sb = StringBuilder()
        sb.appendLine("📋 JURNAL DECIZII AI EVA-1 (Filtru Logcat: EVA_GAME_DECISION)")
        sb.appendLine("Total decizii înregistrate: ${logs.size}")
        sb.appendLine("================================================================")
        logs.forEach { entry ->
            sb.appendLine("[${entry.timestampFormatted}] Pas #${entry.step} • ${entry.gameType.displayName}")
            sb.appendLine("  Joc: ${entry.gameTitle} (${entry.packageName}) | Ecran: ${entry.displayMetrics}")
            sb.appendLine("  Stare Vizuală: ${entry.visionState}")
            sb.appendLine("  Elemente Identificate: ${entry.identifiedElements.size} [${entry.identifiedElements.joinToString { "${it.label}(${it.x.toInt()},${it.y.toInt()})" }}]")
            sb.appendLine("  Decizie: [${entry.actionCategory}] ${entry.chosenAction}")
            sb.appendLine("  Țintă Coordonate: ${entry.coordinatesTarget} | Succes Gest: ${entry.gestureSuccess}")
            sb.appendLine("  Raționament: ${entry.reasoning}")
            if (entry.whyFailedOrSucceededNote.isNotBlank()) {
                sb.appendLine("  Diagnoză: ${entry.whyFailedOrSucceededNote}")
            }
            sb.appendLine("----------------------------------------------------------------")
        }
        return sb.toString()
    }
}
