package com.eva1.companion.engines

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

// ══════════════════════════════════════════════════════════════════════════════
// EVA-1 REAL-TIME SCREEN VISION, PIXEL ANALYZER, FLOATING HUD & GAME SERVICE
//
// Permite asistentului EVA-1:
//   1. Să vadă în timp real tot ce se află pe ecranul telefonului:
//      - Atât prin arborele UI (AccessibilityNodeInfo pe fereastra TYPE_APPLICATION)
//      - Cât și prin captură reală de pixeli (takeScreenshot API 30+) pentru jocuri
//        Unity / Unreal / OpenGL / Vulkan / Canvas.
//   2. Să afișeze un Panou Plutitor (Floating AI Game HUD prin TYPE_ACCESSIBILITY_OVERLAY)
//      direct peste orice joc instalat pe telefon, ca să poți porni/opri AI-ul sau
//      schimba modul chiar din timpul jocului!
//   3. Să detecteze atât butoanele de meniu (PLAY, START, JOACĂ, NEW GAME, RETRY),
//      cât și butoanele de control din jocuri (Stânga, Dreapta, Rotire, Drop, Atac)
//      și să execute gesturi reale calibrate pentru orice joc instalat.
// ══════════════════════════════════════════════════════════════════════════════

data class ScreenUiElement(
    val text: String,
    val contentDescription: String,
    val className: String,
    val viewId: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val bounds: String,
    val centerX: Int,
    val centerY: Int,
    val width: Int = 0,
    val height: Int = 0
)

data class LiveScreenSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val activePackage: String = "",
    val activeWindowTitle: String = "",
    val screenWidth: Int = 1080,
    val screenHeight: Int = 2400,
    val isLandscape: Boolean = false,
    val elements: List<ScreenUiElement> = emptyList()
)

enum class GameInputMethod(val label: String) {
    PURE_TOUCH_GESTURE("Touch & Gesturi Directe"),
    ACTION_BUTTONS_TOUCH("Butoane Active UI / On-Screen"),
    LEGACY_DPAD_BUTTONS("D-Pad Clasic Arcade (Fizic/Emulat)")
}

enum class DetectedGameCategory(val label: String) {
    CARD_SOLITAIRE_TABLE("Solitaire & Jocuri de Cărți (Klondike / Spider)"),
    TOUCH_DRAG_DROP_PUZZLE("Puzzle Drag & Drop (Block Blast / Woodoku)"),
    TOUCH_SWIPE_RUNNER("Runner / Swipe 4D (Subway Surfers / 2048)"),
    TOUCH_MATCH_TAP("Puzzle Match / Tap Direct (Candy Crush / Grilă)"),
    ACTION_JOYSTICK_3D("Action 3D / FPS (PUBG / CODM / Free Fire)"),
    SPORTS_TACTICAL("Sport & Fotbal (FC Mobile / eFootball)"),
    RETRO_DPAD_LEGACY("Arcade D-Pad Clasic")
}

data class GameplayUiControls(
    val startOrPlayButton: ScreenUiElement? = null,
    val startButtonLabel: String = "",
    val leftButton: ScreenUiElement? = null,
    val rightButton: ScreenUiElement? = null,
    val rotateOrUpButton: ScreenUiElement? = null,
    val downOrDropButton: ScreenUiElement? = null,
    val primaryActionButton: ScreenUiElement? = null,
    val clickablePlayfieldNodes: List<ScreenUiElement> = emptyList(),
    val interactiveTouchTargets: List<ScreenUiElement> = emptyList(),
    val inputMethod: GameInputMethod = GameInputMethod.PURE_TOUCH_GESTURE
) {
    // CRITIC: DOAR dacă pe ecran chiar există butoane direcționale explicite (Stânga și Dreapta)
    val hasExplicitDirectionalButtons: Boolean
        get() = leftButton != null && rightButton != null

    val hasDirectionalControls: Boolean
        get() = hasExplicitDirectionalButtons
}

enum class DetectedGamePhase {
    LOADING_OR_SPLASH,
    MENU_OR_DIALOG,
    ACTIVE_GAMEPLAY
}

data class GameVisualAnalysis(
    val width: Int = 1080,
    val height: Int = 2400,
    val isLandscape: Boolean = false,
    val phase: DetectedGamePhase = DetectedGamePhase.ACTIVE_GAMEPLAY,
    val frameDiffScore: Float = 0.05f,
    val detectedMenuButton: Pair<Float, Float>? = null,
    val menuButtonLabel: String = "",
    val laneHazards: FloatArray = floatArrayOf(0f, 0f, 0f),
    val laneHasCoin: BooleanArray = booleanArrayOf(false, false, false),
    val recommendedSafeLane: Int = 1,
    val tetrisBestColumnFrac: Float = 0.5f,
    val tetrisPieceColumnFrac: Float = 0.5f,
    val puzzleSwapCoordinates: FloatArray? = null,
    val enemyTargetPoint: Pair<Float, Float>? = null,
    val hasTargetInCrosshair: Boolean = false,
    val playerHpPercent: Float = 100f,
    val enemyHpPercent: Float = 100f,
    val secondaryBarPercent: Float = 100f,
    val isLowHealthCritical: Boolean = false,
    val visionGameState: DetectedVisionGameState = DetectedVisionGameState.ACTIVE_GAMEPLAY_COMBAT,
    val detectedUiButtons: List<DetectedGameUiButton> = emptyList(),
    val healButtonPoint: Pair<Float, Float>? = null,
    val closePopupPoint: Pair<Float, Float>? = null,
    val aiVisionDirective: String = "",
    // Arhitectură Touchscreen 2026: Drag & Drop, Gesturi și Ținte Interactive pe ecran
    val detectedCategory: DetectedGameCategory = DetectedGameCategory.TOUCH_DRAG_DROP_PUZZLE,
    val salientInteractiveTargets: List<Pair<Float, Float>> = emptyList(),
    val trayPieceTargets: List<Pair<Float, Float>> = emptyList(),
    val boardCenterTarget: Pair<Float, Float>? = null,
    val recommendedDragMotion: FloatArray? = null,
    val recommendedSwipeMotion: FloatArray? = null,
    val scene3dAnalysis: Scene3dVisionAnalysis? = null
)

/**
 * Starea ecranului aplicațiilor/jocurilor externe interpretată de AccessibilityService:
 */
enum class ExternalGameScreenType(val displayName: String) {
    MAIN_MENU_LOBBY("Meniu Principal / Lobby"),
    ACTIVE_GAMEPLAY("Sesiune Activă Gameplay (Canvas 3D)"),
    DIALOG_POPUP_ALERT("Dialog / Pop-up Reclamă / Alertă"),
    VICTORY_OR_DEFEAT("Ecran Final / Rezultat / Victorie / Scor"),
    PAUSE_OR_SETTINGS("Pauză / Meniu Configurare"),
    STORE_OR_REWARD("Revendicare Recompense / Magazin"),
    LOADING_OR_CONNECTING("Ecran Încărcare / Conectare Server")
}

data class ExternalGameUiState(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String = "",
    val appName: String = "",
    val screenType: ExternalGameScreenType = ExternalGameScreenType.ACTIVE_GAMEPLAY,
    val screenWidth: Int = 1080,
    val screenHeight: Int = 2400,
    val isLandscape: Boolean = false,
    val totalElementsCount: Int = 0,
    val startOrPlayButton: ScreenUiElement? = null,
    val closePopupButton: ScreenUiElement? = null,
    val returnHomeButton: ScreenUiElement? = null,
    val restartButton: ScreenUiElement? = null,
    val interactiveActionButtons: List<ScreenUiElement> = emptyList(),
    val visibleTexts: List<String> = emptyList(),
    val hasCanvasOnlyDisplay: Boolean = false,
    val decisionContextSummary: String = "Monitorizare inactivă"
)

data class GestureVerificationFeedback(
    val action: String,
    val androidAccepted: Boolean,
    val callbackCompleted: Boolean,
    val freshFrameObserved: Boolean,
    val frameChanged: Boolean?,
    val note: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class EvaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: EvaAccessibilityService? = null
            private set

        private val _latestSnapshot = MutableStateFlow(LiveScreenSnapshot())
        val latestSnapshot: StateFlow<LiveScreenSnapshot> = _latestSnapshot.asStateFlow()

        private val _currentAppUiState = MutableStateFlow(ExternalGameUiState())
        val currentAppUiState: StateFlow<ExternalGameUiState> = _currentAppUiState.asStateFlow()

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _lastForegroundPackage = MutableStateFlow("")
        val lastForegroundPackage: StateFlow<String> = _lastForegroundPackage.asStateFlow()

        private val _isFloatingHudVisible = MutableStateFlow(false)
        val isFloatingHudVisible: StateFlow<Boolean> = _isFloatingHudVisible.asStateFlow()

        // Rezultatul real al încercării WhatsApp, observabil și după revenirea în EVA.
        private val _whatsAppSendStatus = MutableStateFlow("Nicio trimitere în curs.")
        val whatsAppSendStatus: StateFlow<String> = _whatsAppSendStatus.asStateFlow()

        // Callback-uri conectate de OsOrchestratorEngine pentru control din panoul plutitor peste jocuri
        var onOverlayTogglePlayPause: (() -> Unit)? = null
        var onOverlayCycleMode: (() -> Unit)? = null
        var onOverlayStopBot: (() -> Unit)? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gestureFeedbackSequence = java.util.concurrent.atomic.AtomicLong(0L)
    private val _lastGestureFeedback = MutableStateFlow<GestureVerificationFeedback?>(null)
    val lastGestureFeedback: StateFlow<GestureVerificationFeedback?> = _lastGestureFeedback.asStateFlow()

    @Volatile
    private var lastCapturedBitmap: Bitmap? = null

    @Volatile
    private var lastCaptureTimeMs: Long = 0L

    private var previousFrameLumaSignature: IntArray? = null
    private var lastComputedDiffScore: Float = 0.06f

    // Floating HUD Overlay (TYPE_ACCESSIBILITY_OVERLAY)
    private var overlayView: View? = null
    private var overlayStatusText: TextView? = null
    private var overlaySubText: TextView? = null
    private var overlayVisionBtn: TextView? = null
    private var overlayPlayPauseBtn: TextView? = null
    private var overlayModeBtn: TextView? = null

    private var lastContentChangedSnapshotMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        refreshScreenSnapshot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()
        if (isValidAppPackage(pkg)) {
            _lastForegroundPackage.value = pkg
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                lastContentChangedSnapshotMs = System.currentTimeMillis()
                refreshScreenSnapshot(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = System.currentTimeMillis()
                if (now - lastContentChangedSnapshotMs >= 320L) {
                    lastContentChangedSnapshotMs = now
                    refreshScreenSnapshot(pkg)
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        cancelConfirmedWhatsAppSend()
        hideFloatingGameHud()
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isServiceConnected.value = false
    }

    private fun isValidAppPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lower = pkg.lowercase()
        return !lower.contains("systemui") &&
            !lower.contains("inputmethod") &&
            !lower.contains("permissioncontroller") &&
            !lower.contains("volume") &&
            !lower.contains("nexuslauncher") &&
            !lower.contains("MIUILauncher", ignoreCase = true)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PANOU PLUTITOR PESTE JOCURI (TYPE_ACCESSIBILITY_OVERLAY)
    // Nu necesită permisiuni suplimentare și nu blochează atingerile din joc!
    // ══════════════════════════════════════════════════════════════════════════

    fun showOrUpdateFloatingGameHud(
        isRunning: Boolean,
        gameTitle: String,
        modeName: String,
        statusLine: String
    ) {
        mainHandler.post {
            try {
                if (overlayView == null) {
                    createFloatingHudView()
                }
                val isMpActive = MediaProjectionVisionEngine.isMediaProjectionActive.value
                val mpFps = MediaProjectionVisionEngine.streamFps.value
                overlayStatusText?.text = if (isRunning) {
                    "🤖 EVA-1 • ${gameTitle.take(15)} ($modeName)"
                } else {
                    "⏸️ EVA-1 Pauză • Deschide orice joc și apasă ▶️"
                }
                overlaySubText?.text = statusLine.take(68)
                overlayVisionBtn?.text = if (isMpActive && mpFps > 0) "🎥 ${mpFps}FPS" else if (isMpActive) "🎥 Activă" else "🎥 Captură"
                overlayVisionBtn?.setTextColor(if (isMpActive) Color.BLACK else Color.WHITE)
                overlayVisionBtn?.background = buildPillDrawable(
                    bgColor = if (isMpActive) Color.parseColor("#00E676") else Color.parseColor("#0288D1"),
                    strokeColor = Color.TRANSPARENT
                )
                overlayPlayPauseBtn?.text = if (isRunning) "⏸ Pauză" else "▶️ Joacă"
                overlayPlayPauseBtn?.background = buildPillDrawable(
                    bgColor = if (isRunning) Color.parseColor("#FF9100") else Color.parseColor("#00E676"),
                    strokeColor = Color.TRANSPARENT
                )
                overlayModeBtn?.text = "⚙ ${modeName.take(8)}"
                _isFloatingHudVisible.value = true
            } catch (_: Exception) {
            }
        }
    }

    fun hideFloatingGameHud() {
        mainHandler.post {
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                overlayView?.let { wm.removeViewImmediate(it) }
            } catch (_: Exception) {
            } finally {
                overlayView = null
                overlayStatusText = null
                overlaySubText = null
                overlayVisionBtn = null
                overlayPlayPauseBtn = null
                overlayModeBtn = null
                _isFloatingHudVisible.value = false
            }
        }
    }

    private fun createFloatingHudView() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = buildPillDrawable(
                bgColor = Color.parseColor("#DD081020"),
                strokeColor = Color.parseColor("#00E5FF")
            )
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(this).apply {
            text = "🤖 EVA-1 Autopilot Jocuri"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }

        val subTv = TextView(this).apply {
            text = "Pregătit să joace pe ecranul activ"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            maxLines = 1
        }

        textCol.addView(titleTv)
        textCol.addView(subTv)

        val visionTv = TextView(this).apply {
            val isMpActive = MediaProjectionVisionEngine.isMediaProjectionActive.value
            text = if (isMpActive) "🎥 60FPS" else "🎥 Captură"
            setTextColor(if (isMpActive) Color.BLACK else Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(7), dp(5), dp(7), dp(5))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(5) }
            layoutParams = lp
            background = buildPillDrawable(
                if (isMpActive) Color.parseColor("#00E676") else Color.parseColor("#0288D1"),
                Color.TRANSPARENT
            )
            setOnClickListener {
                if (!MediaProjectionVisionEngine.isMediaProjectionActive.value) {
                    val fgPkg = _lastForegroundPackage.value.takeIf { it != packageName }
                    MediaProjectionRequester.requestMediaProjection(fgPkg)
                }
            }
        }

        val playPauseTv = TextView(this).apply {
            text = "▶️ Joacă"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = buildPillDrawable(Color.parseColor("#00E676"), Color.TRANSPARENT)
            setOnClickListener {
                onOverlayTogglePlayPause?.invoke()
            }
        }

        val modeTv = TextView(this).apply {
            text = "⚙ Mod"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setPadding(dp(7), dp(5), dp(7), dp(5))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(5) }
            layoutParams = lp
            background = buildPillDrawable(Color.parseColor("#1E293B"), Color.parseColor("#7C4DFF"))
            setOnClickListener {
                onOverlayCycleMode?.invoke()
            }
        }

        val closeTv = TextView(this).apply {
            text = "✖"
            setTextColor(Color.parseColor("#FF5252"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(3) }
            layoutParams = lp
            setOnClickListener {
                onOverlayStopBot?.invoke()
                hideFloatingGameHud()
            }
        }

        rootLayout.addView(textCol)
        rootLayout.addView(visionTv)
        rootLayout.addView(playPauseTv)
        rootLayout.addView(modeTv)
        rootLayout.addView(closeTv)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(4)
        }

        wm.addView(rootLayout, params)
        overlayView = rootLayout
        overlayStatusText = titleTv
        overlaySubText = subTv
        overlayVisionBtn = visionTv
        overlayPlayPauseBtn = playPauseTv
        overlayModeBtn = modeTv
        _isFloatingHudVisible.value = true
    }

    private fun buildPillDrawable(bgColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(bgColor)
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    /**
     * Returnează dimensiunile reale ale ecranului în orientarea curentă (Portrait sau Landscape).
     */
    fun getLiveDisplaySize(): Pair<Int, Int> {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                val w = bounds.width().coerceAtLeast(480)
                val h = bounds.height().coerceAtLeast(480)
                w to h
            } else {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                dm.widthPixels.coerceAtLeast(480) to dm.heightPixels.coerceAtLeast(480)
            }
        } catch (_: Exception) {
            val dm = resources.displayMetrics
            dm.widthPixels.coerceAtLeast(720) to dm.heightPixels.coerceAtLeast(1280)
        }
    }

    fun refreshScreenSnapshot(fallbackPkg: String = ""): LiveScreenSnapshot {
        val (screenW, screenH) = getLiveDisplaySize()
        val isLand = screenW > screenH

        val collected = mutableListOf<ScreenUiElement>()
        var primaryAppPkg = ""
        val myPkg = packageName

        // 1. Inspectăm mai întâi fereastra activă (rootInActiveWindow) care reprezintă aplicația/jocul din prim-plan
        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null) {
                val rPkg = activeRoot.packageName?.toString().orEmpty()
                if (isValidAppPackage(rPkg)) {
                    primaryAppPkg = rPkg
                }
                traverseNode(activeRoot, collected, 0)
            }
        } catch (_: Exception) {}

        // 2. Inspectăm ferestrele interactive de tip TYPE_APPLICATION (ignorând propriul overlay EVA-1)
        try {
            val allWindows = windows
            if (!allWindows.isNullOrEmpty()) {
                for (win in allWindows) {
                    if (win.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                    val root = try { win.root } catch (_: Exception) { null } ?: continue
                    val rPkg = root.packageName?.toString().orEmpty()
                    if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION && isValidAppPackage(rPkg)) {
                        // Dacă fereastra activă este un joc extern, îi dăm prioritate maximă
                        if (primaryAppPkg.isBlank() || (primaryAppPkg == myPkg && rPkg != myPkg)) {
                            primaryAppPkg = rPkg
                        }
                    }
                    if (rPkg != myPkg || primaryAppPkg == myPkg) {
                        traverseNode(root, collected, 0)
                    }
                }
            }
        } catch (_: Exception) {}

        val detectedPkg = when {
            primaryAppPkg.isNotBlank() -> primaryAppPkg
            isValidAppPackage(_lastForegroundPackage.value) -> _lastForegroundPackage.value
            fallbackPkg.isNotBlank() -> fallbackPkg
            else -> ""
        }

        if (isValidAppPackage(detectedPkg)) {
            _lastForegroundPackage.value = detectedPkg
        }

        val snap = LiveScreenSnapshot(
            timestamp = System.currentTimeMillis(),
            activePackage = detectedPkg,
            activeWindowTitle = collected.firstOrNull { it.text.isNotBlank() }?.text ?: detectedPkg,
            screenWidth = screenW,
            screenHeight = screenH,
            isLandscape = isLand,
            elements = collected.distinctBy { "${it.text}|${it.contentDescription}|${it.bounds}" }.take(120)
        )
        _latestSnapshot.value = snap
        val interpreted = interpretExternalUiState(snap, detectedPkg)
        _currentAppUiState.value = interpreted
        return snap
    }

    /**
     * Interpretează starea ecranului extern pentru decizii automate AI (Lobby, Gameplay activ, Dialog Pop-up, etc.)
     */
    fun interpretExternalUiState(snap: LiveScreenSnapshot, pkg: String): ExternalGameUiState {
        val elements = snap.elements
        val screenW = snap.screenWidth
        val screenH = snap.screenHeight

        // Căutare butoane explicite de închidere pop-up (X, Close, Dismiss, Închide) — fără a confunda pictogramele de Setări/Pauză din joc!
        val closeBtn = elements.firstOrNull { el ->
            val txt = el.text.lowercase().trim()
            val desc = el.contentDescription.lowercase().trim()
            val id = el.viewId.lowercase()
            el.isClickable && (
                txt == "x" || txt == "✕" || txt == "×" ||
                    desc == "close" || desc == "închide" || desc == "dismiss" ||
                    id.endsWith("btn_close") || id.endsWith("close_button") || id.endsWith("ad_close") || id.endsWith("btn_x")
                )
        }

        // Căutare butoane de start / play / luptă / battle
        val startBtn = elements.firstOrNull { el ->
            val txt = el.text.lowercase().trim()
            val desc = el.contentDescription.lowercase().trim()
            val id = el.viewId.lowercase()
            el.isClickable && (
                txt.contains("play") || txt.contains("start") || txt.contains("battle") || txt.contains("luptă") || txt.contains("atac") ||
                txt.contains("attack") || txt.contains("joacă") || txt.contains("ready") || txt.contains("gata") ||
                desc.contains("play") || desc.contains("start") || id.contains("play") || id.contains("start") || id.contains("battle")
            )
        }

        // Căutare butoane de return home / claim / collect / continue
        val returnHomeBtn = elements.firstOrNull { el ->
            val txt = el.text.lowercase().trim()
            val desc = el.contentDescription.lowercase().trim()
            val id = el.viewId.lowercase()
            el.isClickable && (
                txt.contains("return") || txt.contains("home") || txt.contains("acasă") || txt.contains("claim") ||
                txt.contains("colectează") || txt.contains("collect") || txt.contains("continue") || txt.contains("continuă") ||
                txt.contains("next") || txt.contains("următor") || desc.contains("home") || id.contains("return") || id.contains("home")
            )
        }

        // Căutare restart / retry
        val restartBtn = elements.firstOrNull { el ->
            val txt = el.text.lowercase().trim()
            val desc = el.contentDescription.lowercase().trim()
            val id = el.viewId.lowercase()
            el.isClickable && (
                txt.contains("retry") || txt.contains("restart") || txt.contains("reîncearcă") || txt.contains("again") ||
                desc.contains("retry") || id.contains("retry") || id.contains("restart")
            )
        }

        val allClickable = elements.filter { it.isClickable }
        val allTexts = elements.mapNotNull { if (it.text.isNotBlank()) it.text else null }
        val allTextsLower = allTexts.joinToString(" ").lowercase()

        val screenType = when {
            closeBtn != null && (allTextsLower.contains("promo") || allTextsLower.contains("sale") || allTextsLower.contains("ofer") || allTextsLower.contains("recompensa") || allTextsLower.contains("claim") || elements.size < 15) ->
                ExternalGameScreenType.DIALOG_POPUP_ALERT
            restartBtn != null || allTextsLower.contains("game over") || allTextsLower.contains("victorie") || allTextsLower.contains("victory") || allTextsLower.contains("defeat") || allTextsLower.contains("înfrângere") ->
                ExternalGameScreenType.VICTORY_OR_DEFEAT
            startBtn != null || allTextsLower.contains("lobby") || allTextsLower.contains("select") || allTextsLower.contains("mod joc") || allTextsLower.contains("rank") ->
                ExternalGameScreenType.MAIN_MENU_LOBBY
            allTextsLower.contains("pause") || allTextsLower.contains("pauză") || allTextsLower.contains("resume") || allTextsLower.contains("settings") ->
                ExternalGameScreenType.PAUSE_OR_SETTINGS
            allTextsLower.contains("loading") || allTextsLower.contains("se încarcă") || allTextsLower.contains("connecting") ->
                ExternalGameScreenType.LOADING_OR_CONNECTING
            else ->
                ExternalGameScreenType.ACTIVE_GAMEPLAY
        }

        val isCanvasOnly = allClickable.isEmpty() && elements.size <= 2

        val summary = buildString {
            append("Pachet: $pkg | ")
            append("Stare: ${screenType.displayName} | ")
            if (closeBtn != null) append("Pop-up activ (X la ${closeBtn.centerX}, ${closeBtn.centerY}) | ")
            if (startBtn != null) append("Buton Start (${startBtn.text.ifBlank { "Play" }}) | ")
            if (returnHomeBtn != null) append("Buton Continuare (${returnHomeBtn.text}) | ")
            if (isCanvasOnly) append("Randare Canvas 3D (Reflexe Viziune Pixel)")
            else append("Noduri UI: ${allClickable.size}")
        }

        return ExternalGameUiState(
            timestamp = snap.timestamp,
            packageName = pkg,
            appName = snap.activeWindowTitle,
            screenType = screenType,
            screenWidth = screenW,
            screenHeight = screenH,
            isLandscape = snap.isLandscape,
            totalElementsCount = elements.size,
            startOrPlayButton = startBtn,
            closePopupButton = closeBtn,
            returnHomeButton = returnHomeBtn,
            restartButton = restartBtn,
            interactiveActionButtons = allClickable,
            visibleTexts = allTexts.take(15),
            hasCanvasOnlyDisplay = isCanvasOnly,
            decisionContextSummary = summary
        )
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        out: MutableList<ScreenUiElement>,
        depth: Int
    ) {
        if (node == null || depth > 18 || out.size >= 120) return
        val txt = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "View"
        val id = node.viewIdResourceName?.substringAfterLast('/') ?: ""

        val rect = Rect()
        node.getBoundsInScreen(rect)

        if ((txt.isNotBlank() || desc.isNotBlank() || id.isNotBlank() || node.isClickable || node.isEditable) &&
            rect.width() > 8 && rect.height() > 8
        ) {
            out.add(
                ScreenUiElement(
                    text = txt,
                    contentDescription = desc,
                    className = cls,
                    viewId = id,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    bounds = "[${rect.left},${rect.top} - ${rect.right},${rect.bottom}]",
                    centerX = rect.centerX(),
                    centerY = rect.centerY(),
                    width = rect.width(),
                    height = rect.height()
                )
            )
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                traverseNode(child, out, depth + 1)
            }
        }
    }

    /**
     * Capturează în timp real imaginea de pe ecran (inclusiv jocuri Unity / OpenGL / Vulkan / Canvas):
     *   - Returnează Pair(Bitmap?, Boolean) unde Boolean indică dacă este un cadru NOU capturat acum
     *     (evitând bug-ul în care un cadru cache-uit comparat cu el însuși dădea diff=0.0f).
     */
    suspend fun captureLiveGameBitmapWithFreshFlag(): Pair<Bitmap?, Boolean> {
        val projectedBmp = MediaProjectionVisionEngine.getLatestProjectedBitmap()
        if (projectedBmp != null && !projectedBmp.isRecycled) {
            val isNew = projectedBmp !== lastCapturedBitmap
            lastCapturedBitmap = projectedBmp
            lastCaptureTimeMs = System.currentTimeMillis()
            return projectedBmp to isNew
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return lastCapturedBitmap to false
        val now = System.currentTimeMillis()
        // Respectăm rata de captură Android (~550ms între screenshot-uri)
        if (now - lastCaptureTimeMs < 550L && lastCapturedBitmap != null) {
            return lastCapturedBitmap to false
        }

        // Ascundem temporar panoul plutitor ca să nu apară în analiza de pixeli
        val hadOverlay = overlayView?.visibility == View.VISIBLE
        if (hadOverlay) {
            mainHandler.post { overlayView?.visibility = View.INVISIBLE }
            delay(35)
        }

        val deferred = CompletableDeferred<Pair<Bitmap?, Boolean>>()
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        if (hadOverlay) {
                            mainHandler.post { overlayView?.visibility = View.VISIBLE }
                        }
                        val hwBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val swBitmap = try {
                            val hwBmp = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                            val copy = hwBmp?.copy(Bitmap.Config.ARGB_8888, false)
                            hwBmp?.recycle()
                            copy
                        } catch (_: Exception) {
                            null
                        } finally {
                            try { hwBuffer.close() } catch (_: Exception) {}
                        }
                        if (swBitmap != null) {
                            lastCapturedBitmap = swBitmap
                            lastCaptureTimeMs = System.currentTimeMillis()
                            deferred.complete(swBitmap to true)
                        } else {
                            deferred.complete(lastCapturedBitmap to false)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (hadOverlay) {
                            mainHandler.post { overlayView?.visibility = View.VISIBLE }
                        }
                        deferred.complete(lastCapturedBitmap to false)
                    }
                }
            )
        } catch (_: Exception) {
            if (hadOverlay) {
                mainHandler.post { overlayView?.visibility = View.VISIBLE }
            }
            deferred.complete(lastCapturedBitmap to false)
        }

        val result = withTimeoutOrNull(850L) { deferred.await() } ?: (lastCapturedBitmap to false)
        // A callback that never arrives must not leave the floating HUD hidden.
        if (hadOverlay) mainHandler.post { overlayView?.visibility = View.VISIBLE }
        return result
    }

    suspend fun captureLiveGameBitmap(): Bitmap? {
        return captureLiveGameBitmapWithFreshFlag().first
    }

    /**
     * Caută în arborele AccessibilityNodeInfo orice buton de meniu de joc / pornire / continuare / închidere pop-up.
     */
    fun findGameMenuNodeButton(snap: LiveScreenSnapshot = refreshScreenSnapshot()): Pair<ScreenUiElement, String>? {
        val playKeywords = listOf(
            "play", "start", "joacă", "joaca", "battle", "luptă", "lupta", "fight",
            "continue", "continuă", "continua", "tap to play", "tap to start",
            "next", "următorul", "retry", "reîncearcă", "reincearca", "play again", "resume",
            "claim", "collect", "colectează", "ok", "accept", "confirm",
            "skip", "close", "închide", "inchide", "got it", "let's go"
        )
        val forbiddenToolbarWords = listOf(
            "nuova partita", "new game", "joc nou", "giornaliero", "daily",
            "indietro", "undo", "magia", "magic", "indizio", "hint",
            "settings", "opzioni", "options", "pauză", "pause", "pausa"
        )

        // Ignorăm cuvintele care sunt doar titluri statice sau butoane de bară care resetează meciul
        for (kw in playKeywords) {
            val match = snap.elements.firstOrNull { el ->
                val t = el.text.lowercase().trim()
                val d = el.contentDescription.lowercase().trim()
                val v = el.viewId.lowercase().trim()
                val combined = "$t $d $v"
                val isForbidden = forbiddenToolbarWords.any { fw -> combined.contains(fw) }
                val isBtnLike = el.isClickable || el.className.contains("Button", ignoreCase = true) ||
                    t == kw || d == kw
                val matchesKeyword = (t == kw || t.startsWith("$kw ") || t.endsWith(" $kw") ||
                    d == kw || v.endsWith("_$kw"))
                !isForbidden && isBtnLike && matchesKeyword &&
                    el.centerX in 15..(snap.screenWidth - 15) &&
                    el.centerY in 55..(snap.screenHeight - 25)
            }
            if (match != null) {
                val label = match.text.ifBlank { match.contentDescription.ifBlank { match.viewId.ifBlank { kw.uppercase() } } }
                return match to label
            }
        }
        return null
    }

    /**
     * Detectează controalele reale de Gameplay din arborele UI (pentru jocuri precum Tetris,
     * Block Blast, 2048, Arcade, Emulatoare, Puzzle-uri sau jocuri Compose/Android View care au
     * butoane pe ecran: Stânga, Dreapta, Rotire, Jos/Drop, Start/Retry, etc.).
     */
    fun findGameplayControlNodes(snap: LiveScreenSnapshot = _latestSnapshot.value): GameplayUiControls {
        val w = snap.screenWidth.coerceAtLeast(480)
        val h = snap.screenHeight.coerceAtLeast(480)

        var startBtn: ScreenUiElement? = null
        var startLbl = ""
        findGameMenuNodeButton(snap)?.let {
            startBtn = it.first
            startLbl = it.second
        }

        fun matchesAny(el: ScreenUiElement, vararg tokens: String): Boolean {
            val combined = "${el.text} ${el.contentDescription} ${el.viewId}".lowercase()
            return tokens.any { tk ->
                combined == tk || combined.contains(tk)
            }
        }

        val candidates = snap.elements.filter {
            it.centerY > h * 0.12 && (it.isClickable || it.text.isNotBlank() || it.contentDescription.isNotBlank() || it.viewId.isNotBlank())
        }

        val leftBtn = candidates.firstOrNull {
            matchesAny(it, "btn_left", "dpad_left", "move_left", "arrow_left", "nav_left") ||
                it.text.trim() in listOf("◀", "←", "◄", "⬅") ||
                it.contentDescription.trim().lowercase() in listOf("left", "stânga", "stanga")
        }
        val rightBtn = candidates.firstOrNull {
            matchesAny(it, "btn_right", "dpad_right", "move_right", "arrow_right", "nav_right") ||
                it.text.trim() in listOf("▶", "→", "►", "➡") ||
                it.contentDescription.trim().lowercase() in listOf("right", "dreapta")
        }
        val rotateBtn = candidates.firstOrNull {
            matchesAny(it, "btn_rotate", "dpad_up", "rotate", "rotește", "roteste", "↻", "↺", "🔄", "btn_jump")
        }
        val downBtn = candidates.firstOrNull {
            matchesAny(it, "btn_down", "dpad_down", "hard_drop", "soft_drop", "▼▼")
        }
        val actionBtn = candidates.firstOrNull {
            matchesAny(it, "fire", "shoot", "attack", "foc", "atac", "tragere", "hit", "jump", "salt", "boost", "action")
        }

        // Toate elementele interactive reale de pe ecran (touch targets pentru jocuri touchscreen)
        val allTouchTargets = snap.elements.filter {
            it.isClickable && it != startBtn && it.width > 20 && it.height > 20
        }

        val playfieldClickables = snap.elements.filter {
            it.isClickable &&
                it.centerY in (h * 0.16).toInt()..(h * 0.72).toInt() &&
                it != startBtn && it != leftBtn && it != rightBtn && it != rotateBtn && it != downBtn
        }

        val resolvedInputMethod = when {
            leftBtn != null && rightBtn != null -> GameInputMethod.LEGACY_DPAD_BUTTONS
            actionBtn != null || allTouchTargets.size in 1..5 -> GameInputMethod.ACTION_BUTTONS_TOUCH
            else -> GameInputMethod.PURE_TOUCH_GESTURE
        }

        return GameplayUiControls(
            startOrPlayButton = startBtn,
            startButtonLabel = startLbl,
            leftButton = leftBtn,
            rightButton = rightBtn,
            rotateOrUpButton = rotateBtn,
            downOrDropButton = downBtn,
            primaryActionButton = actionBtn,
            clickablePlayfieldNodes = playfieldClickables,
            interactiveTouchTargets = allTouchTargets,
            inputMethod = resolvedInputMethod
        )
    }

    /**
     * Analizează vizual (Pixel Computer Vision) captura de ecran a jocului pentru:
     *  1. Detectarea meniului jocului și a butoanelor vizuale PLAY / START / BATTLE / CONTINUE / CLOSE („X”).
     *  2. Detectarea obstacolelor și a monedelor pe cele 3 culoare (Runner / Racing).
     *  3. Analiza tablei de Tetris / Block Puzzle (coloana optimă unde stiva este cea mai joasă vs poziția piesei active).
     *  4. Detectarea mutărilor valide pe grilă (Puzzle / Match-3).
     *  5. Detectarea țintelor / inamicilor pe ecran (RPG / Action / Shooter).
     */
    private fun safeGetPixel(bmp: Bitmap, x: Int, y: Int): Int {
        if (bmp.isRecycled) return Color.BLACK
        return try {
            val sx = x.coerceIn(0, (bmp.width - 1).coerceAtLeast(0))
            val sy = y.coerceIn(0, (bmp.height - 1).coerceAtLeast(0))
            bmp.getPixel(sx, sy)
        } catch (_: Exception) {
            Color.BLACK
        }
    }

    suspend fun analyzeLiveGameScreen(
        activeGameTitle: String = "",
        activeMode: GameBotMode = GameBotMode.AUTO_ADAPTIVE
    ): GameVisualAnalysis {
        val (dispW, dispH) = getLiveDisplaySize()
        val isLand = dispW > dispH
        val (bmp, isFreshCapture) = captureLiveGameBitmapWithFreshFlag()

        if (bmp == null || bmp.isRecycled || bmp.width < 32 || bmp.height < 32) {
            return GameVisualAnalysis(
                width = dispW,
                height = dispH,
                isLandscape = isLand,
                phase = DetectedGamePhase.ACTIVE_GAMEPLAY,
                frameDiffScore = 0.05f
            )
        }

        val bw = bmp.width
        val bh = bmp.height

        // 1. Calculăm semnătura de luminanță 12x12 DOAR când avem un cadru nou capturat
        val diffSum: Float
        if (isFreshCapture || previousFrameLumaSignature == null) {
            val sig = IntArray(144)
            var idx = 0
            for (gy in 0 until 12) {
                val py = ((gy + 0.5f) / 12f * bh).toInt().coerceIn(0, bh - 1)
                for (gx in 0 until 12) {
                    val px = ((gx + 0.5f) / 12f * bw).toInt().coerceIn(0, bw - 1)
                    val c = safeGetPixel(bmp, px, py)
                    val luma = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
                    sig[idx++] = luma
                }
            }

            val prevSig = previousFrameLumaSignature
            if (prevSig != null && prevSig.size == sig.size) {
                var sum = 0f
                for (i in sig.indices) {
                    sum += abs(sig[i] - prevSig[i]) / 255f
                }
                diffSum = sum / sig.size.toFloat()
            } else {
                diffSum = 0.06f
            }
            previousFrameLumaSignature = sig
            lastComputedDiffScore = diffSum
        } else {
            diffSum = lastComputedDiffScore
        }

        // 2. Scanăm regiunile tipice de butoane de Meniu Joc (PLAY / START / BATTLE / CONTINUE)
        data class CandidateZone(
            val name: String,
            val xMinFrac: Float,
            val xMaxFrac: Float,
            val yMinFrac: Float,
            val yMaxFrac: Float,
            val priorityWeight: Float
        )

        val candidateZones = if (isLand) {
            listOf(
                CandidateZone("Buton START / BATTLE (Dreapta-Jos - CODM/Brawl/PUBG)", 0.68f, 0.96f, 0.68f, 0.94f, 1.28f),
                CandidateZone("Buton START Lobby (Stânga-Sus - PUBG Mobile)", 0.04f, 0.26f, 0.06f, 0.24f, 1.24f),
                CandidateZone("Buton PLAY / START (Jos-Centru)", 0.32f, 0.68f, 0.66f, 0.92f, 1.25f),
                CandidateZone("Buton MULTIPLAYER / BR (Dreapta-Centru - Call of Duty)", 0.72f, 0.95f, 0.36f, 0.67f, 1.18f),
                CandidateZone("Buton MENIU CENTRAL / CONTINUE", 0.32f, 0.68f, 0.42f, 0.65f, 1.15f),
                CandidateZone("Buton STÂNGA-JOS / START MECI", 0.06f, 0.30f, 0.72f, 0.94f, 1.12f)
            )
        } else {
            listOf(
                CandidateZone("Buton PLAY / JOACĂ (Jos-Centru)", 0.22f, 0.78f, 0.68f, 0.89f, 1.30f),
                CandidateZone("Buton START / CONTINUE (Centru)", 0.22f, 0.78f, 0.45f, 0.66f, 1.20f),
                CandidateZone("Buton BATTLE / ACTION (Jos-Dreapta)", 0.58f, 0.93f, 0.72f, 0.91f, 1.10f)
            )
        }

        var bestZoneScore = 0f
        var bestButtonPoint: Pair<Float, Float>? = null
        var bestButtonLabel = ""

        val hsv = FloatArray(3)
        for (zone in candidateZones) {
            val x0 = (zone.xMinFrac * bw).toInt().coerceIn(0, bw - 2)
            val x1 = (zone.xMaxFrac * bw).toInt().coerceIn(x0 + 1, bw - 1)
            val y0 = (zone.yMinFrac * bh).toInt().coerceIn(0, bh - 2)
            val y1 = (zone.yMaxFrac * bh).toInt().coerceIn(y0 + 1, bh - 1)

            val stepX = ((x1 - x0) / 14).coerceAtLeast(2)
            val stepY = ((y1 - y0) / 8).coerceAtLeast(2)

            var vibrantCount = 0
            var edgeCount = 0
            var totalSamples = 0
            var weightedX = 0f
            var weightedY = 0f
            var weightTotal = 0f

            var py = y0
            while (py <= y1) {
                var px = x0
                var prevLuma = -1
                while (px <= x1) {
                    val c = safeGetPixel(bmp, px, py)
                    val r = Color.red(c)
                    val g = Color.green(c)
                    val b = Color.blue(c)
                    Color.colorToHSV(c, hsv)
                    val sat = hsv[1]
                    val v = hsv[2]
                    val luma = (r * 30 + g * 59 + b * 11) / 100

                    totalSamples++
                    val isVibrantButtonPixel = (sat > 0.42f && v > 0.48f) || (v > 0.88f && sat < 0.18f)
                    val isTextEdge = prevLuma >= 0 && abs(luma - prevLuma) > 48

                    if (isVibrantButtonPixel) {
                        vibrantCount++
                        val w = sat + v
                        weightedX += px * w
                        weightedY += py * w
                        weightTotal += w
                    }
                    if (isTextEdge) {
                        edgeCount++
                    }
                    prevLuma = luma
                    px += stepX
                }
                py += stepY
            }

            if (totalSamples > 0) {
                val vibrantRatio = vibrantCount.toFloat() / totalSamples
                val edgeRatio = edgeCount.toFloat() / totalSamples
                val score = (vibrantRatio * 0.65f + edgeRatio * 0.85f) * zone.priorityWeight
                if (vibrantRatio > 0.25f && edgeRatio > 0.10f && score > bestZoneScore) {
                    bestZoneScore = score
                    val cxBmp = if (weightTotal > 0f) weightedX / weightTotal else (x0 + x1) / 2f
                    val cyBmp = if (weightTotal > 0f) weightedY / weightTotal else (y0 + y1) / 2f
                    val screenX = (cxBmp / bw.toFloat()) * dispW
                    val screenY = (cyBmp / bh.toFloat()) * dispH
                    bestButtonPoint = screenX to screenY
                    bestButtonLabel = zone.name
                }
            }
        }

        // 3. Analizăm cele 3 culoare din zona de gameplay (pentru Runner / Arcade / Racing)
        val laneHazards = FloatArray(3)
        val laneCoins = BooleanArray(3)
        val scanY0 = (bh * 0.34f).toInt().coerceIn(0, bh - 2)
        val scanY1 = (bh * 0.68f).toInt().coerceIn(scanY0 + 1, bh - 1)

        for (lane in 0..2) {
            val lx0 = ((0.12f + lane * 0.26f) * bw).toInt().coerceIn(0, bw - 2)
            val lx1 = ((0.12f + (lane + 1) * 0.26f) * bw).toInt().coerceIn(lx0 + 1, bw - 1)
            val sx = ((lx1 - lx0) / 8).coerceAtLeast(2)
            val sy = ((scanY1 - scanY0) / 10).coerceAtLeast(2)

            var edgeHits = 0
            var goldHits = 0
            var samples = 0
            var py = scanY0
            while (py <= scanY1) {
                var px = lx0
                var prevL = -1
                while (px <= lx1) {
                    val c = safeGetPixel(bmp, px, py)
                    val r = Color.red(c)
                    val g = Color.green(c)
                    val b = Color.blue(c)
                    val l = (r * 30 + g * 59 + b * 11) / 100
                    samples++
                    if (prevL >= 0 && abs(l - prevL) > 42) edgeHits++
                    if (r > 205 && g in 155..245 && b < 95) goldHits++
                    prevL = l
                    px += sx
                }
                py += sy
            }
            laneHazards[lane] = if (samples > 0) edgeHits.toFloat() / samples else 0f
            laneCoins[lane] = goldHits >= 3
        }

        val recommendedLane = (0..2).minByOrNull { lane ->
            laneHazards[lane] - (if (laneCoins[lane]) 0.12f else 0f)
        } ?: 1

        // 3.5. Analizor inteligent pentru Tetris & Block Puzzle (10 coloane pe tabla de joc):
        //      - Detectează unde se află piesa activă sus (y: 16%..38%)
        //      - Detectează care coloană de jos (y: 42%..76%) este cea mai liberă (are cel mai mult spațiu gol)
        var tetrisPieceCol = 4
        var maxTopBrightness = -1f
        val colStackHeights = FloatArray(10)
        for (col in 0 until 10) {
            val cxFrac = 0.12f + (col + 0.5f) * (0.76f / 10f)
            val px = (cxFrac * bw).toInt().coerceIn(0, bw - 1)

            // Căutăm piesa care cade în jumătatea superioară a tablei
            var topVibrantScore = 0f
            for (ry in 0 until 6) {
                val py = ((0.16f + ry * 0.035f) * bh).toInt().coerceIn(0, bh - 1)
                val c = safeGetPixel(bmp, px, py)
                Color.colorToHSV(c, hsv)
                if (hsv[1] > 0.35f && hsv[2] > 0.40f) {
                    topVibrantScore += hsv[1] + hsv[2]
                }
            }
            if (topVibrantScore > maxTopBrightness) {
                maxTopBrightness = topVibrantScore
                tetrisPieceCol = col
            }

            // Măsurăm înălțimea stivei de blocuri în jumătatea inferioară a tablei
            var firstFilledRow = 12
            for (ry in 0 until 12) {
                val py = ((0.40f + ry * 0.03f) * bh).toInt().coerceIn(0, bh - 1)
                val c = safeGetPixel(bmp, px, py)
                Color.colorToHSV(c, hsv)
                if (hsv[1] > 0.35f && hsv[2] > 0.38f) {
                    firstFilledRow = ry
                    break
                }
            }
            // Cu cât firstFilledRow este mai mare, cu atât coloana este mai joasă (mai sigură)
            colStackHeights[col] = (12 - firstFilledRow).toFloat()
        }
        val bestTetrisCol = (0 until 10).minByOrNull { colStackHeights[it] } ?: 4
        val tetrisBestColFrac = 0.12f + (bestTetrisCol + 0.5f) * (0.76f / 10f)
        val tetrisPieceColFrac = 0.12f + (tetrisPieceCol + 0.5f) * (0.76f / 10f)

        // 4. Analizăm o grilă 6x6 în centru pentru jocuri Puzzle / Match-3 (ex: Candy Crush)
        val gridColors = Array(6) { IntArray(6) }
        val gx0 = dispW * 0.14f
        val gxStep = (dispW * 0.72f) / 5f
        val gy0 = dispH * 0.28f
        val gyStep = (dispH * 0.44f) / 5f

        for (r in 0 until 6) {
            for (col in 0 until 6) {
                val bx = (((gx0 + col * gxStep) / dispW) * bw).toInt().coerceIn(0, bw - 1)
                val by = (((gy0 + r * gyStep) / dispH) * bh).toInt().coerceIn(0, bh - 1)
                val p = safeGetPixel(bmp, bx, by)
                val red = Color.red(p)
                val green = Color.green(p)
                val blue = Color.blue(p)
                gridColors[r][col] = when {
                    red > 180 && green < 100 && blue < 100 -> 1 // Roșu
                    red > 190 && green > 160 && blue < 90 -> 2  // Galben/Auriu
                    green > 160 && red < 120 -> 3               // Verde
                    blue > 170 && red < 120 -> 4                // Albastru
                    red > 160 && blue > 160 -> 5                // Mov
                    else -> 6                                   // Portocaliu/Special
                }
            }
        }

        var puzzleSwap: FloatArray? = null
        outer@ for (r in 0 until 5) {
            for (c in 0 until 5) {
                if (c + 2 < 6 && gridColors[r][c] == gridColors[r][c + 1]) {
                    val targetColor = gridColors[r][c]
                    if (r + 1 < 6 && gridColors[r + 1][c + 2] == targetColor) {
                        puzzleSwap = floatArrayOf(
                            gx0 + (c + 2) * gxStep, gy0 + (r + 1) * gyStep,
                            gx0 + (c + 2) * gxStep, gy0 + r * gyStep
                        )
                        break@outer
                    }
                }
                if (r + 2 < 6 && gridColors[r][c] == gridColors[r + 1][c]) {
                    val targetColor = gridColors[r][c]
                    if (c + 1 < 6 && gridColors[r + 2][c + 1] == targetColor) {
                        puzzleSwap = floatArrayOf(
                            gx0 + (c + 1) * gxStep, gy0 + (r + 2) * gyStep,
                            gx0 + c * gxStep, gy0 + (r + 2) * gyStep
                        )
                        break@outer
                    }
                }
            }
        }

        // 5. Analizor 3D Crosshair & Țintă Inamic (PUBG Mobile / Call of Duty Mobile / Free Fire)
        var enemySumX = 0f
        var enemySumY = 0f
        var enemyWeight = 0f
        var crosshairHits = 0

        val fovX0 = (bw * 0.28f).toInt().coerceIn(0, bw - 2)
        val fovX1 = (bw * 0.72f).toInt().coerceIn(fovX0 + 1, bw - 1)
        val fovY0 = (bh * 0.24f).toInt().coerceIn(0, bh - 2)
        val fovY1 = (bh * 0.66f).toInt().coerceIn(fovY0 + 1, bh - 1)
        val fovStepX = ((fovX1 - fovX0) / 16).coerceAtLeast(2)
        val fovStepY = ((fovY1 - fovY0) / 12).coerceAtLeast(2)

        var fy = fovY0
        while (fy <= fovY1) {
            var fx = fovX0
            var prevL = -1
            while (fx <= fovX1) {
                val c = safeGetPixel(bmp, fx, fy)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val l = (r * 30 + g * 59 + b * 11) / 100

                val isRedEnemyMarker = (r > 185 && g < 78 && b < 78) || (r > 215 && g in 75..140 && b < 60)
                val isCenterSilhouette = prevL >= 0 && abs(l - prevL) > 62 &&
                    fx in (bw * 0.38f).toInt()..(bw * 0.62f).toInt() &&
                    fy in (bh * 0.34f).toInt()..(bh * 0.58f).toInt()

                if (isRedEnemyMarker || isCenterSilhouette) {
                    val w = if (isRedEnemyMarker) 3.0f else 1.0f
                    enemySumX += fx * w
                    enemySumY += fy * w
                    enemyWeight += w
                    if (fx in (bw * 0.42f).toInt()..(bw * 0.58f).toInt() &&
                        fy in (bh * 0.36f).toInt()..(bh * 0.58f).toInt()
                    ) {
                        crosshairHits++
                    }
                }
                prevL = l
                fx += fovStepX
            }
            fy += fovStepY
        }

        val detectedEnemyPoint: Pair<Float, Float>? = if (enemyWeight >= 3.0f) {
            val ex = ((enemySumX / enemyWeight) / bw.toFloat()) * dispW
            val ey = ((enemySumY / enemyWeight) / bh.toFloat()) * dispH
            ex to ey
        } else {
            null
        }
        val targetInCrosshair = crosshairHits >= 2

        val isArcadeBoard = activeMode == GameBotMode.TETRIS_ARCADE ||
            activeGameTitle.contains("tetris", ignoreCase = true) ||
            MediaProjectionVisionEngine.isPortraitArcadeBoardScreen(bmp, bw, bh, isLand)

        val isSolitaireBoard = activeMode == GameBotMode.SOLITAIRE_CARDS ||
            activeGameTitle.contains("solit", ignoreCase = true) ||
            activeGameTitle.contains("klondike", ignoreCase = true) ||
            activeGameTitle.contains("spider", ignoreCase = true) ||
            activeGameTitle.contains("freecell", ignoreCase = true) ||
            activeGameTitle.contains("tripledot", ignoreCase = true) ||
            GameScreenAnalysisUtility.isSolitaireOrCardTableScreen(bmp, bw, bh, isLand)

        val isActiveBoardOrCardScreen = isArcadeBoard || isSolitaireBoard ||
            activeMode == GameBotMode.TOUCH_DRAG_PUZZLE ||
            activeMode == GameBotMode.PUZZLE_MATCH

        val detectedPhase = when {
            isActiveBoardOrCardScreen -> DetectedGamePhase.ACTIVE_GAMEPLAY
            diffScoreIsStaticMenu(diffSum, bestZoneScore) -> DetectedGamePhase.MENU_OR_DIALOG
            else -> DetectedGamePhase.ACTIVE_GAMEPLAY
        }

        // 6. Analizor Touchscreen 2026: Detectare piese din tavă (Block Blast / Woodoku / Drag Puzzle),
        // tablă centrală de joc și ținte interactive (salient touch targets)
        val detectedTrayPieces = mutableListOf<Pair<Float, Float>>()
        if (!isSolitaireBoard && !isArcadeBoard) {
            val trayYFrac = 0.81f
            val traySlotXFracs = floatArrayOf(0.22f, 0.50f, 0.78f)
            for (slotX in traySlotXFracs) {
                val px = (slotX * bw).toInt().coerceIn(0, bw - 1)
                val py = (trayYFrac * bh).toInt().coerceIn(0, bh - 1)
                var vibrantScore = 0f
                for (dy in -3..3) {
                    for (dx in -3..3) {
                        val spx = (px + dx * 3).coerceIn(0, bw - 1)
                        val spy = (py + dy * 3).coerceIn(0, bh - 1)
                        val c = safeGetPixel(bmp, spx, spy)
                        Color.colorToHSV(c, hsv)
                        if (hsv[1] > 0.28f && hsv[2] > 0.32f) {
                            vibrantScore += (hsv[1] + hsv[2])
                        }
                    }
                }
                if (vibrantScore > 12f) {
                    detectedTrayPieces.add((slotX * dispW) to (trayYFrac * dispH))
                }
            }
        }

        // Detectare celulă liberă pe tabla centrală (y: 24%..68%)
        val boardGridCols = 8
        val boardGridRows = 8
        var bestBoardCellX = dispW * 0.50f
        var bestBoardCellY = dispH * 0.46f
        var minCellOccupancy = 99999f
        for (r in 0 until boardGridRows) {
            val cyFrac = 0.25f + (r + 0.5f) * (0.42f / boardGridRows)
            for (c in 0 until boardGridCols) {
                val cxFrac = 0.14f + (c + 0.5f) * (0.72f / boardGridCols)
                val px = (cxFrac * bw).toInt().coerceIn(0, bw - 1)
                val py = (cyFrac * bh).toInt().coerceIn(0, bh - 1)
                val cellColor = safeGetPixel(bmp, px, py)
                Color.colorToHSV(cellColor, hsv)
                val occupancyScore = hsv[1] * 2.0f + hsv[2]
                if (occupancyScore < minCellOccupancy) {
                    minCellOccupancy = occupancyScore
                    bestBoardCellX = cxFrac * dispW
                    bestBoardCellY = cyFrac * dispH
                }
            }
        }

        val recommendedDrag = if (detectedTrayPieces.isNotEmpty()) {
            val sourcePiece = detectedTrayPieces.first()
            floatArrayOf(sourcePiece.first, sourcePiece.second, bestBoardCellX, bestBoardCellY)
        } else null

        val recommendedSwipe = when {
            puzzleSwap != null -> puzzleSwap
            laneHazards.any { it > 0.18f } -> {
                val dirX = if (recommendedLane == 0) -dispW * 0.30f else if (recommendedLane == 2) dispW * 0.30f else 0f
                floatArrayOf(dispW * 0.5f, dispH * 0.5f, dispW * 0.5f + dirX, dispH * 0.5f)
            }
            else -> null
        }

        // Identificare ținte interactive proeminente (high-contrast / colorful clusters)
        val salientTargets = mutableListOf<Pair<Float, Float>>()
        for (gy in 2..7) {
            val sy = (gy / 10f * dispH)
            for (gx in 1..4) {
                val sx = (gx / 5f * dispW)
                val bx = ((sx / dispW) * bw).toInt().coerceIn(0, bw - 1)
                val by = ((sy / dispH) * bh).toInt().coerceIn(0, bh - 1)
                val c = safeGetPixel(bmp, bx, by)
                Color.colorToHSV(c, hsv)
                if (hsv[1] > 0.45f && hsv[2] > 0.50f) {
                    salientTargets.add(sx to sy)
                }
            }
        }

        // Clasificare categorie de joc
        val detectedCat = when {
            isSolitaireBoard -> DetectedGameCategory.CARD_SOLITAIRE_TABLE
            isArcadeBoard -> DetectedGameCategory.RETRO_DPAD_LEGACY
            isLand && (crosshairHits >= 2 || enemyWeight >= 3.0f) -> DetectedGameCategory.ACTION_JOYSTICK_3D
            puzzleSwap != null -> DetectedGameCategory.TOUCH_MATCH_TAP
            detectedTrayPieces.isNotEmpty() && !isLand -> DetectedGameCategory.TOUCH_DRAG_DROP_PUZZLE
            laneHazards.any { it > 0.18f } -> DetectedGameCategory.TOUCH_SWIPE_RUNNER
            else -> if (isLand) DetectedGameCategory.ACTION_JOYSTICK_3D else DetectedGameCategory.TOUCH_DRAG_DROP_PUZZLE
        }

        // 7. Rulăm analizorul complet MediaProjection & AI Vision
        val mpVision = MediaProjectionVisionEngine.analyzeGameFrameForAiVision(
            bitmap = bmp,
            screenWidth = dispW,
            screenHeight = dispH,
            activeGameTitle = activeGameTitle,
            activeMode = activeMode
        )
        val healBtn = mpVision.detectedButtons.firstOrNull { it.category == GameUiButtonCategory.HEAL_OR_COVER_BUTTON }
        val closeBtn = mpVision.detectedButtons.firstOrNull { it.category == GameUiButtonCategory.CLOSE_POPUP_X_BUTTON }

        val filteredBestButtonPoint = if (isActiveBoardOrCardScreen) null else bestButtonPoint

        val resolvedMenuButton = filteredBestButtonPoint ?: if (
            !isActiveBoardOrCardScreen && (
                mpVision.gameState == DetectedVisionGameState.MAIN_MENU_LOBBY ||
                    mpVision.gameState == DetectedVisionGameState.VICTORY_OR_DEFEAT_SCREEN ||
                    mpVision.gameState == DetectedVisionGameState.POPUP_OR_REWARD_DIALOG
                )
        ) mpVision.recommendedTapTarget else null

        val resolvedScene3d = if (!isActiveBoardOrCardScreen && (isLand || detectedCat == DetectedGameCategory.ACTION_JOYSTICK_3D)) {
            MediaProjectionVisionEngine.latestScreenAnalysis.value?.scene3dAnalysis
                ?: GameScreenAnalysisUtility.analyze3dSceneEnvironment(bmp, bmp.width, bmp.height, activeGameTitle)
        } else {
            null
        }

        return GameVisualAnalysis(
            width = dispW,
            height = dispH,
            isLandscape = isLand,
            phase = detectedPhase,
            frameDiffScore = diffSum,
            detectedMenuButton = resolvedMenuButton,
            menuButtonLabel = if (filteredBestButtonPoint != null) bestButtonLabel else mpVision.recommendedActionLabel,
            laneHazards = laneHazards,
            laneHasCoin = laneCoins,
            recommendedSafeLane = recommendedLane,
            tetrisBestColumnFrac = tetrisBestColFrac,
            tetrisPieceColumnFrac = tetrisPieceColFrac,
            puzzleSwapCoordinates = puzzleSwap,
            enemyTargetPoint = detectedEnemyPoint,
            hasTargetInCrosshair = targetInCrosshair,
            playerHpPercent = mpVision.healthBars.playerHpPercent,
            enemyHpPercent = mpVision.healthBars.enemyOrBossHpPercent,
            secondaryBarPercent = mpVision.healthBars.secondaryBarPercent,
            isLowHealthCritical = mpVision.healthBars.isLowHealthCritical,
            visionGameState = mpVision.gameState,
            detectedUiButtons = mpVision.detectedButtons,
            healButtonPoint = healBtn?.let { it.centerX to it.centerY },
            closePopupPoint = closeBtn?.let { it.centerX to it.centerY },
            aiVisionDirective = mpVision.aiVisionDirective,
            detectedCategory = detectedCat,
            salientInteractiveTargets = salientTargets,
            trayPieceTargets = detectedTrayPieces,
            boardCenterTarget = bestBoardCellX to bestBoardCellY,
            recommendedDragMotion = recommendedDrag,
            recommendedSwipeMotion = recommendedSwipe,
            scene3dAnalysis = resolvedScene3d
        )
    }

    private fun diffScoreIsStaticMenu(diffSum: Float, buttonScore: Float): Boolean {
        return (diffSum < 0.012f && buttonScore > 0.55f) || diffSum < 0.004f
    }

    /**
     * Înregistrează separat acceptarea gestului de către Android și schimbarea observată în joc.
     * Schimbarea unui cadru nu dovedește că mutarea a fost corectă; aceasta este doar verificare
     * vizuală de progres. Fără MediaProjection, rezultatul rămâne explicit neverificat.
     */
    private fun dispatchTrackedGesture(
        gesture: GestureDescription,
        action: String
    ): Boolean {
        val sequence = gestureFeedbackSequence.incrementAndGet()
        val before = MediaProjectionVisionEngine.latestVisionAnalysis.value
        val hadProjection = MediaProjectionVisionEngine.isMediaProjectionActive.value
        return try {
            val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    mainHandler.postDelayed({
                        if (sequence != gestureFeedbackSequence.get()) return@postDelayed
                        val after = MediaProjectionVisionEngine.latestVisionAnalysis.value
                        val fresh = hadProjection && after.timestamp > before.timestamp
                        val changed = if (fresh) after.frameLumaDiff >= 0.012f else null
                        val note = when {
                            !hadProjection -> "Gest acceptat de Android; rezultat neverificat (captura ecran inactivă)."
                            !fresh -> "Gest acceptat; nu a sosit un cadru nou pentru verificare."
                            changed == true -> "A fost observată o schimbare vizuală după gest; mutarea nu este confirmată semantic."
                            else -> "Cadrul a rămas aproape neschimbat; acțiunea poate să nu fi avut efect."
                        }
                        _lastGestureFeedback.value = GestureVerificationFeedback(
                            action = action,
                            androidAccepted = true,
                            callbackCompleted = true,
                            freshFrameObserved = fresh,
                            frameChanged = changed,
                            note = note
                        )
                    }, 650L)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (sequence != gestureFeedbackSequence.get()) return
                    _lastGestureFeedback.value = GestureVerificationFeedback(
                        action = action,
                        androidAccepted = true,
                        callbackCompleted = false,
                        freshFrameObserved = false,
                        frameChanged = null,
                        note = "Android a anulat gestul; nicio acțiune de joc nu este considerată executată."
                    )
                }
            }, mainHandler)
            if (!accepted && sequence == gestureFeedbackSequence.get()) {
                _lastGestureFeedback.value = GestureVerificationFeedback(
                    action = action,
                    androidAccepted = false,
                    callbackCompleted = false,
                    freshFrameObserved = false,
                    frameChanged = null,
                    note = "Android a refuzat programarea gestului."
                )
            }
            accepted
        } catch (e: Exception) {
            if (sequence == gestureFeedbackSequence.get()) _lastGestureFeedback.value = GestureVerificationFeedback(
                action = action,
                androidAccepted = false,
                callbackCompleted = false,
                freshFrameObserved = false,
                frameChanged = null,
                note = "Eroare dispatchGesture: ${e.message ?: "necunoscută"}"
            )
            false
        }
    }

    // ── Gesturi Reale Calibrate pentru Unity / Unreal / OpenGL și UI Android ──
    fun performTap(x: Float, y: Float, durationMs: Long = 70L): Boolean {
        return try {
            val (w, h) = getLiveDisplaySize()
            val safeX = x.coerceIn(8f, (w - 8).toFloat())
            // Verificăm dacă punctul se află chiar sub capsula centrală a Panoului Plutitor EVA-1 (centru sus: 28%-72% lățime, primele 48dp)
            val isDirectlyUnderHud = _isFloatingHudVisible.value &&
                safeX in (w * 0.28f)..(w * 0.72f) &&
                y < dp(48)
            val safeY = if (isDirectlyUnderHud) {
                dp(52).toFloat()
            } else {
                y.coerceIn(8f, (h - 8).toFloat())
            }
            val path = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX + 0.5f, safeY + 0.5f)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(45L, 600L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchTrackedGesture(gesture, "touch_gesture")
        } catch (_: Exception) {
            false
        }
    }

    fun performBurstTaps(x: Float, y: Float, count: Int = 3, intervalMs: Long = 70L): Boolean {
        return try {
            val (w, h) = getLiveDisplaySize()
            val safeX = x.coerceIn(8f, (w - 8).toFloat())
            val isDirectlyUnderHud = _isFloatingHudVisible.value &&
                safeX in (w * 0.28f)..(w * 0.72f) &&
                y < dp(48)
            val safeY = if (isDirectlyUnderHud) dp(52).toFloat() else y.coerceIn(8f, (h - 8).toFloat())

            val taps = count.coerceIn(1, 5)
            // Construim un singur GestureDescription cu micro-oscilații / stroke-uri eșalonate în timp
            // pentru a evita anularea reciprocă (onCancelled) a apelurilor simultane la dispatchGesture!
            val builder = GestureDescription.Builder()
            val tapDur = 45L
            val gap = intervalMs.coerceAtLeast(55L)
            for (i in 0 until taps) {
                val p = Path().apply {
                    val jitterX = (safeX + (i % 2) * 1.5f).coerceIn(8f, (w - 8).toFloat())
                    moveTo(jitterX, safeY)
                    lineTo(jitterX + 0.5f, safeY + 0.5f)
                }
                builder.addStroke(GestureDescription.StrokeDescription(p, i * gap, tapDur))
            }
            dispatchTrackedGesture(builder.build(), "burst_tap")
        } catch (_: Exception) {
            performTap(x, y, 65L)
        }
    }

    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 165L
    ): Boolean {
        return try {
            val (w, h) = getLiveDisplaySize()
            val sx = startX.coerceIn(8f, (w - 8).toFloat())
            val sy = startY.coerceIn(8f, (h - 8).toFloat())
            val ex = endX.coerceIn(8f, (w - 8).toFloat())
            val ey = endY.coerceIn(8f, (h - 8).toFloat())
            val safeEndX = if (sx == ex && sy == ey) ex + 0.5f else ex
            val safeEndY = if (sx == ex && sy == ey) ey + 0.5f else ey
            val path = Path().apply {
                moveTo(sx, sy)
                lineTo(safeEndX, safeEndY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(50L, 1200L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchTrackedGesture(gesture, "touch_gesture")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Execută o mișcare realistă de Drag & Drop (ridicare piesă din tavă, ținere scurtă și tragere pe tablă):
     * Esențial pentru jocuri touchscreen precum Block Blast, Woodoku, Jigsaw, Candy Crush, Solitaire.
     */
    fun performDragAndDrop(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 440L
    ): Boolean {
        return try {
            val (w, h) = getLiveDisplaySize()
            val minSafeY = dp(56).toFloat().coerceAtMost(h * 0.12f)
            val sx = startX.coerceIn(16f, (w - 16).toFloat())
            val sy = startY.coerceIn(minSafeY, (h - 16).toFloat())
            val ex = endX.coerceIn(16f, (w - 16).toFloat())
            val ey = endY.coerceIn(minSafeY, (h - 16).toFloat())
            val path = Path().apply {
                moveTo(sx, sy)
                lineTo(sx + 1f, sy + 1f)
                lineTo(ex, ey)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(240L, 1200L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchTrackedGesture(gesture, "touch_gesture")
        } catch (_: Exception) {
            false
        }
    }

    fun performLongPress(x: Float, y: Float, durationMs: Long = 550L): Boolean {
        return performTap(x, y, durationMs.coerceIn(400L, 1200L))
    }

    /**
     * Execută simultan mișcarea joystick-ului virtual (mâna stângă) și apăsarea pe atac/abilitate (mâna dreaptă)
     */
    fun performDualThumbAction(
        joyStartX: Float,
        joyStartY: Float,
        joyEndX: Float,
        joyEndY: Float,
        attackX: Float,
        attackY: Float,
        durationMs: Long = 320L
    ): Boolean {
        return try {
            val (w, h) = getLiveDisplaySize()
            val minSafeY = dp(56).toFloat().coerceAtMost(h * 0.12f)
            val jx1 = joyStartX.coerceIn(20f, (w - 20).toFloat())
            val jy1 = joyStartY.coerceIn(minSafeY, (h - 20).toFloat())
            val jx2 = joyEndX.coerceIn(20f, (w - 20).toFloat())
            val jy2 = joyEndY.coerceIn(minSafeY, (h - 20).toFloat())
            val ax = attackX.coerceIn(20f, (w - 20).toFloat())
            val ay = attackY.coerceIn(minSafeY, (h - 20).toFloat())

            val safeJx2 = if (jx1 == jx2 && jy1 == jy2) jx2 + 0.5f else jx2
            val safeJy2 = if (jx1 == jx2 && jy1 == jy2) jy2 + 0.5f else jy2
            val joyPath = Path().apply {
                moveTo(jx1, jy1)
                lineTo(safeJx2, safeJy2)
            }
            val attackPath = Path().apply {
                moveTo(ax, ay)
                lineTo(ax + 0.5f, ay + 0.5f)
            }

            val joyStroke = GestureDescription.StrokeDescription(joyPath, 0L, durationMs.coerceIn(150L, 900L))
            val attackStroke = GestureDescription.StrokeDescription(attackPath, 60L, 110L)

            val gesture = GestureDescription.Builder()
                .addStroke(joyStroke)
                .addStroke(attackStroke)
                .build()
            dispatchTrackedGesture(gesture, "touch_gesture")
        } catch (_: Exception) {
            performTap(attackX, attackY, 95L)
        }
    }

    /**
     * Execută simultan:
     *  - Degetul Stâng: Mișcare 360° pe Joystick-ul virtual (Înainte, Înapoi, Stânga, Dreapta, Diagonal)
     *  - Degetul Drept: Swipe real pe jumătatea dreaptă a ecranului pentru a ROTI CAMERA 3D (Stânga, Dreapta, Sus, Jos)
     * Esențial pentru PUBG Mobile, Call of Duty Mobile, Free Fire, GTA și jocuri 3D Open-World!
     */
    fun performDualStickMoveAndLook(
        joyStartX: Float,
        joyStartY: Float,
        joyEndX: Float,
        joyEndY: Float,
        camStartX: Float,
        camStartY: Float,
        camEndX: Float,
        camEndY: Float,
        durationMs: Long = 360L
    ): Boolean {
        return try {
            val (w, h) = getLiveDisplaySize()
            val minSafeY = dp(56).toFloat().coerceAtMost(h * 0.12f)
            val jx1 = joyStartX.coerceIn(20f, (w - 20).toFloat())
            val jy1 = joyStartY.coerceIn(minSafeY, (h - 20).toFloat())
            val jx2 = joyEndX.coerceIn(20f, (w - 20).toFloat())
            val jy2 = joyEndY.coerceIn(minSafeY, (h - 20).toFloat())

            val cx1 = camStartX.coerceIn(20f, (w - 20).toFloat())
            val cy1 = camStartY.coerceIn(minSafeY, (h - 20).toFloat())
            val cx2 = camEndX.coerceIn(20f, (w - 20).toFloat())
            val cy2 = camEndY.coerceIn(minSafeY, (h - 20).toFloat())

            val safeJx2 = if (abs(jx1 - jx2) < 1f && abs(jy1 - jy2) < 1f) jx2 + 1.5f else jx2
            val safeJy2 = if (abs(jx1 - jx2) < 1f && abs(jy1 - jy2) < 1f) jy2 - 1.5f else jy2

            val safeCx2 = if (abs(cx1 - cx2) < 1f && abs(cy1 - cy2) < 1f) cx2 + 2.0f else cx2
            val safeCy2 = if (abs(cx1 - cx2) < 1f && abs(cy1 - cy2) < 1f) cy2 + 0.5f else cy2

            val joyPath = Path().apply {
                moveTo(jx1, jy1)
                lineTo(safeJx2, safeJy2)
            }
            val camPath = Path().apply {
                moveTo(cx1, cy1)
                lineTo(safeCx2, safeCy2)
            }

            val totalDur = durationMs.coerceIn(160L, 900L)
            val camDur = (totalDur - 40L).coerceIn(110L, 650L)

            val joyStroke = GestureDescription.StrokeDescription(joyPath, 0L, totalDur)
            val camStroke = GestureDescription.StrokeDescription(camPath, 20L, camDur)

            val gesture = GestureDescription.Builder()
                .addStroke(joyStroke)
                .addStroke(camStroke)
                .build()
            dispatchTrackedGesture(gesture, "touch_gesture")
        } catch (_: Exception) {
            performSwipe(joyStartX, joyStartY, joyEndX, joyEndY, durationMs)
        }
    }

    /**
     * Apasă pe un element detectat din UI (prin ACTION_CLICK pe nod sau prin GestureTap la coordonatele sale).
     */
    fun clickElementOrTap(element: ScreenUiElement, durationMs: Long = 90L): Boolean {
        val label = element.text.ifBlank { element.contentDescription.ifBlank { element.viewId } }
        if (label.isNotBlank() && clickNodeMatching(label)) {
            return true
        }
        return performTap(element.centerX.toFloat(), element.centerY.toFloat(), durationMs)
    }

    // A single pending confirmation can exist. Invalidating the token also cancels
    // delayed callbacks from a previous attempt, including after an Activity failure.
    private var whatsappSendToken = 0L
    private var whatsappSendPending = false

    fun cancelConfirmedWhatsAppSend() {
        whatsappSendToken++
        whatsappSendPending = false
        if (_whatsAppSendStatus.value.startsWith("În așteptare")) {
            _whatsAppSendStatus.value = "Trimiterea WhatsApp a fost anulată; nu există confirmare de expediere."
        }
    }

    /** User-approved, single-use WhatsApp send. Expires after 20 seconds. */
    fun armConfirmedWhatsAppSend(expectedMessage: String): Boolean {
        if (expectedMessage.isBlank() || whatsappSendPending) return false
        whatsappSendPending = true
        _whatsAppSendStatus.value = "În așteptare: verific mesajul în WhatsApp (maximum 20 secunde)."
        val token = ++whatsappSendToken
        val deadline = android.os.SystemClock.uptimeMillis() + 20_000L
        var attempts = 0
        fun verifyComposer(root: AccessibilityNodeInfo, depth: Int): Boolean {
            if (depth > 35 || root.packageName?.toString() != "com.whatsapp") return false
            val id = root.viewIdResourceName.orEmpty()
            if ((id == "com.whatsapp:id/entry" || id == "com.whatsapp:id/entry_text") &&
                root.text?.toString()?.trim() == expectedMessage.trim()) return true
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                if (verifyComposer(child, depth + 1)) return true
            }
            return false
        }
        fun poll() {
            if (token != whatsappSendToken || !whatsappSendPending) return
            if (android.os.SystemClock.uptimeMillis() >= deadline || attempts++ >= 30) {
                cancelConfirmedWhatsAppSend()
                _whatsAppSendStatus.value = "Expirat: EVA nu a identificat mesajul și butonul Trimite."
                return
            }
            val root = runCatching { rootInActiveWindow }.getOrNull()
            if (root != null && root.packageName?.toString() == "com.whatsapp" &&
                verifyComposer(root, 0) &&
                clickExactResourceInForeground("com.whatsapp", "com.whatsapp:id/send")) {
                cancelConfirmedWhatsAppSend()
                _whatsAppSendStatus.value = "Butonul Trimite a fost apăsat. Expedierea/livrarea nu sunt confirmate de WhatsApp."
                return
            }
            mainHandler.postDelayed({ poll() }, 650L)
        }
        mainHandler.postDelayed({ poll() }, 700L)
        return true
    }

    /**
     * Click limitat la aplicația așteptată și la un ID exact de resursă.
     * Nu caută în alte ferestre și nu folosește coordonate aproximative.
     * Rezultatul confirmă numai ACTION_CLICK, nu livrarea unui mesaj.
     */
    fun clickExactResourceInForeground(expectedPackage: String, resourceId: String): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        if (root.packageName?.toString() != expectedPackage) return false
        fun click(node: AccessibilityNodeInfo, depth: Int): Boolean {
            if (depth > 40) return false
            if (node.packageName?.toString() != expectedPackage) return false
            if (node.viewIdResourceName == resourceId && node.isEnabled && node.isVisibleToUser) {
                return node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            for (i in 0 until node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                if (click(child, depth + 1)) return true
            }
            return false
        }
        return runCatching { click(root, 0) }.getOrDefault(false)
    }

    // ── Click pe un buton sau element care contine textul cautat ──
    fun clickNodeMatching(vararg keywords: String): Boolean {
        val keys = keywords.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        if (keys.isEmpty()) return false

        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null && findAndClick(activeRoot, keys)) {
                return true
            }
        } catch (_: Exception) {}

        try {
            val allWindows = windows
            if (!allWindows.isNullOrEmpty()) {
                for (win in allWindows) {
                    if (win.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                    val r = try { win.root } catch (_: Exception) { null } ?: continue
                    if (findAndClick(r, keys)) return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    private fun findAndClick(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val txt = node.text?.toString()?.lowercase()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
        val viewId = node.viewIdResourceName?.lowercase()?.substringAfterLast('/') ?: ""

        if (keywords.any { kw -> txt == kw || txt.contains(kw) || desc.contains(kw) || viewId.contains(kw) }) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            var parent = node.parent
            var hops = 0
            while (parent != null && hops < 5) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                parent = parent.parent
                hops++
            }
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 6 && rect.height() > 6) {
                return performTap(rect.centerX().toFloat(), rect.centerY().toFloat(), 95L)
            }
        }

        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (findAndClick(child, keywords)) return true
        }
        return false
    }

    // ── Introducere text intr-un camp editabil ────────────────────
    fun inputTextInFocusedOrFirstEditable(text: String): Boolean {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return false
        return findAndSetText(root, text)
    }

    private fun findAndSetText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        if (node.isEditable) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (findAndSetText(child, text)) return true
        }
        return false
    }
}
