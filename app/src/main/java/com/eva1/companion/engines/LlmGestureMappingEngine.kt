package com.eva1.companion.engines

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Tipul gestului tactil dinamic generat de LLM:
 */
enum class DynamicGestureMotion {
    TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    SWIPE,
    DRAG_AND_DROP,
    DUAL_TOUCH,
    BURST_TAPS,
    WAIT_OBSERVE
}

/**
 * Comanda tactilă dinamică rezolvată pe coordonate reale:
 */
data class DynamicGestureCommand(
    val id: Long = System.currentTimeMillis(),
    val motion: DynamicGestureMotion,
    val startX: Float,
    val startY: Float,
    val endX: Float = startX,
    val endY: Float = startY,
    val secondFingerStartX: Float? = null,
    val secondFingerStartY: Float? = null,
    val secondFingerEndX: Float? = null,
    val secondFingerEndY: Float? = null,
    val durationMs: Long = 75L,
    val repeatCount: Int = 1,
    val reasoning: String = "",
    val rawLlmDirective: String = "",
    val confidence: Float = 0.9f
)

/**
 * Rezultatul execuției fizice a gestului prin dispatchGesture:
 */
data class DynamicGestureExecutionResult(
    val command: DynamicGestureCommand,
    val isDispatched: Boolean,
    val executionLatencyMs: Long,
    val errorMessage: String? = null
)

/**
 * Serviciu de mapare și transformare dinamică a analizei textuale LLM
 * în gesturi tactile precise bazate pe coordonate (dispatchGesture).
 *
 * Elimină complet listele fixe de comenzi pre-scrise și interpretează
 * spațial analiza imaginii/ecranului efectuată de LLM (local sau vision).
 */
class LlmGestureMappingEngine(
    private val accessibilityServiceSupplier: () -> EvaAccessibilityService?
) {
    companion object {
        private const val TAG = "EVA_DYNAMIC_GESTURE"
    }

    private val _latestDispatchedGesture = MutableStateFlow<DynamicGestureCommand?>(null)
    val latestDispatchedGesture: StateFlow<DynamicGestureCommand?> = _latestDispatchedGesture.asStateFlow()

    private val _recentGesturesHistory = MutableStateFlow<List<DynamicGestureExecutionResult>>(emptyList())
    val recentGesturesHistory: StateFlow<List<DynamicGestureExecutionResult>> = _recentGesturesHistory.asStateFlow()

    @Volatile
    private var shellInputUnsupportedOnDevice = false

    /**
     * Parsează textul returnat de LLM și îl transformă într-o comandă tactilă dinamică pe rezoluția ecranului.
     */
    fun parseLlmAnalysisToGesture(
        llmText: String,
        screenWidth: Float,
        screenHeight: Float,
        activeSnapshot: LiveScreenSnapshot? = null
    ): DynamicGestureCommand {
        val cleanText = llmText.trim()
        val cx = screenWidth * 0.5f
        val cy = screenHeight * 0.5f

        // 1. Încercare de parsare din JSON structurat (dacă LLM a returnat obiect JSON)
        tryParseJson(cleanText, screenWidth, screenHeight)?.let { return it }

        var hasExplicitCoords = false

        // 2. Parsare prin etichete explicite (GESTURE:, ACTION:, COORDS:, FROM:, TO:, REASON:)
        var motion = DynamicGestureMotion.TAP
        var startX = cx
        var startY = cy
        var endX = cx
        var endY = cy
        var duration = 75L
        var repeats = 1
        var reason = ""

        val lines = cleanText.lines()
        for (line in lines) {
            val upper = line.uppercase().trim()

            // Identificare tip gest
            if (upper.contains("GESTURE:") || upper.contains("ACTION:") || upper.contains("MOTION:")) {
                val motionStr = line.substringAfter(":").trim().split(" ", ";", "\n").firstOrNull()?.uppercase().orEmpty()
                motion = when {
                    motionStr.contains("SWIPE") || motionStr.contains("SLIDE") -> DynamicGestureMotion.SWIPE
                    motionStr.contains("DRAG") || motionStr.contains("DROP") -> DynamicGestureMotion.DRAG_AND_DROP
                    motionStr.contains("LONG") || motionStr.contains("HOLD") -> DynamicGestureMotion.LONG_PRESS
                    motionStr.contains("BURST") || motionStr.contains("FIRE") -> DynamicGestureMotion.BURST_TAPS
                    motionStr.contains("DOUBLE") -> DynamicGestureMotion.DOUBLE_TAP
                    motionStr.contains("DUAL") || motionStr.contains("JOYSTICK") -> DynamicGestureMotion.DUAL_TOUCH
                    motionStr.contains("WAIT") || motionStr.contains("OBSERVE") -> DynamicGestureMotion.WAIT_OBSERVE
                    else -> DynamicGestureMotion.TAP
                }
            }

            // Identificare coordonate START (COORDS: x,y sau FROM: x,y)
            if (upper.contains("COORDS:") || upper.contains("FROM:") || upper.contains("START:")) {
                val coordsStr = line.substringAfter(":").trim()
                val parsed = extractCoordinates(coordsStr, screenWidth, screenHeight)
                if (parsed != null) {
                    startX = parsed.first
                    startY = parsed.second
                    endX = startX
                    endY = startY
                    hasExplicitCoords = true
                }
            }

            // Identificare coordonate DESTINAȚIE (TO: x,y sau DEST:)
            if (upper.contains("TO:") || upper.contains("DEST:") || upper.contains("END:")) {
                val coordsStr = line.substringAfter(":").trim()
                val parsed = extractCoordinates(coordsStr, screenWidth, screenHeight)
                if (parsed != null) {
                    endX = parsed.first
                    endY = parsed.second
                    hasExplicitCoords = true
                }
            }

            // Identificare durată
            if (upper.contains("DURATION:") || upper.contains("TIME_MS:") || upper.contains("MS:")) {
                val digits = line.substringAfter(":").filter { it.isDigit() }
                val parsedMs = digits.toLongOrNull()
                if (parsedMs != null && parsedMs in 40L..2500L) {
                    duration = parsedMs
                }
            }

            // Identificare motiv / raționament
            if (upper.contains("REASON:") || upper.contains("MOTIV:") || upper.contains("WHY:")) {
                reason = line.substringAfter(":").trim().take(120)
            }
        }

        // 3. Fallback dacă textul este liber (fără prefixe GESTURE:): Căutare pattern-uri de coordonate în text
        if (reason.isBlank()) {
            reason = cleanText.lines().firstOrNull { it.isNotBlank() }?.take(90) ?: "Decizie dinamică LLM"
        }

        if (!hasExplicitCoords) {
            val inlineCoords = extractCoordinates(cleanText, screenWidth, screenHeight)
            if (inlineCoords != null) {
                startX = inlineCoords.first
                startY = inlineCoords.second
                endX = startX
                endY = startY
                hasExplicitCoords = true
            }
        }

        // 4. Căutare nod UI țintă dacă LLM menționează o etichetă de buton existent pe ecran (ex: TARGET: "Attack")
        if (!hasExplicitCoords && activeSnapshot != null) {
            for (element in activeSnapshot.elements) {
                if (element.text.isNotBlank() && cleanText.contains(element.text, ignoreCase = true)) {
                    startX = element.centerX.toFloat()
                    startY = element.centerY.toFloat()
                    endX = startX
                    endY = startY
                    motion = DynamicGestureMotion.TAP
                    reason = "Apăsare pe butonul/nodul UI „${element.text}”"
                    hasExplicitCoords = true
                    break
                }
            }
        }

        // CRITIC PENTRU EVITAREA ATINGERILOR HAOTICE:
        // Dacă nu s-au identificat coordonate clare și niciun buton pe ecran,
        // NICIODATĂ nu apăsăm orbește pe centrul ecranului! Trecem pe mod pasiv de observare.
        if (!hasExplicitCoords && motion != DynamicGestureMotion.WAIT_OBSERVE) {
            motion = DynamicGestureMotion.WAIT_OBSERVE
            reason = if (reason.isNotBlank()) "Observare: $reason" else "Observare pasivă (fără atingeri pe centrul ecranului)"
        }

        // Calibrare durate în funcție de mișcare
        if (motion == DynamicGestureMotion.BURST_TAPS) {
            repeats = 3
            duration = 65L
        } else if (motion == DynamicGestureMotion.LONG_PRESS) {
            duration = 550L
        } else if ((motion == DynamicGestureMotion.SWIPE || motion == DynamicGestureMotion.DRAG_AND_DROP) && duration < 120L) {
            duration = 240L
        }

        return DynamicGestureCommand(
            motion = motion,
            startX = startX.coerceIn(8f, screenWidth - 8f),
            startY = startY.coerceIn(8f, screenHeight - 8f),
            endX = endX.coerceIn(8f, screenWidth - 8f),
            endY = endY.coerceIn(8f, screenHeight - 8f),
            durationMs = duration,
            repeatCount = repeats,
            reasoning = reason,
            rawLlmDirective = cleanText.take(160)
        )
    }

    /**
     * Încearcă să extragă o comandă completă dintr-un payload JSON emis de LLM
     */
    private fun tryParseJson(text: String, width: Float, height: Float): DynamicGestureCommand? {
        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return null

        return try {
            val rawJson = text.substring(jsonStart, jsonEnd + 1)
            val json = JSONObject(rawJson)

            val motionStr = json.optString("gesture").ifBlank { json.optString("action") }.uppercase()
            var motion = when {
                motionStr.contains("SWIPE") -> DynamicGestureMotion.SWIPE
                motionStr.contains("DRAG") -> DynamicGestureMotion.DRAG_AND_DROP
                motionStr.contains("LONG") -> DynamicGestureMotion.LONG_PRESS
                motionStr.contains("BURST") || motionStr.contains("FIRE") -> DynamicGestureMotion.BURST_TAPS
                motionStr.contains("WAIT") -> DynamicGestureMotion.WAIT_OBSERVE
                else -> DynamicGestureMotion.TAP
            }

            var hasCoords = false
            var startX = width * 0.5f
            var startY = height * 0.5f
            var endX = startX
            var endY = startY

            // Extragere coordonate Start
            if (json.has("coords")) {
                val coords = json.getJSONArray("coords")
                val (x, y) = resolveRawCoords(coords.getDouble(0).toFloat(), coords.getDouble(1).toFloat(), width, height)
                startX = x
                startY = y
                endX = x
                endY = y
                hasCoords = true
            } else if (json.has("x") && json.has("y")) {
                val (x, y) = resolveRawCoords(json.getDouble("x").toFloat(), json.getDouble("y").toFloat(), width, height)
                startX = x
                startY = y
                endX = x
                endY = y
                hasCoords = true
            }

            if (json.has("from")) {
                val fromArr = json.getJSONArray("from")
                val (x, y) = resolveRawCoords(fromArr.getDouble(0).toFloat(), fromArr.getDouble(1).toFloat(), width, height)
                startX = x
                startY = y
                hasCoords = true
            }

            if (json.has("to")) {
                val toArr = json.getJSONArray("to")
                val (x, y) = resolveRawCoords(toArr.getDouble(0).toFloat(), toArr.getDouble(1).toFloat(), width, height)
                endX = x
                endY = y
                hasCoords = true
            }

            // Dacă în JSON nu au fost furnizate coordonate și nu e WAIT, trecem pe WAIT_OBSERVE pentru a evita tap pe centru
            if (!hasCoords && motion != DynamicGestureMotion.WAIT_OBSERVE) {
                motion = DynamicGestureMotion.WAIT_OBSERVE
            }

            val duration = json.optLong("duration_ms", if (motion == DynamicGestureMotion.SWIPE) 220L else 75L)
            val reason = json.optString("reasoning").ifBlank { json.optString("reason", "Decizie dinamică JSON") }

            DynamicGestureCommand(
                motion = motion,
                startX = startX.coerceIn(8f, width - 8f),
                startY = startY.coerceIn(8f, height - 8f),
                endX = endX.coerceIn(8f, width - 8f),
                endY = endY.coerceIn(8f, height - 8f),
                durationMs = duration,
                repeatCount = if (motion == DynamicGestureMotion.BURST_TAPS) 3 else 1,
                reasoning = reason,
                rawLlmDirective = rawJson.take(160)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveRawCoords(rawX: Float, rawY: Float, width: Float, height: Float): Pair<Float, Float> {
        val x = if (rawX in 0.001f..1.0f) rawX * width else rawX
        val y = if (rawY in 0.001f..1.0f) rawY * height else rawY
        return x to y
    }

    private fun extractCoordinates(text: String, width: Float, height: Float): Pair<Float, Float>? {
        // Căutare pattern-uri de tip: (0.85, 0.72) sau 0.85, 0.72 sau 850, 1450 sau [0.5, 0.5]
        val matcher = Pattern.compile("[\\[\\(]?\\s*([0-9]*\\.?[0-9]+)%?\\s*[,;\\s]+\\s*([0-9]*\\.?[0-9]+)%?\\s*[\\]\\)]?").matcher(text)
        if (matcher.find()) {
            val v1 = matcher.group(1)?.toFloatOrNull() ?: return null
            val v2 = matcher.group(2)?.toFloatOrNull() ?: return null

            // Normalizare dacă sunt procente (ex: 85, 72)
            val n1 = if (v1 > 1.0f && v1 <= 100.0f && text.contains("%")) v1 / 100f else v1
            val n2 = if (v2 > 1.0f && v2 <= 100.0f && text.contains("%")) v2 / 100f else v2

            val x = if (n1 in 0.001f..1.0f) n1 * width else n1
            val y = if (n2 in 0.001f..1.0f) n2 * height else n2
            return x to y
        }
        return null
    }

    /**
     * Convertește comanda în gest fizic Android (GestureDescription) și o trimite prin dispatchGesture.
     */
    suspend fun translateAndDispatch(
        command: DynamicGestureCommand
    ): DynamicGestureExecutionResult = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()
        val acc = accessibilityServiceSupplier()
        if (acc == null) {
            // Fallback automat prin comenzi Shell de sistem Android (input tap / input swipe)
            val shellOk = when (command.motion) {
                DynamicGestureMotion.WAIT_OBSERVE -> true
                DynamicGestureMotion.TAP, DynamicGestureMotion.DOUBLE_TAP, DynamicGestureMotion.LONG_PRESS -> {
                    executeShellWithTimeout("input tap ${command.startX.toInt()} ${command.startY.toInt()}")
                }
                DynamicGestureMotion.BURST_TAPS -> {
                    executeShellWithTimeout("input tap ${command.startX.toInt()} ${command.startY.toInt()}; input tap ${command.startX.toInt()} ${command.startY.toInt()}")
                }
                DynamicGestureMotion.SWIPE, DynamicGestureMotion.DRAG_AND_DROP, DynamicGestureMotion.DUAL_TOUCH -> {
                    executeShellWithTimeout("input swipe ${command.startX.toInt()} ${command.startY.toInt()} ${command.endX.toInt()} ${command.endY.toInt()} ${command.durationMs}")
                }
            }

            val latency = System.currentTimeMillis() - startTime
            val res = DynamicGestureExecutionResult(
                command = command,
                isDispatched = shellOk,
                executionLatencyMs = latency,
                errorMessage = if (!shellOk) "AccessibilityService offline și comanda shell input a eșuat" else null
            )
            _latestDispatchedGesture.value = command
            Log.i(TAG, "💻 [SHELL INPUT FALLBACK] ${command.motion} la (${command.startX.toInt()}, ${command.startY.toInt()}) | Dispatched: $shellOk (${latency}ms)")
            recordResult(res)
            return@withContext res
        }

        if (command.motion == DynamicGestureMotion.WAIT_OBSERVE) {
            val res = DynamicGestureExecutionResult(command, true, 0L, null)
            recordResult(res)
            return@withContext res
        }

        _latestDispatchedGesture.value = command

        try {
            val gesture = buildGestureDescription(command)
            val deferred = CompletableDeferred<Boolean>()

            val dispatchedOk = acc.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        deferred.complete(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        deferred.complete(false)
                    }
                },
                null
            )

            val latency = System.currentTimeMillis() - startTime
            val completed = if (dispatchedOk) {
                withTimeoutOrNull(command.durationMs + 350L) { deferred.await() } ?: false
            } else {
                // Fallback shell la refuzul temporar al dispatchGesture
                when (command.motion) {
                    DynamicGestureMotion.SWIPE, DynamicGestureMotion.DRAG_AND_DROP -> {
                        executeShellWithTimeout("input swipe ${command.startX.toInt()} ${command.startY.toInt()} ${command.endX.toInt()} ${command.endY.toInt()} ${command.durationMs}")
                    }
                    else -> {
                        executeShellWithTimeout("input tap ${command.startX.toInt()} ${command.startY.toInt()}")
                    }
                }
            }

            val result = DynamicGestureExecutionResult(
                command = command,
                isDispatched = completed || dispatchedOk,
                executionLatencyMs = latency,
                errorMessage = if (!dispatchedOk && !completed) "dispatchGesture a returnat false (ecran blocat sau permisiuni)" else null
            )

            Log.i(TAG, "⚡ [DYNAMIC GESTURE EXECUTED] ${command.motion} la (${command.startX.toInt()}, ${command.startY.toInt()}) → (${command.endX.toInt()}, ${command.endY.toInt()}) | Dispatched: ${result.isDispatched} (${latency}ms) | Motiv: ${command.reasoning}")
            recordResult(result)
            return@withContext result
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val result = DynamicGestureExecutionResult(
                command = command,
                isDispatched = false,
                executionLatencyMs = latency,
                errorMessage = e.message
            )
            Log.e(TAG, "Eroare dispatchGesture: ${e.message}")
            recordResult(result)
            return@withContext result
        }
    }

    private fun buildGestureDescription(cmd: DynamicGestureCommand): GestureDescription {
        val builder = GestureDescription.Builder()

        when (cmd.motion) {
            DynamicGestureMotion.TAP, DynamicGestureMotion.LONG_PRESS -> {
                val path = Path().apply {
                    moveTo(cmd.startX, cmd.startY)
                    lineTo(cmd.startX + 0.5f, cmd.startY + 0.5f)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0L, cmd.durationMs.coerceIn(45L, 800L))
                builder.addStroke(stroke)
            }

            DynamicGestureMotion.BURST_TAPS -> {
                // Multi-tap rapid pe aceeași poziție
                val count = cmd.repeatCount.coerceIn(1, 4)
                var delayOffset = 0L
                for (i in 0 until count) {
                    val path = Path().apply {
                        moveTo(cmd.startX, cmd.startY)
                        lineTo(cmd.startX + 0.5f, cmd.startY + 0.5f)
                    }
                    builder.addStroke(GestureDescription.StrokeDescription(path, delayOffset, 55L))
                    delayOffset += 95L
                }
            }

            DynamicGestureMotion.SWIPE, DynamicGestureMotion.DRAG_AND_DROP -> {
                val path = Path().apply {
                    moveTo(cmd.startX, cmd.startY)
                    lineTo(cmd.endX, cmd.endY)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0L, cmd.durationMs.coerceIn(80L, 1200L))
                builder.addStroke(stroke)
            }

            DynamicGestureMotion.DUAL_TOUCH -> {
                // Mișcare cu 2 degete în paralel (ex: degetul 1 controlează joystick-ul, degetul 2 apasă tragere/acțiune)
                val p1EndX = if (cmd.startX == cmd.endX && cmd.startY == cmd.endY) cmd.endX + 0.5f else cmd.endX
                val p1EndY = if (cmd.startX == cmd.endX && cmd.startY == cmd.endY) cmd.endY + 0.5f else cmd.endY
                val path1 = Path().apply {
                    moveTo(cmd.startX, cmd.startY)
                    lineTo(p1EndX, p1EndY)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path1, 0L, cmd.durationMs.coerceAtLeast(50L)))

                val f2StartX = cmd.secondFingerStartX ?: (cmd.startX + 200f)
                val f2StartY = cmd.secondFingerStartY ?: cmd.startY
                val rawF2EndX = cmd.secondFingerEndX ?: f2StartX
                val rawF2EndY = cmd.secondFingerEndY ?: f2StartY
                val f2EndX = if (f2StartX == rawF2EndX && f2StartY == rawF2EndY) rawF2EndX + 0.5f else rawF2EndX
                val f2EndY = if (f2StartX == rawF2EndX && f2StartY == rawF2EndY) rawF2EndY + 0.5f else rawF2EndY
                val path2 = Path().apply {
                    moveTo(f2StartX, f2StartY)
                    lineTo(f2EndX, f2EndY)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path2, 0L, cmd.durationMs.coerceAtLeast(50L)))
            }

            DynamicGestureMotion.DOUBLE_TAP -> {
                val path1 = Path().apply {
                    moveTo(cmd.startX, cmd.startY)
                    lineTo(cmd.startX + 0.5f, cmd.startY + 0.5f)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path1, 0L, 50L))

                val path2 = Path().apply {
                    moveTo(cmd.startX, cmd.startY)
                    lineTo(cmd.startX + 0.5f, cmd.startY + 0.5f)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path2, 110L, 50L))
            }

            DynamicGestureMotion.WAIT_OBSERVE -> {
                // Nu generăm stroke-uri fizice
            }
        }

        return builder.build()
    }

    private fun recordResult(res: DynamicGestureExecutionResult) {
        val current = _recentGesturesHistory.value
        _recentGesturesHistory.value = (listOf(res) + current).take(25)
    }

    private suspend fun executeShellWithTimeout(command: String, timeoutMs: Long = 250L): Boolean = withContext(Dispatchers.IO) {
        if (shellInputUnsupportedOnDevice) return@withContext false
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            val ok = if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
            if (!ok) {
                shellInputUnsupportedOnDevice = true
            }
            ok
        } catch (_: Exception) {
            shellInputUnsupportedOnDevice = true
            false
        }
    }
}
