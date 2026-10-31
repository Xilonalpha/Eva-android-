package com.eva1.companion.engines

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ══════════════════════════════════════════════════════════════════════════════
// EVA-1 MEDIAPROJECTION SCREEN CAPTURE & AI VISION GAME UI ANALYZER
//
// Implementează:
//   1. Captură de ecran de înaltă frecvență prin Android MediaProjection API
//      (VirtualDisplay + ImageReader RGBA_8888 în ForegroundService dedicat).
//   2. Extragerea elementelor UI din jocuri (Butoane, Bare de Viață / Health Bars,
//      Scut / Elixir / Nitro și Starea Jocului: Lobby, Popup, Gameplay, Low-HP).
//   3. Alimentarea modelului AI Vision (Tensor Base64 + JSON structurat cu
//      coordonate exacte) și redarea fluxului video adnotat în timp real.
// ══════════════════════════════════════════════════════════════════════════════

enum class GameUiButtonCategory(val labelRo: String) {
    START_PLAY_BUTTON("Buton PLAY / START / BATTLE"),
    CLOSE_POPUP_X_BUTTON("Buton Închidere Pop-up (X)"),
    ATTACK_PRIMARY_BUTTON("Buton Atac / Foc Principal"),
    SKILL_OR_NITRO_BUTTON("Buton Abilitate / Scope / Nitro / Pasă"),
    HEAL_OR_COVER_BUTTON("Buton Medkit / Vindecare / Scut"),
    CONTINUE_OR_CLAIM_BUTTON("Buton Continue / Claim / Next")
}

data class DetectedGameUiButton(
    val id: String,
    val category: GameUiButtonCategory,
    val label: String,
    val centerX: Float,
    val centerY: Float,
    val widthPx: Int,
    val heightPx: Int,
    val confidence: Float,
    val dominantHue: Float
)

data class DetectedHealthAndResourceBars(
    val playerHpPercent: Float = 100f,
    val enemyOrBossHpPercent: Float = 100f,
    val secondaryBarPercent: Float = 100f, // Scut / Armură / Elixir / Nitro
    val isLowHealthCritical: Boolean = false,
    val playerBarDetected: Boolean = false,
    val enemyBarDetected: Boolean = false,
    val playerBarRegion: String = "HUD Stânga-Sus / Jos-Centru"
)

enum class DetectedVisionGameState(val displayName: String, val badgeColorHex: Long) {
    MAIN_MENU_LOBBY("Meniu Principal / Lobby Joc", 0xFF00E5FF),
    POPUP_OR_REWARD_DIALOG("Dialog Pop-up / Recompensă / Ofertă", 0xFFFFB300),
    ACTIVE_GAMEPLAY_COMBAT("Gameplay Activ & Combat în Timp Real", 0xFF00E676),
    LOW_HEALTH_CRITICAL("Alertă Viață Scăzută (HP Critic - Necesită Heal/Cover)", 0xFFFF1744),
    VICTORY_OR_DEFEAT_SCREEN("Final de Meci (Victory / Defeat / Continue)", 0xFFD500F9),
    LOADING_TRANSITION("Ecran Încărcare / Tranziție", 0xFF90A4AE)
}

data class MediaProjectionVisionAnalysis(
    val timestamp: Long = System.currentTimeMillis(),
    val captureSource: String = "MediaProjection VirtualDisplay",
    val frameWidth: Int = 1080,
    val frameHeight: Int = 2400,
    val isLandscape: Boolean = false,
    val gameState: DetectedVisionGameState = DetectedVisionGameState.MAIN_MENU_LOBBY,
    val healthBars: DetectedHealthAndResourceBars = DetectedHealthAndResourceBars(),
    val detectedButtons: List<DetectedGameUiButton> = emptyList(),
    val recommendedTapTarget: Pair<Float, Float>? = null,
    val recommendedActionLabel: String = "Așteptare cadru video",
    val aiVisionModelPromptPayload: String = "",
    val aiVisionDirective: String = "Pregătit pentru analiză vizuală MediaProjection",
    val base64ThumbnailPreview: String = "",
    val frameLumaDiff: Float = 0f
)

object MediaProjectionVisionEngine {

    private val _isMediaProjectionActive = MutableStateFlow(false)
    val isMediaProjectionActive: StateFlow<Boolean> = _isMediaProjectionActive.asStateFlow()

    private val _framesCapturedCount = MutableStateFlow(0L)
    val framesCapturedCount: StateFlow<Long> = _framesCapturedCount.asStateFlow()

    private val _streamFps = MutableStateFlow(0)
    val streamFps: StateFlow<Int> = _streamFps.asStateFlow()

    private val _lastFrameLatencyMs = MutableStateFlow(0L)
    val lastFrameLatencyMs: StateFlow<Long> = _lastFrameLatencyMs.asStateFlow()

    private val _latestAnnotatedVisionBitmap = MutableStateFlow<Bitmap?>(null)
    val latestAnnotatedVisionBitmap: StateFlow<Bitmap?> = _latestAnnotatedVisionBitmap.asStateFlow()

    private val _latestVisionAnalysis = MutableStateFlow(MediaProjectionVisionAnalysis())
    val latestVisionAnalysis: StateFlow<MediaProjectionVisionAnalysis> = _latestVisionAnalysis.asStateFlow()

    @Volatile
    private var latestProjectedBitmap: Bitmap? = null

    @Volatile
    private var latestDisplaySize: Pair<Int, Int> = 1080 to 2400

    @Volatile
    private var activeGameHintTitle: String = ""

    @Volatile
    private var activeGameHintMode: GameBotMode = GameBotMode.AUTO_ADAPTIVE

    private var fpsWindowStartMs: Long = 0L
    private var fpsFramesInWindow: Int = 0
    private var lastFullVisionAnalysisMs: Long = 0L

    private var prevLumaGrid: IntArray? = null
    private var lastAnalyzedBitmapRef: Bitmap? = null
    private var lastComputedLumaDiff: Float = 0.05f

    private val _latestScreenAnalysis = MutableStateFlow<ScreenAnalysisResult?>(null)
    val latestScreenAnalysis: StateFlow<ScreenAnalysisResult?> = _latestScreenAnalysis.asStateFlow()

    fun setActiveGameContext(gameTitle: String, mode: GameBotMode) {
        activeGameHintTitle = gameTitle
        activeGameHintMode = mode
    }

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

    fun setProjectionActive(active: Boolean) {
        _isMediaProjectionActive.value = active
        if (!active) {
            latestProjectedBitmap = null
            _latestScreenAnalysis.value = null
            _latestAnnotatedVisionBitmap.value = null
            _streamFps.value = 0
        }
    }

    fun updateProjectedFrame(bitmap: Bitmap, fullDisplayWidth: Int, fullDisplayHeight: Int) {
        if (bitmap.isRecycled) return
        val tStart = System.currentTimeMillis()
        latestProjectedBitmap = bitmap
        latestDisplaySize = fullDisplayWidth to fullDisplayHeight
        _isMediaProjectionActive.value = true
        _framesCapturedCount.value = _framesCapturedCount.value + 1L

        // Calculăm FPS în timp real pe o fereastră glisantă de 1 secundă
        if (fpsWindowStartMs == 0L) {
            fpsWindowStartMs = tStart
            fpsFramesInWindow = 1
        } else {
            fpsFramesInWindow++
            val elapsed = tStart - fpsWindowStartMs
            if (elapsed >= 1000L) {
                _streamFps.value = ((fpsFramesInWindow * 1000L) / elapsed.coerceAtLeast(1L)).toInt().coerceIn(1, 60)
                fpsWindowStartMs = tStart
                fpsFramesInWindow = 0
            }
        }

        // Mapează fluxul de pixeli MediaProjection la elemente UI identificabile și actualizează AI Vision
        try {
            val fgPkg = EvaAccessibilityService.lastForegroundPackage.value
            val hint = if (activeGameHintTitle.isNotBlank()) "$activeGameHintTitle $fgPkg" else fgPkg
            val analysis = GameScreenAnalysisUtility.analyzeMediaProjectionFrame(
                frameBitmap = bitmap,
                displayWidth = fullDisplayWidth,
                displayHeight = fullDisplayHeight,
                gameTitleHint = hint
            )
            _latestScreenAnalysis.value = analysis

            // Rulăm analiza completă AI Vision + adnotarea vizuală la ~8-10 Hz pentru a păstra latența sub 15ms
            if (tStart - lastFullVisionAnalysisMs >= 115L) {
                lastFullVisionAnalysisMs = tStart
                val vision = analyzeGameFrameForAiVision(
                    bitmap = bitmap,
                    screenWidth = fullDisplayWidth,
                    screenHeight = fullDisplayHeight,
                    activeGameTitle = hint,
                    activeMode = activeGameHintMode
                )
                val annotated = buildAnnotatedVisionBitmap(
                    sourceBitmap = bitmap,
                    displayWidth = fullDisplayWidth,
                    displayHeight = fullDisplayHeight,
                    screenAnalysis = analysis,
                    visionAnalysis = vision
                )
                if (annotated != null) {
                    _latestAnnotatedVisionBitmap.value = annotated
                }
            }
        } catch (_: Exception) {}

        _lastFrameLatencyMs.value = (System.currentTimeMillis() - tStart).coerceAtLeast(1L)
    }

    /**
     * Construiește în timp real un cadru video adnotat (Bounding Boxes, Coloane Solitaire/Tetris,
     * Bare de Viață și Reticul Țintă AI) pentru previzualizarea fluxului video MediaProjection.
     */
    private fun buildAnnotatedVisionBitmap(
        sourceBitmap: Bitmap,
        displayWidth: Int,
        displayHeight: Int,
        screenAnalysis: ScreenAnalysisResult?,
        visionAnalysis: MediaProjectionVisionAnalysis
    ): Bitmap? {
        if (sourceBitmap.isRecycled) return null
        return try {
            val isLand = displayWidth > displayHeight
            val targetW = if (isLand) 480 else 270
            val targetH = if (isLand) 270 else 480
            val scaled = Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
            val outBmp = if (scaled.isMutable) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true)
            if (outBmp !== scaled && !scaled.isRecycled) {
                scaled.recycle()
            }
            val canvas = Canvas(outBmp)
            val scaleX = targetW.toFloat() / displayWidth.coerceAtLeast(1).toFloat()
            val scaleY = targetH.toFloat() / displayHeight.coerceAtLeast(1).toFloat()

            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
                color = Color.parseColor("#00E5FF")
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#AA050B14")
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 10.5f
                isFakeBoldText = true
            }

            // 1. Adnotăm tabla de Solitaire (cele 7 coloane Tableau + Stock + Waste)
            val sol = screenAnalysis?.solitaireAnalysis
            if (sol != null && sol.isSolitaireTableDetected) {
                // Pachetul Stock (dreapta-sus)
                val sx = sol.stockPileX * scaleX
                val sy = sol.stockPileY * scaleY
                boxPaint.color = Color.parseColor("#00E5FF")
                canvas.drawRect(sx - 15f, sy - 20f, sx + 15f, sy + 20f, boxPaint)
                canvas.drawText("STOCK", sx - 16f, (sy - 23f).coerceAtLeast(12f), textPaint)

                // Cartea Waste
                if (sol.hasWasteCard) {
                    val wx = sol.wasteCardX * scaleX
                    val wy = sol.wasteCardY * scaleY
                    boxPaint.color = Color.parseColor("#FFD54F")
                    canvas.drawRect(wx - 15f, wy - 20f, wx + 15f, wy + 20f, boxPaint)
                    canvas.drawText("WASTE", wx - 16f, (wy - 23f).coerceAtLeast(12f), textPaint)
                }

                // Cele 7 coloane
                for (col in sol.columns) {
                    val cx = col.centerX * scaleX
                    val cy = col.bottomCardY * scaleY
                    boxPaint.color = when {
                        !col.hasFaceUpCards -> Color.parseColor("#546E7A")
                        col.hasFaceDownCardsAbove -> Color.parseColor("#FFB300")
                        else -> Color.parseColor("#00E676")
                    }
                    canvas.drawRect(cx - 14f, cy - 18f, cx + 14f, cy + 18f, boxPaint)
                    canvas.drawText("C${col.columnIndex + 1}", cx - 7f, (cy + 30f).coerceAtMost(targetH - 6f), textPaint)
                }
            }

            // 2. Adnotăm tabla de Tetris (coloana curentă vs coloana țintă)
            val tetris = screenAnalysis?.tetrisAnalysis
            if (tetris != null) {
                val bounds = screenAnalysis.activeBoardBounds
                boxPaint.color = Color.parseColor("#00E5FF")
                canvas.drawRect(
                    bounds.left * scaleX,
                    bounds.top * scaleY,
                    bounds.right * scaleX,
                    bounds.bottom * scaleY,
                    boxPaint
                )
            }

            // 2.5. Adnotăm Scena 3D (PUBG / CODM / Free Fire / GTA): Culoare Stânga/Centru/Dreapta + Vector Joystick & Cameră
            val scene3d = screenAnalysis?.scene3dAnalysis
            if (scene3d != null && (sol == null || !sol.isSolitaireTableDetected)) {
                val corridors = arrayOf(
                    Triple("L", 0.12f to 0.35f, scene3d.leftCorridorOpenness),
                    Triple("C", 0.38f to 0.62f, scene3d.centerCorridorOpenness),
                    Triple("R", 0.65f to 0.88f, scene3d.rightCorridorOpenness)
                )
                for ((label, xRange, openScore) in corridors) {
                    val leftPx = xRange.first * targetW
                    val rightPx = xRange.second * targetW
                    val topPx = 0.24f * targetH
                    val botPx = 0.64f * targetH
                    boxPaint.strokeWidth = 1.8f
                    boxPaint.color = when {
                        openScore >= 0.58f -> Color.parseColor("#00E676")
                        openScore >= 0.38f -> Color.parseColor("#FFB300")
                        else -> Color.parseColor("#FF1744")
                    }
                    canvas.drawRect(leftPx, topPx, rightPx, botPx, boxPaint)
                    canvas.drawText(
                        "$label:${(openScore * 100).toInt()}%",
                        leftPx + 4f,
                        topPx + 12f,
                        textPaint
                    )
                }

                // Desenăm vectorul Joystick-ului 360° (stânga-jos)
                val jx = 0.18f * targetW
                val jy = 0.74f * targetH
                val jEndX = (jx + scene3d.recommendedJoyDxFrac * 26f).coerceIn(8f, targetW - 8f)
                val jEndY = (jy + scene3d.recommendedJoyDyFrac * 26f).coerceIn(24f, targetH - 8f)
                boxPaint.color = Color.parseColor("#00E5FF")
                boxPaint.strokeWidth = 2.5f
                canvas.drawCircle(jx, jy, 14f, boxPaint)
                canvas.drawLine(jx, jy, jEndX, jEndY, boxPaint)
                canvas.drawCircle(jEndX, jEndY, 4f, boxPaint)

                // Desenăm vectorul de rotire a Camerei 3D (dreapta-mijloc)
                val cx = 0.66f * targetW
                val cy = 0.50f * targetH
                val cEndX = (cx + scene3d.recommendedCameraSwipeDxFrac * 110f).coerceIn(8f, targetW - 8f)
                val cEndY = (cy + scene3d.recommendedCameraSwipeDyFrac * 60f).coerceIn(24f, targetH - 8f)
                boxPaint.color = Color.parseColor("#FFD54F")
                canvas.drawLine(cx, cy, cEndX, cEndY, boxPaint)
                canvas.drawCircle(cEndX, cEndY, 4.5f, boxPaint)
            }

            // 3. Adnotăm butoanele UI detectate în joc
            for (btn in visionAnalysis.detectedButtons.take(5)) {
                val bx = btn.centerX * scaleX
                val by = btn.centerY * scaleY
                val hw = (btn.widthPx * scaleX * 0.5f).coerceIn(14f, 48f)
                val hh = (btn.heightPx * scaleY * 0.5f).coerceIn(10f, 32f)
                boxPaint.color = Color.parseColor("#00E676")
                canvas.drawRect(bx - hw, by - hh, bx + hw, by + hh, boxPaint)
            }

            // 4. Desenăm reticulul țintă AI (Recommended Tap Target)
            val targetPt = screenAnalysis?.recommendedTarget?.let { it.targetX to it.targetY }
                ?: visionAnalysis.recommendedTapTarget
            if (targetPt != null) {
                val tx = (targetPt.first * scaleX).coerceIn(8f, targetW - 8f)
                val ty = (targetPt.second * scaleY).coerceIn(8f, targetH - 8f)
                boxPaint.color = Color.parseColor("#FF1744")
                boxPaint.strokeWidth = 2.6f
                canvas.drawCircle(tx, ty, 11f, boxPaint)
                canvas.drawLine(tx - 16f, ty, tx + 16f, ty, boxPaint)
                canvas.drawLine(tx, ty - 16f, tx, ty + 16f, boxPaint)
            }

            // 5. Banner superior cu telemetrie FPS & Stare AI Vision / Scenă 3D
            canvas.drawRect(0f, 0f, targetW.toFloat(), 22f, fillPaint)
            textPaint.color = Color.parseColor("#00E5FF")
            textPaint.textSize = 10f
            val fpsVal = _streamFps.value
            val fpsLabel = if (fpsVal > 0) "${fpsVal}FPS" else "FPS în măsurare"
            val bannerText = if (scene3d != null && (sol == null || !sol.isSolitaireTableDetected)) {
                "LIVE $fpsLabel • ${scene3d.sceneType.badge} ${scene3d.sceneType.labelRo}"
            } else {
                "LIVE $fpsLabel • ${visionAnalysis.gameState.name.take(16)}"
            }
            canvas.drawText(
                bannerText,
                6f,
                15f,
                textPaint
            )
            outBmp
        } catch (_: Exception) {
            null
        }
    }

    fun updateLatestScreenAnalysis(analysis: ScreenAnalysisResult) {
        _latestScreenAnalysis.value = analysis
    }

    fun getLatestDisplaySize(): Pair<Int, Int> = latestDisplaySize

    @Volatile
    private var lastThumbBitmapRef: Bitmap? = null

    @Volatile
    private var lastCachedBase64Thumb: String = ""

    @Volatile
    private var lastThumbTimestampMs: Long = 0L

    /**
     * Returnează ultimul cadru Bitmap capturat prin MediaProjection (dacă este activ și nereciclat).
     */
    fun getLatestProjectedBitmap(): Bitmap? {
        val bmp = latestProjectedBitmap ?: return null
        return if (!bmp.isRecycled) bmp else null
    }

    /**
     * Detectează dacă ecranul portret curent este o tablă activă de joc de tip Tetris / Arcade
     * (dreptunghi central negru încadrat de margini laterale gri și bară de control jos).
     */
    fun isPortraitArcadeBoardScreen(bmp: Bitmap, bw: Int, bh: Int, isLand: Boolean): Boolean {
        if (isLand || bmp.isRecycled) return false
        val midY = (bh * 0.45f).toInt().coerceIn(0, bh - 1)
        val leftMarginPx = (bw * 0.05f).toInt().coerceIn(0, bw - 1)
        val rightMarginPx = (bw * 0.95f).toInt().coerceIn(0, bw - 1)
        val leftColor = safeGetPixel(bmp, leftMarginPx, midY)
        val rightColor = safeGetPixel(bmp, rightMarginPx, midY)
        val leftLuma = (Color.red(leftColor) * 30 + Color.green(leftColor) * 59 + Color.blue(leftColor) * 11) / 100
        val rightLuma = (Color.red(rightColor) * 30 + Color.green(rightColor) * 59 + Color.blue(rightColor) * 11) / 100

        if (leftLuma < 60 || rightLuma < 60) return false

        var darkBoardSamples = 0
        val totalBoardSamples = 25
        for (ry in 0 until 5) {
            val py = ((0.22f + ry * 0.10f) * bh).toInt().coerceIn(0, bh - 1)
            for (rx in 0 until 5) {
                val px = ((0.24f + rx * 0.13f) * bw).toInt().coerceIn(0, bw - 1)
                val c = safeGetPixel(bmp, px, py)
                val luma = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
                if (luma < 35) darkBoardSamples++
            }
        }
        return darkBoardSamples >= 8
    }

    /**
     * Analizează complet un cadru capturat (din MediaProjection sau Accessibility Screenshot):
     *   1. Identifică barele de viață (HP Jucător, HP Inamic/Boss, Scut/Elixir/Nitro).
     *   2. Identifică toate butoanele UI din joc (Play/Start, Close X, Attack/Fire, Skill/Nitro, Heal/Medkit).
     *   3. Clasifică starea jocului (Lobby, Popup, Gameplay Activ, Viață Critică, Final Meci).
     *   4. Construiește payload-ul multimodal (JSON + Base64 Thumbnail) și generează directiva AI Vision.
     */
    fun analyzeGameFrameForAiVision(
        bitmap: Bitmap,
        screenWidth: Int,
        screenHeight: Int,
        activeGameTitle: String = "",
        activeMode: GameBotMode = GameBotMode.AUTO_ADAPTIVE,
        sourceName: String = if (_isMediaProjectionActive.value) "MediaProjection API (VirtualDisplay 60-FPS)" else "Accessibility Pixel Capture"
    ): MediaProjectionVisionAnalysis {
        val dispW = screenWidth.coerceAtLeast(480)
        val dispH = screenHeight.coerceAtLeast(480)
        if (bitmap.isRecycled) {
            return _latestVisionAnalysis.value.copy(
                timestamp = System.currentTimeMillis(),
                captureSource = sourceName,
                frameWidth = dispW,
                frameHeight = dispH,
                isLandscape = dispW > dispH
            )
        }
        val bw = bitmap.width.coerceAtLeast(1)
        val bh = bitmap.height.coerceAtLeast(1)
        val isLand = dispW > dispH

        // Actualizăm și analiza de elemente UI (GameScreenAnalysisUtility) dacă nu era populată
        try {
            val screenAnalysis = GameScreenAnalysisUtility.analyzeMediaProjectionFrame(
                frameBitmap = bitmap,
                displayWidth = dispW,
                displayHeight = dispH,
                gameTitleHint = activeGameTitle
            )
            _latestScreenAnalysis.value = screenAnalysis
        } catch (_: Exception) {}

        // 1. Calculăm diferența de luminanță între cadre DOAR când avem un Bitmap nou
        val lumaDiff: Float
        if (bitmap !== lastAnalyzedBitmapRef || prevLumaGrid == null) {
            val lumaGrid = IntArray(144)
            var gIdx = 0
            for (gy in 0 until 12) {
                val py = ((gy + 0.5f) / 12f * bh).toInt().coerceIn(0, bh - 1)
                for (gx in 0 until 12) {
                    val px = ((gx + 0.5f) / 12f * bw).toInt().coerceIn(0, bw - 1)
                    val c = safeGetPixel(bitmap, px, py)
                    lumaGrid[gIdx++] = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
                }
            }
            val oldGrid = prevLumaGrid
            var computed = 0.05f
            if (oldGrid != null && oldGrid.size == lumaGrid.size) {
                var sum = 0f
                for (i in lumaGrid.indices) {
                    sum += abs(lumaGrid[i] - oldGrid[i]) / 255f
                }
                computed = sum / lumaGrid.size
            }
            prevLumaGrid = lumaGrid
            lastAnalyzedBitmapRef = bitmap
            lastComputedLumaDiff = computed
            lumaDiff = computed
        } else {
            lumaDiff = lastComputedLumaDiff
        }

        // 2. DETECTARE BARE DE VIAȚĂ (HEALTH BARS) & RESURSE (SCUT / ELIXIR / NITRO)
        val healthBars = detectHealthAndResourceBars(bitmap, bw, bh, isLand)

        // 3. DETECTARE BUTOANE UI JOC (START, CLOSE X, ATTACK, SKILL, HEAL, CONTINUE)
        val isArcadeBoard = activeMode == GameBotMode.TETRIS_ARCADE ||
            activeGameTitle.contains("tetris", ignoreCase = true) ||
            isPortraitArcadeBoardScreen(bitmap, bw, bh, isLand)

        val isSolitaireOrPuzzleBoard = activeMode == GameBotMode.SOLITAIRE_CARDS ||
            activeMode == GameBotMode.TOUCH_DRAG_PUZZLE ||
            activeMode == GameBotMode.PUZZLE_MATCH ||
            activeGameTitle.contains("solit", ignoreCase = true) ||
            activeGameTitle.contains("klondike", ignoreCase = true) ||
            activeGameTitle.contains("spider", ignoreCase = true) ||
            activeGameTitle.contains("freecell", ignoreCase = true) ||
            activeGameTitle.contains("tripledot", ignoreCase = true) ||
            GameScreenAnalysisUtility.isSolitaireOrCardTableScreen(bitmap, bw, bh, isLand)

        val isActiveBoardScreen = isArcadeBoard || isSolitaireOrPuzzleBoard

        val rawButtons = detectGameUiButtons(bitmap, bw, bh, dispW, dispH, isLand)
        // În timpul unui meci activ pe tabla de joc (Tetris, Solitaire, Puzzle), cărțile și piesele
        // NU sunt butoane de Lobby (START/PLAY/CONTINUE/CLOSE X)!
        val buttons = if (isActiveBoardScreen) {
            rawButtons.filterNot {
                it.category == GameUiButtonCategory.START_PLAY_BUTTON ||
                    it.category == GameUiButtonCategory.CONTINUE_OR_CLAIM_BUTTON ||
                    it.category == GameUiButtonCategory.CLOSE_POPUP_X_BUTTON
            }
        } else {
            rawButtons
        }

        // 4. DETERMINARE STARE JOC (GAME STATE)
        val closePopupBtn = buttons.firstOrNull { it.category == GameUiButtonCategory.CLOSE_POPUP_X_BUTTON && it.confidence >= 0.82f }
        val startPlayBtn = buttons.firstOrNull { it.category == GameUiButtonCategory.START_PLAY_BUTTON && it.confidence >= 0.84f }
        val continueBtn = buttons.firstOrNull { it.category == GameUiButtonCategory.CONTINUE_OR_CLAIM_BUTTON && it.confidence >= 0.82f }
        val healBtn = buttons.firstOrNull { it.category == GameUiButtonCategory.HEAL_OR_COVER_BUTTON }
        val attackBtn = buttons.firstOrNull { it.category == GameUiButtonCategory.ATTACK_PRIMARY_BUTTON }

        val gameState = when {
            lumaDiff < 0.002f && buttons.isEmpty() && isScreenNearBlackOrBlank(bitmap) -> DetectedVisionGameState.LOADING_TRANSITION
            isActiveBoardScreen -> DetectedVisionGameState.ACTIVE_GAMEPLAY_COMBAT
            healthBars.isLowHealthCritical && healthBars.playerBarDetected -> DetectedVisionGameState.LOW_HEALTH_CRITICAL
            closePopupBtn != null -> DetectedVisionGameState.POPUP_OR_REWARD_DIALOG
            startPlayBtn != null -> DetectedVisionGameState.MAIN_MENU_LOBBY
            continueBtn != null -> DetectedVisionGameState.VICTORY_OR_DEFEAT_SCREEN
            else -> DetectedVisionGameState.ACTIVE_GAMEPLAY_COMBAT
        }

        // 5. DETERMINARE COORDONATE RECOMANDATE ȘI DIRECTIVĂ AI VISION
        val (recommendedPoint, actionLabel, directive) = when (gameState) {
            DetectedVisionGameState.LOW_HEALTH_CRITICAL -> {
                val target = if (healBtn != null) {
                    healBtn.centerX to healBtn.centerY
                } else if (isLand) {
                    (dispW * 0.74f) to (dispH * 0.88f) // Zona tipică Medkit în PUBG / CODM / RPG
                } else {
                    (dispW * 0.22f) to (dispH * 0.84f)
                }
                Triple(
                    target,
                    "Activare Medkit / Vindecare / Eschivă (HP ${healthBars.playerHpPercent.roundToInt()}%)",
                    "🚨 AI Vision: Barele de viață indică HP Critic (${healthBars.playerHpPercent.roundToInt()}%). Se activează vindecarea/adăpostul la (${target.first.toInt()}, ${target.second.toInt()})."
                )
            }
            DetectedVisionGameState.POPUP_OR_REWARD_DIALOG -> {
                val pt = (closePopupBtn?.centerX ?: (dispW * 0.90f)) to (closePopupBtn?.centerY ?: (dispH * 0.14f))
                Triple(
                    pt,
                    "Închidere Pop-up / Colectare Recompensă",
                    "👁️ AI Vision: Fereastră Pop-up detectată pe ecran. Se apasă butonul „X / Close” la (${pt.first.toInt()}, ${pt.second.toInt()})."
                )
            }
            DetectedVisionGameState.MAIN_MENU_LOBBY -> {
                val btn = startPlayBtn ?: buttons.maxByOrNull { it.confidence }
                val pt = (btn?.centerX ?: (dispW * 0.84f)) to (btn?.centerY ?: (dispH * 0.84f))
                Triple(
                    pt,
                    btn?.label ?: "Apăsare PLAY / START MECI",
                    "🎯 AI Vision: Stare Lobby/Meniu identificată. Butonul „${btn?.label ?: "START"}” localizat la (${pt.first.toInt()}, ${pt.second.toInt()})."
                )
            }
            DetectedVisionGameState.VICTORY_OR_DEFEAT_SCREEN -> {
                val btn = continueBtn ?: startPlayBtn
                val pt = (btn?.centerX ?: (dispW * 0.50f)) to (btn?.centerY ?: (dispH * 0.84f))
                Triple(
                    pt,
                    "Continuare / Meci Nou (Continue)",
                    "🏆 AI Vision: Ecran de final meci detectat. Se apasă „Continue / Play Again” la (${pt.first.toInt()}, ${pt.second.toInt()})."
                )
            }
            DetectedVisionGameState.LOADING_TRANSITION -> {
                Triple(
                    null,
                    "Așteptare încărcare nivel / Scenă 3D",
                    "⏳ AI Vision: Jocul încarcă harta/meciul. Se monitorizează pasiv apariția HUD-ului."
                )
            }
            DetectedVisionGameState.ACTIVE_GAMEPLAY_COMBAT -> {
                val screenRec = _latestScreenAnalysis.value?.recommendedTarget
                val btn = attackBtn ?: buttons.firstOrNull()
                val pt = screenRec?.let { it.targetX to it.targetY } ?: btn?.let { it.centerX to it.centerY }
                Triple(
                    pt,
                    screenRec?.reason ?: "Gameplay Activ (${activeMode.displayName}) • HP ${healthBars.playerHpPercent.roundToInt()}%",
                    "⚡ AI Vision: HUD Gameplay activ (HP Jucător: ${healthBars.playerHpPercent.roundToInt()}%, Inamic/Obiectiv: ${healthBars.enemyOrBossHpPercent.roundToInt()}%, Resurse/Nitro: ${healthBars.secondaryBarPercent.roundToInt()}%)."
                )
            }
        }

        // 6. GENERARE THUMBNAIL BASE64 (cu cache pe referința Bitmap și throttle la 1200ms pentru zero latență în bucla de reflexe)
        val nowMs = System.currentTimeMillis()
        val base64Thumb = if (bitmap === lastThumbBitmapRef && lastCachedBase64Thumb.isNotEmpty()) {
            lastCachedBase64Thumb
        } else if (nowMs - lastThumbTimestampMs < 1200L && lastCachedBase64Thumb.isNotEmpty()) {
            lastCachedBase64Thumb
        } else {
            val encoded = encodeThumbnailBase64(bitmap)
            lastThumbBitmapRef = bitmap
            lastCachedBase64Thumb = encoded
            lastThumbTimestampMs = nowMs
            encoded
        }
        val payloadJson = buildString {
            append("{")
            append("\"source\":\"$sourceName\",")
            append("\"game\":\"${activeGameTitle.ifBlank { "ExternalMobileGame" }}\",")
            append("\"mode\":\"${activeMode.name}\",")
            append("\"resolution\":\"${dispW}x${dispH}\",")
            append("\"orientation\":\"${if (isLand) "LANDSCAPE" else "PORTRAIT"}\",")
            append("\"gameState\":\"${gameState.name}\",")
            append("\"playerHpPercent\":${healthBars.playerHpPercent.roundToInt()},")
            append("\"enemyHpPercent\":${healthBars.enemyOrBossHpPercent.roundToInt()},")
            append("\"secondaryResourcePercent\":${healthBars.secondaryBarPercent.roundToInt()},")
            append("\"lowHealthAlert\":${healthBars.isLowHealthCritical},")
            append("\"buttons\":[")
            buttons.take(6).forEachIndexed { index, b ->
                if (index > 0) append(",")
                append("{\"type\":\"${b.category.name}\",\"label\":\"${b.label}\",\"x\":${b.centerX.toInt()},\"y\":${b.centerY.toInt()},\"conf\":${"%.2f".format(b.confidence)}}")
            }
            append("],")
            append("\"recommendedAction\":{\"label\":\"$actionLabel\",\"x\":${recommendedPoint?.first?.toInt() ?: -1},\"y\":${recommendedPoint?.second?.toInt() ?: -1}}")
            append("}")
        }

        val analysis = MediaProjectionVisionAnalysis(
            timestamp = nowMs,
            captureSource = sourceName,
            frameWidth = dispW,
            frameHeight = dispH,
            isLandscape = isLand,
            gameState = gameState,
            healthBars = healthBars,
            detectedButtons = buttons,
            recommendedTapTarget = recommendedPoint,
            recommendedActionLabel = actionLabel,
            aiVisionModelPromptPayload = payloadJson,
            aiVisionDirective = directive,
            base64ThumbnailPreview = base64Thumb,
            frameLumaDiff = lumaDiff
        )
        _latestVisionAnalysis.value = analysis
        return analysis
    }

    /**
     * Scanează zonele HUD pentru a măsura procentajul real al barelor de viață (Health Bars),
     * barelor de inamic/boss și barelor secundare (Scut, Armură, Elixir, Nitro).
     */
    private fun detectHealthAndResourceBars(
        bmp: Bitmap,
        bw: Int,
        bh: Int,
        isLand: Boolean
    ): DetectedHealthAndResourceBars {
        // 1. Bara de Viață a Jucătorului (Player HP Bar):
        //    - În PUBG / Free Fire / RPG: Stânga-Sus (x: 4%..34%, y: 4%..15%) sau Jos-Centru (x: 28%..72%, y: 88%..97%)
        val topLeftHp = scanHorizontalBarFill(
            bmp = bmp,
            x0Frac = 0.05f,
            x1Frac = 0.34f,
            y0Frac = 0.04f,
            y1Frac = 0.14f,
            targetColorMode = BarColorMode.PLAYER_HP_GREEN_WHITE_RED
        )
        val bottomCenterHp = scanHorizontalBarFill(
            bmp = bmp,
            x0Frac = 0.28f,
            x1Frac = 0.72f,
            y0Frac = 0.88f,
            y1Frac = 0.97f,
            targetColorMode = BarColorMode.PLAYER_HP_GREEN_WHITE_RED
        )

        val (playerHpPct, playerDetected, playerRegion) = when {
            topLeftHp.second >= bottomCenterHp.second && topLeftHp.second > 0.18f ->
                Triple(topLeftHp.first, true, "HUD Stânga-Sus")
            bottomCenterHp.second > 0.18f ->
                Triple(bottomCenterHp.first, true, "HUD Jos-Centru")
            else -> Triple(88f, false, "HUD Standard")
        }

        // 2. Bara de Viață Inamic / Boss / Scor Adversar (Sus-Centru: x: 30%..70%, y: 3%..14%)
        val enemyTopBar = scanHorizontalBarFill(
            bmp = bmp,
            x0Frac = 0.30f,
            x1Frac = 0.70f,
            y0Frac = 0.03f,
            y1Frac = 0.14f,
            targetColorMode = BarColorMode.ENEMY_HP_RED_ORANGE
        )

        // 3. Bara Secundară: Scut / Armură / Elixir (Clash) / Nitro (NFS/Asphalt)
        val secondaryBar = scanHorizontalBarFill(
            bmp = bmp,
            x0Frac = if (isLand) 0.62f else 0.22f,
            x1Frac = if (isLand) 0.94f else 0.78f,
            y0Frac = if (isLand) 0.05f else 0.90f,
            y1Frac = if (isLand) 0.16f else 0.98f,
            targetColorMode = BarColorMode.SECONDARY_BLUE_PURPLE_GOLD
        )

        val clampedPlayerHp = playerHpPct.coerceIn(5f, 100f)
        val clampedEnemyHp = if (enemyTopBar.second > 0.15f) enemyTopBar.first.coerceIn(5f, 100f) else 100f
        val clampedSecondary = if (secondaryBar.second > 0.15f) secondaryBar.first.coerceIn(10f, 100f) else 85f

        return DetectedHealthAndResourceBars(
            playerHpPercent = clampedPlayerHp,
            enemyOrBossHpPercent = clampedEnemyHp,
            secondaryBarPercent = clampedSecondary,
            isLowHealthCritical = playerDetected && clampedPlayerHp < 32f,
            playerBarDetected = playerDetected,
            enemyBarDetected = enemyTopBar.second > 0.15f,
            playerBarRegion = playerRegion
        )
    }

    private enum class BarColorMode {
        PLAYER_HP_GREEN_WHITE_RED,
        ENEMY_HP_RED_ORANGE,
        SECONDARY_BLUE_PURPLE_GOLD
    }

    /**
     * Scanează o regiune dreptunghiulară după o bară orizontală de status (viață / energie)
     * și returnează Pair(fillPercent 0..100, confidence 0..1).
     */
    private fun scanHorizontalBarFill(
        bmp: Bitmap,
        x0Frac: Float,
        x1Frac: Float,
        y0Frac: Float,
        y1Frac: Float,
        targetColorMode: BarColorMode
    ): Pair<Float, Float> {
        val bw = bmp.width
        val bh = bmp.height
        val x0 = (x0Frac * bw).toInt().coerceIn(0, bw - 2)
        val x1 = (x1Frac * bw).toInt().coerceIn(x0 + 1, bw - 1)
        val y0 = (y0Frac * bh).toInt().coerceIn(0, bh - 2)
        val y1 = (y1Frac * bh).toInt().coerceIn(y0 + 1, bh - 1)

        val cols = 20
        val rows = 6
        var bestRowConfidence = 0f
        var bestRowFillPercent = 100f

        for (r in 0 until rows) {
            val py = (y0 + (r + 0.5f) / rows * (y1 - y0)).toInt().coerceIn(0, bh - 1)
            var activeCols = 0
            var rightmostActiveCol = -1
            var darkTrackCols = 0

            for (c in 0 until cols) {
                val px = (x0 + (c + 0.5f) / cols * (x1 - x0)).toInt().coerceIn(0, bw - 1)
                val color = safeGetPixel(bmp, px, py)
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                val luma = (red * 30 + green * 59 + blue * 11) / 100

                val matchesBar = when (targetColorMode) {
                    BarColorMode.PLAYER_HP_GREEN_WHITE_RED ->
                        (green > 155 && red < 140) || // Verde clasic HP
                            (red > 190 && green < 85 && blue < 85) || // Roșu Low-HP
                            (red > 215 && green > 215 && blue > 215) // Alb PUBG HP bar
                    BarColorMode.ENEMY_HP_RED_ORANGE ->
                        (red > 180 && green < 110 && blue < 95) ||
                            (red > 210 && green in 90..170 && blue < 70)
                    BarColorMode.SECONDARY_BLUE_PURPLE_GOLD ->
                        (blue > 175 && green > 110) || // Cyan / Nitro / Scut
                            (red > 165 && blue > 165 && green < 120) || // Elixir Mov (Clash)
                            (red > 205 && green > 165 && blue < 90) // Boost Auriu
                }

                if (matchesBar) {
                    activeCols++
                    rightmostActiveCol = c
                } else if (luma in 15..85) {
                    darkTrackCols++
                }
            }

            if (activeCols >= 3) {
                val continuity = activeCols.toFloat() / (rightmostActiveCol + 1).coerceAtLeast(1)
                val trackScore = (activeCols + darkTrackCols).toFloat() / cols
                val conf = (continuity * 0.65f + trackScore * 0.35f)
                if (conf > bestRowConfidence) {
                    bestRowConfidence = conf
                    bestRowFillPercent = ((rightmostActiveCol + 1).toFloat() / cols * 100f).coerceIn(5f, 100f)
                }
            }
        }

        return bestRowFillPercent to bestRowConfidence
    }

    /**
     * Identifică butoanele interactive din UI-ul jocului (PLAY/START, CLOSE X, ATTACK/FIRE,
     * SKILL/NITRO/SCOPE, HEAL/MEDKIT, CONTINUE) folosind analiza de culoare HSV și densitatea de muchii.
     */
    private fun detectGameUiButtons(
        bmp: Bitmap,
        bw: Int,
        bh: Int,
        dispW: Int,
        dispH: Int,
        isLand: Boolean
    ): List<DetectedGameUiButton> {
        data class RegionSpec(
            val id: String,
            val category: GameUiButtonCategory,
            val label: String,
            val x0: Float,
            val x1: Float,
            val y0: Float,
            val y1: Float
        )

        val specs = if (isLand) {
            listOf(
                RegionSpec("btn_start_br", GameUiButtonCategory.START_PLAY_BUTTON, "START / BATTLE (Dreapta-Jos)", 0.70f, 0.96f, 0.70f, 0.94f),
                RegionSpec("btn_start_tl", GameUiButtonCategory.START_PLAY_BUTTON, "START Lobby (Stânga-Sus PUBG)", 0.04f, 0.25f, 0.06f, 0.24f),
                RegionSpec("btn_attack_bl", GameUiButtonCategory.START_PLAY_BUTTON, "ATTACK! (Stânga-Jos Clash/Strategie)", 0.04f, 0.16f, 0.80f, 0.95f),
                RegionSpec("btn_match_br", GameUiButtonCategory.START_PLAY_BUTTON, "Find Match / Atac (Dreapta-Jos)", 0.72f, 0.92f, 0.68f, 0.86f),
                RegionSpec("btn_play_bc", GameUiButtonCategory.START_PLAY_BUTTON, "PLAY / KICK-OFF (Jos-Centru)", 0.35f, 0.65f, 0.72f, 0.93f),
                RegionSpec("btn_return_bc", GameUiButtonCategory.CONTINUE_OR_CLAIM_BUTTON, "Return Home / Okay (Jos-Centru)", 0.38f, 0.62f, 0.78f, 0.94f),
                RegionSpec("btn_close_tr", GameUiButtonCategory.CLOSE_POPUP_X_BUTTON, "Închide Pop-up „X” (Dreapta-Sus)", 0.82f, 0.97f, 0.05f, 0.24f),
                RegionSpec("btn_attack_fire", GameUiButtonCategory.ATTACK_PRIMARY_BUTTON, "Atac / Foc / Șut (Dreapta-Jos)", 0.80f, 0.95f, 0.62f, 0.86f),
                RegionSpec("btn_skill_scope", GameUiButtonCategory.SKILL_OR_NITRO_BUTTON, "Scope / Abilitate / Pasă / Nitro", 0.72f, 0.94f, 0.44f, 0.64f),
                RegionSpec("btn_heal_medkit", GameUiButtonCategory.HEAL_OR_COVER_BUTTON, "Medkit / Heal / Scut", 0.66f, 0.80f, 0.78f, 0.95f),
                RegionSpec("btn_continue_tr", GameUiButtonCategory.CONTINUE_OR_CLAIM_BUTTON, "Continue / Next (FM / Meniu)", 0.82f, 0.97f, 0.08f, 0.24f)
            )
        } else {
            listOf(
                RegionSpec("btn_play_portrait", GameUiButtonCategory.START_PLAY_BUTTON, "PLAY / JOACĂ (Jos-Centru)", 0.24f, 0.76f, 0.68f, 0.88f),
                RegionSpec("btn_close_portrait", GameUiButtonCategory.CLOSE_POPUP_X_BUTTON, "Închide Pop-up „X” (Dreapta-Sus)", 0.80f, 0.96f, 0.08f, 0.22f),
                RegionSpec("btn_action_portrait", GameUiButtonCategory.ATTACK_PRIMARY_BUTTON, "Acțiune / Lansare Carte", 0.56f, 0.92f, 0.74f, 0.92f),
                RegionSpec("btn_continue_portrait", GameUiButtonCategory.CONTINUE_OR_CLAIM_BUTTON, "Continue / Claim (Centru)", 0.25f, 0.75f, 0.46f, 0.64f)
            )
        }

        val results = mutableListOf<DetectedGameUiButton>()
        val hsv = FloatArray(3)

        for (spec in specs) {
            val px0 = (spec.x0 * bw).toInt().coerceIn(0, bw - 2)
            val px1 = (spec.x1 * bw).toInt().coerceIn(px0 + 1, bw - 1)
            val py0 = (spec.y0 * bh).toInt().coerceIn(0, bh - 2)
            val py1 = (spec.y1 * bh).toInt().coerceIn(py0 + 1, bh - 1)

            val sx = ((px1 - px0) / 10).coerceAtLeast(2)
            val sy = ((py1 - py0) / 7).coerceAtLeast(2)

            var vibrantHits = 0
            var edgeHits = 0
            var samples = 0
            var sumX = 0f
            var sumY = 0f
            var sumW = 0f
            var hueSum = 0f

            var y = py0
            while (y <= py1) {
                var x = px0
                var prevL = -1
                while (x <= px1) {
                    val c = safeGetPixel(bmp, x, y)
                    Color.colorToHSV(c, hsv)
                    val sat = hsv[1]
                    val v = hsv[2]
                    val l = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
                    samples++

                    val isVibrant = (sat > 0.38f && v > 0.45f) || (v > 0.86f && sat < 0.20f)
                    val isEdge = prevL >= 0 && abs(l - prevL) > 45

                    if (isVibrant) {
                        vibrantHits++
                        val weight = sat + v
                        sumX += x * weight
                        sumY += y * weight
                        sumW += weight
                        hueSum += hsv[0]
                    }
                    if (isEdge) edgeHits++
                    prevL = l
                    x += sx
                }
                y += sy
            }

            if (samples > 0) {
                val vibRatio = vibrantHits.toFloat() / samples
                val edgeRatio = edgeHits.toFloat() / samples
                val conf = (vibRatio * 0.55f + edgeRatio * 0.75f).coerceIn(0f, 0.99f)
                if (vibRatio > 0.18f && edgeRatio > 0.06f && conf >= 0.28f) {
                    val cxBmp = if (sumW > 0f) sumX / sumW else (px0 + px1) / 2f
                    val cyBmp = if (sumW > 0f) sumY / sumW else (py0 + py1) / 2f
                    val screenX = (cxBmp / bw.toFloat()) * dispW
                    val screenY = (cyBmp / bh.toFloat()) * dispH
                    results.add(
                        DetectedGameUiButton(
                            id = spec.id,
                            category = spec.category,
                            label = spec.label,
                            centerX = screenX,
                            centerY = screenY,
                            widthPx = ((spec.x1 - spec.x0) * dispW).toInt(),
                            heightPx = ((spec.y1 - spec.y0) * dispH).toInt(),
                            confidence = conf,
                            dominantHue = if (vibrantHits > 0) hueSum / vibrantHits else 0f
                        )
                    )
                }
            }
        }

        return results.sortedByDescending { it.confidence }
    }

    private fun encodeThumbnailBase64(bmp: Bitmap): String {
        if (bmp.isRecycled) return ""
        return try {
            val targetW = 160
            val targetH = max(90, (bmp.height.toFloat() / bmp.width.coerceAtLeast(1) * targetW).toInt())
            val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
            try {
                val bos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 65, bos)
                Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            } finally {
                if (scaled !== bmp) scaled.recycle()
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun isScreenNearBlackOrBlank(bmp: Bitmap): Boolean {
        if (bmp.isRecycled) return false
        val bw = bmp.width
        val bh = bmp.height
        var totalLuma = 0L
        val samples = 36
        for (i in 0 until 6) {
            val py = ((i + 0.5f) / 6f * bh).toInt().coerceIn(0, bh - 1)
            for (j in 0 until 6) {
                val px = ((j + 0.5f) / 6f * bw).toInt().coerceIn(0, bw - 1)
                val c = safeGetPixel(bmp, px, py)
                val luma = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
                totalLuma += luma
            }
        }
        val avgLuma = totalLuma / samples
        return avgLuma < 14
    }
}

class EvaMediaProjectionService : Service() {

    companion object {
        private const val CHANNEL_ID = "eva1_media_projection_channel"
        private const val NOTIFICATION_ID = 42069

        const val ACTION_START_PROJECTION = "com.eva1.companion.START_MEDIA_PROJECTION"
        const val ACTION_STOP_PROJECTION = "com.eva1.companion.STOP_MEDIA_PROJECTION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun startServiceWithProjection(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, EvaMediaProjectionService::class.java).apply {
                action = ACTION_START_PROJECTION
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopProjectionService(context: Context) {
            val intent = Intent(context, EvaMediaProjectionService::class.java).apply {
                action = ACTION_STOP_PROJECTION
            }
            context.startService(intent)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var readerThread: HandlerThread? = null
    private var readerHandler: Handler? = null

    @Volatile
    private var currentFullW: Int = 1080

    @Volatile
    private var currentFullH: Int = 2400

    @Volatile
    private var currentDensity: Int = 420

    @Volatile
    private var lastFrameDeliveredMs: Long = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            cleanupProjectionResources()
            MediaProjectionVisionEngine.setProjectionActive(false)
            stopSelf()
        }
    }

    private val streamHeartbeatRunnable = object : Runnable {
        override fun run() {
            val handler = readerHandler ?: return
            if (mediaProjection == null || !MediaProjectionVisionEngine.isMediaProjectionActive.value) return
            try {
                val (liveW, liveH, liveDensity) = getLiveDisplayMetrics()
                if (liveW != currentFullW || liveH != currentFullH) {
                    rebuildImageReaderForResolution(liveW, liveH, liveDensity, handler)
                } else {
                    val now = System.currentTimeMillis()
                    // Când ecranul jocului este static (ex: Solitaire sau Puzzle așteptând mutarea AI),
                    // SurfaceFlinger nu trimite buffer nou; menținem fluxul video activ la 220ms!
                    if (now - lastFrameDeliveredMs >= 220L) {
                        val lastBmp = MediaProjectionVisionEngine.getLatestProjectedBitmap()
                        if (lastBmp != null && !lastBmp.isRecycled) {
                            lastFrameDeliveredMs = now
                            MediaProjectionVisionEngine.updateProjectedFrame(lastBmp, currentFullW, currentFullH)
                        }
                    }
                }
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 220L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val handler = readerHandler ?: return
        handler.post {
            try {
                val (liveW, liveH, liveDensity) = getLiveDisplayMetrics()
                if (liveW != currentFullW || liveH != currentFullH) {
                    rebuildImageReaderForResolution(liveW, liveH, liveDensity, handler)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_PROJECTION -> {
                cleanupProjectionResources()
                MediaProjectionVisionEngine.setProjectionActive(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_PROJECTION -> {
                createNotificationChannel()
                val notification = buildForegroundNotification()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultData != null) {
                    setupMediaProjectionPipeline(resultCode, resultData)
                }
            }
        }
        return START_STICKY
    }

    private fun getLiveDisplayMetrics(): Triple<Int, Int, Int> {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            val density = dm.densityDpi.coerceAtLeast(160)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                val w = bounds.width().coerceAtLeast(480)
                val h = bounds.height().coerceAtLeast(480)
                Triple(w, h, density)
            } else {
                Triple(dm.widthPixels.coerceAtLeast(480), dm.heightPixels.coerceAtLeast(480), density)
            }
        } catch (_: Exception) {
            val dm = resources.displayMetrics
            Triple(dm.widthPixels.coerceAtLeast(720), dm.heightPixels.coerceAtLeast(1280), dm.densityDpi.coerceAtLeast(160))
        }
    }

    private fun attachImageReaderListener(
        reader: ImageReader,
        capW: Int,
        capH: Int,
        fullW: Int,
        fullH: Int,
        handler: Handler
    ) {
        var lastProcessedTime = 0L
        reader.setOnImageAvailableListener({ ir ->
            val img = try { ir.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastProcessedTime >= 33L) { // 30 FPS în timp real
                    lastProcessedTime = now
                    lastFrameDeliveredMs = now
                    val planes = img.planes
                    if (planes.isNotEmpty()) {
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * capW

                        val paddedWidth = (capW + rowPadding / pixelStride.coerceAtLeast(1)).coerceAtLeast(capW)
                        val rawBitmap = Bitmap.createBitmap(paddedWidth, capH, Bitmap.Config.ARGB_8888)
                        buffer.rewind()
                        rawBitmap.copyPixelsFromBuffer(buffer)

                        val cleanBitmap = if (paddedWidth != capW) {
                            val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, capW, capH)
                            if (cropped !== rawBitmap) {
                                rawBitmap.recycle()
                            }
                            cropped
                        } else {
                            rawBitmap
                        }

                        MediaProjectionVisionEngine.updateProjectedFrame(cleanBitmap, fullW, fullH)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { img.close() } catch (_: Exception) {}
            }
        }, handler)
    }

    private fun rebuildImageReaderForResolution(
        newFullW: Int,
        newFullH: Int,
        newDensity: Int,
        handler: Handler
    ) {
        val vd = virtualDisplay ?: return
        try {
            currentFullW = newFullW
            currentFullH = newFullH
            currentDensity = newDensity
            val capW = (newFullW / 2).coerceAtLeast(240)
            val capH = (newFullH / 2).coerceAtLeast(240)

            val oldReader = imageReader
            val newReader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)
            attachImageReaderListener(newReader, capW, capH, newFullW, newFullH, handler)
            imageReader = newReader

            vd.resize(capW, capH, newDensity)
            vd.surface = newReader.surface
            try { oldReader?.close() } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun setupMediaProjectionPipeline(resultCode: Int, resultData: Intent) {
        cleanupProjectionResources()
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpm.getMediaProjection(resultCode, resultData) ?: return
            mediaProjection = projection

            val thread = HandlerThread("EvaMediaProjectionReader").apply { start() }
            readerThread = thread
            val handler = Handler(thread.looper)
            readerHandler = handler

            // Obligatoriu pe Android 14+ (API 34+): înregistrăm callback-ul înainte de createVirtualDisplay
            projection.registerCallback(projectionCallback, handler)

            val (fullW, fullH, density) = getLiveDisplayMetrics()
            currentFullW = fullW
            currentFullH = fullH
            currentDensity = density

            // Capturăm la jumătate din rezoluția nativă pentru procesare AI Vision rapidă și consum redus de RAM
            val capW = (fullW / 2).coerceAtLeast(240)
            val capH = (fullH / 2).coerceAtLeast(240)

            val reader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            attachImageReaderListener(reader, capW, capH, fullW, fullH, handler)

            virtualDisplay = projection.createVirtualDisplay(
                "EVA1_AI_Vision_Display",
                capW,
                capH,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )
            lastFrameDeliveredMs = System.currentTimeMillis()
            MediaProjectionVisionEngine.setProjectionActive(true)
            handler.postDelayed(streamHeartbeatRunnable, 220L)
        } catch (_: Exception) {
            MediaProjectionVisionEngine.setProjectionActive(false)
        }
    }

    private fun cleanupProjectionResources() {
        try { readerHandler?.removeCallbacksAndMessages(null) } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { mediaProjection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        try { readerThread?.quitSafely() } catch (_: Exception) {}
        readerThread = null
        readerHandler = null
    }

    override fun onDestroy() {
        cleanupProjectionResources()
        MediaProjectionVisionEngine.setProjectionActive(false)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EVA-1 MediaProjection AI Vision",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Analizează în timp real interfața jocurilor (butoane, bare de viață, stare joc) pentru EVA-1"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EVA-1 AI Vision & MediaProjection Activ")
            .setContentText("Analizează în timp real butoanele, barele de viață (HP) și starea jocului")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
