package com.eva1.companion.engines

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Tipul pasului dintr-un flux continuu de evenimente tactile (MotionEvent Stream):
 */
enum class MotionStreamAction(val androidAction: Int, val label: String) {
    DOWN(MotionEvent.ACTION_DOWN, "ACTION_DOWN"),
    MOVE(MotionEvent.ACTION_MOVE, "ACTION_MOVE"),
    UP(MotionEvent.ACTION_UP, "ACTION_UP"),
    POINTER_DOWN(MotionEvent.ACTION_POINTER_DOWN, "ACTION_POINTER_DOWN"),
    POINTER_UP(MotionEvent.ACTION_POINTER_UP, "ACTION_POINTER_UP"),
    CANCEL(MotionEvent.ACTION_CANCEL, "ACTION_CANCEL")
}

/**
 * Punct discret (waypoint) din fluxul MotionEvent generat pentru interacțiunea cu jocul:
 */
data class MotionStreamPoint(
    val action: MotionStreamAction,
    val x: Float,
    val y: Float,
    val secondaryX: Float? = null,
    val secondaryY: Float? = null,
    val pressure: Float = 0.85f,
    val size: Float = 0.12f,
    val offsetTimeMs: Long = 0L
)

/**
 * Reprezentarea completă a unui flux MotionEvent tradus din coordonatele de analiză a ecranului:
 * Conține atât lista nativă de obiecte Android [MotionEvent], cât și traiectoria [points]
 * pentru injectare în View, Instrumentation sau AccessibilityService.dispatchGesture.
 */
data class TouchMotionEventStream(
    val streamId: String = "stream_${System.currentTimeMillis()}",
    val interactionType: String, // TAP, BURST, SWIPE, DRAG, DUAL_TOUCH
    val sourceCategory: UiElementCategory,
    val startX: Float,
    val startY: Float,
    val endX: Float = startX,
    val endY: Float = startY,
    val totalDurationMs: Long,
    val points: List<MotionStreamPoint>,
    val motionEvents: List<MotionEvent>,
    val reason: String
) {
    /**
     * Eliberează în siguranță obiectele native MotionEvent după ce au fost consumate.
     */
    fun recycleMotionEvents() {
        for (ev in motionEvents) {
            try {
                ev.recycle()
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Utilitar central [TouchController]:
 * Traduce coordonatele pe pixeli generate de serviciul de analiză a ecranului ([GameScreenAnalysisUtility]
 * și [MediaProjectionVisionEngine]) în fluxuri de evenimente native Android [MotionEvent]
 * (`ACTION_DOWN` ➔ `ACTION_MOVE` interpolat la 60/120Hz ➔ `ACTION_UP`) necesare pentru interacțiunile din jocuri.
 */
object TouchController {

    private val _lastTranslatedStream = MutableStateFlow<TouchMotionEventStream?>(null)
    val lastTranslatedStream: StateFlow<TouchMotionEventStream?> = _lastTranslatedStream.asStateFlow()

    private val _totalStreamsGenerated = MutableStateFlow(0L)
    val totalStreamsGenerated: StateFlow<Long> = _totalStreamsGenerated.asStateFlow()

    /**
     * Traduce direct rezultatul analizei de ecran ([ScreenAnalysisResult]) într-un flux complet
     * de evenimente [MotionEvent] gata de execuție.
     */
    fun translateScreenAnalysisToMotionStream(
        analysis: ScreenAnalysisResult
    ): TouchMotionEventStream? {
        // 1. Dacă avem o analiză dedicată de Tetris / Arcade, prioritizăm butonul D-Pad corespunzător
        val tetris = analysis.tetrisAnalysis
        if (tetris != null) {
            val matchingBtn = analysis.detectedElements.firstOrNull {
                it.category == tetris.recommendedButtonCategory
            }
            if (matchingBtn != null) {
                return translateElementToMotionStream(
                    element = matchingBtn,
                    reasonOverride = "Tetris/Arcade D-Pad (${matchingBtn.label}): col ${tetris.currentPieceColumn + 1} ➔ ${tetris.targetGapColumn + 1}"
                )
            }
        }

        // 2. Dacă există o țintă recomandată în ScreenAnalysisResult, o traducem în flux MotionEvent
        val target = analysis.recommendedTarget
        if (target != null) {
            return translateTargetToMotionStream(target)
        }

        // 3. Fallback pe primul element primar cu încredere ridicată
        val primaryElement = analysis.detectedElements
            .filter { it.confidence >= 0.65f }
            .maxByOrNull { if (it.isPrimaryAction) it.confidence + 0.25f else it.confidence }

        return primaryElement?.let { translateElementToMotionStream(it) }
    }

    /**
     * Traduce o țintă de interacțiune ([UiInteractionTarget]) într-un flux [TouchMotionEventStream].
     */
    fun translateTargetToMotionStream(
        target: UiInteractionTarget,
        defaultDurationMs: Long = 65L
    ): TouchMotionEventStream {
        return when (target.motionType.uppercase()) {
            "BURST", "BURST_TAPS", "RAPID_TAP" -> createBurstTapMotionStream(
                x = target.targetX,
                y = target.targetY,
                tapCount = 3,
                holdMs = 45L,
                intervalMs = 55L,
                category = target.actionCategory,
                reason = target.reason
            )

            "SWIPE", "FLICK" -> createSwipeMotionStream(
                startX = target.targetX,
                startY = target.targetY,
                endX = target.endX,
                endY = target.endY,
                durationMs = max(110L, defaultDurationMs),
                category = target.actionCategory,
                reason = target.reason
            )

            "DRAG", "DRAG_DROP", "HOLD_DRAG" -> createDragAndDropMotionStream(
                startX = target.targetX,
                startY = target.targetY,
                endX = target.endX,
                endY = target.endY,
                category = target.actionCategory,
                reason = target.reason
            )

            else -> createTapMotionStream(
                x = target.targetX,
                y = target.targetY,
                holdMs = defaultDurationMs.coerceIn(45L, 250L),
                category = target.actionCategory,
                reason = target.reason
            )
        }
    }

    /**
     * Traduce un element UI identificat ([IdentifiableUiElement]) într-un flux [TouchMotionEventStream].
     */
    fun translateElementToMotionStream(
        element: IdentifiableUiElement,
        endX: Float = element.centerX,
        endY: Float = element.centerY,
        reasonOverride: String? = null
    ): TouchMotionEventStream {
        val reason = reasonOverride ?: "${element.label} (${element.description.ifBlank { element.category.displayName }})"
        return when (element.suggestedMotion.uppercase()) {
            "BURST", "BURST_TAPS" -> createBurstTapMotionStream(
                x = element.centerX,
                y = element.centerY,
                tapCount = 3,
                category = element.category,
                reason = reason
            )

            "SWIPE" -> {
                val targetEndX = if (endX == element.centerX) element.centerX else endX
                val targetEndY = if (endY == element.centerY) (element.centerY - 140f).coerceAtLeast(16f) else endY
                createSwipeMotionStream(
                    startX = element.centerX,
                    startY = element.centerY,
                    endX = targetEndX,
                    endY = targetEndY,
                    durationMs = 150L,
                    category = element.category,
                    reason = reason
                )
            }

            "DRAG" -> createDragAndDropMotionStream(
                startX = element.centerX,
                startY = element.centerY,
                endX = endX,
                endY = endY,
                category = element.category,
                reason = reason
            )

            else -> createTapMotionStream(
                x = element.centerX,
                y = element.centerY,
                holdMs = 60L,
                category = element.category,
                reason = reason
            )
        }
    }

    /**
     * Generează un flux [MotionEvent] pentru o atingere punctuală (TAP):
     * `ACTION_DOWN` (t=0) ➔ `ACTION_MOVE` micro-stabilizare ➔ `ACTION_UP` (t=holdMs).
     */
    fun createTapMotionStream(
        x: Float,
        y: Float,
        holdMs: Long = 60L,
        category: UiElementCategory = UiElementCategory.BUTTON_START_PLAY,
        reason: String = "Tap Interaction"
    ): TouchMotionEventStream {
        val clampedHold = holdMs.coerceIn(35L, 600L)
        val baseDownTime = SystemClock.uptimeMillis()

        val points = listOf(
            MotionStreamPoint(
                action = MotionStreamAction.DOWN,
                x = x,
                y = y,
                pressure = 0.88f,
                offsetTimeMs = 0L
            ),
            MotionStreamPoint(
                action = MotionStreamAction.MOVE,
                x = x + 0.4f,
                y = y + 0.4f,
                pressure = 0.90f,
                offsetTimeMs = clampedHold / 2
            ),
            MotionStreamPoint(
                action = MotionStreamAction.UP,
                x = x + 0.4f,
                y = y + 0.4f,
                pressure = 0f,
                offsetTimeMs = clampedHold
            )
        )

        val events = points.map { pt ->
            obtainSinglePointerMotionEvent(
                downTime = baseDownTime,
                eventTime = baseDownTime + pt.offsetTimeMs,
                action = pt.action.androidAction,
                x = pt.x,
                y = pt.y,
                pressure = pt.pressure,
                size = pt.size
            )
        }

        return recordStream(
            TouchMotionEventStream(
                interactionType = "TAP",
                sourceCategory = category,
                startX = x,
                startY = y,
                endX = x,
                endY = y,
                totalDurationMs = clampedHold,
                points = points,
                motionEvents = events,
                reason = reason
            )
        )
    }

    /**
     * Generează un flux [MotionEvent] pentru rafală de atingeri rapide (BURST TAPS):
     * Secvență de cicluri `ACTION_DOWN` ➔ `ACTION_UP` la coordonatele specificate.
     */
    fun createBurstTapMotionStream(
        x: Float,
        y: Float,
        tapCount: Int = 3,
        holdMs: Long = 45L,
        intervalMs: Long = 55L,
        category: UiElementCategory = UiElementCategory.BUTTON_ATTACK_FIRE,
        reason: String = "Burst Tap Interaction"
    ): TouchMotionEventStream {
        val count = tapCount.coerceIn(2, 8)
        val clampedHold = holdMs.coerceIn(30L, 150L)
        val clampedGap = intervalMs.coerceIn(30L, 250L)
        val baseTime = SystemClock.uptimeMillis()

        val points = mutableListOf<MotionStreamPoint>()
        val events = mutableListOf<MotionEvent>()
        var elapsed = 0L

        for (i in 0 until count) {
            val jx = x + (i % 2) * 1.2f
            val jy = y + ((i + 1) % 2) * 1.2f
            val tapDownTime = baseTime + elapsed

            val downPt = MotionStreamPoint(
                action = MotionStreamAction.DOWN,
                x = jx,
                y = jy,
                pressure = 0.92f,
                offsetTimeMs = elapsed
            )
            val upPt = MotionStreamPoint(
                action = MotionStreamAction.UP,
                x = jx,
                y = jy,
                pressure = 0f,
                offsetTimeMs = elapsed + clampedHold
            )
            points.add(downPt)
            points.add(upPt)

            events.add(
                obtainSinglePointerMotionEvent(
                    downTime = tapDownTime,
                    eventTime = tapDownTime,
                    action = MotionEvent.ACTION_DOWN,
                    x = jx,
                    y = jy,
                    pressure = 0.92f,
                    size = 0.14f
                )
            )
            events.add(
                obtainSinglePointerMotionEvent(
                    downTime = tapDownTime,
                    eventTime = tapDownTime + clampedHold,
                    action = MotionEvent.ACTION_UP,
                    x = jx,
                    y = jy,
                    pressure = 0f,
                    size = 0.14f
                )
            )

            elapsed += clampedHold + clampedGap
        }

        return recordStream(
            TouchMotionEventStream(
                interactionType = "BURST",
                sourceCategory = category,
                startX = x,
                startY = y,
                endX = x,
                endY = y,
                totalDurationMs = elapsed,
                points = points,
                motionEvents = events,
                reason = reason
            )
        )
    }

    /**
     * Generează un flux continuu [MotionEvent] pentru glisare (SWIPE / JOYSTICK):
     * `ACTION_DOWN` ➔ N cadre `ACTION_MOVE` interpolate la 60Hz cu curbă smoothstep ➔ `ACTION_UP`.
     */
    fun createSwipeMotionStream(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 160L,
        frameRateHz: Int = 60,
        category: UiElementCategory = UiElementCategory.JOYSTICK_MOVEMENT,
        reason: String = "Swipe Interaction"
    ): TouchMotionEventStream {
        val clampedDuration = durationMs.coerceIn(60L, 1500L)
        val frameIntervalMs = (1000L / frameRateHz.coerceIn(30, 120)).coerceAtLeast(8L)
        val moveSteps = (clampedDuration / frameIntervalMs).toInt().coerceIn(4, 90)
        val baseDownTime = SystemClock.uptimeMillis()

        val points = mutableListOf<MotionStreamPoint>()
        val events = mutableListOf<MotionEvent>()

        // 1. ACTION_DOWN la punctul de start
        points.add(
            MotionStreamPoint(
                action = MotionStreamAction.DOWN,
                x = startX,
                y = startY,
                pressure = 0.85f,
                offsetTimeMs = 0L
            )
        )
        events.add(
            obtainSinglePointerMotionEvent(
                downTime = baseDownTime,
                eventTime = baseDownTime,
                action = MotionEvent.ACTION_DOWN,
                x = startX,
                y = startY,
                pressure = 0.85f,
                size = 0.13f
            )
        )

        // 2. ACTION_MOVE interpolat cu easing natural (Smoothstep)
        for (step in 1 until moveSteps) {
            val t = step.toFloat() / moveSteps.toFloat()
            val easedT = t * t * (3f - 2f * t)
            val curX = startX + (endX - startX) * easedT
            val curY = startY + (endY - startY) * easedT
            val offsetMs = (t * clampedDuration).toLong()
            val pressure = 0.82f + 0.10f * kotlin.math.sin(t * Math.PI).toFloat()

            points.add(
                MotionStreamPoint(
                    action = MotionStreamAction.MOVE,
                    x = curX,
                    y = curY,
                    pressure = pressure,
                    offsetTimeMs = offsetMs
                )
            )
            events.add(
                obtainSinglePointerMotionEvent(
                    downTime = baseDownTime,
                    eventTime = baseDownTime + offsetMs,
                    action = MotionEvent.ACTION_MOVE,
                    x = curX,
                    y = curY,
                    pressure = pressure,
                    size = 0.13f
                )
            )
        }

        // 3. ACTION_UP la destinație
        points.add(
            MotionStreamPoint(
                action = MotionStreamAction.UP,
                x = endX,
                y = endY,
                pressure = 0f,
                offsetTimeMs = clampedDuration
            )
        )
        events.add(
            obtainSinglePointerMotionEvent(
                downTime = baseDownTime,
                eventTime = baseDownTime + clampedDuration,
                action = MotionEvent.ACTION_UP,
                x = endX,
                y = endY,
                pressure = 0f,
                size = 0.13f
            )
        )

        return recordStream(
            TouchMotionEventStream(
                interactionType = "SWIPE",
                sourceCategory = category,
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                totalDurationMs = clampedDuration,
                points = points,
                motionEvents = events,
                reason = reason
            )
        )
    }

    /**
     * Generează un flux [MotionEvent] pentru Drag & Drop (Block Blast, Puzzle, Inventory):
     * Include menținere la start (grab), interpolare lină până la țintă și stabilizare înainte de `ACTION_UP`.
     */
    fun createDragAndDropMotionStream(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        holdStartMs: Long = 90L,
        dragDurationMs: Long = 320L,
        holdEndMs: Long = 50L,
        category: UiElementCategory = UiElementCategory.GAME_BOARD_ACTIVE_FIELD,
        reason: String = "Drag & Drop Interaction"
    ): TouchMotionEventStream {
        val totalDuration = (holdStartMs + dragDurationMs + holdEndMs).coerceIn(180L, 2500L)
        val baseDownTime = SystemClock.uptimeMillis()
        val points = mutableListOf<MotionStreamPoint>()
        val events = mutableListOf<MotionEvent>()

        // 1. ACTION_DOWN pe piesă
        points.add(MotionStreamPoint(MotionStreamAction.DOWN, startX, startY, pressure = 0.90f, offsetTimeMs = 0L))
        events.add(obtainSinglePointerMotionEvent(baseDownTime, baseDownTime, MotionEvent.ACTION_DOWN, startX, startY, 0.90f, 0.15f))

        // 2. Menținere pe loc (Grab Hold)
        points.add(MotionStreamPoint(MotionStreamAction.MOVE, startX + 0.5f, startY - 1.0f, pressure = 0.92f, offsetTimeMs = holdStartMs))
        events.add(obtainSinglePointerMotionEvent(baseDownTime, baseDownTime + holdStartMs, MotionEvent.ACTION_MOVE, startX + 0.5f, startY - 1.0f, 0.92f, 0.15f))

        // 3. Deplasare interpolată
        val moveSteps = 14
        for (i in 1..moveSteps) {
            val t = i.toFloat() / moveSteps.toFloat()
            val easedT = t * t * (3f - 2f * t)
            val cx = startX + (endX - startX) * easedT
            val cy = startY + (endY - startY) * easedT
            val offset = holdStartMs + (t * dragDurationMs).toLong()
            points.add(MotionStreamPoint(MotionStreamAction.MOVE, cx, cy, pressure = 0.88f, offsetTimeMs = offset))
            events.add(obtainSinglePointerMotionEvent(baseDownTime, baseDownTime + offset, MotionEvent.ACTION_MOVE, cx, cy, 0.88f, 0.15f))
        }

        // 4. Stabilizare pe celula țintă + ACTION_UP
        points.add(MotionStreamPoint(MotionStreamAction.MOVE, endX, endY, pressure = 0.85f, offsetTimeMs = totalDuration - 10L))
        events.add(obtainSinglePointerMotionEvent(baseDownTime, baseDownTime + totalDuration - 10L, MotionEvent.ACTION_MOVE, endX, endY, 0.85f, 0.15f))

        points.add(MotionStreamPoint(MotionStreamAction.UP, endX, endY, pressure = 0f, offsetTimeMs = totalDuration))
        events.add(obtainSinglePointerMotionEvent(baseDownTime, baseDownTime + totalDuration, MotionEvent.ACTION_UP, endX, endY, 0f, 0.15f))

        return recordStream(
            TouchMotionEventStream(
                interactionType = "DRAG",
                sourceCategory = category,
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                totalDurationMs = totalDuration,
                points = points,
                motionEvents = events,
                reason = reason
            )
        )
    }

    /**
     * Generează un flux Multi-Touch cu 2 pointeri simultani (degetul 1 = Joystick mișcare, degetul 2 = Aim / Foc):
     * Folosește `PointerProperties` și `PointerCoords` pentru `ACTION_DOWN`, `ACTION_POINTER_DOWN`, `ACTION_MOVE`, `ACTION_POINTER_UP`, `ACTION_UP`.
     */
    fun createMultiTouchMotionStream(
        p1StartX: Float,
        p1StartY: Float,
        p1EndX: Float,
        p1EndY: Float,
        p2StartX: Float,
        p2StartY: Float,
        p2EndX: Float,
        p2EndY: Float,
        durationMs: Long = 220L,
        category: UiElementCategory = UiElementCategory.BUTTON_ATTACK_FIRE,
        reason: String = "Multi-Touch Claw Interaction"
    ): TouchMotionEventStream {
        val clampedDuration = durationMs.coerceIn(80L, 1500L)
        val baseDownTime = SystemClock.uptimeMillis()
        val points = mutableListOf<MotionStreamPoint>()
        val events = mutableListOf<MotionEvent>()

        // 1. Degetul 1: ACTION_DOWN
        points.add(MotionStreamPoint(MotionStreamAction.DOWN, p1StartX, p1StartY, p2StartX, p2StartY, offsetTimeMs = 0L))
        events.add(obtainSinglePointerMotionEvent(baseDownTime, baseDownTime, MotionEvent.ACTION_DOWN, p1StartX, p1StartY, 0.85f, 0.13f))

        // 2. Degetul 2: ACTION_POINTER_DOWN (index 1)
        val ptrDownAction = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        points.add(MotionStreamPoint(MotionStreamAction.POINTER_DOWN, p1StartX, p1StartY, p2StartX, p2StartY, offsetTimeMs = 12L))
        events.add(
            obtainDualPointerMotionEvent(
                downTime = baseDownTime,
                eventTime = baseDownTime + 12L,
                action = ptrDownAction,
                x1 = p1StartX,
                y1 = p1StartY,
                x2 = p2StartX,
                y2 = p2StartY
            )
        )

        // 3. Mișcare simultană cu 2 degete
        val steps = 8
        for (s in 1..steps) {
            val t = s.toFloat() / steps.toFloat()
            val c1x = p1StartX + (p1EndX - p1StartX) * t
            val c1y = p1StartY + (p1EndY - p1StartY) * t
            val c2x = p2StartX + (p2EndX - p2StartX) * t
            val c2y = p2StartY + (p2EndY - p2StartY) * t
            val offset = (t * (clampedDuration - 20L)).toLong().coerceAtLeast(16L)

            points.add(MotionStreamPoint(MotionStreamAction.MOVE, c1x, c1y, c2x, c2y, offsetTimeMs = offset))
            events.add(
                obtainDualPointerMotionEvent(
                    downTime = baseDownTime,
                    eventTime = baseDownTime + offset,
                    action = MotionEvent.ACTION_MOVE,
                    x1 = c1x,
                    y1 = c1y,
                    x2 = c2x,
                    y2 = c2y
                )
            )
        }

        // 4. Ridicare degetul 2: ACTION_POINTER_UP
        val ptrUpAction = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        points.add(MotionStreamPoint(MotionStreamAction.POINTER_UP, p1EndX, p1EndY, p2EndX, p2EndY, offsetTimeMs = clampedDuration - 8L))
        events.add(
            obtainDualPointerMotionEvent(
                downTime = baseDownTime,
                eventTime = baseDownTime + clampedDuration - 8L,
                action = ptrUpAction,
                x1 = p1EndX,
                y1 = p1EndY,
                x2 = p2EndX,
                y2 = p2EndY
            )
        )

        // 5. Ridicare degetul 1: ACTION_UP
        points.add(MotionStreamPoint(MotionStreamAction.UP, p1EndX, p1EndY, pressure = 0f, offsetTimeMs = clampedDuration))
        events.add(obtainSinglePointerMotionEvent(baseDownTime, baseDownTime + clampedDuration, MotionEvent.ACTION_UP, p1EndX, p1EndY, 0f, 0.13f))

        return recordStream(
            TouchMotionEventStream(
                interactionType = "DUAL_TOUCH",
                sourceCategory = category,
                startX = p1StartX,
                startY = p1StartY,
                endX = p1EndX,
                endY = p1EndY,
                totalDurationMs = clampedDuration,
                points = points,
                motionEvents = events,
                reason = reason
            )
        )
    }

    /**
     * Convertește un [TouchMotionEventStream] într-un [GestureDescription] nativ Android
     * pentru injectare în aplicații/jocuri externe prin [EvaAccessibilityService.dispatchGesture].
     */
    fun toAccessibilityGesture(
        stream: TouchMotionEventStream,
        displayWidth: Int = 1080,
        displayHeight: Int = 2400
    ): GestureDescription? {
        if (stream.points.isEmpty()) return null
        return try {
            val maxW = (displayWidth - 8).coerceAtLeast(100).toFloat()
            val maxH = (displayHeight - 8).coerceAtLeast(100).toFloat()
            val builder = GestureDescription.Builder()

            if (stream.interactionType == "BURST") {
                // Pentru rafală, construim câte un StrokeDescription decalat temporal pentru fiecare ciclu DOWN->UP
                var i = 0
                while (i + 1 < stream.points.size) {
                    val downPt = stream.points[i]
                    val upPt = stream.points[i + 1]
                    val sx = downPt.x.coerceIn(8f, maxW)
                    val sy = downPt.y.coerceIn(8f, maxH)
                    val path = Path().apply {
                        moveTo(sx, sy)
                        lineTo(sx + 0.5f, sy + 0.5f)
                    }
                    val startTime = downPt.offsetTimeMs.coerceAtLeast(0L)
                    val dur = (upPt.offsetTimeMs - downPt.offsetTimeMs).coerceIn(30L, 300L)
                    builder.addStroke(GestureDescription.StrokeDescription(path, startTime, dur))
                    i += 2
                }
                return builder.build()
            }

            // Traseul principal (degetul 1) din secvența de puncte MotionEvent
            val primaryPath = Path()
            val first = stream.points.first()
            var lastX = first.x.coerceIn(8f, maxW)
            var lastY = first.y.coerceIn(8f, maxH)
            primaryPath.moveTo(lastX, lastY)
            var addedSegment = false
            for (idx in 1 until stream.points.size) {
                val pt = stream.points[idx]
                val nx = pt.x.coerceIn(8f, maxW)
                val ny = pt.y.coerceIn(8f, maxH)
                if (nx != lastX || ny != lastY) {
                    primaryPath.lineTo(nx, ny)
                    lastX = nx
                    lastY = ny
                    addedSegment = true
                }
            }
            if (!addedSegment) {
                primaryPath.lineTo((lastX + 0.5f).coerceIn(8f, maxW), (lastY + 0.5f).coerceIn(8f, maxH))
            }
            val duration = stream.totalDurationMs.coerceIn(40L, 3000L)
            builder.addStroke(GestureDescription.StrokeDescription(primaryPath, 0L, duration))

            // Dacă este DUAL_TOUCH, adăugăm și al doilea Stroke simultan pentru degetul 2
            val secondaryPoints = stream.points.filter { it.secondaryX != null && it.secondaryY != null }
            if (stream.interactionType == "DUAL_TOUCH" && secondaryPoints.isNotEmpty()) {
                val secPath = Path()
                val secFirst = secondaryPoints.first()
                var sLastX = secFirst.secondaryX!!.coerceIn(8f, maxW)
                var sLastY = secFirst.secondaryY!!.coerceIn(8f, maxH)
                secPath.moveTo(sLastX, sLastY)
                var secAdded = false
                for (idx in 1 until secondaryPoints.size) {
                    val spt = secondaryPoints[idx]
                    val snx = spt.secondaryX!!.coerceIn(8f, maxW)
                    val sny = spt.secondaryY!!.coerceIn(8f, maxH)
                    if (snx != sLastX || sny != sLastY) {
                        secPath.lineTo(snx, sny)
                        sLastX = snx
                        sLastY = sny
                        secAdded = true
                    }
                }
                if (!secAdded) {
                    secPath.lineTo((sLastX + 0.5f).coerceIn(8f, maxW), (sLastY + 0.5f).coerceIn(8f, maxH))
                }
                builder.addStroke(GestureDescription.StrokeDescription(secPath, 10L, (duration - 15L).coerceAtLeast(40L)))
            }

            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Trimite secvența de obiecte [MotionEvent] direct către o instanță [View] Android.
     * Returnează numărul de evenimente acceptate de View.
     */
    fun dispatchStreamToView(view: View, stream: TouchMotionEventStream): Int {
        var handledCount = 0
        for (ev in stream.motionEvents) {
            try {
                if (view.dispatchTouchEvent(ev)) {
                    handledCount++
                }
            } catch (_: Exception) {
            }
        }
        return handledCount
    }

    /**
     * Execută un [TouchMotionEventStream] pe ecranul jocului activ folosind serviciul de accesibilitate
     * (sau metodele calibrate de tap/swipe/drag) și eliberează evenimentele native la final.
     */
    fun executeMotionStream(
        stream: TouchMotionEventStream,
        accessibilityService: EvaAccessibilityService? = EvaAccessibilityService.instance
    ): Boolean {
        val acc = accessibilityService ?: return false
        return try {
            val (dispW, dispH) = acc.getLiveDisplaySize()
            val gesture = toAccessibilityGesture(stream, dispW, dispH)
            if (gesture != null) {
                acc.dispatchGesture(gesture, null, null)
            } else {
                when (stream.interactionType) {
                    "BURST" -> acc.performBurstTaps(stream.startX, stream.startY, 3, 60L)
                    "SWIPE" -> acc.performSwipe(stream.startX, stream.startY, stream.endX, stream.endY, stream.totalDurationMs)
                    "DRAG" -> acc.performDragAndDrop(stream.startX, stream.startY, stream.endX, stream.endY, stream.totalDurationMs)
                    else -> acc.performTap(stream.startX, stream.startY, stream.totalDurationMs)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun obtainSinglePointerMotionEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
        pressure: Float,
        size: Float
    ): MotionEvent {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                this.pressure = pressure
                this.size = size
                this.touchMajor = 14f
                this.touchMinor = 12f
            }
        )
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            props,
            coords,
            0,
            0,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
    }

    private fun obtainDualPointerMotionEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): MotionEvent {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
            MotionEvent.PointerProperties().apply {
                id = 1
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = x1
                this.y = y1
                this.pressure = 0.85f
                this.size = 0.13f
                this.touchMajor = 14f
                this.touchMinor = 12f
            },
            MotionEvent.PointerCoords().apply {
                this.x = x2
                this.y = y2
                this.pressure = 0.88f
                this.size = 0.13f
                this.touchMajor = 14f
                this.touchMinor = 12f
            }
        )
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            2,
            props,
            coords,
            0,
            0,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
    }

    private fun recordStream(stream: TouchMotionEventStream): TouchMotionEventStream {
        // Reciclăm evenimentele din stream-ul anterior dacă nu mai sunt folosite
        val prev = _lastTranslatedStream.value
        if (prev != null && prev !== stream) {
            prev.recycleMotionEvents()
        }
        _lastTranslatedStream.value = stream
        _totalStreamsGenerated.value = _totalStreamsGenerated.value + 1L
        return stream
    }
}
