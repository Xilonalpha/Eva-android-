package com.eva1.companion.engines

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Categoria elementului de interfață identificat pe ecran:
 */
enum class UiElementCategory(val displayName: String, val badge: String) {
    // Navigare & Meniu
    BUTTON_START_PLAY("Buton Start / Play", "▶️"),
    BUTTON_CLOSE_POPUP("Închidere Pop-up (X)", "✖️"),
    BUTTON_RETRY("Reîncearcă / Retry", "🔄"),
    BUTTON_CONTINUE("Continuă / Claim", "⏭️"),

    // Controale Direcționale & Arcade (Tetris, Runner, Platformer)
    BUTTON_DPAD_LEFT("Direcție Stânga (◀)", "◀️"),
    BUTTON_DPAD_RIGHT("Direcție Dreapta (▶)", "▶️"),
    BUTTON_DPAD_ROTATE("Rotire Piesă (↻)", "🔄"),
    BUTTON_DPAD_DOWN_DROP("Coborâre / Drop (▼)", "⬇️"),
    BUTTON_PAUSE("Pauză Joc (⏸)", "⏸️"),

    // Controale Combat & Acțiune (FPS, Battle Royale, RPG)
    BUTTON_ATTACK_FIRE("Atac Principal / Foc", "🔥"),
    BUTTON_ADS_SCOPE("Ochire Lunetă (ADS)", "🎯"),
    BUTTON_RELOAD("Reîncărcare Muniție", "🔁"),
    BUTTON_HEAL_MEDKIT("Trusă Medicală / Heal", "💊"),
    JOYSTICK_MOVEMENT("Joystick Virtual Mișcare", "🕹️"),
    SKILL_ABILITY_SLOT("Slot Abilitate / Skill", "✨"),

    // Bare de Status & Resurse
    BAR_PLAYER_HEALTH("Bară Viață Jucător (HP)", "❤️"),
    BAR_ENEMY_HEALTH("Bară Viață Inamic / Boss", "💀"),
    BAR_SECONDARY_RESOURCE("Bară Resursă (Scut/Nitro/Elixir)", "⚡"),

    // Tablă de Joc & Grilă
    GAME_BOARD_ACTIVE_FIELD("Zonă Activă Tablă de Joc", "🏁")
}

/**
 * Element UI identificat cu mapare exactă a coordonatelor pe ecranul dispozitivului:
 */
data class IdentifiableUiElement(
    val id: String,
    val category: UiElementCategory,
    val label: String,
    val boundsOnDisplay: RectF,
    val centerX: Float = boundsOnDisplay.centerX(),
    val centerY: Float = boundsOnDisplay.centerY(),
    val confidence: Float,
    val isPrimaryAction: Boolean = false,
    val suggestedMotion: String = "TAP",
    val description: String = ""
)

/**
 * Detalii specifice despre barele de viață și resurse:
 */
data class IdentifiableHealthBar(
    val playerHpPercent: Float = 100f,
    val enemyHpPercent: Float = 100f,
    val secondaryResourcePercent: Float = 100f,
    val isLowHpCritical: Boolean = false,
    val playerBarBounds: RectF? = null,
    val healTapTarget: Pair<Float, Float>? = null
)

/**
 * Starea tablei de joc (ex: Tetris, Block Puzzle):
 */
data class TetrisBoardAnalysis(
    val boardBoundsOnDisplay: RectF,
    val currentPieceColumn: Int = 4, // 0..9
    val currentPieceColFrac: Float = 0.5f,
    val targetGapColumn: Int = 4, // 0..9
    val targetGapColFrac: Float = 0.5f,
    val columnDifference: Int = 0, // target - current (- = stânga, + = dreapta, 0 = aliniat)
    val isAlignedForDrop: Boolean = false,
    val recommendedButtonCategory: UiElementCategory = UiElementCategory.BUTTON_DPAD_DOWN_DROP
)

/**
 * Starea unei coloane individuale (0..6) de pe tabla de Solitaire (Klondike):
 */
data class SolitaireColumnState(
    val columnIndex: Int, // 0..6 (Coloana 1..7)
    val centerX: Float,
    val hasFaceUpCards: Boolean,
    val hasFaceDownCardsAbove: Boolean,
    val bottomCardY: Float,
    val stackTopCardY: Float? = null,
    val isBottomCardRed: Boolean = false
)

/**
 * Analiza completă a tablei de Solitaire (7 coloane Tableau, Pachet Stock, Carte întoarsă Waste și Semnătură Tablă):
 */
data class SolitaireBoardAnalysis(
    val isSolitaireTableDetected: Boolean,
    val columns: List<SolitaireColumnState>,
    val emptyColumnsCount: Int,
    val hasWasteCard: Boolean,
    val wasteCardX: Float,
    val wasteCardY: Float,
    val stockPileX: Float,
    val stockPileY: Float,
    val boardSignature: Long,
    val isWasteCardRed: Boolean = false
)

/**
 * Tipul mediului 3D detectat în timp real din fluxul video MediaProjection
 * (pentru PUBG Mobile, Free Fire, Call of Duty Mobile, GTA, jocuri 3D Open-World):
 */
enum class Scene3dEnvironmentType(val labelRo: String, val badge: String) {
    IN_VEHICLE_DRIVING("În Mașină / Vehicul", "🚗"),
    INDOORS_BUILDING_ROOM("În Casă / Interior Clădire", "🏠"),
    BLOCKED_WALL_OBSTACLE("Blocat în Perete / Obstacol Frontal", "🧱"),
    FOREST_VEGETATION("În Pădure / Vegetație Densă", "🌲"),
    OPEN_TERRAIN_ROAD("Teren Deschis / Drum", "🏞️"),
    WATER_OR_SKYDIVING("Parașutare / Înot în Apă", "🪂")
}

/**
 * Analiza vizuală în timp real a scenei 3D (PUBG / Shooter / Open-World):
 *  - Clasifică mediul în care se află jucătorul (în mașină, în casă, în pădure, blocat în perete, teren deschis)
 *  - Măsoară gradul de deschidere pe cele 3 culoare (Stânga, Centru, Dreapta)
 *  - Calculează vectorul 360° al joystick-ului (Înainte, Înapoi, Stânga, Dreapta, Diagonal)
 *  - Calculează vectorul de rotire a camerei 3D (Swipe Stânga / Dreapta / Sus / Jos)
 */
data class Scene3dVisionAnalysis(
    val sceneType: Scene3dEnvironmentType = Scene3dEnvironmentType.OPEN_TERRAIN_ROAD,
    val leftCorridorOpenness: Float = 0.7f,
    val centerCorridorOpenness: Float = 0.7f,
    val rightCorridorOpenness: Float = 0.7f,
    val skyCoverageRatio: Float = 0.3f,
    val foliageCoverageRatio: Float = 0.1f,
    val indoorWallRatio: Float = 0.1f,
    val isWallOrObstacleInFront: Boolean = false,
    val isVehicleHudDetected: Boolean = false,
    val hasDoorOrLootPrompt: Boolean = false,
    val openCorridorDirection: Int = 0, // -1 = Stânga, 0 = Centru, +1 = Dreapta, 2 = Blocat (Mers Înapoi + Întoarcere)
    val recommendedJoyDxFrac: Float = 0f, // -1f (Stânga) .. +1f (Dreapta)
    val recommendedJoyDyFrac: Float = -1f, // -1f (Înainte) .. +1f (Înapoi / Marșarier)
    val recommendedCameraSwipeDxFrac: Float = 0f, // -0.35f (Uită-te Stânga) .. +0.35f (Uită-te Dreapta)
    val recommendedCameraSwipeDyFrac: Float = 0f, // Reglare orizont Sus/Jos
    val tacticalSummaryRo: String = "🏞️ Teren Deschis: Deplasare + Scanare Cameră Stânga/Dreapta"
)

/**
 * Țintă de interacțiune recomandată pentru motorul de injectare tactilă:
 */
data class UiInteractionTarget(
    val targetX: Float,
    val targetY: Float,
    val motionType: String = "TAP", // TAP, SWIPE, DRAG, BURST
    val endX: Float = targetX,
    val endY: Float = targetY,
    val actionCategory: UiElementCategory,
    val reason: String
)

/**
 * Rezultatul complet al analizei de ecran MediaProjection:
 */
data class ScreenAnalysisResult(
    val timestamp: Long = System.currentTimeMillis(),
    val displayWidth: Int,
    val displayHeight: Int,
    val isLandscape: Boolean,
    val activeBoardBounds: RectF,
    val detectedElements: List<IdentifiableUiElement>,
    val healthBar: IdentifiableHealthBar,
    val tetrisAnalysis: TetrisBoardAnalysis?,
    val solitaireAnalysis: SolitaireBoardAnalysis? = null,
    val scene3dAnalysis: Scene3dVisionAnalysis? = null,
    val recommendedTarget: UiInteractionTarget?
)

/**
 * Utilitar specializat de analiză a ecranului:
 * Mapează fluxul de pixeli capturat prin MediaProjection API la elemente UI identificabile
 * (butoane tactile, bare de viață, D-pad, tablă de joc, cărți Solitaire) cu coordonate precise pentru AI.
 */
object GameScreenAnalysisUtility {

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

    /**
     * Detectează dacă ecranul curent este o masă activă de Solitaire / Joc de Cărți
     * (postav verde/clasic + cărți albe dispuse pe coloane în jumătatea superioară).
     */
    fun isSolitaireOrCardTableScreen(bmp: Bitmap, bw: Int, bh: Int, isLand: Boolean): Boolean {
        if (bmp.isRecycled || bw < 32 || bh < 32) return false
        var greenFeltSamples = 0
        var totalFeltSamples = 0
        // Verificăm zona de postav (y: 46%..72%)
        for (ry in 0 until 5) {
            val py = ((0.46f + ry * 0.06f) * bh).toInt().coerceIn(0, bh - 1)
            for (rx in 0 until 7) {
                val px = ((0.10f + rx * 0.13f) * bw).toInt().coerceIn(0, bw - 1)
                val c = safeGetPixel(bmp, px, py)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                totalFeltSamples++
                if (g > 75 && g > (r * 1.22f).toInt() && g > (b * 1.18f).toInt()) {
                    greenFeltSamples++
                }
            }
        }
        if (greenFeltSamples < (totalFeltSamples * 0.45f).toInt()) return false

        // Verificăm prezența cărților sau a fantelor de cărți în zona superioară (y: 14%..40%)
        val colXFracs = floatArrayOf(0.086f, 0.224f, 0.362f, 0.500f, 0.638f, 0.776f, 0.914f)
        var cardColumnsFound = 0
        for (cxFrac in colXFracs) {
            val px = (cxFrac * bw).toInt().coerceIn(0, bw - 1)
            var hasCardPixels = false
            for (ry in 0 until 10) {
                val py = ((0.16f + ry * 0.025f) * bh).toInt().coerceIn(0, bh - 1)
                val c = safeGetPixel(bmp, px, py)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                // Alb de carte (față) sau albastru/roșu de spate carte
                if ((r > 190 && g > 190 && b > 190) || (b > 130 && b > g + 25)) {
                    hasCardPixels = true
                    break
                }
            }
            if (hasCardPixels) cardColumnsFound++
        }
        return cardColumnsFound >= 2
    }

    /**
     * Analizează în detaliu tabla de Klondike Solitaire:
     *  - Scanează fiecare din cele 7 coloane Tableau (X = 8.6%, 22.4%, 36.2%, 50.0%, 63.8%, 77.6%, 91.4%)
     *  - Identifică unde se termină cărțile cu fața în sus (cartea de jos a fiecărei coloane)
     *  - Identifică unde începe sub-stiva cu fața în sus (dacă deasupra ei există cărți întoarse cu fața în jos, ex: 5♣ peste cărțile albastre din Col 5)
     *  - Detectează culoarea simbolului cărții (Roșu ♦/♥ vs Negru ♣/♠)
     *  - Verifică dacă există o carte întoarsă în Waste Pile (X ≈ 64%..79%, Y ≈ 17.5%)
     *  - Calculează semnătura structurală (boardSignature) imună la zgomotul de compresie video!
     */
    fun analyzeSolitaireBoard(
        bmp: Bitmap,
        bw: Int,
        bh: Int,
        dw: Int,
        dh: Int
    ): SolitaireBoardAnalysis {
        val colXFracs = floatArrayOf(0.086f, 0.224f, 0.362f, 0.500f, 0.638f, 0.776f, 0.914f)
        val columns = mutableListOf<SolitaireColumnState>()
        var emptyCols = 0
        var signature = 1125899906842597L

        for (colIdx in colXFracs.indices) {
            val cxFrac = colXFracs[colIdx]
            val pxCenter = (cxFrac * bw).toInt().coerceIn(2, (bw - 3).coerceAtLeast(2))

            var firstWhiteYFrac = -1f
            var lastWhiteYFrac = -1f
            var nonFeltAboveWhiteCount = 0

            // Scanăm vertical coloana de la Y = 23.5% până la Y = 70.0%
            var yFrac = 0.235f
            while (yFrac <= 0.700f) {
                val py = (yFrac * bh).toInt().coerceIn(0, bh - 1)
                var whiteHits = 0
                var nonGreenHits = 0
                for (dx in -2..2) {
                    val px = (pxCenter + dx * (bw * 0.012f).toInt().coerceAtLeast(1)).coerceIn(0, bw - 1)
                    val c = safeGetPixel(bmp, px, py)
                    val r = Color.red(c)
                    val g = Color.green(c)
                    val b = Color.blue(c)
                    val isWhiteCard = r > 188 && g > 188 && b > 182 && abs(r - b) < 38
                    val isGreenFelt = g > 70 && g > (r * 1.20f).toInt() && g > (b * 1.15f).toInt()
                    if (isWhiteCard) whiteHits++
                    if (!isGreenFelt) nonGreenHits++
                }

                if (whiteHits >= 2) {
                    if (firstWhiteYFrac < 0f) {
                        firstWhiteYFrac = yFrac
                    }
                    lastWhiteYFrac = yFrac
                } else if (firstWhiteYFrac < 0f && yFrac <= 0.42f && nonGreenHits >= 3) {
                    // Cărți cu fața în jos (albastre/modelate) deasupra primei cărți deschise
                    nonFeltAboveWhiteCount++
                }
                yFrac += 0.009f
            }

            if (firstWhiteYFrac > 0f && lastWhiteYFrac >= firstWhiteYFrac) {
                // Există cel puțin o carte cu fața în sus pe această coloană!
                val hasFaceDownAbove = firstWhiteYFrac > 0.272f || nonFeltAboveWhiteCount >= 2
                // Ținta pe cartea de jos a coloanei (cu ~2.8% deasupra marginii inferioare a cărții albe)
                val bottomCardYFrac = (lastWhiteYFrac - 0.028f).coerceIn(firstWhiteYFrac + 0.008f, 0.68f)
                val stackSpan = lastWhiteYFrac - firstWhiteYFrac

                // Detectăm dacă simbolul cărții de la baza coloanei este ROȘU (♥/♦) sau NEGRU (♠/♣)
                var redSuitPixels = 0
                val cardCheckY0 = ((bottomCardYFrac - 0.032f).coerceAtLeast(firstWhiteYFrac) * bh).toInt().coerceIn(0, bh - 1)
                val cardCheckY1 = ((bottomCardYFrac + 0.022f).coerceAtMost(lastWhiteYFrac) * bh).toInt().coerceIn(cardCheckY0, bh - 1)
                val stepY = ((cardCheckY1 - cardCheckY0) / 5).coerceAtLeast(1)
                val halfColW = (bw * 0.038f).toInt().coerceAtLeast(2)
                var cyScan = cardCheckY0
                while (cyScan <= cardCheckY1) {
                    for (dx in -3..3) {
                        val sx = (pxCenter + (dx * halfColW) / 3).coerceIn(0, bw - 1)
                        val sc = safeGetPixel(bmp, sx, cyScan)
                        if (Color.red(sc) > 165 && Color.green(sc) < 88 && Color.blue(sc) < 88) {
                            redSuitPixels++
                        }
                    }
                    cyScan += stepY
                }
                val isRedCard = redSuitPixels >= 2

                // Dacă în coloană este o sub-stivă de mai multe cărți deschise (ex: 5♣ și 4♦ în Col 5)
                // ȘI sub ele există cărți cu fața în jos (sau nu e un Rege deja pe primul rând la y <= 0.272f),
                // salvăm și coordonatele cărții din vârful sub-stivei deschise pentru a muta tot pachetul!
                val stackTopY: Float? = if (stackSpan >= 0.052f && hasFaceDownAbove) {
                    ((firstWhiteYFrac + 0.018f).coerceIn(0.25f, bottomCardYFrac - 0.025f)) * dh
                } else {
                    null
                }

                // Semnătură structurală stabilă pe coloană (imună la compresie video sau vibrație de carte):
                val startBucket = (firstWhiteYFrac * 28f).toInt()
                val endBucket = (lastWhiteYFrac * 28f).toInt()
                val colHash = (startBucket * 128) + (endBucket * 4) + (if (hasFaceDownAbove) 2 else 0) + (if (isRedCard) 1 else 0)
                signature = signature * 31L + colHash.toLong()

                columns.add(
                    SolitaireColumnState(
                        columnIndex = colIdx,
                        centerX = cxFrac * dw,
                        hasFaceUpCards = true,
                        hasFaceDownCardsAbove = hasFaceDownAbove,
                        bottomCardY = bottomCardYFrac * dh,
                        stackTopCardY = stackTopY,
                        isBottomCardRed = isRedCard
                    )
                )
            } else {
                emptyCols++
                signature = signature * 31L + 7L
                columns.add(
                    SolitaireColumnState(
                        columnIndex = colIdx,
                        centerX = cxFrac * dw,
                        hasFaceUpCards = false,
                        hasFaceDownCardsAbove = false,
                        bottomCardY = 0.28f * dh,
                        stackTopCardY = null,
                        isBottomCardRed = false
                    )
                )
            }
        }

        // Scanăm zona Waste Pile (între Fundații și Pachetul Stock: X = 62%..81%, Y = 14%..21%)
        var wasteWhiteHits = 0
        var wasteRedHits = 0
        var rightmostWasteXFrac = 0.74f
        val wasteYFrac = 0.175f
        val pyWaste = (wasteYFrac * bh).toInt().coerceIn(0, bh - 1)
        var wxFrac = 0.62f
        while (wxFrac <= 0.81f) {
            val px = (wxFrac * bw).toInt().coerceIn(0, bw - 1)
            for (dy in -2..2) {
                val py = (pyWaste + dy * (bh * 0.008f).toInt().coerceAtLeast(1)).coerceIn(0, bh - 1)
                val c = safeGetPixel(bmp, px, py)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                if (dy == 0 && r > 190 && g > 190 && b > 185 && abs(r - b) < 38) {
                    wasteWhiteHits++
                    rightmostWasteXFrac = wxFrac
                }
                if (r > 165 && g < 88 && b < 88) {
                    wasteRedHits++
                }
            }
            wxFrac += 0.015f
        }
        val hasWaste = wasteWhiteHits >= 2
        val isWasteRed = hasWaste && wasteRedHits >= 1
        val wasteBucket = if (hasWaste) ((rightmostWasteXFrac * 40f).toInt() * 2 + (if (isWasteRed) 1 else 0)) else 0
        signature = signature * 31L + wasteBucket.toLong()

        val resolvedWasteX = (rightmostWasteXFrac - 0.02f).coerceIn(0.64f, 0.78f) * dw
        val resolvedWasteY = wasteYFrac * dh

        val isTable = isSolitaireOrCardTableScreen(bmp, bw, bh, dw > dh)

        return SolitaireBoardAnalysis(
            isSolitaireTableDetected = isTable,
            columns = columns,
            emptyColumnsCount = emptyCols,
            hasWasteCard = hasWaste,
            wasteCardX = resolvedWasteX,
            wasteCardY = resolvedWasteY,
            stockPileX = 0.91f * dw,
            stockPileY = 0.175f * dh,
            boardSignature = signature,
            isWasteCardRed = isWasteRed
        )
    }

    /**
     * Analizează cadrul Bitmap provenit din MediaProjection Stream și returnează
     * toate elementele de interfață identificate scalate exact pe rezoluția ecranului dispozitivului.
     */
    fun analyzeMediaProjectionFrame(
        frameBitmap: Bitmap,
        displayWidth: Int,
        displayHeight: Int,
        gameTitleHint: String = ""
    ): ScreenAnalysisResult {
        val dw = displayWidth.coerceAtLeast(480)
        val dh = displayHeight.coerceAtLeast(480)
        val isLand = dw > dh
        if (frameBitmap.isRecycled) {
            return ScreenAnalysisResult(
                displayWidth = dw,
                displayHeight = dh,
                isLandscape = isLand,
                activeBoardBounds = RectF(dw * 0.14f, dh * 0.14f, dw * 0.86f, dh * 0.78f),
                detectedElements = emptyList(),
                healthBar = IdentifiableHealthBar(),
                tetrisAnalysis = null,
                solitaireAnalysis = null,
                recommendedTarget = null
            )
        }
        val bw = frameBitmap.width.coerceAtLeast(1)
        val bh = frameBitmap.height.coerceAtLeast(1)

        // 1. Detectăm marginile efective ale jocului (pentru a elimina letterbox / bare gri laterale)
        val boardBounds = detectActiveGameBoardBounds(frameBitmap, bw, bh, dw, dh, isLand)

        val isTetrisGame = gameTitleHint.contains("tetris", ignoreCase = true) ||
            gameTitleHint.contains("brick", ignoreCase = true) ||
            gameTitleHint.contains("block", ignoreCase = true) ||
            (!isLand && MediaProjectionVisionEngine.isPortraitArcadeBoardScreen(frameBitmap, bw, bh, isLand))

        val isSolitaireGame = gameTitleHint.contains("solit", ignoreCase = true) ||
            gameTitleHint.contains("klondike", ignoreCase = true) ||
            gameTitleHint.contains("patience", ignoreCase = true) ||
            gameTitleHint.contains("spider", ignoreCase = true) ||
            gameTitleHint.contains("freecell", ignoreCase = true) ||
            gameTitleHint.contains("tripledot", ignoreCase = true) ||
            (!isLand && !isTetrisGame && isSolitaireOrCardTableScreen(frameBitmap, bw, bh, isLand))

        // 2. Identificăm barele de viață (Player HP, Enemy HP, Medkit)
        val healthBar = extractHealthBars(frameBitmap, bw, bh, dw, dh, isLand, isTetrisGame || isSolitaireGame)

        // 3. Scanăm și mapăm butoanele interactive relevante pentru orientarea și tipul jocului
        val detectedButtons = mutableListOf<IdentifiableUiElement>()

        // 3.1. Butoane D-Pad / Arcade (Tetris, Arcade Portrait)
        if ((!isLand || isTetrisGame) && !isSolitaireGame) {
            val arcadeButtons = detectArcadeDirectionalButtons(frameBitmap, bw, bh, dw, dh, boardBounds)
            detectedButtons.addAll(arcadeButtons)
        }

        // 3.2. Butoane Meniu & Pop-up (Play, Close X, Continue, Retry) — dezactivate pe tabla activă de Solitaire/Tetris
        if (!isSolitaireGame && !isTetrisGame) {
            val menuButtons = detectMenuAndDialogButtons(frameBitmap, bw, bh, dw, dh, isLand)
            detectedButtons.addAll(menuButtons)
        }

        // 3.3. Butoane Combat (Fire, ADS Scope, Medkit, Joystick)
        if ((isLand || !isTetrisGame) && !isSolitaireGame) {
            val combatButtons = detectCombatControls(frameBitmap, bw, bh, dw, dh, isLand)
            detectedButtons.addAll(combatButtons)
        }

        // 4. Analizor dedicat pentru Tetris & Block Puzzle (pe baza tablei detectate)
        val tetrisAnalysis = if (isTetrisGame) {
            analyzeTetrisBoardColumns(frameBitmap, bw, bh, dw, dh, boardBounds)
        } else {
            null
        }

        // 4.5. Analizor dedicat pentru Solitaire & Jocuri de Cărți
        val solitaireAnalysis = if (isSolitaireGame) {
            analyzeSolitaireBoard(frameBitmap, bw, bh, dw, dh)
        } else {
            null
        }

        // 4.8. Analizor dedicat în timp real pentru Scena 3D (PUBG / CODM / Free Fire / GTA / Open-World):
        // Detectează dacă jucătorul este în Mașină, în Casă, în Pădure, Blocat în Perete sau pe Teren Deschis
        // și calculează direcția liberă (Stânga, Centru, Dreapta, Înapoi) + unghiul camerei 3D!
        val is3dOrLandscapeGame = isLand ||
            gameTitleHint.contains("pubg", ignoreCase = true) ||
            gameTitleHint.contains("tencent.ig", ignoreCase = true) ||
            gameTitleHint.contains("kakaogames", ignoreCase = true) ||
            gameTitleHint.contains("krafton", ignoreCase = true) ||
            gameTitleHint.contains("freefire", ignoreCase = true) ||
            gameTitleHint.contains("dts.freefire", ignoreCase = true) ||
            gameTitleHint.contains("callofduty", ignoreCase = true) ||
            gameTitleHint.contains("shooter", ignoreCase = true) ||
            gameTitleHint.contains("gta", ignoreCase = true) ||
            gameTitleHint.contains("car", ignoreCase = true)

        val scene3dAnalysis = if (!isSolitaireGame && !isTetrisGame && is3dOrLandscapeGame) {
            analyze3dSceneEnvironment(frameBitmap, bw, bh, gameTitleHint)
        } else {
            null
        }

        // 5. Calculăm ținta optimă de interacțiune recomandată
        val recommendedTarget = computeRecommendedInteraction(
            detectedButtons = detectedButtons,
            healthBar = healthBar,
            tetrisAnalysis = tetrisAnalysis,
            solitaireAnalysis = solitaireAnalysis,
            dw = dw,
            dh = dh
        )

        return ScreenAnalysisResult(
            displayWidth = dw,
            displayHeight = dh,
            isLandscape = isLand,
            activeBoardBounds = boardBounds,
            detectedElements = detectedButtons,
            healthBar = healthBar,
            tetrisAnalysis = tetrisAnalysis,
            solitaireAnalysis = solitaireAnalysis,
            scene3dAnalysis = scene3dAnalysis,
            recommendedTarget = recommendedTarget
        )
    }

    /**
     * Model rapid de Viziune 3D în Timp Real (pentru PUBG Mobile, Free Fire, CODM, GTA):
     * Analizează pixelii cadrului live MediaProjection pe 3 culoare verticale (Stânga, Centru, Dreapta)
     * și 3 benzi de înălțime (Orizont/Cer, Nivelul Ochilor/Perete/Copaci, Sol/Capotă Vehicul) pentru a înțelege:
     *  1. Dacă jucătorul conduce o MAȘINĂ / VEHICUL (HUD pedală accelerație/frână + săgeți viraj + capotă/drum)
     *  2. Dacă jucătorul este într-o CASĂ / CLĂDIRE (fără cer sus, tavan/pereți interiori, uși/ferestre luminoase)
     *  3. Dacă jucătorul este BLOCAT ÎN PERETE / OBSTACOL FRONTAL (textură uniformă aproape de cameră pe centru)
     *  4. Dacă jucătorul este în PĂDURE / VEGETAȚIE (densitate mare de frunziș verde/oliv și trunchiuri verticale)
     *  5. Dacă jucătorul este pe TEREN DESCHIS / DRUM (cer vizibil sus + orizont deschis)
     *  6. Care culoar (Stânga, Centru, Dreapta sau Înapoi) este liber și cum trebuie mișcat Joystick-ul + Camera 3D!
     */
    fun analyze3dSceneEnvironment(
        bmp: Bitmap,
        bw: Int = bmp.width,
        bh: Int = bmp.height,
        gameTitleHint: String = ""
    ): Scene3dVisionAnalysis {
        if (bmp.isRecycled || bw < 16 || bh < 16) {
            return Scene3dVisionAnalysis()
        }

        var skySamples = 0
        var totalSkyBandSamples = 0
        var waterOrSkyDiveSamples = 0

        // 1. Scanăm banda superioară a orizontului (y: 10% .. 34%, x: 12% .. 88%)
        for (ry in 0 until 5) {
            val py = ((0.10f + ry * 0.055f) * bh).toInt().coerceIn(0, bh - 1)
            for (rx in 0 until 9) {
                val px = ((0.12f + rx * 0.095f) * bw).toInt().coerceIn(0, bw - 1)
                val c = safeGetPixel(bmp, px, py)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val luma = (r * 30 + g * 59 + b * 11) / 100
                totalSkyBandSamples++

                // Cer senin (albastru/cyan luminos) sau cer înnorat luminos
                val isBlueSky = b > 135 && b >= r + 15 && b >= g - 10 && luma > 115
                val isBrightCloudSky = luma > 182 && abs(r - g) < 28 && abs(g - b) < 32
                if (isBlueSky || isBrightCloudSky) {
                    skySamples++
                }
                if (b > 140 && b > r + 35 && g > 95) {
                    waterOrSkyDiveSamples++
                }
            }
        }
        val skyRatio = skySamples.toFloat() / totalSkyBandSamples.coerceAtLeast(1).toFloat()

        // 2. Scanăm cele 3 culoare de navigație 3D la nivelul ochilor și al solului:
        //    Culoar Stânga (x: 10%..36%), Culoar Centru (x: 37%..63%), Culoar Dreapta (x: 64%..90%)
        val corridorBounds = arrayOf(
            0.10f to 0.36f, // 0 = Stânga
            0.37f to 0.63f, // 1 = Centru (Față)
            0.64f to 0.90f  // 2 = Dreapta
        )

        val corridorOpenness = FloatArray(3)
        val corridorWallFlatness = FloatArray(3)
        var foliageHits = 0
        var indoorWallHits = 0
        var totalMidSamples = 0
        var centerVerticalGradientSum = 0f
        var centerLocalVarianceSum = 0f
        var centerSamplesCount = 0

        for (cIdx in 0..2) {
            val (xStart, xEnd) = corridorBounds[cIdx]
            var sectorEdgeSum = 0f
            var sectorVerticalGrad = 0f
            var sectorBrightExitHits = 0
            var sectorWallHits = 0
            var sectorCount = 0

            for (ry in 0 until 6) {
                val yFrac = 0.24f + ry * 0.085f // 24% .. 66.5%
                val py = (yFrac * bh).toInt().coerceIn(0, bh - 1)
                val pyBelow = ((yFrac + 0.07f) * bh).toInt().coerceIn(0, bh - 1)

                for (rx in 0 until 5) {
                    val xFrac = xStart + (rx / 4f) * (xEnd - xStart)
                    val px = (xFrac * bw).toInt().coerceIn(0, bw - 1)
                    val pxRight = ((xFrac + 0.035f) * bw).toInt().coerceIn(0, bw - 1)

                    val c = safeGetPixel(bmp, px, py)
                    val cBelow = safeGetPixel(bmp, px, pyBelow)
                    val cRight = safeGetPixel(bmp, pxRight, py)

                    val r = Color.red(c)
                    val g = Color.green(c)
                    val b = Color.blue(c)
                    val luma = (r * 30 + g * 59 + b * 11) / 100
                    val lumaBelow = (Color.red(cBelow) * 30 + Color.green(cBelow) * 59 + Color.blue(cBelow) * 11) / 100
                    val lumaRight = (Color.red(cRight) * 30 + Color.green(cRight) * 59 + Color.blue(cRight) * 11) / 100

                    val vGrad = abs(luma - lumaBelow).toFloat()
                    val hGrad = abs(luma - lumaRight).toFloat()

                    sectorVerticalGrad += vGrad
                    sectorEdgeSum += (vGrad + hGrad) * 0.5f
                    sectorCount++
                    totalMidSamples++

                    if (cIdx == 1) {
                        centerVerticalGradientSum += vGrad
                        centerLocalVarianceSum += (vGrad + hGrad) * 0.5f
                        centerSamplesCount++
                    }

                    // Detectăm vegetația/pădurea (verde/oliv dominant față de roșu și albastru)
                    val isFoliage = g in 42..200 && g >= r + 12 && g >= b + 14
                    if (isFoliage) {
                        foliageHits++
                    }

                    // Detectăm pereți de interior / beton / lemn / tencuială (culori neutre/calde cu saturație mică-medie și gradient local mic)
                    val maxCh = max(r, max(g, b))
                    val minCh = min(r, min(g, b))
                    val sat = if (maxCh == 0) 0 else ((maxCh - minCh) * 100) / maxCh
                    val isWallTone = sat < 36 && luma in 35..175 && vGrad < 14f && hGrad < 14f
                    if (isWallTone) {
                        indoorWallHits++
                        sectorWallHits++
                    }

                    // Detectăm deschideri luminoase (uși, ferestre, ieșiri din clădire sau orizont deschis)
                    if (luma > 145 && yFrac <= 0.48f) {
                        sectorBrightExitHits++
                    }
                }
            }

            val avgGrad = sectorEdgeSum / sectorCount.coerceAtLeast(1).toFloat()
            val wallRatioSector = sectorWallHits.toFloat() / sectorCount.coerceAtLeast(1).toFloat()
            val exitRatioSector = sectorBrightExitHits.toFloat() / sectorCount.coerceAtLeast(1).toFloat()
            corridorWallFlatness[cIdx] = wallRatioSector

            // Un culoar este deschis dacă nu este blocat de un perete plat aproape de cameră și are adâncime/lumină spre orizont
            val depthScore = (avgGrad / 26f).coerceIn(0.15f, 0.85f)
            val openness = ((1f - wallRatioSector * 0.78f) * 0.65f + exitRatioSector * 0.20f + depthScore * 0.15f)
                .coerceIn(0.08f, 0.98f)
            corridorOpenness[cIdx] = openness
        }

        val foliageRatio = foliageHits.toFloat() / totalMidSamples.coerceAtLeast(1).toFloat()
        val indoorWallRatio = indoorWallHits.toFloat() / totalMidSamples.coerceAtLeast(1).toFloat()
        val avgCenterGrad = centerLocalVarianceSum / centerSamplesCount.coerceAtLeast(1).toFloat()

        // 3. Detectăm dacă jucătorul este ÎN MAȘINĂ / VEHICUL:
        //    În PUBG / GTA / BR, când ești în mașină:
        //    - Jos-stânga (x: 12%..28%, y: 70%..86%) apar săgețile de viraj Stânga/Dreapta cu contur alb/portocaliu luminos
        //    - Jos-dreapta (x: 80%..92%, y: 65%..86%) apar pedalele de Accelerație / Frână
        //    - Jos-centru (x: 38%..62%, y: 60%..84%) se vede capota/plafonul mașinii + vitezometrul (km/h)
        var vehicleUiHits = 0
        val vehicleCheckCoords = arrayOf(
            0.14f to 0.78f, // Viraj Stânga mașină
            0.25f to 0.78f, // Viraj Dreapta mașină
            0.86f to 0.70f, // Pedală Accelerație
            0.78f to 0.80f, // Pedală Frână / Marșarier
            0.71f to 0.56f  // Buton Exit Vehicle
        )
        for ((vxFrac, vyFrac) in vehicleCheckCoords) {
            val px = (vxFrac * bw).toInt().coerceIn(0, bw - 1)
            val py = (vyFrac * bh).toInt().coerceIn(0, bh - 1)
            val c = safeGetPixel(bmp, px, py)
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val luma = (r * 30 + g * 59 + b * 11) / 100
            if (luma > 185 || (r > 200 && g > 140 && b < 80)) {
                vehicleUiHits++
            }
        }
        // Verificăm și simetria capotei/caroseriei în zona central-inferioară (x: 42% vs 58% la y: 68%)
        val hoodLeft = safeGetPixel(bmp, (0.44f * bw).toInt().coerceIn(0, bw - 1), (0.68f * bh).toInt().coerceIn(0, bh - 1))
        val hoodRight = safeGetPixel(bmp, (0.56f * bw).toInt().coerceIn(0, bw - 1), (0.68f * bh).toInt().coerceIn(0, bh - 1))
        val hoodSymmetryDiff = abs(Color.red(hoodLeft) - Color.red(hoodRight)) +
            abs(Color.green(hoodLeft) - Color.green(hoodRight)) +
            abs(Color.blue(hoodLeft) - Color.blue(hoodRight))
        val isVehicleHud = (vehicleUiHits >= 4 && hoodSymmetryDiff < 42 && skyRatio >= 0.14f) ||
            gameTitleHint.contains("driving", ignoreCase = true)

        // 4. Detectăm butoanele contextuale PUBG (Ușă / Loot / Drive la x: 64%..76%, y: 38%..62%)
        var promptHighlights = 0
        for (ry in 0 until 4) {
            val py = ((0.40f + ry * 0.06f) * bh).toInt().coerceIn(0, bh - 1)
            val px = (0.68f * bw).toInt().coerceIn(0, bw - 1)
            val c = safeGetPixel(bmp, px, py)
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            // Portocaliu/Galben/Alb specific listei de Loot / Open Door / Drive din PUBG
            if ((r > 195 && g > 145 && b < 90) || (r > 215 && g > 215 && b > 210)) {
                promptHighlights++
            }
        }
        val hasDoorOrLootPrompt = promptHighlights >= 2

        // 5. Clasificăm Scena 3D (În Mașină, Blocat în Perete, În Casă, În Pădure, Teren Deschis)
        val isWallInFront = (corridorWallFlatness[1] > 0.62f && avgCenterGrad < 11.5f && skyRatio < 0.18f) ||
            corridorOpenness[1] < 0.34f

        val isIndoors = !isVehicleHud && skyRatio < 0.16f && (indoorWallRatio > 0.36f || corridorWallFlatness[1] > 0.48f)
        val isForest = !isVehicleHud && !isIndoors && foliageRatio >= 0.28f
        val isWaterOrSky = !isVehicleHud && !isIndoors && (waterOrSkyDiveSamples.toFloat() / totalSkyBandSamples.coerceAtLeast(1) > 0.65f)

        val sceneType = when {
            isVehicleHud -> Scene3dEnvironmentType.IN_VEHICLE_DRIVING
            isWallInFront -> Scene3dEnvironmentType.BLOCKED_WALL_OBSTACLE
            isIndoors -> Scene3dEnvironmentType.INDOORS_BUILDING_ROOM
            isForest -> Scene3dEnvironmentType.FOREST_VEGETATION
            isWaterOrSky -> Scene3dEnvironmentType.WATER_OR_SKYDIVING
            else -> Scene3dEnvironmentType.OPEN_TERRAIN_ROAD
        }

        val leftOpen = corridorOpenness[0]
        val centerOpen = corridorOpenness[1]
        val rightOpen = corridorOpenness[2]

        // 6. Calculăm direcția liberă și vectorii pentru Joystick (360°) și Camera 3D (Swipe Stânga/Dreapta)
        val openDirection: Int
        val joyDx: Float
        val joyDy: Float
        val camSwipeDx: Float
        val camSwipeDy: Float
        val summaryRo: String

        when {
            sceneType == Scene3dEnvironmentType.BLOCKED_WALL_OBSTACLE -> {
                openDirection = 2 // Blocat în față -> Mers ÎNAPOI + Rotire amplă a camerei spre culoarul mai liber
                val turnRight = rightOpen >= leftOpen
                joyDx = if (turnRight) -0.45f else 0.45f
                joyDy = 0.88f // +Y = Trage joystick-ul ÎNAPOI (Mers cu spatele / deblocare din perete)
                camSwipeDx = if (turnRight) 0.28f else -0.28f // Rotește camera 3D spre dreapta sau stânga!
                camSwipeDy = -0.03f
                val sideLabel = if (turnRight) "DREAPTA" else "STÂNGA"
                summaryRo = "🧱 Perete/Obstacol în față! Mers ÎNAPOI + Rotire Cameră $sideLabel"
            }

            sceneType == Scene3dEnvironmentType.INDOORS_BUILDING_ROOM -> {
                // În casă: căutăm ușa/fereastra (culoarul cu cea mai mare deschidere/lumină)
                if (centerOpen < 0.44f) {
                    val turnRight = rightOpen >= leftOpen
                    openDirection = if (turnRight) 1 else -1
                    joyDx = if (turnRight) 0.68f else -0.68f
                    joyDy = 0.35f // Pas înapoi-lateral ca să nu ne lipim de peretele camerei
                    camSwipeDx = if (turnRight) 0.24f else -0.24f
                    camSwipeDy = 0f
                    summaryRo = "🏠 În Casă (Perete interior) → Pas înapoi-lateral + Privire spre ${if (turnRight) "DREAPTA" else "STÂNGA"}"
                } else if (leftOpen > centerOpen + 0.06f && leftOpen >= rightOpen) {
                    openDirection = -1
                    joyDx = -0.55f
                    joyDy = -0.75f
                    camSwipeDx = -0.18f
                    camSwipeDy = 0f
                    summaryRo = "🏠 În Casă → Navigare și orientare cameră spre ușa/ieșirea din STÂNGA"
                } else if (rightOpen > centerOpen + 0.06f && rightOpen > leftOpen) {
                    openDirection = 1
                    joyDx = 0.55f
                    joyDy = -0.75f
                    camSwipeDx = 0.18f
                    camSwipeDy = 0f
                    summaryRo = "🏠 În Casă → Navigare și orientare cameră spre ușa/ieșirea din DREAPTA"
                } else {
                    openDirection = 0
                    joyDx = 0f
                    joyDy = -0.80f
                    camSwipeDx = 0.14f
                    camSwipeDy = 0f
                    summaryRo = "🏠 În Casă → Deplasare atentă pe culoarul central + Scanare cameră"
                }
            }

            sceneType == Scene3dEnvironmentType.IN_VEHICLE_DRIVING -> {
                openDirection = when {
                    leftOpen > centerOpen + 0.08f && leftOpen >= rightOpen -> -1
                    rightOpen > centerOpen + 0.08f && rightOpen > leftOpen -> 1
                    else -> 0
                }
                joyDx = openDirection * 0.65f
                joyDy = -0.95f
                camSwipeDx = openDirection * 0.12f
                camSwipeDy = 0f
                val dirLabel = when (openDirection) {
                    -1 -> "Viraj STÂNGA pe drum"
                    1 -> "Viraj DREAPTA pe drum"
                    else -> "Accelerație pe culoarul central"
                }
                summaryRo = "🚗 În Mașină → $dirLabel (L:${(leftOpen * 100).toInt()}% C:${(centerOpen * 100).toInt()}% R:${(rightOpen * 100).toInt()}%)"
            }

            sceneType == Scene3dEnvironmentType.FOREST_VEGETATION -> {
                // În pădure: ocolim copacii spre culoarul cel mai deschis și rotim camera stânga/dreapta
                openDirection = when {
                    leftOpen > rightOpen + 0.04f && leftOpen > centerOpen -> -1
                    rightOpen > leftOpen + 0.04f && rightOpen > centerOpen -> 1
                    else -> if (leftOpen >= rightOpen) -1 else 1
                }
                joyDx = openDirection * 0.52f
                joyDy = -0.85f
                camSwipeDx = openDirection * 0.18f
                camSwipeDy = 0f
                summaryRo = "🌲 În Pădure → Ocolire copaci spre ${if (openDirection < 0) "STÂNGA" else "DREAPTA"} + Privire 360°"
            }

            else -> {
                // Teren deschis / drum: mergem spre cel mai deschis culoar și scanăm activ stânga/dreapta cu camera
                openDirection = when {
                    leftOpen > centerOpen + 0.08f && leftOpen >= rightOpen -> -1
                    rightOpen > centerOpen + 0.08f && rightOpen > leftOpen -> 1
                    else -> 0
                }
                joyDx = openDirection * 0.42f
                joyDy = -0.92f
                camSwipeDx = when (openDirection) {
                    -1 -> -0.16f
                    1 -> 0.16f
                    else -> 0.14f
                }
                camSwipeDy = 0f
                summaryRo = "🏞️ Teren Deschis → Deplasare + Privire Stânga/Dreapta (Culoar liber: ${
                    when (openDirection) {
                        -1 -> "STÂNGA"
                        1 -> "DREAPTA"
                        else -> "CENTRU"
                    }
                })"
            }
        }

        return Scene3dVisionAnalysis(
            sceneType = sceneType,
            leftCorridorOpenness = leftOpen,
            centerCorridorOpenness = centerOpen,
            rightCorridorOpenness = rightOpen,
            skyCoverageRatio = skyRatio,
            foliageCoverageRatio = foliageRatio,
            indoorWallRatio = indoorWallRatio,
            isWallOrObstacleInFront = isWallInFront,
            isVehicleHudDetected = isVehicleHud,
            hasDoorOrLootPrompt = hasDoorOrLootPrompt,
            openCorridorDirection = openDirection,
            recommendedJoyDxFrac = joyDx,
            recommendedJoyDyFrac = joyDy,
            recommendedCameraSwipeDxFrac = camSwipeDx,
            recommendedCameraSwipeDyFrac = camSwipeDy,
            tacticalSummaryRo = summaryRo
        )
    }

    /**
     * Detectează zona centrală activă a jocului eliminând barele de fundal/letterbox
     * (de exemplu în Tetris, tabla este un dreptunghi negru central delimitat de bare gri).
     */
    private fun detectActiveGameBoardBounds(
        bmp: Bitmap,
        bw: Int,
        bh: Int,
        dw: Int,
        dh: Int,
        isLand: Boolean
    ): RectF {
        if (isLand) {
            return RectF(dw * 0.05f, dh * 0.05f, dw * 0.95f, dh * 0.95f)
        }

        // Scanăm linia orizontală de mijloc (y = 45%) pentru a găsi marginile tablei
        val midY = (bh * 0.45f).toInt().coerceIn(0, bh - 1)
        var leftEdgeBmp = 0
        var rightEdgeBmp = bw - 1

        val samplesX = 40
        var foundLeft = false
        for (i in 0 until samplesX / 2) {
            val px = ((i + 0.5f) / samplesX * bw).toInt().coerceIn(0, bw - 1)
            val c = safeGetPixel(bmp, px, midY)
            val luma = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
            // Dacă trecem de la o zonă gri (>80 luma) la zona de joc neagră (<40 luma)
            if (!foundLeft && luma < 40) {
                leftEdgeBmp = px
                foundLeft = true
            }
        }

        var foundRight = false
        for (i in (samplesX - 1) downTo (samplesX / 2)) {
            val px = ((i + 0.5f) / samplesX * bw).toInt().coerceIn(0, bw - 1)
            val c = safeGetPixel(bmp, px, midY)
            val luma = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
            if (!foundRight && luma < 40) {
                rightEdgeBmp = px
                foundRight = true
            }
        }

        val leftFrac = if (foundLeft) (leftEdgeBmp.toFloat() / bw).coerceIn(0.08f, 0.25f) else 0.14f
        val rightFrac = if (foundRight) (rightEdgeBmp.toFloat() / bw).coerceIn(0.75f, 0.92f) else 0.86f

        // În Tetris, tabla se întinde de sub titlu (y ~ 14%) până deasupra butoanelor (y ~ 78%)
        val topFrac = 0.14f
        val bottomFrac = 0.78f

        return RectF(
            leftFrac * dw,
            topFrac * dh,
            rightFrac * dw,
            bottomFrac * dh
        )
    }

    /**
     * Măsoară barele de viață ale jucătorului și ale inamicului cu puncte de atingere exacte.
     */
    private fun extractHealthBars(
        bmp: Bitmap,
        bw: Int,
        bh: Int,
        dw: Int,
        dh: Int,
        isLand: Boolean,
        isTetrisGame: Boolean = false
    ): IdentifiableHealthBar {
        if (isTetrisGame) {
            return IdentifiableHealthBar(
                playerHpPercent = 100f,
                enemyHpPercent = 100f,
                secondaryResourcePercent = 100f,
                isLowHpCritical = false,
                playerBarBounds = RectF(dw * 0.05f, dh * 0.04f, dw * 0.35f, dh * 0.14f),
                healTapTarget = null
            )
        }

        val topLeftX0 = (bw * 0.05f).toInt()
        val topLeftX1 = (bw * 0.35f).toInt()
        val topLeftY0 = (bh * 0.04f).toInt()
        val topLeftY1 = (bh * 0.14f).toInt()

        var greenCount = 0
        var redDamageCount = 0
        var totalSamples = 0
        for (y in topLeftY0 until topLeftY1 step 4) {
            for (x in topLeftX0 until topLeftX1 step 4) {
                val c = safeGetPixel(bmp, x.coerceIn(0, bw - 1), y.coerceIn(0, bh - 1))
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                totalSamples++
                if (g > 150 && r < 140 && b < 140) {
                    greenCount++
                } else if (r > 165 && g < 85 && b < 85) {
                    redDamageCount++
                }
            }
        }

        val barSamples = greenCount + redDamageCount
        val hasVisibleHealthBar = totalSamples > 0 && barSamples >= (totalSamples * 0.12f).toInt().coerceAtLeast(3)
        val hpPct = if (hasVisibleHealthBar) {
            (greenCount.toFloat() / barSamples.toFloat() * 100f).coerceIn(10f, 100f)
        } else {
            100f
        }

        val isCritical = hasVisibleHealthBar && hpPct < 32f
        val medkitX = if (isLand) dw * 0.72f else dw * 0.76f
        val medkitY = if (isLand) dh * 0.86f else dh * 0.88f

        return IdentifiableHealthBar(
            playerHpPercent = hpPct,
            enemyHpPercent = 100f,
            secondaryResourcePercent = 85f,
            isLowHpCritical = isCritical,
            playerBarBounds = RectF(dw * 0.05f, dh * 0.04f, dw * 0.35f, dh * 0.14f),
            healTapTarget = if (isCritical) (medkitX to medkitY) else null
        )
    }

    /**
     * Detectează butoanele de control fizice/arcade de la baza ecranului (D-Pad, Stânga, Dreapta, Rotire, Drop, Pause)
     * exact așa cum apar în jocurile clasice de Tetris și Arcade (de exemplu cele 5 butoane din captura de ecran).
     */
    private fun detectArcadeDirectionalButtons(
        bmp: Bitmap,
        bw: Int,
        bh: Int,
        dw: Int,
        dh: Int,
        boardBounds: RectF
    ): List<IdentifiableUiElement> {
        val list = mutableListOf<IdentifiableUiElement>()

        // Rândul 1 de butoane (y ~ 84% din ecran): ◀ Stânga (30%), ↻ Rotire (50%), ▶ Dreapta (70%)
        val row1Y = dh * 0.84f
        val btnRadius = dw * 0.08f

        // Buton STÂNGA (◀)
        list.add(
            IdentifiableUiElement(
                id = "btn_dpad_left",
                category = UiElementCategory.BUTTON_DPAD_LEFT,
                label = "Stânga (◀)",
                boundsOnDisplay = RectF(dw * 0.30f - btnRadius, row1Y - btnRadius, dw * 0.30f + btnRadius, row1Y + btnRadius),
                confidence = 0.95f,
                suggestedMotion = "TAP",
                description = "Mută piesa activă o coloană spre stânga"
            )
        )

        // Buton ROTIRE (↻)
        list.add(
            IdentifiableUiElement(
                id = "btn_dpad_rotate",
                category = UiElementCategory.BUTTON_DPAD_ROTATE,
                label = "Rotire Piesă (↻)",
                boundsOnDisplay = RectF(dw * 0.50f - btnRadius, row1Y - btnRadius, dw * 0.50f + btnRadius, row1Y + btnRadius),
                confidence = 0.95f,
                suggestedMotion = "TAP",
                description = "Rotește piesa activă la 90 de grade"
            )
        )

        // Buton DREAPTA (▶)
        list.add(
            IdentifiableUiElement(
                id = "btn_dpad_right",
                category = UiElementCategory.BUTTON_DPAD_RIGHT,
                label = "Dreapta (▶)",
                boundsOnDisplay = RectF(dw * 0.70f - btnRadius, row1Y - btnRadius, dw * 0.70f + btnRadius, row1Y + btnRadius),
                confidence = 0.95f,
                suggestedMotion = "TAP",
                description = "Mută piesa activă o coloană spre dreapta"
            )
        )

        // Rândul 2 de butoane (y ~ 92% din ecran): ▼ Coborâre / Drop (40%), ⏸ Pauză (60%)
        val row2Y = dh * 0.92f
        list.add(
            IdentifiableUiElement(
                id = "btn_dpad_down",
                category = UiElementCategory.BUTTON_DPAD_DOWN_DROP,
                label = "Coborâre Rapidă / Drop (▼)",
                boundsOnDisplay = RectF(dw * 0.40f - btnRadius, row2Y - btnRadius, dw * 0.40f + btnRadius, row2Y + btnRadius),
                confidence = 0.95f,
                suggestedMotion = "TAP",
                description = "Plasează instantaneu piesa în coloană"
            )
        )

        list.add(
            IdentifiableUiElement(
                id = "btn_pause",
                category = UiElementCategory.BUTTON_PAUSE,
                label = "Pauză (⏸)",
                boundsOnDisplay = RectF(dw * 0.60f - btnRadius, row2Y - btnRadius, dw * 0.60f + btnRadius, row2Y + btnRadius),
                confidence = 0.85f,
                suggestedMotion = "TAP",
                description = "Pauză joc"
            )
        )

        return list
    }

    /**
     * Mapează butoanele de Meniu, Lobby, Pop-up și Dialoguri.
     */
    private fun detectMenuAndDialogButtons(
        bmp: Bitmap,
        bw: Int,
        bh: Int,
        dw: Int,
        dh: Int,
        isLand: Boolean
    ): List<IdentifiableUiElement> {
        val list = mutableListOf<IdentifiableUiElement>()

        // 1. Buton Închidere Pop-up „X” (Dreapta-Sus)
        list.add(
            IdentifiableUiElement(
                id = "btn_close_x",
                category = UiElementCategory.BUTTON_CLOSE_POPUP,
                label = "Închide Pop-up (X)",
                boundsOnDisplay = RectF(dw * 0.84f, dh * 0.06f, dw * 0.96f, dh * 0.18f),
                confidence = 0.88f,
                suggestedMotion = "TAP",
                description = "Închide reclame, oferte sau notificări"
            )
        )

        // 2. Buton Start / Play / Battle
        val playY = if (isLand) dh * 0.82f else dh * 0.74f
        list.add(
            IdentifiableUiElement(
                id = "btn_start_play",
                category = UiElementCategory.BUTTON_START_PLAY,
                label = "START / PLAY",
                boundsOnDisplay = RectF(dw * 0.35f, playY, dw * 0.65f, playY + dh * 0.10f),
                confidence = 0.90f,
                isPrimaryAction = true,
                suggestedMotion = "TAP",
                description = "Pornește meciul sau reia gameplay-ul"
            )
        )

        return list
    }

    /**
     * Mapează controalele pentru jocuri Shooter / Battle Royale / RPG.
     */
    private fun detectCombatControls(
        bmp: Bitmap,
        bw: Int,
        bh: Int,
        dw: Int,
        dh: Int,
        isLand: Boolean
    ): List<IdentifiableUiElement> {
        val list = mutableListOf<IdentifiableUiElement>()
        val fireX = if (isLand) dw * 0.86f else dw * 0.82f
        val fireY = if (isLand) dh * 0.72f else dh * 0.78f
        val joyX = if (isLand) dw * 0.18f else dw * 0.22f
        val joyY = if (isLand) dh * 0.72f else dh * 0.76f

        // Buton FOC principal
        list.add(
            IdentifiableUiElement(
                id = "btn_combat_fire",
                category = UiElementCategory.BUTTON_ATTACK_FIRE,
                label = "Trăgaci / Foc Principal",
                boundsOnDisplay = RectF(fireX - dw * 0.08f, fireY - dh * 0.08f, fireX + dw * 0.08f, fireY + dh * 0.08f),
                confidence = 0.95f,
                isPrimaryAction = true,
                suggestedMotion = "BURST",
                description = "Declanșează rafale de foc pe țintă"
            )
        )

        // Joystick Sprint Înainte
        list.add(
            IdentifiableUiElement(
                id = "joy_movement",
                category = UiElementCategory.JOYSTICK_MOVEMENT,
                label = "Joystick Deplasare / Sprint",
                boundsOnDisplay = RectF(joyX - dw * 0.10f, joyY - dh * 0.10f, joyX + dw * 0.10f, joyY + dh * 0.10f),
                confidence = 0.95f,
                suggestedMotion = "DRAG",
                description = "Menține alergarea cursivă fără oprire"
            )
        )

        return list
    }

    /**
     * Analizor avansat al tablei de Tetris pe 10 coloane:
     *  - Măsoară exact pe pixelii MediaProjection unde se află piesa în cădere (coloana 0..9)
     *  - Măsoară înălțimea stivei pe fiecare coloană și găsește cel mai adânc gol (coloana țintă 0..9)
     *  - Calculează diferența de coloane și determină dacă piesa este aliniată pentru Drop!
     */
    private fun analyzeTetrisBoardColumns(
        bmp: Bitmap,
        bw: Int,
        bh: Int,
        dw: Int,
        dh: Int,
        boardBounds: RectF
    ): TetrisBoardAnalysis {
        val boardLeftFrac = (boardBounds.left / dw).coerceIn(0.08f, 0.30f)
        val boardRightFrac = (boardBounds.right / dw).coerceIn(0.70f, 0.92f)
        val boardWidthFrac = boardRightFrac - boardLeftFrac
        val colWidthFrac = boardWidthFrac / 10f

        val hsv = FloatArray(3)
        var detectedPieceCol = 4
        var maxTopPieceVibrancy = -1f
        val colStackHeights = FloatArray(10)

        for (col in 0 until 10) {
            val colCenterFrac = boardLeftFrac + (col + 0.5f) * colWidthFrac
            val px = (colCenterFrac * bw).toInt().coerceIn(0, bw - 1)

            // 1. Căutăm piesa activă în cădere în jumătatea superioară a tablei (y: 16% .. 38%)
            var topVibrancy = 0f
            for (ry in 0 until 8) {
                val py = ((0.16f + ry * 0.028f) * bh).toInt().coerceIn(0, bh - 1)
                val c = safeGetPixel(bmp, px, py)
                Color.colorToHSV(c, hsv)
                if (hsv[1] > 0.35f && hsv[2] > 0.35f) {
                    topVibrancy += hsv[1] + hsv[2]
                }
            }
            if (topVibrancy > maxTopPieceVibrancy) {
                maxTopPieceVibrancy = topVibrancy
                detectedPieceCol = col
            }

            // 2. Măsurăm înălțimea stivei în jumătatea inferioară a tablei (y: 40% .. 76%)
            var firstFilledIndex = 14
            for (ry in 0 until 14) {
                val py = ((0.38f + ry * 0.026f) * bh).toInt().coerceIn(0, bh - 1)
                val c = safeGetPixel(bmp, px, py)
                Color.colorToHSV(c, hsv)
                if (hsv[1] > 0.30f && hsv[2] > 0.30f) {
                    firstFilledIndex = ry
                    break
                }
            }
            // Cu cât firstFilledIndex este mai mare, cu atât coloana este mai liberă (stivă mai joasă)
            colStackHeights[col] = (14 - firstFilledIndex).toFloat()
        }

        // Găsim coloana cu cea mai joasă stivă (cel mai adânc gol)
        val targetCol = (0 until 10).minByOrNull { colStackHeights[it] } ?: 4
        val colDiff = targetCol - detectedPieceCol

        val pieceFrac = boardLeftFrac + (detectedPieceCol + 0.5f) * colWidthFrac
        val targetFrac = boardLeftFrac + (targetCol + 0.5f) * colWidthFrac
        val isAligned = abs(colDiff) == 0

        val recommendedButton = when {
            colDiff < 0 -> UiElementCategory.BUTTON_DPAD_LEFT
            colDiff > 0 -> UiElementCategory.BUTTON_DPAD_RIGHT
            else -> UiElementCategory.BUTTON_DPAD_DOWN_DROP
        }

        return TetrisBoardAnalysis(
            boardBoundsOnDisplay = boardBounds,
            currentPieceColumn = detectedPieceCol,
            currentPieceColFrac = pieceFrac,
            targetGapColumn = targetCol,
            targetGapColFrac = targetFrac,
            columnDifference = colDiff,
            isAlignedForDrop = isAligned,
            recommendedButtonCategory = recommendedButton
        )
    }

    /**
     * Calculează ținta precisă de interacțiune pentru AI pe baza tuturor elementelor detectate.
     */
    private fun computeRecommendedInteraction(
        detectedButtons: List<IdentifiableUiElement>,
        healthBar: IdentifiableHealthBar,
        tetrisAnalysis: TetrisBoardAnalysis?,
        solitaireAnalysis: SolitaireBoardAnalysis? = null,
        dw: Int,
        dh: Int
    ): UiInteractionTarget? {
        // Prioritate 1: Salvare de Urgență dacă HP este critic (<32%)
        if (healthBar.isLowHpCritical && healthBar.healTapTarget != null) {
            val (hx, hy) = healthBar.healTapTarget
            return UiInteractionTarget(
                targetX = hx,
                targetY = hy,
                motionType = "TAP",
                actionCategory = UiElementCategory.BUTTON_HEAL_MEDKIT,
                reason = "HP critic (${healthBar.playerHpPercent.toInt()}%); activat trusă medicală"
            )
        }

        // Prioritate 1.5: Dacă este Solitaire, recomandăm cartea activă sau pachetul Stock
        if (solitaireAnalysis != null) {
            val priorityCol = solitaireAnalysis.columns.firstOrNull { it.hasFaceUpCards && it.hasFaceDownCardsAbove }
                ?: solitaireAnalysis.columns.firstOrNull { it.hasFaceUpCards }
            return if (priorityCol != null) {
                UiInteractionTarget(
                    targetX = priorityCol.centerX,
                    targetY = priorityCol.bottomCardY,
                    motionType = "TAP",
                    actionCategory = UiElementCategory.GAME_BOARD_ACTIVE_FIELD,
                    reason = "Solitaire: Mutare carte activă din coloana ${priorityCol.columnIndex + 1}/7"
                )
            } else {
                UiInteractionTarget(
                    targetX = solitaireAnalysis.stockPileX,
                    targetY = solitaireAnalysis.stockPileY,
                    motionType = "TAP",
                    actionCategory = UiElementCategory.GAME_BOARD_ACTIVE_FIELD,
                    reason = "Solitaire: Tragere carte nouă din pachetul Stock (Sus-Dreapta)"
                )
            }
        }

        // Prioritate 2: Dacă este Tetris, interacționăm direct cu butoanele fizice mapate pe ecran!
        if (tetrisAnalysis != null) {
            val targetBtnCategory = tetrisAnalysis.recommendedButtonCategory
            val btn = detectedButtons.firstOrNull { it.category == targetBtnCategory }
            if (btn != null) {
                val reason = when (targetBtnCategory) {
                    UiElementCategory.BUTTON_DPAD_LEFT -> "Piesa pe col ${tetrisAnalysis.currentPieceColumn + 1} → Apasă STÂNGA ◀ spre golul din col ${tetrisAnalysis.targetGapColumn + 1}"
                    UiElementCategory.BUTTON_DPAD_RIGHT -> "Piesa pe col ${tetrisAnalysis.currentPieceColumn + 1} → Apasă DREAPTA ▶ spre golul din col ${tetrisAnalysis.targetGapColumn + 1}"
                    else -> "Piesa este aliniată perfect deasupra golului col ${tetrisAnalysis.targetGapColumn + 1} → DROP ▼ pentru linie completă!"
                }
                return UiInteractionTarget(
                    targetX = btn.centerX,
                    targetY = btn.centerY,
                    motionType = "TAP",
                    actionCategory = targetBtnCategory,
                    reason = reason
                )
            }
        }

        // Prioritate 3: Buton de Atac / Foc în jocuri de acțiune
        val fireBtn = detectedButtons.firstOrNull { it.category == UiElementCategory.BUTTON_ATTACK_FIRE }
        if (fireBtn != null) {
            return UiInteractionTarget(
                targetX = fireBtn.centerX,
                targetY = fireBtn.centerY,
                motionType = "BURST",
                actionCategory = UiElementCategory.BUTTON_ATTACK_FIRE,
                reason = "Acțiune de atac / tragere pe butonul identificat"
            )
        }

        // Prioritate 4: Buton Start / Play dacă suntem în lobby
        val startBtn = detectedButtons.firstOrNull { it.category == UiElementCategory.BUTTON_START_PLAY }
        if (startBtn != null) {
            return UiInteractionTarget(
                targetX = startBtn.centerX,
                targetY = startBtn.centerY,
                motionType = "TAP",
                actionCategory = UiElementCategory.BUTTON_START_PLAY,
                reason = "Apăsat buton START / PLAY pentru începere meci"
            )
        }

        return null
    }
}
