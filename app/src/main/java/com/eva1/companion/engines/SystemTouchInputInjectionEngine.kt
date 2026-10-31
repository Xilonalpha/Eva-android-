package com.eva1.companion.engines

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Modul de injectare a comenzilor tactile la nivel de sistem Android:
 */
enum class TouchInjectionMethod(val displayName: String, val badge: String) {
    ACCESSIBILITY_DISPATCH("Serviciu Accesibilitate (dispatchGesture)", "⚡"),
    SHELL_INPUT_COMMAND("Comenzi Shell Android (input tap/swipe)", "💻"),
    AUTO_HYBRID("Mod Hibrid Inteligent (Accesibilitate + Fallback Shell)", "🧠")
}

/**
 * Raport de execuție a gestului tactil injectat:
 */
data class TouchInjectionReport(
    val timestamp: Long = System.currentTimeMillis(),
    val methodUsed: TouchInjectionMethod,
    val gestureType: String,
    val startX: Float,
    val startY: Float,
    val endX: Float = startX,
    val endY: Float = startY,
    val durationMs: Long,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val details: String = ""
)

/**
 * Motor centralizat de injectare a atingerilor și gesturilor tactile la nivel de sistem:
 *  1. Folosește API-ul oficial Android `dispatchGesture` prin `EvaAccessibilityService`.
 *  2. Are fallback automat pe comenzi shell de sistem (`sh -c input tap ...` sau `input swipe ...`)
 *     dacă serviciul de accesibilitate nu a fost încă pornit sau întâmpină restricții.
 *  3. Garantează atingeri precise la coordonatele `(X, Y)` determinate de analiza de ecran (Computer Vision / LLM).
 */
class SystemTouchInputInjectionEngine(
    private val context: Context? = null,
    private val accessibilityServiceSupplier: () -> EvaAccessibilityService? = { EvaAccessibilityService.instance }
) {
    companion object {
        private const val TAG = "EVA_TOUCH_INJECTION"
        val instance = SystemTouchInputInjectionEngine(context = run {
            try {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as? Context
            } catch (_: Exception) {
                null
            } ?: ApplicationContextProvider.appContext
        })
    }

    private val _injectionMethod = MutableStateFlow(TouchInjectionMethod.AUTO_HYBRID)
    val injectionMethod: StateFlow<TouchInjectionMethod> = _injectionMethod.asStateFlow()

    private val _lastReport = MutableStateFlow<TouchInjectionReport?>(null)
    val lastReport: StateFlow<TouchInjectionReport?> = _lastReport.asStateFlow()

    private val _totalInjectionsCount = MutableStateFlow(0L)
    val totalInjectionsCount: StateFlow<Long> = _totalInjectionsCount.asStateFlow()

    fun setInjectionMethod(method: TouchInjectionMethod) {
        _injectionMethod.value = method
    }

    /**
     * Injectează o atingere simplă (TAP) precisă la coordonatele specificate.
     */
    suspend fun injectTap(
        x: Float,
        y: Float,
        durationMs: Long = 70L,
        reason: String = ""
    ): Boolean = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val acc = accessibilityServiceSupplier()
        val preferred = _injectionMethod.value

        var success = false
        var usedMethod = TouchInjectionMethod.SHELL_INPUT_COMMAND

        if (preferred != TouchInjectionMethod.SHELL_INPUT_COMMAND && acc != null) {
            success = acc.performTap(x, y, durationMs)
            usedMethod = TouchInjectionMethod.ACCESSIBILITY_DISPATCH
        }

        // Fallback pe comandă shell de sistem dacă accesibilitatea a eșuat sau e dezactivată
        if (!success && (preferred == TouchInjectionMethod.AUTO_HYBRID || preferred == TouchInjectionMethod.SHELL_INPUT_COMMAND)) {
            success = executeShellTap(x.roundToInt(), y.roundToInt())
            usedMethod = TouchInjectionMethod.SHELL_INPUT_COMMAND
        }

        val latency = System.currentTimeMillis() - startTime
        val report = TouchInjectionReport(
            methodUsed = usedMethod,
            gestureType = "TAP",
            startX = x,
            startY = y,
            durationMs = durationMs,
            isSuccess = success,
            latencyMs = latency,
            details = if (reason.isNotBlank()) reason else "Tap precis la (${x.roundToInt()}, ${y.roundToInt()})"
        )
        _lastReport.value = report
        _totalInjectionsCount.value = _totalInjectionsCount.value + 1L
        success
    }

    /**
     * Injectează o rafală de atingeri rapide consecutive (BURST TAPS / Turbo-Fire)
     */
    suspend fun injectBurstTaps(
        x: Float,
        y: Float,
        count: Int = 3,
        intervalMs: Long = 65L,
        reason: String = ""
    ): Boolean = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val acc = accessibilityServiceSupplier()
        val preferred = _injectionMethod.value
        val taps = count.coerceIn(1, 6)

        var success = false
        var usedMethod = TouchInjectionMethod.SHELL_INPUT_COMMAND

        if (preferred != TouchInjectionMethod.SHELL_INPUT_COMMAND && acc != null) {
            success = acc.performBurstTaps(x, y, taps, intervalMs)
            usedMethod = TouchInjectionMethod.ACCESSIBILITY_DISPATCH
        }

        if (!success && (preferred == TouchInjectionMethod.AUTO_HYBRID || preferred == TouchInjectionMethod.SHELL_INPUT_COMMAND)) {
            val cmd = buildString {
                for (i in 0 until taps) {
                    append("input tap ${x.roundToInt()} ${y.roundToInt()}; ")
                }
            }
            success = executeShellDirect(cmd)
            usedMethod = TouchInjectionMethod.SHELL_INPUT_COMMAND
        }

        val latency = System.currentTimeMillis() - startTime
        _lastReport.value = TouchInjectionReport(
            methodUsed = usedMethod,
            gestureType = "BURST_TAPS_x$taps",
            startX = x,
            startY = y,
            durationMs = taps * intervalMs,
            isSuccess = success,
            latencyMs = latency,
            details = reason.ifBlank { "Rafală de $taps atingeri la (${x.roundToInt()}, ${y.roundToInt()})" }
        )
        _totalInjectionsCount.value = _totalInjectionsCount.value + taps
        success
    }

    /**
     * Injectează o glisare / swipe precis între două coordonate.
     */
    suspend fun injectSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 180L,
        reason: String = ""
    ): Boolean = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val acc = accessibilityServiceSupplier()
        val preferred = _injectionMethod.value

        var success = false
        var usedMethod = TouchInjectionMethod.SHELL_INPUT_COMMAND

        if (preferred != TouchInjectionMethod.SHELL_INPUT_COMMAND && acc != null) {
            success = acc.performSwipe(startX, startY, endX, endY, durationMs)
            usedMethod = TouchInjectionMethod.ACCESSIBILITY_DISPATCH
        }

        if (!success && (preferred == TouchInjectionMethod.AUTO_HYBRID || preferred == TouchInjectionMethod.SHELL_INPUT_COMMAND)) {
            success = executeShellSwipe(
                startX.roundToInt(),
                startY.roundToInt(),
                endX.roundToInt(),
                endY.roundToInt(),
                durationMs
            )
            usedMethod = TouchInjectionMethod.SHELL_INPUT_COMMAND
        }

        val latency = System.currentTimeMillis() - startTime
        _lastReport.value = TouchInjectionReport(
            methodUsed = usedMethod,
            gestureType = "SWIPE",
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            durationMs = durationMs,
            isSuccess = success,
            latencyMs = latency,
            details = reason.ifBlank { "Swipe (${startX.roundToInt()}, ${startY.roundToInt()}) -> (${endX.roundToInt()}, ${endY.roundToInt()})" }
        )
        _totalInjectionsCount.value = _totalInjectionsCount.value + 1L
        success
    }

    /**
     * Injectează un gest de Drag & Drop (ridicare, menținere și plasare precisă).
     */
    suspend fun injectDragAndDrop(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 440L,
        reason: String = ""
    ): Boolean = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val acc = accessibilityServiceSupplier()
        val preferred = _injectionMethod.value

        var success = false
        var usedMethod = TouchInjectionMethod.SHELL_INPUT_COMMAND

        if (preferred != TouchInjectionMethod.SHELL_INPUT_COMMAND && acc != null) {
            success = acc.performDragAndDrop(startX, startY, endX, endY, durationMs)
            usedMethod = TouchInjectionMethod.ACCESSIBILITY_DISPATCH
        }

        if (!success && (preferred == TouchInjectionMethod.AUTO_HYBRID || preferred == TouchInjectionMethod.SHELL_INPUT_COMMAND)) {
            // Prin shell, swipe cu durată mai lungă simulează drag & drop
            success = executeShellSwipe(
                startX.roundToInt(),
                startY.roundToInt(),
                endX.roundToInt(),
                endY.roundToInt(),
                durationMs.coerceAtLeast(350L)
            )
            usedMethod = TouchInjectionMethod.SHELL_INPUT_COMMAND
        }

        val latency = System.currentTimeMillis() - startTime
        _lastReport.value = TouchInjectionReport(
            methodUsed = usedMethod,
            gestureType = "DRAG_AND_DROP",
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            durationMs = durationMs,
            isSuccess = success,
            latencyMs = latency,
            details = reason.ifBlank { "Drag & drop (${startX.roundToInt()}, ${startY.roundToInt()}) -> (${endX.roundToInt()}, ${endY.roundToInt()})" }
        )
        _totalInjectionsCount.value = _totalInjectionsCount.value + 1L
        success
    }

    /**
     * Injectează gestul Dual-Thumb (control simultan cu ambele mâini / două degete):
     * Mâna stângă: Joystick mișcare/sprint; Mâna dreaptă: Tragere/Abilitate/Pan cameră.
     */
    suspend fun injectDualThumb(
        joyStartX: Float,
        joyStartY: Float,
        joyEndX: Float,
        joyEndY: Float,
        attackX: Float,
        attackY: Float,
        durationMs: Long = 340L,
        reason: String = ""
    ): Boolean = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val acc = accessibilityServiceSupplier()
        val preferred = _injectionMethod.value

        var success = false
        var usedMethod = TouchInjectionMethod.SHELL_INPUT_COMMAND

        if (preferred != TouchInjectionMethod.SHELL_INPUT_COMMAND && acc != null) {
            success = acc.performDualThumbAction(joyStartX, joyStartY, joyEndX, joyEndY, attackX, attackY, durationMs)
            usedMethod = TouchInjectionMethod.ACCESSIBILITY_DISPATCH
        }

        if (!success && (preferred == TouchInjectionMethod.AUTO_HYBRID || preferred == TouchInjectionMethod.SHELL_INPUT_COMMAND)) {
            // Shell fallback: execută secvențial atac + mișcare joystick
            val cmd = "input tap ${attackX.roundToInt()} ${attackY.roundToInt()} & input swipe ${joyStartX.roundToInt()} ${joyStartY.roundToInt()} ${joyEndX.roundToInt()} ${joyEndY.roundToInt()} $durationMs"
            success = executeShellDirect(cmd)
            usedMethod = TouchInjectionMethod.SHELL_INPUT_COMMAND
        }

        val latency = System.currentTimeMillis() - startTime
        _lastReport.value = TouchInjectionReport(
            methodUsed = usedMethod,
            gestureType = "DUAL_THUMB",
            startX = joyStartX,
            startY = joyStartY,
            endX = attackX,
            endY = attackY,
            durationMs = durationMs,
            isSuccess = success,
            latencyMs = latency,
            details = reason.ifBlank { "Dual-thumb: Joy (${joyStartX.roundToInt()}, ${joyStartY.roundToInt()}) + Atac (${attackX.roundToInt()}, ${attackY.roundToInt()})" }
        )
        _totalInjectionsCount.value = _totalInjectionsCount.value + 1L
        success
    }

    @Volatile
    private var shellInjectionPermitted: Boolean? = null

    private fun canAttemptShellFallback(preferred: TouchInjectionMethod): Boolean {
        if (preferred == TouchInjectionMethod.SHELL_INPUT_COMMAND) return true
        if (preferred == TouchInjectionMethod.ACCESSIBILITY_DISPATCH) return false
        return shellInjectionPermitted != false
    }

    /**
     * Execuție shell optimizată pentru 'input tap'
     */
    private fun executeShellTap(x: Int, y: Int): Boolean {
        if (_injectionMethod.value == TouchInjectionMethod.AUTO_HYBRID && shellInjectionPermitted == false) {
            return false
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "input tap $x $y"))
            val finished = process.waitFor(180L, java.util.concurrent.TimeUnit.MILLISECONDS)
            val ok = if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
            if (!ok && shellInjectionPermitted == null) {
                shellInjectionPermitted = false
            } else if (ok) {
                shellInjectionPermitted = true
            }
            ok
        } catch (e: Exception) {
            shellInjectionPermitted = false
            Log.w(TAG, "Eroare execuție shell input tap: ${e.message}")
            false
        }
    }

    /**
     * Execuție shell optimizată pentru 'input swipe'
     */
    private fun executeShellSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        if (_injectionMethod.value == TouchInjectionMethod.AUTO_HYBRID && shellInjectionPermitted == false) {
            return false
        }
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "input swipe $x1 $y1 $x2 $y2 $durationMs")
            )
            val finished = process.waitFor(durationMs + 200L, java.util.concurrent.TimeUnit.MILLISECONDS)
            val ok = if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
            if (!ok && shellInjectionPermitted == null) {
                shellInjectionPermitted = false
            } else if (ok) {
                shellInjectionPermitted = true
            }
            ok
        } catch (e: Exception) {
            shellInjectionPermitted = false
            Log.w(TAG, "Eroare execuție shell input swipe: ${e.message}")
            false
        }
    }

    private fun executeShellDirect(command: String): Boolean {
        if (_injectionMethod.value == TouchInjectionMethod.AUTO_HYBRID && shellInjectionPermitted == false) {
            return false
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val finished = process.waitFor(180L, java.util.concurrent.TimeUnit.MILLISECONDS)
            val ok = if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
            if (!ok && shellInjectionPermitted == null) {
                shellInjectionPermitted = false
            } else if (ok) {
                shellInjectionPermitted = true
            }
            ok
        } catch (_: Exception) {
            shellInjectionPermitted = false
            false
        }
    }
}

/**
 * Furnizor intern pentru context global în cazul injectărilor fără referință explicită
 */
object ApplicationContextProvider {
    var appContext: Context? = null
}
