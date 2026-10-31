package com.eva1.companion.engines

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
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
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.random.Random

// ══════════════════════════════════════════════════════════════════════════════
// EVA-1 DEEP OS ORCHESTRATOR ENGINE (2026 UNRESTRICTED EDITION)
//
// Capabilități complete de control profund asupra telefonului:
//   1. Trimite mesaje pe WhatsApp persoanei/numărului cerut + Auto-Send Accessibility
//   2. Deschide YouTube / YouTube Music și pune muzica sau videoclipul dorit
//   3. Vede în timp real ecranul telefonului (UI Tree, texte, butoane, coordonate)
//   4. Se joacă autonom pe jocurile de pe telefon în locul utilizatorului (Game Bot)
//   5. Lansează orice aplicație instalată, controlează Clipboard, Shell & Telemetrie
// ══════════════════════════════════════════════════════════════════════════════

data class DeviceTelemetry(
    val model: String,
    val androidVersion: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val batteryTempC: Float,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val storageFreeGb: Float,
    val storageTotalGb: Float,
    val networkType: String,
    val cpuCores: Int
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isGame: Boolean = false
)

enum class GameBotMode(val displayName: String, val description: String) {
    AUTO_ADAPTIVE(
        "Adaptiv AI Touch Vision",
        "Detectează automat tipul jocului (Drag & Drop, Swipe Runner, Tap sau D-Pad) și execută gesturi precise"
    ),
    TOUCH_DRAG_PUZZLE(
        "Block Blast & Drag Puzzle",
        "Trage piesele din tava inferioară și le plasează pe grila de joc (Block Blast, Woodoku, Drag Puzzle)"
    ),
    SOLITAIRE_CARDS(
        "Solitaire & Jocuri de Cărți (Klondike / Spider)",
        "Mută automat cărțile pe cele 7 coloane și la fundație (A..K), întoarce cărțile ascunse și trage din pachet (Stock)"
    ),
    TETRIS_ARCADE(
        "Tetris, Block & Arcade",
        "Glisări rapide și tap-uri pe ecran sau butoane direcționale pentru Tetris, 2048, Snake și jocuri Arcade"
    ),
    FPS_BATTLE_ROYALE(
        "FPS & Battle Royale (PUBG / COD)",
        "Sprint Joystick, Rotire Cameră 3D, Țintire Crosshair, Rafală Foc, Scope & Reload pentru PUBG, Call of Duty, Free Fire"
    ),
    FOOTBALL_SPORTS(
        "Fotbal & Sport (FC Mobile / eFootball / DLS)",
        "Dribling Joystick, Pasă (Pass), Pasă filtrantă (Through), Sprint și Șut la poartă (Shoot)"
    ),
    FOOTBALL_MANAGER(
        "Football Manager & Simulare (FM / Top Eleven / OSM)",
        "Navigare inteligentă meniu, Auto-Pick echipă, Continue, Kick-Off, tactică și simulare meci"
    ),
    CLASH_BASE_STRATEGY(
        "Strategie & Bază (Clash of Clans / Clash Royale)",
        "Colectare resurse, buton Attack / Find Match, selectare trupe din bara de jos și plasare pe perimetrul bazei"
    ),
    RACING_NFS(
        "Curse Auto (Need for Speed / Asphalt / Real Racing)",
        "Viraje Stânga/Dreapta ghidate vizual după pistă, Swipe-Up Nitro Boost și Drift în curbe"
    ),
    OPEN_WORLD_GTA(
        "Aventură Open-World (GTA Mobile / Minecraft)",
        "Deplasare 3D, rotire cameră, intrare/ieșire vehicul, accelerație + direcție auto și acțiune/misiuni"
    ),
    ENDLESS_RUNNER(
        "Runner & Arcade (Swipe 4D)",
        "Salturi, eschive stânga/dreapta și slide pentru Subway Surfers, Temple Run"
    ),
    CLICKER_IDLE(
        "Clicker & Idle RPG (Turbo Tap)",
        "Tap-uri rapide multi-zonă, colectare aur și upgrade-uri automate"
    ),
    RPG_STRATEGY(
        "RPG, MOBA & Action (Brawl Stars / Mobile Legends)",
        "Joystick mișcare + activare abilități, atac și colectare recompense"
    ),
    PUZZLE_MATCH(
        "Puzzle & Match-3 (Candy Crush / Grilă)",
        "Scanează grila după culori și efectuează glisări orizontale/verticale pentru combinații"
    )
}

data class TacticalGameAction(
    val status: String,
    val pauseAfterActionMs: Long = 400L,
    val targetCoordinates: String = "",
    val gestureDispatched: Boolean = true,
    val actionCategory: String = "TACTICAL_ACTION",
    val reasoning: String = ""
)

enum class ClashState {
    VILLAGE_HOME,
    ATTACK_MAP,
    SCOUTING_ENEMY,
    DEPLOYING_TROOPS,
    WATCHING_BATTLE,
    BATTLE_OVER
}

data class LiveGameObstacle(
    val id: Int,
    val lane: Int, // 0 = Stanga, 1 = Centru, 2 = Dreapta
    val yProgress: Float, // 0f (sus) -> 1f (jos la jucator)
    val isCoin: Boolean = false
)

data class LiveGameArenaState(
    val isRunning: Boolean = false,
    val gameTitle: String = "EVA-1 Cyber Runner & AI Arena",
    val mode: GameBotMode = GameBotMode.AUTO_ADAPTIVE,
    val playerLane: Int = 1,
    val isJumping: Boolean = false,
    val score: Int = 0,
    val coins: Int = 0,
    val combo: Int = 1,
    val level: Int = 1,
    val bossHpPercent: Float = 100f,
    val obstacles: List<LiveGameObstacle> = emptyList(),
    val lastAiDecision: String = "Pregătit — selectează un joc instalat sau apasă „Joacă în Locul Meu”!",
    val externalAppLaunched: Boolean = false,
    val externalPackageName: String = ""
)

data class LearnedUserGesture(
    val id: String = java.util.UUID.randomUUID().toString(),
    val gameKey: String,
    val actionLabel: String,
    val actionTypeKey: String, // ex: "FIRE", "RELOAD", "SCOPE", "HEAL", "JOYSTICK", "ATTACK", "DROP"
    val targetXFrac: Float,
    val targetYFrac: Float,
    val motionType: String = "TAP", // "TAP", "SWIPE", "BURST"
    val endXFrac: Float = 0f,
    val endYFrac: Float = 0f,
    val durationMs: Long = 85L,
    val note: String = ""
)

class OsOrchestratorEngine(
    private val context: Context,
    val offlineLlmEngine: OfflineLlmEngine? = null
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gameBotJob: Job? = null

    private val _isGameBotRunning = MutableStateFlow(false)
    val isGameBotRunning: StateFlow<Boolean> = _isGameBotRunning.asStateFlow()

    private val _gameBotStatus = MutableStateFlow("Oprit — pregătit să preia orice joc de pe telefon")
    val gameBotStatus: StateFlow<String> = _gameBotStatus.asStateFlow()

    private val _gameBotActionsCount = MutableStateFlow(0)
    val gameBotActionsCount: StateFlow<Int> = _gameBotActionsCount.asStateFlow()

    private val _activeGameMode = MutableStateFlow(GameBotMode.AUTO_ADAPTIVE)
    val activeGameMode: StateFlow<GameBotMode> = _activeGameMode.asStateFlow()

    private val _targetGameName = MutableStateFlow("")
    val targetGameName: StateFlow<String> = _targetGameName.asStateFlow()

    private val _liveGameState = MutableStateFlow(LiveGameArenaState())
    val liveGameState: StateFlow<LiveGameArenaState> = _liveGameState.asStateFlow()

    private val _lastScreenVisionReport = MutableStateFlow("")
    val lastScreenVisionReport: StateFlow<String> = _lastScreenVisionReport.asStateFlow()

    // ── GESTIONARE MODELE LLM LOCALE PENTRU JOCURI (11 MODELE) ──
    private val _selectedGameLlmModelId = MutableStateFlow("Gemma-2-9B-Vision-Action-MLC")
    val selectedGameLlmModelId: StateFlow<String> = _selectedGameLlmModelId.asStateFlow()

    fun selectGameLlmModel(modelId: String) {
        _selectedGameLlmModelId.value = modelId
        engineScope.launch {
            offlineLlmEngine?.initModel(modelId)
        }
    }

    // ── IMITATION LEARNING: CLONARE GESTURI UTILIZATOR ──
    private val gesturePrefs = context.getSharedPreferences("eva1_learned_gestures", Context.MODE_PRIVATE)

    private fun loadSavedLearnedGestures(): List<LearnedUserGesture> {
        val raw = gesturePrefs.getString("gestures_json", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<LearnedUserGesture>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    LearnedUserGesture(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        gameKey = obj.optString("gameKey"),
                        actionLabel = obj.optString("actionLabel"),
                        actionTypeKey = obj.optString("actionTypeKey"),
                        targetXFrac = obj.optDouble("targetXFrac", 0.5).toFloat(),
                        targetYFrac = obj.optDouble("targetYFrac", 0.5).toFloat(),
                        motionType = obj.optString("motionType", "TAP"),
                        endXFrac = obj.optDouble("endXFrac", 0.0).toFloat(),
                        endYFrac = obj.optDouble("endYFrac", 0.0).toFloat(),
                        durationMs = obj.optLong("durationMs", 85L),
                        note = obj.optString("note")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistLearnedGestures(list: List<LearnedUserGesture>) {
        try {
            val arr = org.json.JSONArray()
            for (g in list) {
                val obj = org.json.JSONObject().apply {
                    put("id", g.id)
                    put("gameKey", g.gameKey)
                    put("actionLabel", g.actionLabel)
                    put("actionTypeKey", g.actionTypeKey)
                    put("targetXFrac", g.targetXFrac.toDouble())
                    put("targetYFrac", g.targetYFrac.toDouble())
                    put("motionType", g.motionType)
                    put("endXFrac", g.endXFrac.toDouble())
                    put("endYFrac", g.endYFrac.toDouble())
                    put("durationMs", g.durationMs)
                    put("note", g.note)
                }
                arr.put(obj)
            }
            gesturePrefs.edit().putString("gestures_json", arr.toString()).commit()
        } catch (_: Exception) {}
    }

    private val _learnedGestures = MutableStateFlow<List<LearnedUserGesture>>(loadSavedLearnedGestures())
    val learnedGestures: StateFlow<List<LearnedUserGesture>> = _learnedGestures.asStateFlow()

    fun recordLearnedGesture(
        gameKey: String,
        actionLabel: String,
        actionTypeKey: String,
        targetXFrac: Float,
        targetYFrac: Float,
        motionType: String = "TAP",
        endXFrac: Float = 0f,
        endYFrac: Float = 0f,
        durationMs: Long = 85L,
        note: String = ""
    ) {
        val cleanKey = gameKey.lowercase().trim()
        val gesture = LearnedUserGesture(
            gameKey = cleanKey,
            actionLabel = actionLabel,
            actionTypeKey = actionTypeKey.uppercase(),
            targetXFrac = targetXFrac.coerceIn(0.01f, 0.99f),
            targetYFrac = targetYFrac.coerceIn(0.01f, 0.99f),
            motionType = motionType,
            endXFrac = endXFrac.coerceIn(0.01f, 0.99f),
            endYFrac = endYFrac.coerceIn(0.01f, 0.99f),
            durationMs = durationMs,
            note = note.ifBlank { "Clonat din stilul utilizatorului" }
        )
        val filtered = _learnedGestures.value.filterNot {
            it.gameKey == cleanKey && it.actionTypeKey.equals(actionTypeKey, ignoreCase = true)
        }
        val updated = filtered + gesture
        _learnedGestures.value = updated
        persistLearnedGestures(updated)
    }

    fun removeLearnedGesture(id: String) {
        val updated = _learnedGestures.value.filterNot { it.id == id }
        _learnedGestures.value = updated
        persistLearnedGestures(updated)
    }

    fun getLearnedGesture(gamePkg: String, actionTypeKey: String): LearnedUserGesture? {
        val clean = gamePkg.lowercase()
        return _learnedGestures.value.firstOrNull {
            (clean.contains(it.gameKey) || it.gameKey.contains(clean)) &&
            it.actionTypeKey.equals(actionTypeKey, ignoreCase = true)
        }
    }

    // Motor Cognitiv & Învățare Autonomă (Căutare online ghiduri, profiluri jocuri & reinforcement learning)
    val gameCognition = EvaGameCognitionEngine(context)

    // Modul Interfațare Modele LLM Locale (llama.cpp server pe 127.0.0.1 sau cele 11 modele offline)
    val localLlmBridge = LocalLlmGameBridgeEngine(context, offlineLlmEngine)

    // Serviciu de Mapare Dinamică a Textului LLM în Gesturi Tactile pe Coordonate (dispatchGesture)
    val gestureMapper = LlmGestureMappingEngine { EvaAccessibilityService.instance }

    // Conductă de Viziune Multimodală (Gemini Vision / Local Vision Model / Smart CV)
    val multiModalVision = MultiModalGameVisionEngine(
        context = context,
        apiKeyProvider = {
            val prefs = context.getSharedPreferences("eva1_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getString("gemini_key", "")
            if (!saved.isNullOrBlank()) saved else com.example.BuildConfig.GEMINI_API_KEY
        },
        localBridgeEngineSupplier = { localLlmBridge },
        offlineLlmEngineSupplier = { offlineLlmEngine }
    )

    // Arhitectură pe Două Viteze (Dual-Brain): Sistemul 1 (Reflexe 16-60ms) + Sistemul 2 (Strateg 5-10s)
    val dualBrainEngine = DualBrainGameEngine(
        context = context,
        multiModalVision = multiModalVision,
        offlineLlmEngineSupplier = { offlineLlmEngine }
    )

    // Mașină de stare dedicată pentru Clash of Clans & jocuri strategice tactile
    private var clashCurrentState = ClashState.VILLAGE_HOME
    private var clashScoutCount = 0
    private var clashWaveStep = 0
    private var clashBattleWaitCycles = 0

    // Mașină de stare & memorie anti-buclă pentru Solitaire / Klondike (7 coloane + Waste + Stock)
    private var lastSolitaireBoardSig: Long = 0L
    private val solitaireTriedMovesOnBoard = mutableSetOf<String>()
    private var lastSolitaireActionWasDraw: Boolean = false
    private var solitaireStockCycleCount: Int = 0

    init {
        // Pre-încărcare profiluri gesturi reale pentru jocurile populare DOAR dacă nu există deja gesturi salvate:
        if (_learnedGestures.value.isEmpty()) {
            recordLearnedGesture("pubg", "Tragere Foc Principal (Dreapta)", "FIRE", 0.86f, 0.72f, "BURST")
            recordLearnedGesture("pubg", "Reîncărcare Muniție", "RELOAD", 0.78f, 0.88f, "TAP")
            recordLearnedGesture("pubg", "Activare Lunetă (ADS Scope)", "SCOPE", 0.93f, 0.56f, "TAP")
            recordLearnedGesture("pubg", "Trusă Medicală / Medkit", "HEAL", 0.74f, 0.88f, "TAP")
            recordLearnedGesture("pubg", "Joystick Sprint Înainte", "JOYSTICK", 0.18f, 0.72f, "SWIPE", 0.18f, 0.50f, 420L)

            recordLearnedGesture("clash", "Buton Start Atac (Stânga-Jos)", "ATTACK", 0.08f, 0.88f, "TAP")
            recordLearnedGesture("clash", "Buton Find a Match (Dreapta-Jos)", "FIND_MATCH", 0.82f, 0.76f, "TAP")
            recordLearnedGesture("clash", "Desfășurare Val Tancuri (Slot 1)", "DROP_SLOT_1", 0.18f, 0.88f, "TAP")
            recordLearnedGesture("clash", "Desfășurare Val DPS (Slot 2)", "DROP_SLOT_2", 0.28f, 0.88f, "TAP")
            recordLearnedGesture("clash", "Buton Return Home", "RETURN_HOME", 0.50f, 0.85f, "TAP")
        }
        // Conectăm butoanele din Panoul Plutitor (Floating HUD peste jocuri) direct la Autopilot
        EvaAccessibilityService.onOverlayTogglePlayPause = {
            if (_isGameBotRunning.value) {
                stopGameAutopilot(keepOverlayVisible = true)
            } else {
                val fgPkg = EvaAccessibilityService.lastForegroundPackage.value
                val target = if (fgPkg.isNotBlank() && fgPkg != context.packageName) {
                    fgPkg
                } else {
                    _targetGameName.value
                }
                startGameAutopilot(target, _activeGameMode.value, launchExternalIfNeeded = fgPkg == context.packageName)
            }
        }
        EvaAccessibilityService.onOverlayCycleMode = {
            val all = GameBotMode.entries
            val nextIdx = (all.indexOf(_activeGameMode.value) + 1) % all.size
            val nextMode = all[nextIdx]
            _activeGameMode.value = nextMode
            _liveGameState.value = _liveGameState.value.copy(mode = nextMode)
            EvaAccessibilityService.instance?.showOrUpdateFloatingGameHud(
                isRunning = _isGameBotRunning.value,
                gameTitle = _targetGameName.value.ifBlank { "Joc Activ" },
                modeName = nextMode.displayName,
                statusLine = "Mod schimbat în: ${nextMode.displayName}"
            )
        }
        EvaAccessibilityService.onOverlayStopBot = {
            stopGameAutopilot(keepOverlayVisible = false)
        }
    }

    fun toggleFloatingGameHud(show: Boolean) {
        val acc = EvaAccessibilityService.instance ?: return
        if (show) {
            acc.showOrUpdateFloatingGameHud(
                isRunning = _isGameBotRunning.value,
                gameTitle = _targetGameName.value.ifBlank { "Orice Joc Activ" },
                modeName = _activeGameMode.value.displayName,
                statusLine = "Deschide orice joc de pe telefon și apasă ▶️ Joacă Aici!"
            )
        } else {
            acc.hideFloatingGameHud()
        }
    }

    // ── 1. Telemetrie completa a telefonului ──────────────────────
    fun getDeviceTelemetry(): DeviceTelemetry {
        val bm = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bm?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bm?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val status = bm?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = bm?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val usedRamMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = (stat.availableBytes / (1024f * 1024f * 1024f))
        val totalGb = (stat.totalBytes / (1024f * 1024f * 1024f))

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val netType = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Date Mobile (4G/5G)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Conectat"
        }

        return DeviceTelemetry(
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempC = tempTenths / 10f,
            ramUsedMb = usedRamMb,
            ramTotalMb = totalRamMb,
            storageFreeGb = freeGb,
            storageTotalGb = totalGb,
            networkType = netType,
            cpuCores = Runtime.getRuntime().availableProcessors()
        )
    }

    // ── 2. Lista aplicatiilor si jocurilor instalate + lansare rapida ──
    private fun isAppLikelyGame(appInfo: android.content.pm.ApplicationInfo?, label: String, pkg: String): Boolean {
        if (pkg == context.packageName) return false
        val lowerPkg = pkg.lowercase()
        val lowerLabel = label.lowercase()

        // Excludem pachetele de sistem Android uzuale
        val systemExcludes = listOf(
            "com.android.settings", "com.android.vending", "com.android.chrome",
            "com.google.android.gms", "com.google.android.youtube", "com.google.android.gm",
            "com.google.android.apps.maps", "com.google.android.apps.photos",
            "com.google.android.dialer", "com.google.android.contacts", "com.google.android.deskclock",
            "com.google.android.calculator", "com.google.android.calendar", "com.whatsapp",
            "com.facebook.katana", "com.instagram.android", "com.zhiliaoapp.musically"
        )
        if (systemExcludes.any { lowerPkg == it }) return false

        if (appInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
            ) {
                return true
            }
            @Suppress("DEPRECATION")
            if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0) {
                return true
            }
        }

        val gameKeywords = listOf(
            "game", "joc", "tetris", "block", "puzzle", "subway", "runner", "clash", "brawl",
            "pubg", "tencent.ig", "callofduty", "codm", "freefire", "fifa", "fcmobile", "pesam",
            "efootball", "dls", "soccer", "football", "racing", "nfs", "asphalt", "carx",
            "chess", "sah", "2048", "candy", "solitaire", "sudoku", "arcade", "craft", "minecraft",
            "roblox", "gtasa", "gtavc", "gta", "supercell", "gameloft", "rovio", "miniclip",
            "playrix", "voodoo", "ketchapp", "kiloo", "king.", "zynga", "ea.game", "ea.gp",
            "unity", "flappy", "snake", "brick", "breaker", "shooter", "battle", "strike", "royale"
        )
        return gameKeywords.any { kw -> lowerPkg.contains(kw) || lowerLabel.contains(kw) }
    }

    fun getInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        return resolveInfos.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = ri.loadLabel(pm).toString()
            val appInfo = ri.activityInfo?.applicationInfo
            val isGame = isAppLikelyGame(appInfo, label, pkg)
            InstalledAppInfo(appName = label, packageName = pkg, isGame = isGame)
        }.distinctBy { it.packageName }
            .sortedWith(compareByDescending<InstalledAppInfo> { it.isGame }.thenBy { it.appName.lowercase() })
    }

    fun getInstalledGames(): List<InstalledAppInfo> {
        val all = getInstalledApps()
        val games = all.filter { it.isGame }
        if (games.isNotEmpty()) return games
        // Dacă jocurile instalate nu și-au declarat flag-ul CATEGORY_GAME în Manifest,
        // returnăm aplicațiile non-sistem instalate de utilizator
        return all.filter {
            !it.packageName.startsWith("com.android.") &&
                !it.packageName.startsWith("com.google.android.")
        }.ifEmpty { all }
    }

    fun launchApp(queryNameOrPackage: String): Boolean {
        val pm = context.packageManager
        val directIntent = pm.getLaunchIntentForPackage(queryNameOrPackage)
        if (directIntent != null) {
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(directIntent)
            return true
        }

        val match = getInstalledApps().firstOrNull {
            it.appName.contains(queryNameOrPackage, ignoreCase = true) ||
                it.packageName.contains(queryNameOrPackage, ignoreCase = true)
        } ?: return false

        val intent = pm.getLaunchIntentForPackage(match.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    // ── 3. WHATSAPP DIRECT MESSAGING ──────────────────────────────
    // Android intents confirm only that the destination UI was opened, not that a message was sent.
    fun sendWhatsAppMessage(recipient: String, message: String): String {
        val cleanRecipient = recipient.trim()
        val cleanMessage = message.trim()
        if (cleanRecipient.isEmpty() || cleanMessage.isEmpty())
            return "⚠️ Destinatarul și mesajul sunt obligatorii. Nu am trimis nimic."
        val directNumber = extractPhoneNumber(cleanRecipient)
        val phone = directNumber ?: lookupContactPhoneByName(cleanRecipient)
        if (phone == null) {
            return "⚠️ Nu am identificat un număr unic pentru «$cleanRecipient». Selectează contactul exact sau furnizează numărul; nu am trimis nimic."
        }
        // WhatsApp public API prepares a message; it does not provide a delivery receipt.
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(cleanMessage)}")
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "📨 Conversația WhatsApp pentru $cleanRecipient este deschisă cu mesajul pregătit. Verifică destinatarul și apasă Trimite; livrarea nu este confirmată."
        } catch (e: Exception) {
            "⚠️ Nu am putut deschide WhatsApp instalat: ${e.message ?: "aplicația indisponibilă"}. Mesajul nu a fost trimis."
        }
    }

    /**
     * Pas separat, invocat numai după ce utilizatorul a verificat pe ecran
     * destinatarul și textul mesajului pregătit în WhatsApp.
     * Nu afirmă livrare: Android Accessibility confirmă doar apăsarea butonului.
     */
    fun confirmAndPressWhatsAppSend(userConfirmedRecipientAndText: Boolean): String {
        if (!userConfirmedRecipientAndText) return "⚠️ Confirmă mai întâi destinatarul și textul din WhatsApp. Nu am trimis nimic."
        val accessibility = EvaAccessibilityService.instance
            ?: return "⚠️ Activează serviciul de accesibilitate EVA pentru trimitere."
        val pressed = accessibility.clickExactResourceInForeground(
            "com.whatsapp", "com.whatsapp:id/send"
        )
        return if (pressed) {
            "📤 Am apăsat Trimite în WhatsApp. Verifică bifa de livrare; livrarea nu poate fi garantată prin Accessibility."
        } else {
            "⚠️ Butonul de trimitere WhatsApp nu a fost găsit în conversația activă. Nu am apăsat nimic."
        }
    }

    /** User explicitly approved the displayed recipient and text in EVA before opening WhatsApp. */
    fun sendConfirmedWhatsAppMessage(recipient: String, message: String): String {
        val service = EvaAccessibilityService.instance
            ?: return "⚠️ Activează Accessibility EVA înainte de trimiterea confirmată."
        if (recipient.isBlank() || message.isBlank()) return "⚠️ Destinatarul și mesajul sunt obligatorii."
        val phone = extractPhoneNumber(recipient.trim()) ?: lookupContactPhoneByName(recipient.trim())
            ?: return "⚠️ Contact inexistent sau ambiguu; nu am trimis nimic."
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message.trim())}")
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null)
                return "⚠️ WhatsApp nu este disponibil pentru deschiderea conversației."
            if (!service.armConfirmedWhatsAppSend(message.trim()))
                return "⚠️ Nu am putut pregăti confirmarea Accessibility."
            context.startActivity(intent)
            "📤 Trimitere autorizată pentru $recipient. EVA va apăsa Trimite numai dacă găsește exact textul confirmat în WhatsApp în 20 secunde. Livrarea nu este garantată."
        } catch (e: Exception) {
            service.cancelConfirmedWhatsAppSend()
            "⚠️ Nu am putut deschide WhatsApp: ${e.message}. Trimiterea nu este confirmată."
        }
    }

    private fun extractPhoneNumber(input: String): String? {
        val value = input.trim()
        // Never infer a phone number from an arbitrary contact name containing digits.
        if (!value.matches(Regex("^\\+?[0-9 ()-]{9,24}$"))) return null
        val digits = value.filter(Char::isDigit)
        if (digits.length !in 9..15) return null
        // No country code can be inferred safely from a national number.
        // Require explicit international prefix for directly entered numbers.
        if (!value.startsWith("+") && !value.startsWith("00")) return null
        return if (value.startsWith("00")) digits.drop(2).takeIf { it.length in 9..15 } else digits
    }

    private fun lookupContactPhoneByName(name: String): String? {
        if (name.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE = ?"
            context.contentResolver.query(uri, projection, selection, arrayOf(name), null)?.use { cursor ->
                val matches = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    val normalized = cursor.getString(2).orEmpty()
                val raw = cursor.getString(1).orEmpty()
                (extractPhoneNumber(normalized) ?: extractPhoneNumber(raw))?.let(matches::add)
                }
                matches.singleOrNull() // Ambiguous contacts must never be selected automatically.
            }
        } catch (_: Exception) { null }
    }

    // Observație UI, nu dovadă de audio/video decodat sau livrare media.
    private val _youTubePlaybackStatus = MutableStateFlow("Nicio redare YouTube solicitată.")
    val youTubePlaybackStatus: StateFlow<String> = _youTubePlaybackStatus.asStateFlow()
    private var youtubeObservationJob: Job? = null
    private val youtubeRequestSequence = java.util.concurrent.atomic.AtomicLong(0L)

    private fun observeYouTubeUi(requestSequence: Long) {
        youtubeObservationJob?.cancel()
        _youTubePlaybackStatus.value = "Se verifică interfața YouTube…"
        youtubeObservationJob = engineScope.launch {
            var youtubeSeen = false
            var consecutivePlayerFrames = 0
            var consecutiveOtherAppFrames = 0
            var lastSnapshotTime = 0L
            repeat(12) { attempt ->
                delay(1000)
                if (requestSequence != youtubeRequestSequence.get()) return@launch
                val service = EvaAccessibilityService.instance
                if (service == null) {
                    if (attempt < 3) return@repeat
                    _youTubePlaybackStatus.value = "Verificare indisponibilă: activează Accessibility EVA."
                    return@launch
                }
                val snapshot = runCatching { service.refreshScreenSnapshot() }.getOrNull()
                    ?: return@repeat
                // Never count a cached or duplicate UI frame as independent evidence.
                if (snapshot.timestamp <= lastSnapshotTime) return@repeat
                lastSnapshotTime = snapshot.timestamp
                if (snapshot.activePackage != "com.google.android.youtube") {
                    consecutivePlayerFrames = 0
                    if (youtubeSeen && ++consecutiveOtherAppFrames >= 2) {
                        _youTubePlaybackStatus.value = "YouTube a ieșit din prim-plan; observația s-a oprit."
                        return@launch
                    }
                    return@repeat
                }
                youtubeSeen = true
                consecutiveOtherAppFrames = 0
                val labels = snapshot.elements.asSequence()
                    .flatMap { sequenceOf(it.contentDescription, it.text) }
                    .map { it.trim().lowercase(java.util.Locale.ROOT) }
                val pauseVisible = labels.any {
                    it == "pause" || it == "pune pauză" || it == "întrerupe" || it == "pause video"
                }
                consecutivePlayerFrames = if (pauseVisible) consecutivePlayerFrames + 1 else 0
                if (consecutivePlayerFrames >= 2) {
                    _youTubePlaybackStatus.value = "Controlul Pauză a fost observat în două cadre consecutive. Audio și identitatea videoclipului rămân neconfirmate."
                    return@launch
                }
            }
            if (requestSequence != youtubeRequestSequence.get()) return@launch
            _youTubePlaybackStatus.value = if (youtubeSeen) {
                "YouTube este deschis; nu există dovezi UI suficiente pentru a confirma redarea."
            } else {
                "Nu am putut confirma că YouTube a ajuns în prim-plan."
            }
        }
    }

    // ── 4. YOUTUBE MUSIC & VIDEO DIRECT PLAYBACK ──────────────────
    fun playOnYouTube(query: String): String {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return "⚠️ Spune ce melodie sau videoclip dorești."
        youtubeObservationJob?.cancel()
        val requestSequence = youtubeRequestSequence.incrementAndGet()
        _youTubePlaybackStatus.value = "Solicitare YouTube în curs…"
        // Resolve explicit YouTube links before treating their text as a search query.
        val videoId = runCatching {
            // Accept a standalone YouTube ID, but never guess an ID from a title.
            if (cleanQuery.matches(Regex("[A-Za-z0-9_-]{11}"))) return@runCatching cleanQuery
            val uri = Uri.parse(cleanQuery)
            val host = (uri.host ?: "").lowercase(java.util.Locale.ROOT)
            val id = when (host) {
                "youtu.be", "www.youtu.be" -> uri.pathSegments.firstOrNull()
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> {
                    when (uri.pathSegments.firstOrNull()) {
                        "watch" -> uri.getQueryParameter("v")
                        "shorts", "live", "embed" -> uri.pathSegments.getOrNull(1)
                        else -> null
                    }
                }
                else -> null
            }
            id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        }.getOrNull()
        if (videoId != null) {
            return try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                observeYouTubeUi(requestSequence)
                "▶️ Am solicitat deschiderea videoclipului $videoId în YouTube. Redarea efectivă nu este confirmată."
            } catch (e: Exception) {
                _youTubePlaybackStatus.value = "YouTube nu a putut fi deschis."
                "⚠️ Nu am putut deschide videoclipul în YouTube: ${e.message ?: "aplicația indisponibilă"}."
            }
        }
        val playIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra("query", cleanQuery)
            putExtra(Intent.EXTRA_TEXT, cleanQuery)
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(playIntent) }.isSuccess) {
            observeYouTubeUi(requestSequence)
            return "▶️ Am solicitat YouTube să redea «$cleanQuery». Nu există confirmare de redare prin acest Intent."
        }
        return try {
            val searchUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, searchUri).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            // A search result is not a playing video: do not keep an older playback observation alive.
            youtubeObservationJob?.cancel()
            _youTubePlaybackStatus.value = "Căutare deschisă; selectează un rezultat pentru redare."
            "🔎 Am deschis căutarea în YouTube pentru «$cleanQuery». Selectează videoclipul; redarea nu a fost pornită automat."
        } catch (e: Exception) {
            _youTubePlaybackStatus.value = "YouTube nu a putut fi deschis."
            "⚠️ YouTube nu a putut fi deschis: ${e.message ?: "aplicația indisponibilă"}"
        }
    }

    // ── 5. REAL-TIME SCREEN VISION ("Să vadă în timp real ecranul telefonului") ──
    fun inspectLiveScreen(evaInternalScreenContext: String = ""): String {
        val acc = EvaAccessibilityService.instance
        val snap = acc?.refreshScreenSnapshot() ?: EvaAccessibilityService.latestSnapshot.value
        val telemetry = getDeviceTelemetry()
        val (liveW, liveH) = acc?.getLiveDisplaySize() ?: (
            context.resources.displayMetrics.widthPixels to context.resources.displayMetrics.heightPixels
        )
        val orientationLabel = if (liveW > liveH) "Landscape (Orizontal - Joc/Video)" else "Portrait (Vertical)"
        val resolution = "${liveW}x${liveH} px ($orientationLabel)"
        val clipPreview = runCatching { readClipboard().take(120) }.getOrDefault("")

        val report = buildString {
            appendLine("👁️ **[Viziune Ecran în Timp Real • EVA-1 Live Screen & Pixel Vision]**")
            appendLine("• **Rezoluție & Orientare Activă:** $resolution")
            appendLine("• **Dispozitiv:** ${telemetry.model} (${telemetry.androidVersion}) • Baterie ${telemetry.batteryPercent}% • RAM ${telemetry.ramUsedMb}/${telemetry.ramTotalMb} MB")

            if (acc != null) {
                val activePkg = snap.activePackage.ifBlank {
                    EvaAccessibilityService.lastForegroundPackage.value.ifBlank { context.packageName }
                }
                appendLine("• **Serviciu Viziune Globală (Accessibility + Pixel Screenshot):** 🟢 ACTIV")
                appendLine("• **Pachet Aplicație/Joc în Prim-Plan:** `$activePkg`")
                appendLine("• **Titlu Fereastră Activă:** ${snap.activeWindowTitle.ifBlank { activePkg }}")

                val menuNode = acc.findGameMenuNodeButton(snap)
                if (menuNode != null) {
                    appendLine("• **Buton Meniu Joc Detectat (UI):** „${menuNode.second}” la (${menuNode.first.centerX}, ${menuNode.first.centerY})")
                }

                if (snap.elements.isNotEmpty()) {
                    appendLine()
                    appendLine("🔍 **Elemente Vizibile pe Ecran (${snap.elements.size} noduri detectate):**")
                    val texts = snap.elements.filter { it.text.isNotBlank() || it.contentDescription.isNotBlank() }
                    texts.take(18).forEachIndexed { idx, el ->
                        val label = el.text.ifBlank { el.contentDescription }
                        val roleBadge = when {
                            el.isEditable -> "[Câmp Input]"
                            el.isClickable -> "[Buton/Click]"
                            else -> "[Text]"
                        }
                        appendLine("  ${idx + 1}. $roleBadge **„${label.take(70)}”** la (${el.centerX}, ${el.centerY})")
                    }
                } else {
                    appendLine()
                    appendLine("🎮 **Mod Fereastră OpenGL / Unity / Vulkan Detectat:** Jocul randează direct pe suprafață grafică 3D. EVA-1 folosește **Viziunea pe Pixeli (`takeScreenshot`)** pentru a vedea butoanele din meniu (PLAY / START / BATTLE) și obstacolele din joc!")
                }
            } else {
                appendLine("• **Fereastră Activă Curentă:** Aplicația EVA-1 Companion (`${context.packageName}`)")
                if (evaInternalScreenContext.isNotBlank()) {
                    appendLine()
                    appendLine("📋 **Conținut Vizibil pe Ecranul Curent EVA-1:**")
                    appendLine(evaInternalScreenContext)
                }
                appendLine()
                appendLine("⚠️ **Activare Necesară pentru Jocuri Externe:** Apasă pe butonul **„Activează Serviciu Global”** din tab-ul **OS Control** și activează **„EVA-1 Viziune Ecran & Autopilot Jocuri”** pentru ca EVA să poată vedea meniul jocurilor instalate și să apese singură pe PLAY/START!")
            }

            val mpAnalysis = MediaProjectionVisionEngine.latestVisionAnalysis.value
            appendLine()
            appendLine("📡 **[MediaProjection API & AI Vision Game Analyzer]:**")
            appendLine("• **Captură MediaProjection 60-FPS:** ${if (MediaProjectionVisionEngine.isMediaProjectionActive.value) "🟢 ACTIVĂ (${MediaProjectionVisionEngine.framesCapturedCount.value} cadre procesate)" else "🟡 Standby (Folosește Pixel Screenshot / Apasă „Activează MediaProjection” în OS Control)"}")
            appendLine("• **Stare Joc Detectată:** **${mpAnalysis.gameState.displayName}**")
            appendLine("• **Bare de Viață & Resurse (HUD):** HP Jucător **${mpAnalysis.healthBars.playerHpPercent.toInt()}%** • HP Inamic/Boss **${mpAnalysis.healthBars.enemyOrBossHpPercent.toInt()}%** • Scut/Nitro/Elixir **${mpAnalysis.healthBars.secondaryBarPercent.toInt()}%**")
            if (mpAnalysis.detectedButtons.isNotEmpty()) {
                appendLine("• **Butoane Joc Identificate (${mpAnalysis.detectedButtons.size}):** " +
                    mpAnalysis.detectedButtons.take(4).joinToString(" | ") { "${it.label} (${it.centerX.toInt()},${it.centerY.toInt()})" }
                )
            }
            appendLine("• **Directivă Model AI Vision:** ${mpAnalysis.aiVisionDirective}")

            if (clipPreview.isNotBlank()) {
                appendLine("• **Conținut Clipboard Curent:** „$clipPreview”")
            }
            if (_isGameBotRunning.value) {
                appendLine("• **Autopilot Jocuri:** 🟢 ACTIV (${_activeGameMode.value.displayName}, ${_gameBotActionsCount.value} acțiuni executate)")
            }
        }.trim()

        _lastScreenVisionReport.value = report
        return report
    }

    fun startMediaProjection(resultCode: Int, data: Intent) {
        EvaMediaProjectionService.startServiceWithProjection(context, resultCode, data)
    }

    fun stopMediaProjection() {
        EvaMediaProjectionService.stopProjectionService(context)
    }

    /**
     * Declanșează imediat o captură și analiză completă în modelul AI Vision (MediaProjection / Accessibility Bitmap),
     * identificând butoanele, barele de viață (HP) și starea jocului.
     */
    suspend fun triggerAiVisionFrameAnalysis(): MediaProjectionVisionAnalysis {
        val acc = EvaAccessibilityService.instance
        val (w, h) = acc?.getLiveDisplaySize() ?: (
            context.resources.displayMetrics.widthPixels.coerceAtLeast(720) to
                context.resources.displayMetrics.heightPixels.coerceAtLeast(1280)
        )
        val bmp = MediaProjectionVisionEngine.getLatestProjectedBitmap()
            ?: acc?.captureLiveGameBitmap()

        if (bmp != null) {
            val mmResult = multiModalVision.analyzeScreenFrame(
                bitmap = bmp,
                screenWidth = w.toFloat(),
                screenHeight = h.toFloat(),
                activeGameTitle = _targetGameName.value,
                activeMode = _activeGameMode.value
            )

            _lastScreenVisionReport.value = buildString {
                append("👁️ [${mmResult.providerUsed.displayName}] ${mmResult.modelName} (${mmResult.latencyMs}ms)\n")
                append("• Stare Joc: ${mmResult.gameState.displayName} | HP: ${mmResult.playerHpPercent.toInt()}% | Inamic: ${mmResult.enemyHpPercent.toInt()}%\n")
                append("• Butoane UI Detectate: ${mmResult.interactiveButtons.size} (ex: ${mmResult.interactiveButtons.take(3).joinToString { "${it.label} (${(it.normalizedX * 100).toInt()}%, ${(it.normalizedY * 100).toInt()}%)" }})\n")
                if (mmResult.enemies.isNotEmpty()) {
                    append("• Inamici în FOV: ${mmResult.enemies.size} | Crosshair: ${if (mmResult.hasTargetInCrosshair) "DA (În Reticul)" else "NU"}\n")
                }
                append("• Recomandare Gestuală: ${mmResult.recommendedAction} (${mmResult.recommendedGesture.motion}) → ${mmResult.recommendedGesture.reasoning}")
            }

            return MediaProjectionVisionEngine.analyzeGameFrameForAiVision(
                bitmap = bmp,
                screenWidth = w,
                screenHeight = h,
                activeGameTitle = _targetGameName.value,
                activeMode = _activeGameMode.value
            )
        }
        return MediaProjectionVisionEngine.latestVisionAnalysis.value
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ── 6. AUTONOMOUS GAME BOT ("Să se joace pe jocurile de pe telefon în locul meu") ──
    fun cleanGameQuery(rawInput: String): String {
        val trimmedRaw = rawInput.trim()
        // Dacă intrarea este deja un nume de pachet Android (ex: com.tripledot.solitaire), îl returnăm intact!
        if (trimmedRaw.contains(".") && !trimmedRaw.contains(" ")) {
            return trimmedRaw
        }
        val cleaned = rawInput
            .replace(
                Regex(
                    "(?i)\\b(te rog|vreau|să|sa|te|joci|joacă-te|joaca-te|joacă|joaca|juca|joace|" +
                        "singur|autonom|automat|autopilot|în locul meu|in locul meu|pe telefon|pe telefonul meu|" +
                        "din telefon|din telefonul meu|la mine|pe jocul|pe jocurile|jocurile|jocul|joc|" +
                        "instalat|instalate|aplicatia|aplicația|meu|mele|pe care|care|îl|il|le|am|" +
                        "îi|ii|ți le|ti le|cer|deschide|pornește|porneste|intră|intra|" +
                        "lansează|lanseaza|acum|și|si|cu|la|în|in|pe|un|o|de)\\b"
                ),
                " "
            )
            .replace(Regex("(?i)\\b(pugg|pub\\s*g|pugb|pubg\\s*mobile)\\b"), "PUBG Mobile")
            .replace(Regex("(?i)\\b(call\\s+off\\s+duty|call\\s+of\\s+duty|cod\\s*mobile|codm|warzone)\\b"), "Call of Duty")
            .replace(Regex("(?i)\\b(free\\s*fire)\\b"), "Free Fire")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned
    }

    private fun findMatchingInstalledGame(cleanTarget: String): InstalledAppInfo? {
        val apps = getInstalledApps().filter { it.packageName != context.packageName }
        if (apps.isEmpty()) return null
        if (cleanTarget.isBlank()) return null

        val lower = cleanTarget.lowercase()

        // 0. Potrivire directă pe packageName exact
        apps.firstOrNull { it.packageName.equals(cleanTarget, ignoreCase = true) }?.let { return it }

        // 1. Potrivire pe pachete oficiale cunoscute (Solitaire, Tetris, PUBG Mobile, Call of Duty, FC Mobile, Clash, NFS, GTA etc.)
        val knownPackages = when {
            lower.contains("solit") || lower.contains("klondike") || lower.contains("spider") ||
                lower.contains("freecell") || lower.contains("tripledot") -> listOf(
                "com.tripledot.solitaire", "com.mobilityware.solitaire", "com.brainium.solitairefree",
                "com.microsoft.microsoftsolitairecollection", "com.king.pyramidsolitairesaga"
            )
            lower.contains("tetris") || lower.contains("brick") -> listOf(
                "com.example.tetris", "com.ea.game.tetris2011_row", "com.n3twork.tetris", "com.playstudios.tetris"
            )
            lower.contains("pubg") || lower.contains("pugg") -> listOf(
                "com.tencent.ig", "com.pubg.imobile", "com.pubg.newstate", "com.rekoo.pubgm", "com.vng.pubgmobile"
            )
            lower.contains("call of duty") || lower.contains("cod") || lower.contains("warzone") -> listOf(
                "com.activision.callofduty.shooter", "com.garena.game.codm", "com.activision.callofduty.warzone", "com.tencent.tmgp.kr.codm"
            )
            lower.contains("free fire") -> listOf("com.dts.freefireth", "com.dts.freefiremax")
            lower.contains("manager") || lower.contains("top eleven") || lower.contains("osm") -> listOf(
                "com.sega.soccer", "com.netflix.NGP.FootballManagerMobile", "eu.nordeus.topeleven.android",
                "com.gamebasics.osm", "com.invinciblesstudio.soccermanager"
            )
            lower.contains("fifa") || lower.contains("fc mobile") || lower.contains("fotbal") || lower.contains("football") ||
                lower.contains("efootball") || lower.contains("pes") || lower.contains("dls") || lower.contains("dream league") -> listOf(
                "com.ea.gp.fifamobile", "jp.konami.pesam", "com.firsttouchgames.dls7", "com.firsttouchgames.dls3"
            )
            lower.contains("clash of clans") || lower.contains("coc") -> listOf("com.supercell.clashofclans")
            lower.contains("clash royale") -> listOf("com.supercell.clashroyale")
            lower.contains("brawl") -> listOf("com.supercell.brawlstars")
            lower.contains("need for speed") || lower.contains("nfs") || lower.contains("asphalt") || lower.contains("real racing") ||
                lower.contains("carx") -> listOf(
                "com.ea.game.nfs14_row", "com.ea.games.nfs13_row", "com.gameloft.android.ANMP.GloftA9HM",
                "com.ea.games.r3_row", "com.carxtech.sr"
            )
            lower.contains("gta") || lower.contains("san andreas") || lower.contains("vice city") || lower.contains("grand theft") -> listOf(
                "com.rockstargames.gtasa", "com.rockstargames.gtavc", "com.rockstargames.gta3",
                "com.netflix.NGP.GTASanAndreasDefinitiveEdition", "com.gameloft.android.ANMP.GloftGGHM"
            )
            lower.contains("minecraft") -> listOf("com.mojang.minecraftpe")
            lower.contains("subway") -> listOf("com.kiloo.subwaysurf")
            lower.contains("candy") -> listOf("com.king.candycrushsaga", "com.king.candycrushsodasaga")
            lower.contains("roblox") -> listOf("com.roblox.client")
            else -> emptyList()
        }
        if (knownPackages.isNotEmpty()) {
            apps.firstOrNull { app ->
                knownPackages.any { pkg -> app.packageName.equals(pkg, ignoreCase = true) }
            }?.let { return it }
        }

        // 2. Potrivire directă pe nume complet sau pachet (prioritizând jocurile instalate)
        val sortedByGamePriority = apps.sortedByDescending { it.isGame }
        sortedByGamePriority.firstOrNull {
            it.appName.equals(cleanTarget, ignoreCase = true) ||
                it.appName.contains(cleanTarget, ignoreCase = true) ||
                it.packageName.contains(cleanTarget, ignoreCase = true)
        }?.let { return it }

        // 3. Potrivire pe cuvinte cheie relevante (ignorând stop-words comune)
        val ignoredStopWords = setOf("com", "android", "google", "app", "apps", "example", "test", "main")
        val tokens = cleanTarget.split(" ")
            .map { it.trim().lowercase() }
            .filter { it.length >= 3 && it !in ignoredStopWords }
        if (tokens.isNotEmpty()) {
            sortedByGamePriority.firstOrNull { app ->
                val appLower = app.appName.lowercase()
                val pkgTail = app.packageName.substringAfterLast('.').lowercase()
                tokens.any { tk -> appLower.contains(tk) || pkgTail.contains(tk) }
            }?.let { return it }
        }
        return null
    }

    fun manualMoveLane(delta: Int) {
        val cur = _liveGameState.value
        val nextLane = (cur.playerLane + delta).coerceIn(0, 2)
        _liveGameState.value = cur.copy(
            playerLane = nextLane,
            lastAiDecision = "🕹️ Control Manual: Schimbat pe banda ${nextLane + 1}"
        )
    }

    fun manualJumpOrAttack() {
        val cur = _liveGameState.value
        _liveGameState.value = cur.copy(
            isJumping = true,
            score = cur.score + 35,
            bossHpPercent = (cur.bossHpPercent - 6f).coerceIn(5f, 100f),
            lastAiDecision = "🔥 Acțiune Directă: Salt & Atac Special (+35 pct)!"
        )
    }

    fun startGameAutopilot(
        targetGame: String = "",
        mode: GameBotMode = GameBotMode.AUTO_ADAPTIVE,
        launchExternalIfNeeded: Boolean = true,
        forceInternalDemo: Boolean = false
    ): String {
        gameBotJob?.cancel()
        val isExplicitInternalDemo = forceInternalDemo ||
            targetGame.equals("DEMO_INTERN", ignoreCase = true) ||
            targetGame.contains("Cyber Runner", ignoreCase = true)

        val cleanTarget = if (isExplicitInternalDemo) "" else cleanGameQuery(targetGame)

        // Dacă utilizatorul nu a scris numele exact al jocului (sau a apăsat direct „Joacă în Locul Meu” / a cerut în Chat „joacă pe jocurile mele”),
        // selectăm automat jocul instalat pe telefon (sau aplicația externă aflată deja pe ecran)!
        val matchedApp: InstalledAppInfo? = when {
            isExplicitInternalDemo -> null
            cleanTarget.isNotBlank() -> findMatchingInstalledGame(cleanTarget)
            else -> {
                val fgPkg = EvaAccessibilityService.lastForegroundPackage.value
                val apps = getInstalledApps()
                val fgMatch = if (fgPkg.isNotBlank() && fgPkg != context.packageName) {
                    apps.firstOrNull { it.packageName == fgPkg }
                } else null
                fgMatch ?: getInstalledGames().firstOrNull()
            }
        }

        val pkgLower = matchedApp?.packageName?.lowercase().orEmpty()
        val nameLower = (matchedApp?.appName ?: cleanTarget).lowercase()

        val resolvedMode = when {
            mode != GameBotMode.AUTO_ADAPTIVE -> mode

            nameLower.contains("solit") || nameLower.contains("klondike") || nameLower.contains("spider") ||
                nameLower.contains("freecell") || nameLower.contains("patience") ||
                nameLower.contains("carte") || nameLower.contains("carti") || nameLower.contains("cărți") ||
                pkgLower.contains("solit") || pkgLower.contains("tripledot") ||
                pkgLower.contains("mobilityware") || pkgLower.contains("klondike") -> GameBotMode.SOLITAIRE_CARDS

            nameLower.contains("blast") || nameLower.contains("woodoku") || nameLower.contains("block puzzle") ||
                nameLower.contains("jigsaw") || nameLower.contains("drag") ||
                pkgLower.contains("blockblast") || pkgLower.contains("woodoku") -> GameBotMode.TOUCH_DRAG_PUZZLE

            nameLower.contains("tetris") || nameLower.contains("brick") ||
                nameLower.contains("2048") || nameLower.contains("arcade") || nameLower.contains("snake") ||
                pkgLower.contains("tetris") || pkgLower.contains("brick") -> GameBotMode.TETRIS_ARCADE

            nameLower.contains("pubg") || nameLower.contains("pugg") ||
                nameLower.contains("call of duty") || nameLower.contains("cod") ||
                nameLower.contains("warzone") || nameLower.contains("free fire") ||
                nameLower.contains("standoff") || nameLower.contains("counter") ||
                nameLower.contains("shooter") || nameLower.contains("fps") ||
                pkgLower.contains("tencent.ig") || pkgLower.contains("callofduty") ||
                pkgLower.contains("freefire") || pkgLower.contains("pubg") -> GameBotMode.FPS_BATTLE_ROYALE

            nameLower.contains("manager") || nameLower.contains("top eleven") ||
                nameLower.contains("osm") || nameLower.contains("fm ") ||
                pkgLower.contains("topeleven") || pkgLower.contains("soccermanager") ||
                pkgLower.contains("footballmanager") || pkgLower.contains("osm") -> GameBotMode.FOOTBALL_MANAGER

            nameLower.contains("fotbal") || nameLower.contains("football") ||
                nameLower.contains("fifa") || nameLower.contains("fc mobile") ||
                nameLower.contains("fc 2") || nameLower.contains("efootball") ||
                nameLower.contains("pes") || nameLower.contains("dls") ||
                nameLower.contains("dream league") || nameLower.contains("soccer") ||
                pkgLower.contains("fifamobile") || pkgLower.contains("pesam") ||
                pkgLower.contains("dls") -> GameBotMode.FOOTBALL_SPORTS

            nameLower.contains("clash of clans") || nameLower.contains("clash royale") ||
                nameLower.contains("clash") || nameLower.contains("boom beach") ||
                nameLower.contains("lords mobile") ||
                pkgLower.contains("clashofclans") || pkgLower.contains("clashroyale") -> GameBotMode.CLASH_BASE_STRATEGY

            nameLower.contains("need for speed") || nameLower.contains("nfs") ||
                nameLower.contains("asphalt") || nameLower.contains("real racing") ||
                nameLower.contains("carx") || nameLower.contains("mașini") ||
                nameLower.contains("masini") || nameLower.contains("curse") ||
                nameLower.contains("racing") ||
                pkgLower.contains("nfs") || pkgLower.contains("glofta9") ||
                pkgLower.contains("r3_row") || pkgLower.contains("carx") -> GameBotMode.RACING_NFS

            nameLower.contains("gta") || nameLower.contains("san andreas") ||
                nameLower.contains("vice city") || nameLower.contains("grand theft") ||
                nameLower.contains("gangstar") || nameLower.contains("minecraft") ||
                nameLower.contains("aventur") || nameLower.contains("open world") ||
                pkgLower.contains("gtasa") || pkgLower.contains("gtavc") ||
                pkgLower.contains("gta") || pkgLower.contains("minecraft") -> GameBotMode.OPEN_WORLD_GTA

            nameLower.contains("subway") || nameLower.contains("temple") ||
                nameLower.contains("runner") || nameLower.contains("hill") ||
                nameLower.contains("sonic") -> GameBotMode.ENDLESS_RUNNER

            nameLower.contains("candy") || nameLower.contains("puzzle") ||
                nameLower.contains("match") || nameLower.contains("șah") ||
                nameLower.contains("sah") -> GameBotMode.PUZZLE_MATCH

            nameLower.contains("click") || nameLower.contains("idle") ||
                nameLower.contains("tap") || nameLower.contains("tycoon") -> GameBotMode.CLICKER_IDLE

            nameLower.contains("rpg") || nameLower.contains("brawl") ||
                nameLower.contains("roblox") || nameLower.contains("legends") -> GameBotMode.RPG_STRATEGY

            else -> mode
        }

        val displayTitle = when {
            matchedApp != null -> matchedApp.appName
            cleanTarget.isNotBlank() -> cleanTarget.replaceFirstChar { it.uppercase() }
            else -> "EVA-1 Cyber Runner & AI Arena"
        }

        var externalLaunched = false
        var externalPkg = ""
        if (!isExplicitInternalDemo) {
            if (matchedApp != null) {
                externalPkg = matchedApp.packageName
            } else if (cleanTarget.contains(".")) {
                externalPkg = cleanTarget
            }
        }

        val profile = gameCognition.attachToGame(displayTitle, externalPkg)
        val finalResolvedMode = if (resolvedMode == GameBotMode.AUTO_ADAPTIVE) profile.genre else resolvedMode

        // Setăm contextul jocului activ în motorul MediaProjection pentru analiza de flux video în timp real
        MediaProjectionVisionEngine.setActiveGameContext(displayTitle, finalResolvedMode)

        // Dacă MediaProjection nu este încă activ și avem o punte activă către MainActivity,
        // cerem MAI ÎNTÂI permisiunea MediaProjection și îi transmitem pachetul jocului (externalPkg)
        // pentru a fi lansat automat imediat după ce utilizatorul apasă „Start now” în dialogul Android!
        val requestedMpFirst = if (!MediaProjectionVisionEngine.isMediaProjectionActive.value && MediaProjectionRequester.isAvailable) {
            MediaProjectionRequester.requestMediaProjection(
                launchPackageAfterGrant = if (launchExternalIfNeeded && externalPkg.isNotBlank()) externalPkg else null
            )
        } else {
            false
        }

        if (externalPkg.isNotBlank()) {
            externalLaunched = if (launchExternalIfNeeded && !requestedMpFirst) {
                launchApp(externalPkg)
            } else {
                true
            }
        }

        _activeGameMode.value = finalResolvedMode
        _targetGameName.value = displayTitle
        _gameBotActionsCount.value = 0
        _isGameBotRunning.value = true
        clashCurrentState = ClashState.VILLAGE_HOME
        clashScoutCount = 0
        clashWaveStep = 0
        clashBattleWaitCycles = 0
        lastSolitaireBoardSig = 0L
        solitaireTriedMovesOnBoard.clear()
        lastSolitaireActionWasDraw = false
        solitaireStockCycleCount = 0

        // Afișăm automat Panoul Plutitor (Floating HUD Overlay) peste joc dacă Serviciul Global este activ
        val tierBadge = profile.skillTier.badge
        EvaAccessibilityService.instance?.showOrUpdateFloatingGameHud(
            isRunning = true,
            gameTitle = displayTitle,
            modeName = "${profile.skillTier.displayName} $tierBadge",
            statusLine = "🎥 Flux Video MediaProjection + Autopilot în $displayTitle..."
        )

        _liveGameState.value = LiveGameArenaState(
            isRunning = true,
            gameTitle = displayTitle,
            mode = finalResolvedMode,
            playerLane = 1,
            isJumping = false,
            score = 0,
            coins = 0,
            combo = 1,
            level = 1,
            bossHpPercent = 100f,
            obstacles = listOf(
                LiveGameObstacle(id = 1, lane = 0, yProgress = 0.15f, isCoin = false),
                LiveGameObstacle(id = 2, lane = 1, yProgress = 0.38f, isCoin = true),
                LiveGameObstacle(id = 3, lane = 2, yProgress = 0.62f, isCoin = false)
            ),
            lastAiDecision = "🤖 AI Autopilot a preluat controlul în „$displayTitle” (Flux Video MediaProjection + Controale Tactile)!",
            externalAppLaunched = externalLaunched,
            externalPackageName = externalPkg
        )

        _gameBotStatus.value = "🟢 Autopilot AI ACTIV: joacă „$displayTitle” (${finalResolvedMode.displayName})"

        // Pornim Bucla Strategică (Sistemul 2 - LLM / VLM la 5-10 secunde) pe thread dedicat de fundal
        dualBrainEngine.startStrategicCommanderLoop(displayTitle)

        gameBotJob = engineScope.launch {
            if (externalLaunched && launchExternalIfNeeded) {
                // Așteptăm 1.8 secunde ca jocul instalat să se deschidă în prim-plan
                delay(1800)
            }

            var nextObsId = 10
            var tickCount = 0
            var staticMenuStreak = 0

            while (isActive && _isGameBotRunning.value) {
                tickCount++
                val cur = _liveGameState.value
                val currentMode = _activeGameMode.value

                // 1. Avansăm obstacolele și monedele în Arena Live (și sincronizăm cu jocul extern)
                val speed = (0.09f + (cur.level * 0.008f)).coerceAtMost(0.16f)
                val movedObstacles = cur.obstacles.mapNotNull { obs ->
                    val nextY = obs.yProgress + speed
                    if (nextY >= 1.05f) null else obs.copy(yProgress = nextY)
                }.toMutableList()

                if (movedObstacles.size < 5 && (tickCount % 2 == 0)) {
                    val spawnLane = Random.nextInt(0, 3)
                    val isCoin = Random.nextFloat() < 0.42f
                    movedObstacles.add(
                        LiveGameObstacle(
                            id = nextObsId++,
                            lane = spawnLane,
                            yProgress = 0.02f,
                            isCoin = isCoin
                        )
                    )
                }

                // 2. Motorul de Decizie AI Autopilot
                var newLane = cur.playerLane
                var jumping = false
                var decision = cur.lastAiDecision
                var addedScore = 12 * cur.combo
                var addedCoins = 0
                var newCombo = cur.combo

                val dangerInCurrentLane = movedObstacles.firstOrNull {
                    !it.isCoin && it.lane == newLane && it.yProgress in 0.54f..0.88f
                }

                if (dangerInCurrentLane != null) {
                    val safeLanes = (0..2).filter { candidateLane ->
                        movedObstacles.none { !it.isCoin && it.lane == candidateLane && it.yProgress in 0.45f..0.92f }
                    }
                    if (safeLanes.isNotEmpty()) {
                        val chosen = safeLanes.minByOrNull { kotlin.math.abs(it - newLane) } ?: safeLanes.first()
                        val dirName = if (chosen < newLane) "STÂNGA" else "DREAPTA"
                        newLane = chosen
                        newCombo = (newCombo + 1).coerceAtMost(10)
                        addedScore += 45 * newCombo
                        decision = "⚡ AI Eschivă ($dirName): Obstacol evitat pe banda ${chosen + 1}! (Combo x$newCombo)"
                        if (isExplicitInternalDemo) {
                            EvaGameDecisionLogger.logSimpleTestGameDecision(
                                step = tickCount,
                                playerLane = cur.playerLane,
                                dangerObsLane = dangerInCurrentLane.lane,
                                dangerObsY = dangerInCurrentLane.yProgress,
                                bonusCoinLane = null,
                                decisionSummary = decision,
                                actionType = "ESCHIVĂ_BANDĂ",
                                laneChosen = newLane,
                                jumped = false
                            )
                        }
                    } else {
                        jumping = true
                        addedScore += 60
                        decision = "🚀 AI Salt Acrobatic peste obstacol la y=${"%.2f".format(dangerInCurrentLane.yProgress)}!"
                        if (isExplicitInternalDemo) {
                            EvaGameDecisionLogger.logSimpleTestGameDecision(
                                step = tickCount,
                                playerLane = cur.playerLane,
                                dangerObsLane = dangerInCurrentLane.lane,
                                dangerObsY = dangerInCurrentLane.yProgress,
                                bonusCoinLane = null,
                                decisionSummary = decision,
                                actionType = "SALT_ACROBATIC",
                                laneChosen = newLane,
                                jumped = true
                            )
                        }
                    }
                } else {
                    val coinNearby = movedObstacles.firstOrNull {
                        it.isCoin && it.yProgress in 0.45f..0.85f
                    }
                    if (coinNearby != null && coinNearby.lane != newLane) {
                        val targetLaneSafe = movedObstacles.none {
                            !it.isCoin && it.lane == coinNearby.lane && it.yProgress in 0.45f..0.90f
                        }
                        if (targetLaneSafe) {
                            newLane = coinNearby.lane
                            decision = "🪙 AI Colectare: Trecere pe banda ${newLane + 1} pentru monedă bonus!"
                        }
                    }
                }

                val remainingObstacles = mutableListOf<LiveGameObstacle>()
                for (obs in movedObstacles) {
                    if (obs.isCoin && obs.lane == newLane && obs.yProgress >= 0.78f) {
                        addedCoins += 1
                        addedScore += 100
                        decision = "✨ AI Bonus: Monedă colectată pe banda ${newLane + 1}! (+100 pct)"
                        if (isExplicitInternalDemo) {
                            EvaGameDecisionLogger.logSimpleTestGameDecision(
                                step = tickCount,
                                playerLane = cur.playerLane,
                                dangerObsLane = null,
                                dangerObsY = null,
                                bonusCoinLane = newLane,
                                decisionSummary = decision,
                                actionType = "COLECTARE_MONEDĂ",
                                laneChosen = newLane,
                                jumped = jumping
                            )
                        }
                    } else {
                        remainingObstacles.add(obs)
                    }
                }

                // 3. Sistemul 1: Reflexe Motorii Rapide în jocul extern (frecvență fluidă 35-50ms)
                var reflexDelayMs = 0L
                var actionGestureDispatched = false
                var verificationStartMs = 0L
                val isExternalGameForeground = (!isExplicitInternalDemo && externalPkg.isNotBlank() && externalPkg != context.packageName) ||
                    (EvaAccessibilityService.lastForegroundPackage.value.isNotBlank() && EvaAccessibilityService.lastForegroundPackage.value != context.packageName)

                // În jocurile externe rulăm continuu la viteză de reflex; în demo intern păstrăm ritmul de 160ms
                val shouldTick = isExternalGameForeground || (tickCount % 2 == 0)

                if (shouldTick) {
                    val step = _gameBotActionsCount.value + 1
                    _gameBotActionsCount.value = step
                    val acc = EvaAccessibilityService.instance
                    val snap = acc?.refreshScreenSnapshot(externalPkg)
                    val activePkg = snap?.activePackage
                        ?.ifBlank { EvaAccessibilityService.lastForegroundPackage.value }
                        .orEmpty()

                    if (isExternalGameForeground) {
                        if (acc != null) {
                            val liveTitle = _targetGameName.value.ifBlank { activePkg.substringAfterLast('.') }
                            val inferredLiveMode = if (currentMode == GameBotMode.AUTO_ADAPTIVE) {
                                gameCognition.inferGenreFromIdentity(liveTitle, activePkg)
                            } else {
                                currentMode
                            }
                            val curVisual = acc.analyzeLiveGameScreen(
                                activeGameTitle = "$liveTitle $activePkg",
                                activeMode = inferredLiveMode
                            )
                            val effectiveLoopMode = when {
                                inferredLiveMode != GameBotMode.AUTO_ADAPTIVE -> inferredLiveMode
                                curVisual.detectedCategory == DetectedGameCategory.CARD_SOLITAIRE_TABLE -> GameBotMode.SOLITAIRE_CARDS
                                else -> currentMode
                            }
                            if (effectiveLoopMode != currentMode && currentMode == GameBotMode.AUTO_ADAPTIVE) {
                                _activeGameMode.value = effectiveLoopMode
                            }
                            val actionResult = executeExternalGameVisionStep(
                                acc = acc,
                                snap = snap ?: EvaAccessibilityService.latestSnapshot.value,
                                visual = curVisual,
                                resolvedMode = effectiveLoopMode,
                                step = step,
                                arenaLane = newLane,
                                arenaJumping = jumping,
                                staticMenuStreak = staticMenuStreak,
                                onUpdateStaticStreak = { staticMenuStreak = it },
                                onOverrideLane = { newLane = it }
                            )
                            if (actionResult.status.isNotBlank()) {
                                decision = actionResult.status
                            }
                            reflexDelayMs = actionResult.pauseAfterActionMs
                            actionGestureDispatched = actionResult.gestureDispatched
                            verificationStartMs = if (actionGestureDispatched) System.currentTimeMillis() else 0L

                            // Învățare prin Întărire (Reinforcement Learning) & Actualizare Nivel AI
                            gameCognition.evaluateActionOutcome(
                                packageName = activePkg,
                                hpBefore = cur.bossHpPercent,
                                hpAfter = curVisual.playerHpPercent,
                                frameDiff = curVisual.frameDiffScore,
                                enemyInCrosshair = curVisual.hasTargetInCrosshair,
                                actionExecuted = actionResult
                            )

                            // Jurnalizare Structurată a Deciziei în Logcat (Tag: EVA_GAME_DECISION)
                            EvaGameDecisionLogger.logExternalComplexDecision(
                                step = step,
                                gameTitle = _targetGameName.value.ifBlank { activePkg.substringAfterLast('.') },
                                packageName = activePkg,
                                displayWidth = curVisual.width,
                                displayHeight = curVisual.height,
                                isLandscape = curVisual.isLandscape,
                                visionState = curVisual.visionGameState.name,
                                frameDiff = curVisual.frameDiffScore,
                                staticStreak = staticMenuStreak,
                                playerHp = curVisual.playerHpPercent,
                                enemyHp = curVisual.enemyHpPercent,
                                secondaryResource = curVisual.secondaryBarPercent,
                                identifiedButtons = curVisual.detectedUiButtons,
                                menuTarget = curVisual.detectedMenuButton,
                                menuLabel = curVisual.menuButtonLabel,
                                chosenActionDescription = actionResult.status,
                                actionCategory = actionResult.actionCategory,
                                targetCoordinates = actionResult.targetCoordinates,
                                gestureResult = actionResult.gestureDispatched,
                                reasoning = actionResult.reasoning.ifBlank { actionResult.status },
                                whyFailurePotential = when {
                                    !actionResult.gestureDispatched -> "Gestul a eșuat la trimitere prin Accesibilitate (posibil permisiune oprită sau fereastră securizată)."
                                    curVisual.detectedUiButtons.isEmpty() && curVisual.visionGameState == DetectedVisionGameState.MAIN_MENU_LOBBY -> "Jocul folosește un motor grafic pur (Unity / Unreal / OpenGL / Vulkan) fără noduri UI standard în ierarhia Android; AI se bazează pe poziționare relativă și analiză cromatică."
                                    staticMenuStreak >= 3 -> "Ecran static persistent ($staticMenuStreak cicluri). Posibil dialog/ofertă nesolicitată sau tranziție lentă a serverului."
                                    curVisual.isLowHealthCritical -> "HP critic identificat; acțiune defensivă prioritară."
                                    else -> "Comportament adaptat dinamicii jocului complex."
                                }
                            )

                            val strategicDirective = dualBrainEngine.activeDirective.value
                            val hudModeLabel = "${strategicDirective.posture.badge} ${strategicDirective.posture.displayName.take(12)}"

                            acc.showOrUpdateFloatingGameHud(
                                isRunning = true,
                                gameTitle = _targetGameName.value.ifBlank { activePkg.substringAfterLast('.') },
                                modeName = hudModeLabel,
                                statusLine = "Reflex #$step: $decision"
                            )
                        } else {
                            decision = "⚠️ Activează „Serviciu Global” (Accesibilitate) în OS Control pentru ca Android să permită atingerile în $displayTitle!"
                        }
                    }
                }

                val nextScore = cur.score + addedScore
                val nextCoins = cur.coins + addedCoins
                val nextLevel = 1 + (nextScore / 1500)
                val nextBossHp = if (cur.bossHpPercent <= 8f) 100f else (cur.bossHpPercent - 3.5f).coerceAtLeast(5f)

                _liveGameState.value = cur.copy(
                    isRunning = true,
                    mode = currentMode,
                    playerLane = newLane,
                    isJumping = jumping,
                    score = nextScore,
                    coins = nextCoins,
                    combo = newCombo,
                    level = nextLevel,
                    bossHpPercent = nextBossHp,
                    obstacles = remainingObstacles,
                    lastAiDecision = decision
                )
                _gameBotStatus.value = "🟢 Pas #${_gameBotActionsCount.value} • Scor: $nextScore • $decision"

                val nextDelay = if (isExternalGameForeground) {
                    // Respectăm durata completă a gestului (ex: 380ms sprint joystick în PUBG sau 460ms drag în Puzzle)
                    // pentru ca următorul pas să nu anuleze (onCancelled) gestul aflat în desfășurare!
                    maxOf(reflexDelayMs.coerceIn(45L, 1250L), if (actionGestureDispatched) 850L else 45L)
                } else {
                    160L
                }
                delay(nextDelay)
                if (isExternalGameForeground && actionGestureDispatched) {
                    val feedback = EvaAccessibilityService.instance?.lastGestureFeedback?.value
                    if (feedback != null && feedback.timestampMs >= verificationStartMs) {
                        EvaGameDecisionLogger.logPostActionVerification(
                            packageName = activePkg,
                            gameTitle = _targetGameName.value.ifBlank { activePkg.substringAfterLast('.') },
                            feedback = feedback
                        )
                        decision = "$decision • ${feedback.note}"
                        _gameBotStatus.value = "🟢 Pas #${_gameBotActionsCount.value} • $decision"
                    } else {
                        Log.w("EVA_GAME_DECISION", "[POST-ACTION][UNVERIFIED] Nu a sosit feedback-ul de verificare pentru $activePkg.")
                    }
                }
            }
        }

        val accConnected = EvaAccessibilityService.isServiceConnected.value
        return buildString {
            appendLine("🎮 **[EVA-1 Autonomous Game Player • JOCURI INSTALATE & FLOATING HUD]**")
            appendLine("• **Joc Selectat & Lansat:** **$displayTitle** (${resolvedMode.displayName})")
            if (externalLaunched) {
                appendLine("• **Pachet Joc pe Telefon:** `$externalPkg`")
                appendLine("• **Control Real în Joc:** EVA-1 detectează automat atât butoanele din interfața jocului (**START / PLAY / Stânga / Dreapta / Rotire / Drop / Atac**), cât și pixelii tablei de joc în timp real!")
            } else {
                appendLine("• **Mod Demo Intern:** Arena Live este activă. Pentru a juca un joc instalat, apasă pe oricare dintre jocurile tale afișate în lista din **OS Control**!")
            }
            if (accConnected) {
                appendLine("• **Serviciu Viziune & Panou Plutitor (HUD):** 🟢 **ACTIV** — Panoul de control EVA-1 apare acum direct deasupra jocului ca să poți da **▶️ Joacă Aici / ⏸ Pauză / ⚙ Mod** fără să ieși din joc!")
            } else {
                appendLine("• ⚠️ **IMPORTANT (OBLIGATORIU ÎN ANDROID PENTRU JOCURI EXTERNE):** Serviciul Global de Accesibilitate nu este încă activat! Mergi în tab-ul **OS Control** -> apasă **„Activează Serviciu Global”** și pornește **„EVA-1 Viziune Ecran & Autopilot Jocuri”**.")
            }
        }.trim()
    }

    /**
     * Execută control inteligent pentru jocuri de tip Tetris, Block Puzzle, 2048, Arcade și Emulatoare:
     *  - Dacă jocul expune butoane reale pe ecran (Stânga / Dreapta / Rotire / Jos-Drop), apasă direct pe ele
     *    ghidat de poziția piesei și de coloana cea mai liberă de pe tablă!
     *  - Dacă jocul randează totul pe Canvas/OpenGL fără noduri UI, combină apăsările pe bara de control
     *    de jos cu gesturi de glisare/tap pe tabla de joc.
     */
    private fun executeTetrisOrArcadeControlsStep(
        acc: EvaAccessibilityService,
        controls: GameplayUiControls,
        visual: GameVisualAnalysis,
        step: Int,
        w: Float,
        h: Float
    ): TacticalGameAction {
        val screenAnalysis = MediaProjectionVisionEngine.latestScreenAnalysis.value
        val tetris = screenAnalysis?.tetrisAnalysis
        val uiElements = screenAnalysis?.detectedElements ?: emptyList()

        // 1. Identificăm butoanele arcade din analiza de pixeli a fluxului MediaProjection
        val leftBtn = uiElements.firstOrNull { it.id == "btn_dpad_left" || it.category == UiElementCategory.BUTTON_DPAD_LEFT }
        val rightBtn = uiElements.firstOrNull { it.id == "btn_dpad_right" || it.category == UiElementCategory.BUTTON_DPAD_RIGHT }
        val rotateBtn = uiElements.firstOrNull { it.id == "btn_dpad_rotate" || it.category == UiElementCategory.BUTTON_DPAD_ROTATE }
        val dropBtn = uiElements.firstOrNull { it.id == "btn_dpad_down" || it.category == UiElementCategory.BUTTON_DPAD_DOWN_DROP }

        // Coordonate precise pe ecranul dispozitivului (identificate din frame-ul MediaProjection sau fallback calibrare arcade)
        val leftX = leftBtn?.centerX ?: (w * 0.30f)
        val leftY = leftBtn?.centerY ?: (h * 0.84f)
        val rightX = rightBtn?.centerX ?: (w * 0.70f)
        val rightY = rightBtn?.centerY ?: (h * 0.84f)
        val rotX = rotateBtn?.centerX ?: (w * 0.50f)
        val rotY = rotateBtn?.centerY ?: (h * 0.84f)
        val dropX = dropBtn?.centerX ?: (w * 0.40f)
        val dropY = dropBtn?.centerY ?: (h * 0.92f)

        // 2. Calculăm coloana piesei active și coloana optimă cu cel mai mare gol / cea mai joasă stivă
        val targetColIdx = tetris?.targetGapColumn ?: ((visual.tetrisBestColumnFrac - 0.12f) / (0.76f / 10f)).toInt().coerceIn(0, 9)
        val pieceColIdx = tetris?.currentPieceColumn ?: ((visual.tetrisPieceColumnFrac - 0.12f) / (0.76f / 10f)).toInt().coerceIn(0, 9)
        val colDiff = tetris?.columnDifference ?: (targetColIdx - pieceColIdx)

        // Rotim piesa periodic la începutul coborârii pentru a evita acumularea verticală pe o singură orientare
        if (step % 6 == 1 && kotlin.math.abs(colDiff) <= 2) {
            val stream = rotateBtn?.let { TouchController.translateElementToMotionStream(it) }
                ?: TouchController.createTapMotionStream(
                    x = rotX,
                    y = rotY,
                    holdMs = 55L,
                    category = UiElementCategory.BUTTON_DPAD_ROTATE,
                    reason = "Rotire piesă (↻)"
                )
            val nodeRotate = controls.rotateOrUpButton
            if (nodeRotate != null) {
                acc.clickElementOrTap(nodeRotate, 55L)
            } else {
                TouchController.executeMotionStream(stream, acc)
            }
            return TacticalGameAction(
                status = "🔄 TouchController (${stream.motionEvents.size} MotionEvents): Rotire piesă (↻) la (${rotX.toInt()}, ${rotY.toInt()})",
                pauseAfterActionMs = 70L,
                targetCoordinates = "(${rotX.toInt()}, ${rotY.toInt()})",
                gestureDispatched = true,
                actionCategory = "ROTATE",
                reasoning = "Ajustare orientare piesă înainte de plasarea în coloana ${targetColIdx + 1}."
            )
        }

        return when {
            colDiff > 0 -> {
                // Deplasare spre DREAPTA prin TouchController MotionEvent Stream
                val stream = rightBtn?.let { TouchController.translateElementToMotionStream(it) }
                    ?: TouchController.createTapMotionStream(
                        x = rightX,
                        y = rightY,
                        holdMs = 55L,
                        category = UiElementCategory.BUTTON_DPAD_RIGHT,
                        reason = "Deplasare dreapta spre col ${targetColIdx + 1}"
                    )
                val nodeRight = controls.rightButton
                if (nodeRight != null) {
                    acc.clickElementOrTap(nodeRight, 55L)
                } else {
                    TouchController.executeMotionStream(stream, acc)
                }
                TacticalGameAction(
                    status = "🧱 TouchController (${stream.motionEvents.size} MotionEvents): DREAPTA ▶ (${rightX.toInt()}, ${rightY.toInt()}) spre col ${targetColIdx + 1}/10",
                    pauseAfterActionMs = 70L,
                    targetCoordinates = "(${rightX.toInt()}, ${rightY.toInt()})",
                    gestureDispatched = true,
                    actionCategory = "MOVE_RIGHT",
                    reasoning = "Piesa este la col ${pieceColIdx + 1}, cel mai mic teanc/gol este la col ${targetColIdx + 1} (diferență: +$colDiff)."
                )
            }
            colDiff < 0 -> {
                // Deplasare spre STÂNGA prin TouchController MotionEvent Stream
                val stream = leftBtn?.let { TouchController.translateElementToMotionStream(it) }
                    ?: TouchController.createTapMotionStream(
                        x = leftX,
                        y = leftY,
                        holdMs = 55L,
                        category = UiElementCategory.BUTTON_DPAD_LEFT,
                        reason = "Deplasare stânga spre col ${targetColIdx + 1}"
                    )
                val nodeLeft = controls.leftButton
                if (nodeLeft != null) {
                    acc.clickElementOrTap(nodeLeft, 55L)
                } else {
                    TouchController.executeMotionStream(stream, acc)
                }
                TacticalGameAction(
                    status = "🧱 TouchController (${stream.motionEvents.size} MotionEvents): STÂNGA ◀ (${leftX.toInt()}, ${leftY.toInt()}) spre col ${targetColIdx + 1}/10",
                    pauseAfterActionMs = 70L,
                    targetCoordinates = "(${leftX.toInt()}, ${leftY.toInt()})",
                    gestureDispatched = true,
                    actionCategory = "MOVE_LEFT",
                    reasoning = "Piesa este la col ${pieceColIdx + 1}, cel mai mic teanc/gol este la col ${targetColIdx + 1} (diferență: $colDiff)."
                )
            }
            else -> {
                // Piesa este aliniată deasupra coloanei țintă -> Apăsăm butonul DOWN/DROP (0.40, 0.92)!
                val stream = dropBtn?.let { TouchController.translateElementToMotionStream(it) }
                    ?: TouchController.createTapMotionStream(
                        x = dropX,
                        y = dropY,
                        holdMs = 70L,
                        category = UiElementCategory.BUTTON_DPAD_DOWN_DROP,
                        reason = "Drop în coloana ${targetColIdx + 1}"
                    )
                val nodeDrop = controls.downOrDropButton
                if (nodeDrop != null) {
                    acc.clickElementOrTap(nodeDrop, 70L)
                } else {
                    TouchController.executeMotionStream(stream, acc)
                }
                TacticalGameAction(
                    status = "⬇️ TouchController (${stream.motionEvents.size} MotionEvents): DROP ▼ în coloana ${targetColIdx + 1} la (${dropX.toInt()}, ${dropY.toInt()})!",
                    pauseAfterActionMs = 80L,
                    targetCoordinates = "(${dropX.toInt()}, ${dropY.toInt()})",
                    gestureDispatched = true,
                    actionCategory = "DROP",
                    reasoning = "Piesa este aliniată perfect pe axa verticală deasupra golului ${targetColIdx + 1} → Coborâre rapidă pentru fixare!"
                )
            }
        }
    }

    /**
     * Execută acțiuni de Drag & Drop pentru puzzle-uri mobile moderne (Block Blast, Woodoku, 1010!):
     * Ridică piesele din tava inferioară și le trage pe tabla de joc în celulele optime.
     */
    private fun executeTouchDragAndDropPuzzleStep(
        acc: EvaAccessibilityService,
        visual: GameVisualAnalysis,
        step: Int,
        w: Float,
        h: Float
    ): TacticalGameAction {
        val recommendedDrag = visual.recommendedDragMotion
        if (recommendedDrag != null && recommendedDrag.size == 4) {
            val sx = recommendedDrag[0]
            val sy = recommendedDrag[1]
            val ex = recommendedDrag[2]
            val ey = recommendedDrag[3]
            val dragStream = TouchController.createDragAndDropMotionStream(
                startX = sx,
                startY = sy,
                endX = ex,
                endY = ey,
                reason = "Plasare piesă pe grilă"
            )
            TouchController.executeMotionStream(dragStream, acc)
            return TacticalGameAction(
                "🖐️ TouchController Drag (${dragStream.motionEvents.size} MotionEvents): (${sx.toInt()}, ${sy.toInt()}) → (${ex.toInt()}, ${ey.toInt()})",
                pauseAfterActionMs = 1200L
            )
        }

        val trayPieces = visual.trayPieceTargets
        val pieceIndex = step % 3
        val sourceX = if (trayPieces.isNotEmpty()) {
            trayPieces[step % trayPieces.size].first
        } else {
            w * (0.22f + pieceIndex * 0.28f)
        }
        val sourceY = if (trayPieces.isNotEmpty()) {
            trayPieces[step % trayPieces.size].second
        } else {
            h * 0.81f
        }

        val targetCell = visual.boardCenterTarget
        val destX = targetCell?.first ?: (w * (0.24f + ((step * 2) % 6) * 0.10f))
        val destY = targetCell?.second ?: (h * (0.30f + ((step * 3) % 6) * 0.06f))

        acc.performDragAndDrop(sourceX, sourceY, destX, destY, 460L)
        return TacticalGameAction(
            "🧩 Block Blast / Touch Drag: Ridicat piesa #${pieceIndex + 1} (${sourceX.toInt()}, ${sourceY.toInt()}) → Grilă (${destX.toInt()}, ${destY.toInt()})",
            pauseAfterActionMs = 1150L
        )
    }

    /**
     * Motor Tactic Dedicat pentru Solitaire / Solitario (Klondike, Spider, FreeCell):
     *  - Scanează în timp real cele 7 coloane Tableau (X = 8.6%, 22.4%, 36.2%, 50.0%, 63.8%, 77.6%, 91.4%),
     *    cartea întoarsă din Waste (73%, 17.5%) și pachetul Stock din dreapta-sus (91%, 17.5%).
     *  - Folosește semnătura de pixeli a tablei (`boardSignature`) și memoria mutărilor încercate (`solitaireTriedMovesOnBoard`)
     *    astfel încât să NU apese niciodată în buclă pe aceeași carte blocată (ex: Rege aflat deja în vârful coloanei 1)!
     *  - Când o carte se mută (la Fundație A..K sau pe altă coloană), semnătura tablei se schimbă și AI-ul reevaluează
     *    toate coloanele pentru mutări în lanț.
     *  - Când nicio carte de pe cele 7 coloane nu mai are mutare validă, apasă pe pachetul Stock (91%, 17.5%) pentru a trage
     *    o carte nouă în Waste.
     *  - Nu atinge niciodată bara de unelte de jos (Y > 72%: Nuova partita, Giornaliero, Indizio, Magia, Indietro).
     */
    private suspend fun executeSolitaireCardGameStep(
        acc: EvaAccessibilityService,
        visual: GameVisualAnalysis,
        step: Int,
        w: Float,
        h: Float
    ): TacticalGameAction {
        var solAnalysis = MediaProjectionVisionEngine.latestScreenAnalysis.value?.solitaireAnalysis
        if (solAnalysis == null) {
            val liveBmp = MediaProjectionVisionEngine.getLatestProjectedBitmap() ?: acc.captureLiveGameBitmap()
            if (liveBmp != null && !liveBmp.isRecycled) {
                try {
                    solAnalysis = GameScreenAnalysisUtility.analyzeSolitaireBoard(
                        bmp = liveBmp,
                        bw = liveBmp.width,
                        bh = liveBmp.height,
                        dw = w.toInt(),
                        dh = h.toInt()
                    )
                } catch (_: Exception) {}
            }
        }

        // Dacă avem analiza reală pe pixeli a celor 7 coloane + Waste + Stock:
        if (solAnalysis != null) {
            // Dacă tabla s-a schimbat față de pasul anterior (o carte s-a mutat sau s-a tras din pachet),
            // resetăm lista cărților deja testate pe această configurație!
            if (solAnalysis.boardSignature != lastSolitaireBoardSig) {
                lastSolitaireBoardSig = solAnalysis.boardSignature
                if (!lastSolitaireActionWasDraw) {
                    // O carte de pe tablă sau din Waste chiar s-a mutat cu succes!
                    solitaireTriedMovesOnBoard.clear()
                }
            }

            data class SolitaireCandidateMove(
                val key: String,
                val x: Float,
                val y: Float,
                val label: String,
                val reasoning: String
            )

            val candidates = mutableListOf<SolitaireCandidateMove>()

            // 1. Dacă tocmai am tras o carte nouă din pachet (Stock) și există carte vizibilă în Waste, o încercăm prima!
            if (lastSolitaireActionWasDraw && solAnalysis.hasWasteCard) {
                candidates.add(
                    SolitaireCandidateMove(
                        key = "WASTE_CARD",
                        x = solAnalysis.wasteCardX,
                        y = solAnalysis.wasteCardY,
                        label = "Carte Pachet Întors (Waste)",
                        reasoning = "Carte nouă trasă din pachetul Stock; se încearcă plasarea ei la Fundație sau pe una din cele 7 coloane."
                    )
                )
            }

            // 2. PRIORITATE MAXIMĂ: Coloanele care au cărți ascunse (cu fața în jos, albastre) deasupra cărților deschise!
            // (În captura ta: Coloanele 5, 6 și 7 au cărți albastre ascunse care trebuie deblocate primele!)
            val colsWithHidden = solAnalysis.columns.filter { it.hasFaceUpCards && it.hasFaceDownCardsAbove }
            for (col in colsWithHidden) {
                candidates.add(
                    SolitaireCandidateMove(
                        key = "COL_${col.columnIndex}_BOTTOM",
                        x = col.centerX,
                        y = col.bottomCardY,
                        label = "Coloana ${col.columnIndex + 1} (Carte Jos - Deblocare)",
                        reasoning = "Coloana ${col.columnIndex + 1} conține cărți ascunse cu fața în jos; se testează cartea de jos pentru urcare la Fundație sau mutare."
                    )
                )
                if (col.stackTopCardY != null) {
                    candidates.add(
                        SolitaireCandidateMove(
                            key = "COL_${col.columnIndex}_TOP",
                            x = col.centerX,
                            y = col.stackTopCardY,
                            label = "Coloana ${col.columnIndex + 1} (Vârf Sub-Stivă - Eliberare Carte Ascunsă)",
                            reasoning = "Mutare sub-stivă din coloana ${col.columnIndex + 1} pentru a întoarce cartea albastră cu fața în jos."
                        )
                    )
                }
            }

            // 3. Cartea din Waste (dacă există și nu a fost deja pusă pe poziția 1)
            if (!lastSolitaireActionWasDraw && solAnalysis.hasWasteCard) {
                candidates.add(
                    SolitaireCandidateMove(
                        key = "WASTE_CARD",
                        x = solAnalysis.wasteCardX,
                        y = solAnalysis.wasteCardY,
                        label = "Carte Pachet Întors (Waste)",
                        reasoning = "Verificare carte disponibilă în zona Waste pentru avansare la Fundație sau Tableau."
                    )
                )
            }

            // 4. Coloanele deja deschise complet (fără cărți cu fața în jos deasupra, ex: Coloana 1 cu K♠..7♣ din captura ta):
            // Apăsăm DOAR cartea de jos (ex: 7♣ pentru a urca la Fundație sau a primi 6 roșu),
            // NICIODATĂ cartea de sus (K♠) pentru a nu muta Regele la infinit între coloanele goale!
            val openCols = solAnalysis.columns.filter { it.hasFaceUpCards && !it.hasFaceDownCardsAbove }
            for (col in openCols) {
                candidates.add(
                    SolitaireCandidateMove(
                        key = "COL_${col.columnIndex}_BOTTOM",
                        x = col.centerX,
                        y = col.bottomCardY,
                        label = "Coloana ${col.columnIndex + 1} (Carte Activă Jos)",
                        reasoning = "Testare carte de la baza coloanei deschise ${col.columnIndex + 1} (fără a muta Regele din vârf pe coloane goale)."
                    )
                )
            }

            // Alegem prima mutare candidată care NU a fost deja încercată pe configurația actuală a tablei:
            val nextMove = candidates.firstOrNull { it.key !in solitaireTriedMovesOnBoard }
            if (nextMove != null) {
                solitaireTriedMovesOnBoard.add(nextMove.key)
                lastSolitaireActionWasDraw = false
                val tx = nextMove.x.coerceIn(w * 0.04f, w * 0.96f)
                val ty = nextMove.y.coerceIn(h * 0.14f, h * 0.68f) // Strict deasupra barei de jos (Y <= 68%)!
                val stream = TouchController.createTapMotionStream(
                    x = tx,
                    y = ty,
                    holdMs = 70L,
                    category = UiElementCategory.GAME_BOARD_ACTIVE_FIELD,
                    reason = nextMove.label
                )
                val dispatched = TouchController.executeMotionStream(stream, acc)
                return TacticalGameAction(
                    status = "🃏 Solitaire AI: ${nextMove.label} la (${tx.toInt()}, ${ty.toInt()})",
                    pauseAfterActionMs = 540L,
                    targetCoordinates = "(${tx.toInt()}, ${ty.toInt()})",
                    gestureDispatched = dispatched,
                    actionCategory = "SOLITAIRE_${nextMove.key}",
                    reasoning = nextMove.reasoning
                )
            }

            // 5. Toate cărțile de pe cele 7 coloane și din Waste au fost verificate pe această tablă fără mutare validă
            // (exact ca în captura ta unde pe tablă sunt 7♣, 4♦, 6♣, 6♠ și niciuna nu se poate muta încă):
            // Apăsăm pe PACHETUL STOCK (dreapta-sus: 91%, 17.5%) pentru a trage o carte nouă!
            val stockX = solAnalysis.stockPileX.coerceIn(w * 0.82f, w * 0.96f)
            val stockY = solAnalysis.stockPileY.coerceIn(h * 0.13f, h * 0.23f)
            solitaireTriedMovesOnBoard.clear()
            // După ce tragem o carte din Stock, nu mai re-apăsăm imediat pe coloanele care tocmai au fost verificate,
            // ci testăm mai întâi cartea nouă din Waste și apoi coloanele cu cărți ascunse!
            lastSolitaireActionWasDraw = true
            solitaireStockCycleCount++

            val stream = TouchController.createTapMotionStream(
                x = stockX,
                y = stockY,
                holdMs = 75L,
                category = UiElementCategory.GAME_BOARD_ACTIVE_FIELD,
                reason = "Tragere carte din pachet (Stock)"
            )
            val dispatched = TouchController.executeMotionStream(stream, acc)
            return TacticalGameAction(
                status = "🃏 Solitaire AI: Nicio mutare directă pe coloane → Tras carte nouă din Pachet (Stock) la (${stockX.toInt()}, ${stockY.toInt()})",
                pauseAfterActionMs = 580L,
                targetCoordinates = "(${stockX.toInt()}, ${stockY.toInt()})",
                gestureDispatched = dispatched,
                actionCategory = "SOLITAIRE_DRAW_STOCK",
                reasoning = "Toate cărțile active de pe cele 7 coloane au fost verificate; se extrage o carte nouă din pachetul Stock (dreapta-sus)."
            )
        }

        // Fallback calibrat geometric pe layout-ul Klondike Solitaire (când MediaProjection nu a livrat încă primul frame):
        val colCentersX = floatArrayOf(0.086f, 0.224f, 0.362f, 0.500f, 0.638f, 0.776f, 0.914f)
        val cyclePos = step % 9
        return when (cyclePos) {
            0 -> {
                // Trage carte din pachetul Stock (dreapta-sus: 91%, 17.5%)
                val sx = w * 0.91f
                val sy = h * 0.175f
                val tapOk = acc.performTap(sx, sy, 75L)
                TacticalGameAction(
                    status = "🃏 Solitaire AI: Extragere carte din Pachet (Stock) la (${sx.toInt()}, ${sy.toInt()})",
                    pauseAfterActionMs = 560L,
                    targetCoordinates = "(${sx.toInt()}, ${sy.toInt()})",
                    gestureDispatched = tapOk,
                    actionCategory = "SOLITAIRE_DRAW_STOCK",
                    reasoning = "Extragere carte nouă din pachetul Stock aflat în colțul dreapta-sus al tablei."
                )
            }
            1 -> {
                // Apasă cartea întoarsă din Waste (73%, 17.5%)
                val wx = w * 0.73f
                val wy = h * 0.175f
                val tapOk = acc.performTap(wx, wy, 75L)
                TacticalGameAction(
                    status = "🃏 Solitaire AI: Mutare carte din Waste la (${wx.toInt()}, ${wy.toInt()})",
                    pauseAfterActionMs = 540L,
                    targetCoordinates = "(${wx.toInt()}, ${wy.toInt()})",
                    gestureDispatched = tapOk,
                    actionCategory = "SOLITAIRE_PLAY_WASTE",
                    reasoning = "Încercare plasare carte întoarsă din Waste pe coloanele Tableau sau la Fundație."
                )
            }
            else -> {
                // Parcurge de la dreapta la stânga coloanele 7..1 (deoarece coloanele 5-7 au cele mai multe cărți ascunse)
                val colIdx = (8 - cyclePos).coerceIn(0, 6)
                val cx = w * colCentersX[colIdx]
                val cy = h * 0.34f
                val tapOk = acc.performTap(cx, cy, 75L)
                TacticalGameAction(
                    status = "🃏 Solitaire AI: Verificare Coloana ${colIdx + 1}/7 la (${cx.toInt()}, ${cy.toInt()})",
                    pauseAfterActionMs = 520L,
                    targetCoordinates = "(${cx.toInt()}, ${cy.toInt()})",
                    gestureDispatched = tapOk,
                    actionCategory = "SOLITAIRE_TAP_COL_${colIdx + 1}",
                    reasoning = "Verificare carte din coloana ${colIdx + 1} pentru mutare automată."
                )
            }
        }
    }

    /**
     * Motor Strategic Avansat pentru Jocuri de Bază & Război (Clash of Clans, Clash Royale, Rise of Kingdoms):
     * Elimină complet spam-ul de click-uri haotice printr-o Mașină de Stări Deliberată:
     * 1. VILLAGE_HOME: Colectare resurse & inițiere atac prin butonul dedicat "Attack!" (stânga-jos).
     * 2. ATTACK_MAP: Selectare "Find a Match" (Atac Multiplayer) și așteptare sincronizare nori.
     * 3. SCOUTING_ENEMY: Scanare bază inamică; decizie de atac sau "Next".
     * 4. DEPLOYING_TROOPS: Desfășurare metodică a trupelor pe valuri (Tancuri pe flanc -> DPS din spate -> Eroi).
     * 5. WATCHING_BATTLE: Observare calmă a luptei (FĂRĂ atingeri haotice!) + activare abilitate Erou.
     * 6. BATTLE_OVER: Apăsare "Return Home" și revenire în sat.
     */
    private fun executeClashOfClansStrategicStep(
        acc: EvaAccessibilityService,
        snap: LiveScreenSnapshot,
        visual: GameVisualAnalysis,
        step: Int,
        w: Float,
        h: Float
    ): TacticalGameAction {
        if (!visual.isLandscape) {
            // Mod Portrait pentru Clash Royale / Tower Defense vertical:
            val cardSlot = step % 4
            val cardX = w * (0.22f + cardSlot * 0.18f)
            val cardY = h * 0.85f
            val bridgeX = if (step % 2 == 0) w * 0.28f else w * 0.72f
            val bridgeY = h * 0.52f
            acc.performSwipe(cardX, cardY, bridgeX, bridgeY, 230L)
            return TacticalGameAction(
                "👑 Clash Royale: Lansat cartea #${cardSlot + 1} pe podul ${if (step % 2 == 0) "Stâng" else "Drept"}. Pauză pentru generare elixir...",
                pauseAfterActionMs = 2200L
            )
        }

        // Landscape: Clash of Clans / DomiNations / Rise of Kingdoms
        // 1. PRIORITATE ZERO: Închidere pop-up promoțional / ofertă / clan war (butonul 'X')
        val closeBtn = visual.detectedUiButtons.firstOrNull { it.category == GameUiButtonCategory.CLOSE_POPUP_X_BUTTON }
        if (closeBtn != null) {
            val tapOk = acc.performTap(closeBtn.centerX, closeBtn.centerY, 70L)
            return TacticalGameAction(
                status = "❌ Clash of Clans: Închis pop-up / dialog la (${closeBtn.centerX.toInt()}, ${closeBtn.centerY.toInt()})",
                pauseAfterActionMs = 1400L,
                targetCoordinates = "(${closeBtn.centerX.toInt()}, ${closeBtn.centerY.toInt()})",
                gestureDispatched = tapOk,
                actionCategory = "POPUP_DISMISS",
                reasoning = "Fereastră pop-up detectată de AI Vision; se apasă X pentru a elibera interfața jocului."
            )
        }

        // 2. Buton de Return Home / Okay / Continue (Final de bătălie sau victorie)
        val returnHomeBtn = visual.detectedUiButtons.firstOrNull { it.id == "btn_return_bc" || (it.category == GameUiButtonCategory.CONTINUE_OR_CLAIM_BUTTON && it.centerY > h * 0.70f) }
        if (returnHomeBtn != null) {
            val tapOk = acc.performTap(returnHomeBtn.centerX, returnHomeBtn.centerY, 75L)
            clashCurrentState = ClashState.VILLAGE_HOME
            clashScoutCount = 0
            return TacticalGameAction(
                status = "🏠 Clash of Clans: Apăsat „Return Home” la (${returnHomeBtn.centerX.toInt()}, ${returnHomeBtn.centerY.toInt()}). Revenire în sat...",
                pauseAfterActionMs = 2800L,
                targetCoordinates = "(${returnHomeBtn.centerX.toInt()}, ${returnHomeBtn.centerY.toInt()})",
                gestureDispatched = tapOk,
                actionCategory = "RETURN_HOME",
                reasoning = "Bătălia este finalizată; butonul Return Home a fost identificat pe ecran."
            )
        }

        // 3. Buton Find a Match (Multiplayer) în meniul de atac
        val findMatchBtn = visual.detectedUiButtons.firstOrNull { it.id == "btn_match_br" }
        if (findMatchBtn != null) {
            val tapOk = acc.performTap(findMatchBtn.centerX, findMatchBtn.centerY, 75L)
            clashCurrentState = ClashState.SCOUTING_ENEMY
            clashScoutCount = 0
            return TacticalGameAction(
                status = "⚔️ Meniu Atac Clash: Selectat „Find a Match” la (${findMatchBtn.centerX.toInt()}, ${findMatchBtn.centerY.toInt()}). Căutare rival...",
                pauseAfterActionMs = 3200L,
                targetCoordinates = "(${findMatchBtn.centerX.toInt()}, ${findMatchBtn.centerY.toInt()})",
                gestureDispatched = tapOk,
                actionCategory = "FIND_MATCH",
                reasoning = "Meniul de atac a fost deschis; butonul portocaliu Find a Match a fost identificat în dreapta-jos."
            )
        }

        // 4. Butonul mare ATTACK! din colțul stânga-jos în satul principal
        val attackBtn = visual.detectedUiButtons.firstOrNull { it.id == "btn_attack_bl" }
        if (attackBtn != null && clashCurrentState != ClashState.DEPLOYING_TROOPS && clashCurrentState != ClashState.WATCHING_BATTLE) {
            val tapOk = acc.performTap(attackBtn.centerX, attackBtn.centerY, 75L)
            clashCurrentState = ClashState.ATTACK_MAP
            return TacticalGameAction(
                status = "🏰 Sat Clash: Apăsat butonul „ATTACK!” (stânga-jos: ${attackBtn.centerX.toInt()}, ${attackBtn.centerY.toInt()})",
                pauseAfterActionMs = 2200L,
                targetCoordinates = "(${attackBtn.centerX.toInt()}, ${attackBtn.centerY.toInt()})",
                gestureDispatched = tapOk,
                actionCategory = "ATTACK_MENU_OPEN",
                reasoning = "Suntem în satul principal; butonul de pornire atac este activ în stânga-jos."
            )
        }

        // 5. Mașina de Stări Reacțională
        return when (clashCurrentState) {
            ClashState.VILLAGE_HOME -> {
                TacticalGameAction(
                    status = "🏰 Sat Clash: Monitorizare pasivă a bazei. AI-ul observă satul și nu trimite comenzi forțate în buclă.",
                    pauseAfterActionMs = 2800L,
                    targetCoordinates = "Sat Principal (Așteptare Activă)",
                    gestureDispatched = true,
                    actionCategory = "OBSERVE_VILLAGE",
                    reasoning = "Suntem în satul principal. Nu trimitem atingeri automate fără un eveniment clar pe ecran."
                )
            }

            ClashState.ATTACK_MAP -> {
                val defaultMatchX = w * 0.82f
                val defaultMatchY = h * 0.76f
                val tapOk = acc.performTap(defaultMatchX, defaultMatchY, 75L)
                clashCurrentState = ClashState.SCOUTING_ENEMY
                clashScoutCount = 0
                TacticalGameAction(
                    status = "⚔️ Meniu Atac Clash: Selectat „Find a Match” (${defaultMatchX.toInt()}, ${defaultMatchY.toInt()})",
                    pauseAfterActionMs = 3200L,
                    targetCoordinates = "(${defaultMatchX.toInt()}, ${defaultMatchY.toInt()})",
                    gestureDispatched = tapOk,
                    actionCategory = "FIND_MATCH",
                    reasoning = "Tranziție în ecranul de căutare multiplayer; așteptare sincronizare nori."
                )
            }

            ClashState.SCOUTING_ENEMY -> {
                clashScoutCount++
                if (clashScoutCount <= 1) {
                    TacticalGameAction(
                        status = "🔍 Scouting Bază Inamică: Scanare amplasare Town Hall și apărări...",
                        pauseAfterActionMs = 2200L,
                        targetCoordinates = "Ecran Observare Bază",
                        gestureDispatched = true,
                        actionCategory = "SCOUT_BASE",
                        reasoning = "Observare preliminară a bazei inamice pentru alegerea flancului vulnerabil."
                    )
                } else {
                    clashCurrentState = ClashState.DEPLOYING_TROOPS
                    clashWaveStep = 0
                    TacticalGameAction(
                        status = "🎯 Clash of Clans: Bază aprobată! Inițiere atac tactic coordonat pe flanc...",
                        pauseAfterActionMs = 1000L,
                        targetCoordinates = "Flanc Sud-Vest",
                        gestureDispatched = true,
                        actionCategory = "START_DEPLOYMENT",
                        reasoning = "Baza este confirmată pentru atac; începe lansarea trupelor pe valuri."
                    )
                }
            }

            ClashState.DEPLOYING_TROOPS -> {
                val flankX = w * 0.18f
                val flankY = h * 0.48f

                val action = when (clashWaveStep) {
                    0 -> {
                        val tapOk = acc.performTap(w * 0.16f, h * 0.88f, 75L)
                        TacticalGameAction(
                            status = "🛡️ Val 1: Selectat Tancuri/Giganți din bara de trupe (Slot 1)",
                            pauseAfterActionMs = 600L,
                            targetCoordinates = "(${w * 0.16f.toInt()}, ${h * 0.88f.toInt()})",
                            gestureDispatched = tapOk,
                            actionCategory = "SELECT_TROOP_TANK",
                            reasoning = "Selectare trupe de absorbție foc din bara de jos."
                        )
                    }
                    1 -> {
                        val t1 = acc.performTap(flankX, flankY, 70L)
                        val t2 = acc.performTap(flankX + w * 0.04f, flankY + h * 0.04f, 70L)
                        TacticalGameAction(
                            status = "🛡️ Val 1: Desfășurat unități grele pentru absorbție foc apărări",
                            pauseAfterActionMs = 700L,
                            targetCoordinates = "(${flankX.toInt()}, ${flankY.toInt()})",
                            gestureDispatched = t1 && t2,
                            actionCategory = "DEPLOY_TANKS",
                            reasoning = "Plasare tancuri în afara liniei roșii pentru a atrage tunurile și turnurile de arcași."
                        )
                    }
                    2 -> {
                        val tapOk = acc.performTap(w * 0.25f, h * 0.88f, 75L)
                        TacticalGameAction(
                            status = "🏹 Val 2: Selectat Arcași / Vrăjitori (Slot 2)",
                            pauseAfterActionMs = 600L,
                            targetCoordinates = "(${w * 0.25f.toInt()}, ${h * 0.88f.toInt()})",
                            gestureDispatched = tapOk,
                            actionCategory = "SELECT_TROOP_DPS",
                            reasoning = "Selectare unități cu daune ridicate la distanță din bara de jos."
                        )
                    }
                    3 -> {
                        val t1 = acc.performTap(flankX - w * 0.03f, flankY - h * 0.04f, 70L)
                        val t2 = acc.performTap(flankX + w * 0.03f, flankY - h * 0.04f, 70L)
                        val t3 = acc.performTap(flankX, flankY - h * 0.06f, 70L)
                        TacticalGameAction(
                            status = "🏹 Val 2: Desfășurat trupe DPS în spatele tancurilor",
                            pauseAfterActionMs = 800L,
                            targetCoordinates = "(${flankX.toInt()}, ${flankY.toInt()})",
                            gestureDispatched = t1 && t2 && t3,
                            actionCategory = "DEPLOY_DPS",
                            reasoning = "Răspândire metodică a arcașilor în spatele primei linii pentru distrugere clădiri exterioare."
                        )
                    }
                    4 -> {
                        val tapOk = acc.performTap(w * 0.42f, h * 0.88f, 75L)
                        TacticalGameAction(
                            status = "👑 Val 3: Selectat Eroul Principal (Slot 4)",
                            pauseAfterActionMs = 600L,
                            targetCoordinates = "(${w * 0.42f.toInt()}, ${h * 0.88f.toInt()})",
                            gestureDispatched = tapOk,
                            actionCategory = "SELECT_HERO",
                            reasoning = "Selectare Erou (Barbarian King / Queen) pentru străpungere centrală."
                        )
                    }
                    5 -> {
                        val tapOk = acc.performTap(flankX + w * 0.02f, flankY + h * 0.02f, 75L)
                        clashCurrentState = ClashState.WATCHING_BATTLE
                        clashBattleWaitCycles = 0
                        TacticalGameAction(
                            status = "👑 Val 3: Erou intrat în luptă! Trecere în supraveghere tactică calmă...",
                            pauseAfterActionMs = 2800L,
                            targetCoordinates = "(${flankX.toInt()}, ${flankY.toInt()})",
                            gestureDispatched = tapOk,
                            actionCategory = "DEPLOY_HERO",
                            reasoning = "Toate unitățile principale au fost plasate. Se evită spam-ul de atingeri."
                        )
                    }
                    else -> {
                        clashCurrentState = ClashState.WATCHING_BATTLE
                        clashBattleWaitCycles = 0
                        TacticalGameAction(
                            status = "⚔️ Desfășurare finalizată. Observare evoluție luptă.",
                            pauseAfterActionMs = 2200L,
                            targetCoordinates = "Observare",
                            gestureDispatched = true,
                            actionCategory = "OBSERVE_BATTLE",
                            reasoning = "Desfășurarea atacului s-a încheiat; trupele luptă autonom."
                        )
                    }
                }
                clashWaveStep++
                action
            }

            ClashState.WATCHING_BATTLE -> {
                clashBattleWaitCycles++
                if (clashBattleWaitCycles == 3) {
                    val tapOk = acc.performTap(w * 0.42f, h * 0.88f, 75L)
                    TacticalGameAction(
                        status = "⚡ Clash: Activată abilitatea specială a Eroului pentru spargere Town Hall!",
                        pauseAfterActionMs = 3000L,
                        targetCoordinates = "(${w * 0.42f.toInt()}, ${h * 0.88f.toInt()})",
                        gestureDispatched = tapOk,
                        actionCategory = "HERO_SPECIAL_ABILITY",
                        reasoning = "Activare Iron Fist / Royal Cloak pentru maximizarea procentajului de distrugere."
                    )
                } else if (clashBattleWaitCycles >= 7 || (visual.frameDiffScore < 0.005f && clashBattleWaitCycles >= 4)) {
                    val rx = w * 0.50f
                    val ry = h * 0.85f
                    val tapOk = acc.performTap(rx, ry, 75L)
                    clashCurrentState = ClashState.VILLAGE_HOME
                    clashScoutCount = 0
                    TacticalGameAction(
                        status = "🏁 Clash: Bătălie finalizată! Apăsat „Return Home” (${rx.toInt()}, ${ry.toInt()})",
                        pauseAfterActionMs = 3200L,
                        targetCoordinates = "(${rx.toInt()}, ${ry.toInt()})",
                        gestureDispatched = tapOk,
                        actionCategory = "RETURN_HOME",
                        reasoning = "Raidul s-a încheiat; apăsare pe butonul Return Home pentru revenire în sat."
                    )
                } else {
                    TacticalGameAction(
                        status = "🛡️ Supraveghere Luptă: Trupele luptă metodic (${clashBattleWaitCycles * 3}s). Fără spam haotic.",
                        pauseAfterActionMs = 3000L,
                        targetCoordinates = "Supraveghere",
                        gestureDispatched = true,
                        actionCategory = "OBSERVE_BATTLE",
                        reasoning = "Monitorizare viață trupe și progres distrugere."
                    )
                }
            }

            ClashState.BATTLE_OVER -> {
                val rx = w * 0.50f
                val ry = h * 0.85f
                val tapOk = acc.performTap(rx, ry, 75L)
                clashCurrentState = ClashState.VILLAGE_HOME
                clashScoutCount = 0
                TacticalGameAction(
                    status = "🏠 Revenire în Sat: Apăsat „Return Home” (${rx.toInt()}, ${ry.toInt()})",
                    pauseAfterActionMs = 3200L,
                    targetCoordinates = "(${rx.toInt()}, ${ry.toInt()})",
                    gestureDispatched = tapOk,
                    actionCategory = "RETURN_HOME",
                    reasoning = "Finalizare ciclu atac și întoarcere în satul principal."
                )
            }
        }
    }

    suspend fun consultLocalLlmTacticalAdvice(
        gameTitle: String,
        screenSummary: String,
        detectedElements: List<String>,
        playerHp: Float,
        staticStreak: Int,
        screenWidth: Float = 1080f,
        screenHeight: Float = 2400f
    ): String {
        val result = localLlmBridge.requestGameTacticalDecision(
            gameTitle = gameTitle,
            screenSummary = screenSummary,
            detectedElements = detectedElements.map {
                IdentifiedGameElement(category = "UI_BUTTON", label = it, x = 0f, y = 0f)
            },
            playerHp = playerHp,
            staticStreak = staticStreak,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            learnedGestures = _learnedGestures.value
        )
        return "🧠 [${result.engineSource}]: ${result.actionType} -> ${result.reasoning}"
    }

    /**
     * Execută un pas complet de Viziune + Acțiune într-un joc real instalat pe telefon:
     *  1. Inspectează mai întâi controalele UI reale din fereastra jocului (`findGameplayControlNodes`).
     *  2. Gestionează dialogurile și pop-up-urile fără blocaje.
     *  3. Rulează mașina de stări adecvată genului de joc cu pauze tactice adaptate ritmului jocului.
     */
    private suspend fun executeExternalGameVisionStep(
        acc: EvaAccessibilityService,
        snap: LiveScreenSnapshot,
        visual: GameVisualAnalysis,
        resolvedMode: GameBotMode,
        step: Int,
        arenaLane: Int,
        arenaJumping: Boolean,
        staticMenuStreak: Int,
        onUpdateStaticStreak: (Int) -> Unit,
        onOverrideLane: (Int) -> Unit
    ): TacticalGameAction {
        val uiControls = acc.findGameplayControlNodes(snap)
        val interpretedUi = EvaAccessibilityService.currentAppUiState.value

        // PASUL 0: Auto-Dismiss Pop-up / Dialoguri de promovare detectate de AccessibilityService
        val popupBtn = interpretedUi.closePopupButton
        if (popupBtn != null && (interpretedUi.screenType == ExternalGameScreenType.DIALOG_POPUP_ALERT || step % 4 == 0)) {
            val clicked = acc.clickElementOrTap(popupBtn, 85L)
            if (clicked) {
                onUpdateStaticStreak(0)
                return TacticalGameAction(
                    status = "❌ Pop-up Detectat (Accessibility): Închis alertă/reclamă la (${popupBtn.centerX}, ${popupBtn.centerY})",
                    pauseAfterActionMs = 1200L,
                    targetCoordinates = "(${popupBtn.centerX}, ${popupBtn.centerY})",
                    gestureDispatched = clicked,
                    actionCategory = "DISMISS_POPUP",
                    reasoning = "AccessibilityService a interpretat starea UI ca ${interpretedUi.screenType.displayName} și a apăsat butonul de închidere: ${interpretedUi.decisionContextSummary}"
                )
            }
        }

        // PASUL 1: Dacă există un buton clar de START / PLAY / NEW GAME / RETRY în nodurile UI
        val startBtn = uiControls.startOrPlayButton ?: interpretedUi.startOrPlayButton
        val shouldClickStart = startBtn != null && (
            step <= 2 ||
            interpretedUi.screenType == ExternalGameScreenType.MAIN_MENU_LOBBY ||
            interpretedUi.screenType == ExternalGameScreenType.VICTORY_OR_DEFEAT ||
            interpretedUi.screenType == ExternalGameScreenType.LOADING_OR_CONNECTING
        )
        if (shouldClickStart) {
            val clicked = acc.clickElementOrTap(startBtn, 105L)
            if (clicked) {
                onUpdateStaticStreak(0)
                return TacticalGameAction(
                    status = "🎯 Buton Joc Detectat (UI): Apăsat „${uiControls.startButtonLabel.take(22)}” la (${startBtn.centerX.toInt()}, ${startBtn.centerY.toInt()})",
                    pauseAfterActionMs = 1500L,
                    targetCoordinates = "(${startBtn.centerX.toInt()}, ${startBtn.centerY.toInt()})",
                    gestureDispatched = clicked,
                    actionCategory = "START_OR_PLAY_BUTTON",
                    reasoning = "Nod UI de pornire/reluare detectat în ierarhia ecranului cu eticheta: „${uiControls.startButtonLabel}”."
                )
            }
        }

        // PASUL 2: Dimensiunile reale ale ecranului și evaluarea Sistemului 1 (Reflex Motor Ultra-Rapid)
        val w = visual.width.toFloat().coerceAtLeast(480f)
        val h = visual.height.toFloat().coerceAtLeast(480f)
        val cx = w * 0.5f
        val cy = h * 0.5f

        val gameIdentity = (_targetGameName.value + " " + snap.activePackage + " " + _liveGameState.value.externalPackageName).lowercase()
        val isSolitaireOrCardGame = resolvedMode == GameBotMode.SOLITAIRE_CARDS ||
            visual.detectedCategory == DetectedGameCategory.CARD_SOLITAIRE_TABLE ||
            gameIdentity.contains("solit") ||
            gameIdentity.contains("klondike") ||
            gameIdentity.contains("spider") ||
            gameIdentity.contains("freecell") ||
            gameIdentity.contains("patience") ||
            gameIdentity.contains("tripledot") ||
            gameIdentity.contains("mobilityware") ||
            MediaProjectionVisionEngine.latestScreenAnalysis.value?.solitaireAnalysis?.isSolitaireTableDetected == true

        val isTetrisOrArcadeGame = !isSolitaireOrCardGame && (
            resolvedMode == GameBotMode.TETRIS_ARCADE ||
                gameIdentity.contains("tetris") ||
                gameIdentity.contains("brick") ||
                uiControls.hasExplicitDirectionalButtons
            )

        // Evaluăm Sistemul 1 (DualBrain Fast Reflex 16-60ms) pe fiecare ciclu!
        val effectiveMode = when {
            isSolitaireOrCardGame -> GameBotMode.SOLITAIRE_CARDS
            isTetrisOrArcadeGame -> GameBotMode.TETRIS_ARCADE
            else -> resolvedMode
        }
        dualBrainEngine.evaluateFastReflex(visual, w, h, effectiveMode)

        // Verificare ecran de încărcare / tranziție (doar la primii 2 pași dacă ecranul este într-adevăr negru/splash)
        if (step <= 2 && (visual.visionGameState == DetectedVisionGameState.LOADING_TRANSITION || visual.phase == DetectedGamePhase.LOADING_OR_SPLASH)) {
            return TacticalGameAction(
                status = "⏳ Încărcare joc în curs — Se așteaptă finalizarea animațiilor...",
                pauseAfterActionMs = 1200L,
                targetCoordinates = "N/A (Ecran Încărcare)",
                gestureDispatched = true,
                actionCategory = "WAIT_LOADING",
                reasoning = "Ecran de încărcare / splash detectat la start; se temporizează gesturile pentru a evita clickuri premature."
            )
        }

        val isStaticScreen = visual.frameDiffScore < 0.008f
        val nextStreak = if (isStaticScreen) staticMenuStreak + 1 else 0
        onUpdateStaticStreak(nextStreak)

        // RUTA DIRECTĂ PENTRU SOLITAIRE / JOCURI DE CĂRȚI (Klondike / Spider / FreeCell):
        // Într-un joc de cărți ecranul este static în timp ce așteaptă mutarea jucătorului,
        // deci NU permitem filtrelor de meniu static ("AI Vision Meniu" / "Deblocare meniu") să deturneze jocul!
        if (isSolitaireOrCardGame) {
            return executeSolitaireCardGameStep(acc, visual, step, w, h)
        }

        // RUTA DIRECTĂ ULTRA-RAPIDĂ PENTRU TETRIS / ARCADE:
        // Nu lăsăm filtrele de meniu, MultiModalVision (pas par) sau LocalLlmBridge (pas % 3) să blocheze piesa în cădere!
        if (isTetrisOrArcadeGame) {
            return executeTetrisOrArcadeControlsStep(acc, uiControls, visual, step, w, h)
        }

        val isTurnBasedPuzzleBoard = (effectiveMode == GameBotMode.TOUCH_DRAG_PUZZLE || effectiveMode == GameBotMode.PUZZLE_MATCH) &&
            visual.visionGameState == DetectedVisionGameState.ACTIVE_GAMEPLAY_COMBAT

        // 1. Închidere dialoguri / pop-up-uri detectate pe ecran (doar când ecranul e static sau butonul X are încredere ridicată)
        val closeBtn = visual.detectedUiButtons.firstOrNull { it.category == GameUiButtonCategory.CLOSE_POPUP_X_BUTTON && it.confidence >= 0.72f }
        if (closeBtn != null && isStaticScreen && !isTurnBasedPuzzleBoard) {
            val tapOk = acc.performTap(closeBtn.centerX, closeBtn.centerY, 70L)
            return TacticalGameAction(
                status = "👁️ AI Vision: Închis dialog / pop-up la (${closeBtn.centerX.toInt()}, ${closeBtn.centerY.toInt()})",
                pauseAfterActionMs = 1200L,
                targetCoordinates = "(${closeBtn.centerX.toInt()}, ${closeBtn.centerY.toInt()})",
                gestureDispatched = tapOk,
                actionCategory = "POPUP_DISMISS",
                reasoning = "Buton X de închidere identificat prin Computer Vision în fereastra pop-up."
            )
        }

        // 2. Verificare Alertă HP critic (pentru jocuri de acțiune / shooter)
        if (visual.isLowHealthCritical && step % 4 == 0 && !isTurnBasedPuzzleBoard) {
            val (hx, hy) = visual.healButtonPoint ?: ((w * 0.74f) to (h * 0.88f))
            val tapOk = acc.performTap(hx, hy, 75L)
            return TacticalGameAction(
                status = "🚨 AI Vision (HP Critic ${visual.playerHpPercent.toInt()}%): Activat Medkit / Vindecare la (${hx.toInt()}, ${hy.toInt()})",
                pauseAfterActionMs = 350L,
                targetCoordinates = "(${hx.toInt()}, ${hy.toInt()})",
                gestureDispatched = tapOk,
                actionCategory = "HEAL_ACTION",
                reasoning = "HP la nivel periculos (<30%); activare imediată a butonului de vindecare/kit medical."
            )
        }

        // 3. Buton meniu / start / play / continue — DOAR dacă ecranul este cu adevărat static în lobby/final meci
        if (!isTurnBasedPuzzleBoard && visual.detectedMenuButton != null && isStaticScreen && (nextStreak >= 2 || visual.visionGameState == DetectedVisionGameState.MAIN_MENU_LOBBY || visual.visionGameState == DetectedVisionGameState.VICTORY_OR_DEFEAT_SCREEN)) {
            val (bx, by) = visual.detectedMenuButton
            val tapOk = acc.performTap(bx, by, 75L)
            return TacticalGameAction(
                status = "👁️ AI Vision Meniu: Apăsat ${visual.menuButtonLabel} la (${bx.toInt()}, ${by.toInt()})",
                pauseAfterActionMs = 1200L,
                targetCoordinates = "(${bx.toInt()}, ${by.toInt()})",
                gestureDispatched = tapOk,
                actionCategory = "MENU_BUTTON",
                reasoning = "Buton navigare/continuare identificat de Computer Vision pe ecran static în starea ${visual.visionGameState.name}."
            )
        }

        // 3.5 & 4. INTERVENȚIE MULTIMODALĂ / LLM LOCAL DOAR LA BLOCAJ ÎN MENIU STATIC (nextStreak >= 3)
        // În timpul gameplay-ului activ, Strategul (DualBrainGameEngine Sistemul 2) rulează deja asincron în fundal
        // la fiecare 6.5 secunde fără să blocheze sau să întrerupă reflexele motorii!
        var mmAnalysis: MultiModalVisionAnalysis? = null
        if (nextStreak >= 3 && !isTurnBasedPuzzleBoard) {
            val liveBmp = MediaProjectionVisionEngine.getLatestProjectedBitmap() ?: acc.captureLiveGameBitmap()
            if (liveBmp != null) {
                val analysis = multiModalVision.analyzeScreenFrame(
                    bitmap = liveBmp,
                    screenWidth = w,
                    screenHeight = h,
                    activeGameTitle = _targetGameName.value.ifBlank { snap.activePackage },
                    activeMode = effectiveMode
                )
                mmAnalysis = analysis
                _lastScreenVisionReport.value = buildString {
                    append("👁️ [${analysis.providerUsed.displayName}] ${analysis.modelName} (${analysis.latencyMs}ms)\n")
                    append("• Stare: ${analysis.gameState.displayName} | HP Jucător: ${analysis.playerHpPercent.toInt()}%\n")
                    append("• Butoane UI Detectate: ${analysis.interactiveButtons.size} | Inamici în FOV: ${analysis.enemies.size}\n")
                    append("• Recomandare Gest: ${analysis.recommendedAction} (${analysis.recommendedGesture.motion}) → ${analysis.recommendedGesture.reasoning}")
                }

                if (analysis.recommendedGesture.motion != DynamicGestureMotion.WAIT_OBSERVE && analysis.recommendedGesture.startX > 0f) {
                    val execResult = gestureMapper.translateAndDispatch(analysis.recommendedGesture)
                    if (execResult.isDispatched) {
                        onUpdateStaticStreak(0)
                        return TacticalGameAction(
                            status = "✨ [${analysis.providerUsed.badge} ${analysis.modelName}]: Deblocare meniu ${analysis.recommendedGesture.motion} la (${analysis.recommendedGesture.startX.toInt()}, ${analysis.recommendedGesture.startY.toInt()})",
                            pauseAfterActionMs = 950L,
                            targetCoordinates = "(${analysis.recommendedGesture.startX.toInt()}, ${analysis.recommendedGesture.startY.toInt()})",
                            gestureDispatched = true,
                            actionCategory = "MULTIMODAL_GESTURE_${analysis.recommendedGesture.motion}",
                            reasoning = "[${analysis.providerUsed.displayName} • ${analysis.latencyMs}ms] ${analysis.recommendedGesture.reasoning}"
                        )
                    }
                }
            }
        }

        // Detectare automată dacă jocul activ este de tip strategie/Clash
        val isClashOrStrategy = gameIdentity.contains("clash") ||
            gameIdentity.contains("supercell") ||
            gameIdentity.contains("strategy") ||
            effectiveMode == GameBotMode.CLASH_BASE_STRATEGY

        if (isClashOrStrategy) {
            return executeClashOfClansStrategicStep(acc, snap, visual, step, w, h)
        }

        // PASUL 3: GAMEPLAY ACTIV GHIDAT DE VIZIUNEA PE PIXELI ȘI ELEMENTELE UI ÎN TIMP REAL
        return when (effectiveMode) {
            GameBotMode.SOLITAIRE_CARDS -> {
                executeSolitaireCardGameStep(acc, visual, step, w, h)
            }

            GameBotMode.TOUCH_DRAG_PUZZLE -> {
                executeTouchDragAndDropPuzzleStep(acc, visual, step, w, h)
            }

            GameBotMode.TETRIS_ARCADE -> {
                executeTetrisOrArcadeControlsStep(acc, uiControls, visual, step, w, h)
            }

            GameBotMode.CLASH_BASE_STRATEGY -> {
                executeClashOfClansStrategicStep(acc, snap, visual, step, w, h)
            }

            GameBotMode.FPS_BATTLE_ROYALE -> {
                val screenAnalysis = MediaProjectionVisionEngine.latestScreenAnalysis.value
                val screenElements = screenAnalysis?.detectedElements ?: emptyList()
                val mappedFire = screenElements.firstOrNull { it.category == UiElementCategory.BUTTON_ATTACK_FIRE }
                val mappedJoy = screenElements.firstOrNull { it.category == UiElementCategory.JOYSTICK_MOVEMENT }

                val mmFire = mmAnalysis?.interactiveButtons?.firstOrNull {
                    it.category.contains("ATTACK", ignoreCase = true) || it.category.contains("FIRE", ignoreCase = true) || it.label.contains("fire", ignoreCase = true)
                }
                val gFire = getLearnedGesture(gameIdentity, "FIRE")
                val fireRightX = mmFire?.let { it.normalizedX * w } ?: gFire?.let { it.targetXFrac * w } ?: mappedFire?.centerX ?: (w * 0.86f)
                val fireRightY = mmFire?.let { it.normalizedY * h } ?: gFire?.let { it.targetYFrac * h } ?: mappedFire?.centerY ?: (h * 0.72f)

                val mmScope = mmAnalysis?.interactiveButtons?.firstOrNull {
                    it.category.contains("SCOPE", ignoreCase = true) || it.label.contains("scope", ignoreCase = true) || it.label.contains("ads", ignoreCase = true)
                }
                val gScope = getLearnedGesture(gameIdentity, "SCOPE")
                val adsScopeX = mmScope?.let { it.normalizedX * w } ?: gScope?.let { it.targetXFrac * w } ?: (w * 0.93f)
                val adsScopeY = mmScope?.let { it.normalizedY * h } ?: gScope?.let { it.targetYFrac * h } ?: (h * 0.56f)

                val mmJoy = mmAnalysis?.interactiveButtons?.firstOrNull {
                    it.category.contains("JOYSTICK", ignoreCase = true) || it.label.contains("joy", ignoreCase = true)
                }
                val gJoy = getLearnedGesture(gameIdentity, "JOYSTICK")
                val joyX = mmJoy?.let { it.normalizedX * w } ?: gJoy?.let { it.targetXFrac * w } ?: mappedJoy?.centerX ?: (w * 0.18f)
                val joyY = mmJoy?.let { it.normalizedY * h } ?: gJoy?.let { it.targetYFrac * h } ?: mappedJoy?.centerY ?: (h * 0.72f)

                val camStartX = w * 0.66f
                val camStartY = h * 0.48f

                val mmEnemy = mmAnalysis?.enemies?.firstOrNull()
                val enemyPt = mmEnemy?.let { (it.normalizedX * w) to (it.normalizedY * h) } ?: visual.enemyTargetPoint
                val hasCrosshair = mmAnalysis?.hasTargetInCrosshair == true || visual.hasTargetInCrosshair
                val isLowHp = mmAnalysis?.isLowHpCritical == true || visual.isLowHealthCritical || (mmAnalysis?.playerHpPercent ?: visual.playerHpPercent) < 42f

                // Preluăm analiza în timp real a Scenei 3D (Mașină, Casă, Pădure, Perete/Obstacol, Teren Deschis)
                val scene3d = visual.scene3dAnalysis
                    ?: screenAnalysis?.scene3dAnalysis
                    ?: Scene3dVisionAnalysis()

                val isStuckOrBlocked = scene3d.sceneType == Scene3dEnvironmentType.BLOCKED_WALL_OBSTACLE ||
                    scene3d.isWallOrObstacleInFront ||
                    (visual.frameDiffScore < 0.015f && step > 2)

                val directive = dualBrainEngine.activeDirective.value
                val posture = directive.posture

                when {
                    // Cazul 1: Viață Critică (<42%) -> Pas înapoi la adăpost + aplicare Medkit
                    isLowHp -> {
                        val mmHeal = mmAnalysis?.interactiveButtons?.firstOrNull {
                            it.category.contains("HEAL", ignoreCase = true) || it.label.contains("heal", ignoreCase = true) || it.label.contains("medkit", ignoreCase = true)
                        }
                        val (hx, hy) = mmHeal?.let { (it.normalizedX * w) to (it.normalizedY * h) } ?: visual.healButtonPoint ?: ((w * 0.74f) to (h * 0.88f))
                        acc.performDualThumbAction(
                            joyStartX = joyX,
                            joyStartY = joyY,
                            joyEndX = joyX - w * 0.08f,
                            joyEndY = joyY + h * 0.14f, // Mers ÎNAPOI spre adăpost în timp ce aplică Medkit!
                            attackX = hx,
                            attackY = hy,
                            durationMs = 280L
                        )
                        val hpVal = (mmAnalysis?.playerHpPercent ?: visual.playerHpPercent).toInt()
                        TacticalGameAction("🚑 [${scene3d.sceneType.badge} ${scene3d.sceneType.labelRo}] HP Critic ($hpVal%) → Retragere ÎNAPOI + Aplicat Medkit!", 295L)
                    }

                    // Cazul 2: Țintă direct în Crosshair -> Rafală de foc susținută + Strafe evaziv stânga/dreapta/înapoi!
                    hasCrosshair -> {
                        val strafeOffset = if (step % 2 == 0) w * 0.08f else -w * 0.08f
                        val backStepOffset = if (step % 4 == 0) h * 0.08f else -h * 0.04f
                        acc.performDualThumbAction(
                            joyStartX = joyX,
                            joyStartY = joyY,
                            joyEndX = joyX + strafeOffset,
                            joyEndY = joyY + backStepOffset,
                            attackX = fireRightX,
                            attackY = fireRightY,
                            durationMs = 280L
                        )
                        TacticalGameAction("⚔️ [${scene3d.sceneType.badge} ${scene3d.sceneType.labelRo}] Inamic în reticul! RAFALĂ FOC + Eschivă laterală!", 295L)
                    }

                    // Cazul 3: Inamic detectat în câmpul vizual (FOV) -> Swipe cameră 3D pe inamic + Foc / ADS
                    enemyPt != null -> {
                        val dx = (enemyPt.first - cx).coerceIn(-w * 0.35f, w * 0.35f)
                        val dy = (enemyPt.second - cy).coerceIn(-h * 0.28f, h * 0.28f)
                        val strafeX = if (step % 2 == 0) -w * 0.07f else w * 0.07f
                        acc.performDualStickMoveAndLook(
                            joyStartX = joyX,
                            joyStartY = joyY,
                            joyEndX = joyX + strafeX,
                            joyEndY = joyY - h * 0.05f,
                            camStartX = camStartX,
                            camStartY = camStartY,
                            camEndX = camStartX + dx * 0.65f,
                            camEndY = camStartY + dy * 0.65f,
                            durationMs = 240L
                        )
                        TacticalGameAction("🎯 [${scene3d.sceneType.badge} ${scene3d.sceneType.labelRo}] Rotit cameră 3D pe inamic + Strafe tactic!", 255L)
                    }

                    // Cazul 4: Jucătorul este ÎN MAȘINĂ / VEHICUL -> Condus inteligent (Accelerație, Frână/Marșarier, Viraj Stânga/Dreapta)
                    scene3d.sceneType == Scene3dEnvironmentType.IN_VEHICLE_DRIVING -> {
                        val gasX = w * 0.86f
                        val gasY = h * 0.70f
                        val brakeReverseX = w * 0.78f
                        val brakeReverseY = h * 0.82f
                        val steerLeftX = w * 0.14f
                        val steerRightX = w * 0.25f
                        val steerY = h * 0.78f

                        if (isStuckOrBlocked) {
                            // Mașină blocată în obstacol/perete -> Marșarier (Frână) + Viraj invers!
                            val reverseSteerX = if (scene3d.rightCorridorOpenness >= scene3d.leftCorridorOpenness) steerLeftX else steerRightX
                            acc.performDualThumbAction(
                                joyStartX = reverseSteerX,
                                joyStartY = steerY,
                                joyEndX = reverseSteerX,
                                joyEndY = steerY,
                                attackX = brakeReverseX,
                                attackY = brakeReverseY,
                                durationMs = 420L
                            )
                            TacticalGameAction("🚗 [În Mașină] Obstacol în față → MARȘARIER + Redresare direcție!", 440L)
                        } else if (scene3d.openCorridorDirection < 0) {
                            // Drumul se deschide la STÂNGA -> Viraj Stânga + Accelerație
                            acc.performDualThumbAction(
                                joyStartX = steerLeftX,
                                joyStartY = steerY,
                                joyEndX = steerLeftX,
                                joyEndY = steerY,
                                attackX = gasX,
                                attackY = gasY,
                                durationMs = 340L
                            )
                            TacticalGameAction("🚗 [În Mașină] Condus pe drum → Viraj STÂNGA + Accelerație!", 355L)
                        } else if (scene3d.openCorridorDirection > 0) {
                            // Drumul se deschide la DREAPTA -> Viraj Dreapta + Accelerație
                            acc.performDualThumbAction(
                                joyStartX = steerRightX,
                                joyStartY = steerY,
                                joyEndX = steerRightX,
                                joyEndY = steerY,
                                attackX = gasX,
                                attackY = gasY,
                                durationMs = 340L
                            )
                            TacticalGameAction("🚗 [În Mașină] Condus pe drum → Viraj DREAPTA + Accelerație!", 355L)
                        } else {
                            // Drum drept -> Accelerație + ajustare ușoară a camerei pe orizont
                            acc.performTap(gasX, gasY, 350L)
                            TacticalGameAction("🚗 [În Mașină] Drum liber pe centru → Accelerație stabilă!", 365L)
                        }
                    }

                    // Cazul 5: BLOCAT ÎN PERETE / OBSTACOL ÎN FAȚĂ -> Mers ÎNAPOI cu Joystick-ul + Rotire Amplă Cameră 3D sau Salt (Vault)!
                    isStuckOrBlocked -> {
                        val turnRight = scene3d.rightCorridorOpenness >= scene3d.leftCorridorOpenness
                        if (step % 3 == 2) {
                            // Încercăm Salt / Cățărare peste fereastră sau gard (Vault) simultan cu înaintare oblică
                            val jumpBtnX = w * 0.92f
                            val jumpBtnY = h * 0.62f
                            val sideOffset = if (turnRight) w * 0.11f else -w * 0.11f
                            acc.performDualThumbAction(
                                joyStartX = joyX,
                                joyStartY = joyY,
                                joyEndX = joyX + sideOffset,
                                joyEndY = joyY - h * 0.14f,
                                attackX = jumpBtnX,
                                attackY = jumpBtnY,
                                durationMs = 300L
                            )
                            TacticalGameAction("🧱 [Obstacol Detectat] Salt / Escaladare (Vault) + Ocolire spre ${if (turnRight) "DREAPTA" else "STÂNGA"}!", 320L)
                        } else {
                            // Tragem Joystick-ul ÎNAPOI (+Y) și simultan dăm SWIPE amplu pe Cameră spre stânga sau dreapta!
                            val backJoyEndX = joyX + (if (turnRight) -w * 0.08f else w * 0.08f)
                            val backJoyEndY = (joyY + h * 0.16f).coerceAtMost(h - 24f)
                            val camTurnDeltaX = if (turnRight) w * 0.26f else -w * 0.26f

                            acc.performDualStickMoveAndLook(
                                joyStartX = joyX,
                                joyStartY = joyY,
                                joyEndX = backJoyEndX,
                                joyEndY = backJoyEndY,
                                camStartX = camStartX,
                                camStartY = camStartY,
                                camEndX = camStartX + camTurnDeltaX,
                                camEndY = camStartY,
                                durationMs = 380L
                            )
                            val dirRo = if (turnRight) "DREAPTA" else "STÂNGA"
                            TacticalGameAction(
                                "🧱 [Perete/Obstacol în Față] Mers ÎNAPOI cu joystick-ul + Rotire Cameră 3D spre $dirRo (L:${(scene3d.leftCorridorOpenness * 100).toInt()}% R:${(scene3d.rightCorridorOpenness * 100).toInt()}%)!",
                                395L
                            )
                        }
                    }

                    // Cazul 6: Jucătorul este ÎN CASĂ / CLĂDIRE -> Navigare 360° prin camere, deschidere uși/loot și ieșire pe ușă/fereastră
                    scene3d.sceneType == Scene3dEnvironmentType.INDOORS_BUILDING_ROOM -> {
                        if (scene3d.hasDoorOrLootPrompt && step % 3 == 0) {
                            // Apăsăm butonul contextual de Deschidere Ușă / Colectare Loot
                            val interactX = w * 0.68f
                            val interactY = h * 0.46f
                            acc.performTap(interactX, interactY, 90L)
                            TacticalGameAction("🏠 [În Casă] Ușă / Echipament detectat → Apăsat Interacțiune / Loot!", 240L)
                        } else {
                            // În interiorul casei: ne mișcăm în 360° (înainte, înapoi, stânga, dreapta) și rotim camera spre ieșirea luminoasă!
                            val indoorJoyEndX = joyX + (scene3d.recommendedJoyDxFrac * w * 0.13f)
                            val indoorJoyEndY = joyY + (scene3d.recommendedJoyDyFrac * h * 0.15f)
                            val indoorCamDeltaX = if (kotlin.math.abs(scene3d.recommendedCameraSwipeDxFrac) > 0.05f) {
                                scene3d.recommendedCameraSwipeDxFrac * w
                            } else {
                                if (step % 2 == 0) w * 0.18f else -w * 0.18f
                            }

                            acc.performDualStickMoveAndLook(
                                joyStartX = joyX,
                                joyStartY = joyY,
                                joyEndX = indoorJoyEndX,
                                joyEndY = indoorJoyEndY,
                                camStartX = camStartX,
                                camStartY = camStartY,
                                camEndX = camStartX + indoorCamDeltaX,
                                camEndY = camStartY,
                                durationMs = 350L
                            )
                            TacticalGameAction("🏠 ${scene3d.tacticalSummaryRo}", 365L)
                        }
                    }

                    // Cazul 7: Jucătorul este ÎN PĂDURE / VEGETAȚIE -> Ocolire copaci (Stânga/Dreapta/Diagonal) + Rotire Cameră Stânga/Dreapta
                    scene3d.sceneType == Scene3dEnvironmentType.FOREST_VEGETATION -> {
                        // Alternăm ocolirea spre culoarul liber cu scanarea stânga-dreapta a camerei printre copaci
                        val zigZagSign = when {
                            scene3d.openCorridorDirection != 0 -> scene3d.openCorridorDirection.toFloat()
                            step % 4 < 2 -> -1f
                            else -> 1f
                        }
                        val joyTargetX = joyX + (zigZagSign * w * 0.11f)
                        val joyTargetY = if (scene3d.centerCorridorOpenness < 0.42f) {
                            joyY + h * 0.10f // Pas oblic înapoi dacă avem un trunchi de copac chiar în față
                        } else {
                            joyY - h * 0.16f // Înaintare diagonală pe lângă copaci
                        }
                        // Camera privește activ stânga și dreapta (Swipe real pe partea dreaptă a ecranului!)
                        val camLookDeltaX = when (step % 4) {
                            0 -> zigZagSign * w * 0.20f
                            1 -> -zigZagSign * w * 0.16f
                            2 -> zigZagSign * w * 0.14f
                            else -> if (scene3d.rightCorridorOpenness >= scene3d.leftCorridorOpenness) w * 0.18f else -w * 0.18f
                        }

                        acc.performDualStickMoveAndLook(
                            joyStartX = joyX,
                            joyStartY = joyY,
                            joyEndX = joyTargetX,
                            joyEndY = joyTargetY,
                            camStartX = camStartX,
                            camStartY = camStartY,
                            camEndX = camStartX + camLookDeltaX,
                            camEndY = camStartY,
                            durationMs = 360L
                        )
                        val lookDirRo = if (camLookDeltaX >= 0f) "DREAPTA" else "STÂNGA"
                        TacticalGameAction(
                            "🌲 [În Pădure] Ocolire copaci pe culoarul liber + Privire Cameră 3D spre $lookDirRo!",
                            375L
                        )
                    }

                    // Cazul 8: TEREN DESCHIS / DRUM -> Explorare 360° (Înainte, Stânga, Dreapta, Pas tactic Înapoi) + Scanare Cameră Stânga/Dreapta!
                    else -> {
                        if (scene3d.hasDoorOrLootPrompt && step % 4 == 0) {
                            // Dacă suntem lângă o mașină („Drive / Get In”) sau cutie de Loot, interacționăm cu ea!
                            val driveOrLootX = w * 0.68f
                            val driveOrLootY = h * 0.52f
                            acc.performTap(driveOrLootX, driveOrLootY, 90L)
                            TacticalGameAction("🏞️ [Teren Deschis] Vehicul / Loot detectat lângă jucător → Apăsat Drive / Pickup!", 250L)
                        } else {
                            // Ciclu complet de mișcare pe joystick + privire activă stânga/dreapta cu camera 3D:
                            // Nu mai ținem niciodată joystick-ul blocat doar înainte și nu mai facem tap gol pe cameră!
                            val phaseInCycle = step % 6
                            val joyDeltaX: Float
                            val joyDeltaY: Float
                            val camSwipeDeltaX: Float
                            val camSwipeDeltaY: Float
                            val moveDescRo: String

                            when (phaseInCycle) {
                                0 -> {
                                    // Înaintare + Rotire Cameră spre DREAPTA (scanare orizont dreapta)
                                    joyDeltaX = scene3d.recommendedJoyDxFrac * w * 0.10f
                                    joyDeltaY = -h * 0.17f
                                    camSwipeDeltaX = w * 0.20f
                                    camSwipeDeltaY = 0f
                                    moveDescRo = "Înaintare + Privire Cameră DREAPTA ▶"
                                }
                                1 -> {
                                    // Deplasare Diagonal-Stânga + Rotire Cameră spre STÂNGA (scanare orizont stânga)
                                    joyDeltaX = -w * 0.10f
                                    joyDeltaY = -h * 0.15f
                                    camSwipeDeltaX = -w * 0.22f
                                    camSwipeDeltaY = 0f
                                    moveDescRo = "Strafe Diagonal-STÂNGA + Privire Cameră STÂNGA ◀"
                                }
                                2 -> {
                                    // Orientare pe culoarul cel mai deschis detectat de Vision 3D
                                    joyDeltaX = scene3d.recommendedJoyDxFrac * w * 0.12f
                                    joyDeltaY = scene3d.recommendedJoyDyFrac * h * 0.17f
                                    camSwipeDeltaX = scene3d.recommendedCameraSwipeDxFrac * w
                                    camSwipeDeltaY = -h * 0.02f
                                    moveDescRo = scene3d.tacticalSummaryRo
                                }
                                3 -> {
                                    // Deplasare Diagonal-Dreapta + Scanare amplă spre DREAPTA
                                    joyDeltaX = w * 0.10f
                                    joyDeltaY = -h * 0.15f
                                    camSwipeDeltaX = w * 0.18f
                                    camSwipeDeltaY = 0.02f * h
                                    moveDescRo = "Strafe Diagonal-DREAPTA + Scanare Orizont DREAPTA ▶"
                                }
                                4 -> {
                                    // Pas tactic Înapoi-Lateral (verificare flancuri/spate) + Rotire Cameră Stânga
                                    val sideSign = if (scene3d.leftCorridorOpenness >= scene3d.rightCorridorOpenness) -1f else 1f
                                    joyDeltaX = sideSign * w * 0.09f
                                    joyDeltaY = h * 0.11f // Mers tactic cu spatele / repoziționare
                                    camSwipeDeltaX = sideSign * w * 0.24f
                                    camSwipeDeltaY = 0f
                                    moveDescRo = "Pas tactic ÎNAPOI-LATERAL + Verificare flancuri 360°"
                                }
                                else -> {
                                    // Sprint spre culoarul liber + Redresare privire pe centru-stânga
                                    joyDeltaX = scene3d.recommendedJoyDxFrac * w * 0.08f
                                    joyDeltaY = -h * 0.18f
                                    camSwipeDeltaX = -w * 0.16f
                                    camSwipeDeltaY = 0f
                                    moveDescRo = "Sprint Tactic + Scanare Cameră STÂNGA ◀"
                                }
                            }

                            acc.performDualStickMoveAndLook(
                                joyStartX = joyX,
                                joyStartY = joyY,
                                joyEndX = joyX + joyDeltaX,
                                joyEndY = joyY + joyDeltaY,
                                camStartX = camStartX,
                                camStartY = camStartY,
                                camEndX = camStartX + camSwipeDeltaX,
                                camEndY = camStartY + camSwipeDeltaY,
                                durationMs = 370L
                            )
                            TacticalGameAction(
                                "🧭 [${scene3d.sceneType.badge} ${scene3d.sceneType.labelRo}] $moveDescRo",
                                385L
                            )
                        }
                    }
                }
            }

            GameBotMode.FOOTBALL_SPORTS -> {
                val mmPass = mmAnalysis?.interactiveButtons?.firstOrNull { it.label.contains("pass", ignoreCase = true) }
                val mmShoot = mmAnalysis?.interactiveButtons?.firstOrNull { it.label.contains("shoot", ignoreCase = true) || it.label.contains("șut", ignoreCase = true) || it.label.contains("sut", ignoreCase = true) }
                val joyX = w * 0.18f
                val joyY = h * 0.74f
                val passBtnX = mmPass?.let { it.normalizedX * w } ?: (w * 0.78f)
                val passBtnY = mmPass?.let { it.normalizedY * h } ?: (h * 0.82f)
                val throughBtnX = w * 0.82f
                val throughBtnY = h * 0.68f
                val shootBtnX = mmShoot?.let { it.normalizedX * w } ?: (w * 0.88f)
                val shootBtnY = mmShoot?.let { it.normalizedY * h } ?: (h * 0.62f)
                val sprintTackleX = w * 0.88f
                val sprintTackleY = h * 0.80f

                val joyDriveX = joyX + w * 0.08f
                val joyDriveY = joyY - h * 0.02f

                when {
                    // Cazul 1: Țintă / Poartă în vizor -> Finalizare cu ȘUT
                    visual.hasTargetInCrosshair || visual.enemyTargetPoint != null -> {
                        acc.performDualThumbAction(
                            joyStartX = joyX,
                            joyStartY = joyY,
                            joyEndX = joyDriveX,
                            joyEndY = joyDriveY,
                            attackX = shootBtnX,
                            attackY = shootBtnY,
                            durationMs = 280L
                        )
                        TacticalGameAction("⚽ Fotbal Tactic: Orientare spre poartă + ȘUT Cadrat!", 400L)
                    }

                    // Cazul 2: Fază de construcție / Pasă
                    step % 3 == 0 -> {
                        val isThrough = step % 6 == 0
                        val targetBtnX = if (isThrough) throughBtnX else passBtnX
                        val targetBtnY = if (isThrough) throughBtnY else passBtnY
                        val label = if (isThrough) "Pasă Filtrantă (Through Ball)" else "Pasă Scurtă (Ground Pass)"
                        acc.performDualThumbAction(
                            joyStartX = joyX,
                            joyStartY = joyY,
                            joyEndX = joyDriveX,
                            joyEndY = joyDriveY,
                            attackX = targetBtnX,
                            attackY = targetBtnY,
                            durationMs = 220L
                        )
                        TacticalGameAction("⚽ Fotbal Tactic: $label către coechipier", 450L)
                    }

                    // Cazul 3: Fază defensivă sau presing pe adversar -> TACKLE / SPRINT
                    step % 3 == 1 -> {
                        acc.performDualThumbAction(
                            joyStartX = joyX,
                            joyStartY = joyY,
                            joyEndX = joyX + w * 0.04f,
                            joyEndY = joyY,
                            attackX = sprintTackleX,
                            attackY = sprintTackleY,
                            durationMs = 320L
                        )
                        TacticalGameAction("⚽ Fotbal Tactic: Presing activ & Deposedare (Tackle / Sprint)", 350L)
                    }

                    // Cazul 4: Dribling & Înaintare lină cu joystick-ul
                    else -> {
                        acc.performSwipe(joyX, joyY, joyDriveX, joyDriveY, 350L)
                        TacticalGameAction("⚽ Fotbal Tactic: Dribling fluid spre terenul advers", 380L)
                    }
                }
            }

            GameBotMode.FOOTBALL_MANAGER -> {
                val detectedBtn = visual.detectedMenuButton
                if (detectedBtn != null) {
                    acc.performTap(detectedBtn.first, detectedBtn.second, 115L)
                    TacticalGameAction("📋 Football Manager: Apăsat buton tactic detectat la (${detectedBtn.first.toInt()}, ${detectedBtn.second.toInt()})", 1200L)
                } else {
                    TacticalGameAction(
                        status = "📋 Football Manager: Monitorizare meci și statistici de pe bancă",
                        pauseAfterActionMs = 1500L,
                        targetCoordinates = "N/A (Observare Pasivă)",
                        gestureDispatched = true,
                        actionCategory = "OBSERVE_PASSIVE",
                        reasoning = "Niciun buton de acțiune activ în acest moment."
                    )
                }
            }

            GameBotMode.RACING_NFS -> {
                val safeLane = visual.recommendedSafeLane
                onOverrideLane(safeLane)

                val isCarStuckOnWall = visual.frameDiffScore < 0.005f && step > 3
                val nitroX = w * 0.88f
                val nitroY = h * 0.60f
                val brakeX = w * 0.16f
                val brakeY = h * 0.82f

                when {
                    // Cazul 1: Mașina este blocată într-o barieră/parapet -> Marșarier și redresare
                    isCarStuckOnWall -> {
                        acc.performTap(brakeX, brakeY, 450L)
                        TacticalGameAction("🚨 Curse Auto: Redresare mașină din parapet (Frână/Marșarier)", 650L)
                    }

                    // Cazul 2: Traseu viraj Stânga -> Atingere fluidă pe zona stângă
                    safeLane == 0 -> {
                        acc.performTap(w * 0.16f, h * 0.74f, 260L)
                        TacticalGameAction("🏎️ Curse Auto: Viraj fluid STÂNGA pe trasa ideală", 280L)
                    }

                    // Cazul 3: Traseu viraj Dreapta -> Atingere fluidă pe zona dreaptă
                    safeLane == 2 -> {
                        acc.performTap(w * 0.84f, h * 0.74f, 260L)
                        TacticalGameAction("🏎️ Curse Auto: Viraj fluid DREAPTA pe trasa ideală", 280L)
                    }

                    // Cazul 4: Linie dreaptă (Culoar central liber) -> Nitro Boost!
                    step % 3 == 0 -> {
                        acc.performTap(nitroX, nitroY, 220L)
                        TacticalGameAction("🔥 Curse Auto: NITRO SHOCKWAVE Activat pe linie dreaptă!", 600L)
                    }

                    else -> {
                        acc.performTap(w * 0.84f, h * 0.84f, 200L)
                        TacticalGameAction("⚡ Curse Auto: Accelerație stabilă pe traiectoria optimă", 260L)
                    }
                }
            }

            GameBotMode.OPEN_WORLD_GTA -> {
                val joyX = w * 0.18f
                val joyY = h * 0.72f
                val camStartX = w * 0.66f
                val camStartY = h * 0.48f
                val actionBtnX = w * 0.88f
                val actionBtnY = h * 0.76f
                val scene3d = visual.scene3dAnalysis
                    ?: MediaProjectionVisionEngine.latestScreenAnalysis.value?.scene3dAnalysis
                    ?: Scene3dVisionAnalysis()

                when {
                    visual.hasTargetInCrosshair || visual.enemyTargetPoint != null -> {
                        acc.performDualThumbAction(
                            joyStartX = joyX,
                            joyStartY = joyY,
                            joyEndX = joyX + (if (step % 2 == 0) w * 0.07f else -w * 0.07f),
                            joyEndY = joyY - h * 0.04f,
                            attackX = actionBtnX,
                            attackY = actionBtnY,
                            durationMs = 340L
                        )
                        TacticalGameAction("💥 [${scene3d.sceneType.badge} ${scene3d.sceneType.labelRo}] Țintă detectată → Combat simultan cu deplasare!", 360L)
                    }
                    scene3d.isWallOrObstacleInFront || (visual.frameDiffScore < 0.015f && step > 2) -> {
                        val turnRight = scene3d.rightCorridorOpenness >= scene3d.leftCorridorOpenness
                        acc.performDualStickMoveAndLook(
                            joyStartX = joyX,
                            joyStartY = joyY,
                            joyEndX = joyX + (if (turnRight) -w * 0.08f else w * 0.08f),
                            joyEndY = joyY + h * 0.16f, // Mers ÎNAPOI pentru deblocare
                            camStartX = camStartX,
                            camStartY = camStartY,
                            camEndX = camStartX + (if (turnRight) w * 0.25f else -w * 0.25f),
                            camEndY = camStartY,
                            durationMs = 370L
                        )
                        TacticalGameAction("🧱 [${scene3d.sceneType.badge}] Obstacol în față → Mers ÎNAPOI + Rotire Cameră 3D!", 385L)
                    }
                    else -> {
                        val camDeltaX = when (step % 4) {
                            0 -> w * 0.20f
                            1 -> -w * 0.20f
                            2 -> scene3d.recommendedCameraSwipeDxFrac * w
                            else -> if (scene3d.rightCorridorOpenness >= scene3d.leftCorridorOpenness) w * 0.16f else -w * 0.16f
                        }
                        acc.performDualStickMoveAndLook(
                            joyStartX = joyX,
                            joyStartY = joyY,
                            joyEndX = joyX + scene3d.recommendedJoyDxFrac * w * 0.12f,
                            joyEndY = joyY + scene3d.recommendedJoyDyFrac * h * 0.16f,
                            camStartX = camStartX,
                            camStartY = camStartY,
                            camEndX = camStartX + camDeltaX,
                            camEndY = camStartY,
                            durationMs = 370L
                        )
                        TacticalGameAction("🌆 ${scene3d.tacticalSummaryRo}", 385L)
                    }
                }
            }

            GameBotMode.ENDLESS_RUNNER -> {
                val safeLane = visual.recommendedSafeLane
                val curHazard = visual.laneHazards.getOrElse(arenaLane) { 0f }
                onOverrideLane(safeLane)

                when {
                    curHazard > 0.20f && safeLane < arenaLane -> {
                        acc.performSwipe(cx, cy, cx - w * 0.34f, cy, 175L)
                        TacticalGameAction("👁️ Gameplay Runner: Obstacol detectat (densitate ${"%.0f".format(curHazard * 100)}%) → Swipe STÂNGA", 260L)
                    }
                    curHazard > 0.20f && safeLane > arenaLane -> {
                        acc.performSwipe(cx, cy, cx + w * 0.34f, cy, 175L)
                        TacticalGameAction("👁️ Gameplay Runner: Obstacol detectat (densitate ${"%.0f".format(curHazard * 100)}%) → Swipe DREAPTA", 260L)
                    }
                    curHazard > 0.18f || arenaJumping -> {
                        acc.performSwipe(cx, h * 0.68f, cx, h * 0.28f, 175L)
                        TacticalGameAction("👁️ Gameplay Runner: Barieră în față → Swipe SUS (Salt)", 280L)
                    }
                    step % 5 == 0 -> {
                        acc.performSwipe(cx, h * 0.35f, cx, h * 0.74f, 175L)
                        TacticalGameAction("👁️ Gameplay Runner: Eschivă joasă → Swipe JOS (Slide)", 260L)
                    }
                    else -> {
                        val dir = if (safeLane == 0) -1 else if (safeLane == 2) 1 else 0
                        if (dir != 0) {
                            acc.performSwipe(cx, cy, cx + dir * (w * 0.32f), cy, 175L)
                            TacticalGameAction("🪙 Gameplay Runner: Colectare monede pe culoarul ${safeLane + 1}", 260L)
                        } else {
                            acc.performSwipe(cx, h * 0.66f, cx, h * 0.30f, 175L)
                            TacticalGameAction("⚡ Gameplay Runner: Sprint & Salt pe culoarul central", 260L)
                        }
                    }
                }
            }

            GameBotMode.PUZZLE_MATCH -> {
                val swap = visual.puzzleSwapCoordinates
                if (swap != null && swap.size == 4) {
                    acc.performSwipe(swap[0], swap[1], swap[2], swap[3], 220L)
                    TacticalGameAction(
                        "🧩 Viziune Puzzle: Potrivire 3 piese detectată → Swipe (${swap[0].toInt()},${swap[1].toInt()}) → (${swap[2].toInt()},${swap[3].toInt()})",
                        pauseAfterActionMs = 1350L
                    )
                } else if (uiControls.clickablePlayfieldNodes.isNotEmpty()) {
                    val targetNode = uiControls.clickablePlayfieldNodes[step % uiControls.clickablePlayfieldNodes.size]
                    acc.clickElementOrTap(targetNode, 95L)
                    TacticalGameAction("🧩 Puzzle UI: Apăsat piesă/celulă la (${targetNode.centerX}, ${targetNode.centerY})", 900L)
                } else {
                    val row = (step / 2) % 5
                    val col = step % 5
                    val gx = w * (0.20f + col * 0.14f)
                    val gy = h * (0.32f + row * 0.09f)
                    val horiz = step % 2 == 0
                    val ex = if (horiz) gx + w * 0.14f else gx
                    val ey = if (horiz) gy else gy + h * 0.09f
                    acc.performSwipe(gx, gy, ex, ey, 220L)
                    TacticalGameAction("🧩 Gameplay Puzzle: Mutare pe grilă la rândul ${row + 1}, coloana ${col + 1}", 1200L)
                }
            }

            GameBotMode.RPG_STRATEGY -> {
                if (visual.isLandscape) {
                    val joyCenterX = w * 0.18f
                    val joyCenterY = h * 0.70f
                    val angleStep = (step % 6)
                    val joyTargetX = joyCenterX + when (angleStep) {
                        0, 1, 2 -> w * 0.08f
                        3 -> -w * 0.07f
                        else -> 0f
                    }
                    val joyTargetY = joyCenterY + when (angleStep) {
                        0, 4 -> -h * 0.14f
                        2, 5 -> h * 0.10f
                        else -> -h * 0.08f
                    }
                    val skillSlot = step % 4
                    val (atkX, atkY) = when (skillSlot) {
                        0 -> (w * 0.86f) to (h * 0.76f)
                        1 -> (w * 0.75f) to (h * 0.82f)
                        2 -> (w * 0.80f) to (h * 0.62f)
                        else -> (w * 0.90f) to (h * 0.56f)
                    }
                    acc.performDualThumbAction(joyCenterX, joyCenterY, joyTargetX, joyTargetY, atkX, atkY, 340L)
                    TacticalGameAction("⚔️ Gameplay RPG/Action (Dual-Thumb): Mișcare Joystick + Atac/Abilitate #${skillSlot + 1}", 450L)
                } else {
                    val cardSlot = step % 4
                    val cardX = w * (0.24f + cardSlot * 0.18f)
                    val cardY = h * 0.85f
                    val deployX = if (step % 2 == 0) w * 0.30f else w * 0.70f
                    val deployY = h * 0.52f
                    acc.performSwipe(cardX, cardY, deployX, deployY, 240L)
                    TacticalGameAction("⚔️ Gameplay Strategie: Lansare unitate #${cardSlot + 1} pe culoarul ${if (step % 2 == 0) "Stâng" else "Drept"}", 1200L)
                }
            }

            GameBotMode.CLICKER_IDLE -> {
                if (step % 4 == 0) {
                    val upgX = w * (0.22f + (step % 3) * 0.28f)
                    val upgY = h * 0.82f
                    acc.performTap(upgX, upgY, 95L)
                    TacticalGameAction("⚡ Gameplay Clicker: Achiziție Upgrade automat la (${upgX.toInt()}, ${upgY.toInt()})", 600L)
                } else {
                    val tx = cx + Random.nextInt(-90, 90)
                    val ty = h * 0.46f + Random.nextInt(-80, 80)
                    acc.performTap(tx, ty, 75L)
                    TacticalGameAction("🔥 Gameplay Clicker: Turbo-Tap pe ținta centrală (${tx.toInt()}, ${ty.toInt()})", 220L)
                }
            }

            GameBotMode.AUTO_ADAPTIVE -> {
                when {
                    visual.detectedCategory == DetectedGameCategory.TOUCH_DRAG_DROP_PUZZLE ||
                        visual.trayPieceTargets.isNotEmpty() -> {
                        executeTouchDragAndDropPuzzleStep(acc, visual, step, w, h)
                    }

                    visual.detectedCategory == DetectedGameCategory.TOUCH_SWIPE_RUNNER -> {
                        val safeLane = visual.recommendedSafeLane
                        onOverrideLane(safeLane)
                        val swipe = visual.recommendedSwipeMotion
                        if (swipe != null && swipe.size == 4) {
                            acc.performSwipe(swipe[0], swipe[1], swipe[2], swipe[3], 175L)
                            TacticalGameAction("🏃 AI Touch Runner: Swipe adaptiv pe culoarul ${safeLane + 1}", 280L)
                        } else {
                            val dirX = if (safeLane == 0) -w * 0.32f else if (safeLane == 2) w * 0.32f else 0f
                            if (dirX != 0f) {
                                acc.performSwipe(cx, cy, cx + dirX, cy, 175L)
                                TacticalGameAction("🏃 AI Touch Runner: Glisare laterală pe culoar liber", 280L)
                            } else {
                                acc.performSwipe(cx, h * 0.65f, cx, h * 0.30f, 175L)
                                TacticalGameAction("🏃 AI Touch Runner: Swipe Sus (Salt / Înainte)", 280L)
                            }
                        }
                    }

                    visual.detectedCategory == DetectedGameCategory.TOUCH_MATCH_TAP || visual.puzzleSwapCoordinates != null -> {
                        val swap = visual.puzzleSwapCoordinates
                        if (swap != null && swap.size == 4) {
                            acc.performSwipe(swap[0], swap[1], swap[2], swap[3], 220L)
                            TacticalGameAction("🧩 AI Touch Match: Potrivire gestuală (${swap[0].toInt()},${swap[1].toInt()}) → (${swap[2].toInt()},${swap[3].toInt()})", 1300L)
                        } else if (visual.salientInteractiveTargets.isNotEmpty()) {
                            val target = visual.salientInteractiveTargets[step % visual.salientInteractiveTargets.size]
                            acc.performTap(target.first, target.second, 90L)
                            TacticalGameAction("🧩 AI Touch Target: Apăsat element colorat activ la (${target.first.toInt()}, ${target.second.toInt()})", 900L)
                        } else {
                            TacticalGameAction(
                                status = "🧩 AI Touch Match: Analiză tablă joc (fără atingeri oarbe)",
                                pauseAfterActionMs = 1200L,
                                targetCoordinates = "N/A (Observare Pasivă)",
                                gestureDispatched = true,
                                actionCategory = "OBSERVE_PASSIVE",
                                reasoning = "Fără potrivire evidentă; temporizare pentru animații de pe ecran."
                            )
                        }
                    }

                    uiControls.clickablePlayfieldNodes.isNotEmpty() -> {
                        val node = uiControls.clickablePlayfieldNodes.first()
                        acc.clickElementOrTap(node, 95L)
                        val lbl = node.text.ifBlank { node.contentDescription.ifBlank { "Control UI" } }
                        TacticalGameAction("🎯 Viziune UI Reacțională: Apăsat „${lbl.take(18)}” la (${node.centerX}, ${node.centerY})", 800L)
                    }

                    visual.salientInteractiveTargets.isNotEmpty() -> {
                        val target = visual.salientInteractiveTargets.first()
                        acc.performTap(target.first, target.second, 90L)
                        TacticalGameAction("🎯 Viziune Pixel: Acțiune directă pe țintă detectată (${target.first.toInt()}, ${target.second.toInt()})", 750L)
                    }

                    else -> {
                        TacticalGameAction(
                            status = "👀 Mod Adaptiv Natural: Ecran stabil. AI-ul observă acțiunea fără să apese haotic în gol.",
                            pauseAfterActionMs = 1500L,
                            targetCoordinates = "N/A (Observare Pasivă)",
                            gestureDispatched = true,
                            actionCategory = "OBSERVE_STABLE",
                            reasoning = "Niciun element interactiv sau pericol iminent pe ecran. Se evită trimiterea de comenzi oarbe repetate."
                        )
                    }
                }
            }
        }
    }

    fun stopGameAutopilot(keepOverlayVisible: Boolean = true): String {
        val total = _gameBotActionsCount.value
        val cur = _liveGameState.value
        val game = _targetGameName.value.ifBlank { cur.gameTitle }
        _isGameBotRunning.value = false
        dualBrainEngine.stopStrategicCommanderLoop()
        gameBotJob?.cancel()
        gameBotJob = null
        clashCurrentState = ClashState.VILLAGE_HOME
        clashScoutCount = 0
        clashWaveStep = 0
        clashBattleWaitCycles = 0
        _liveGameState.value = cur.copy(
            isRunning = false,
            lastAiDecision = "Oprit • Scor final: ${cur.score} pct (${cur.coins} monede)"
        )
        _gameBotStatus.value = "Oprit (Scor: ${cur.score} pct • $total mutări în $game)"
        val acc = EvaAccessibilityService.instance
        if (acc != null) {
            if (keepOverlayVisible && EvaAccessibilityService.isFloatingHudVisible.value) {
                acc.showOrUpdateFloatingGameHud(
                    isRunning = false,
                    gameTitle = game,
                    modeName = _activeGameMode.value.displayName,
                    statusLine = "Oprit ($total mutări). Apasă ▶️ Joacă Aici pentru a relua!"
                )
            } else if (!keepOverlayVisible) {
                acc.hideFloatingGameHud()
            }
        }
        return "🛑 **[EVA-1 Game Bot]** Autopilotul pentru **$game** s-a oprit. Scor obținut de EVA-1: **${cur.score} puncte** (${cur.coins} monede, $total mutări AI)."
    }

    private fun dispatchAutonomousTap(acc: EvaAccessibilityService?, x: Float, y: Float, durationMs: Long = 105L) {
        if (acc != null) {
            acc.performTap(x, y, durationMs)
        } else {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "input tap ${x.toInt()} ${y.toInt()}"))
            }
        }
    }

    private fun dispatchAutonomousSwipe(
        acc: EvaAccessibilityService?,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long = 200L
    ) {
        if (acc != null) {
            acc.performSwipe(x1, y1, x2, y2, durationMs)
        } else {
            runCatching {
                Runtime.getRuntime().exec(
                    arrayOf("sh", "-c", "input swipe ${x1.toInt()} ${y1.toInt()} ${x2.toInt()} ${y2.toInt()} $durationMs")
                )
            }
        }
    }

    // ── 7. Clipboard Manager ──────────────────────────────────────
    fun readClipboard(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
    }

    fun writeClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    // ── 8. Executie comenzi shell Android (sandbox non-root) ──────
    suspend fun executeShellCommand(command: String): String = withContext(Dispatchers.IO) {
        val forbidden = listOf("rm -rf", "mkfs", "dd if=", "reboot", "su ")
        if (forbidden.any { command.contains(it, ignoreCase = true) }) {
            return@withContext "Comandă blocată de filtrul de siguranță EVA-1."
        }

        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val finished = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext "Comanda shell a depășit limita de timp (8s) și a fost oprită automat."
            }
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            buildString {
                if (stdout.isNotBlank()) append(stdout.trim())
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("[STDERR]: ${stderr.trim()}")
                }
                if (isEmpty()) append("(Comandă executată fără output, exit=${process.exitValue()})")
            }
        } catch (e: Exception) {
            "Eroare execuție shell: ${e.message}"
        }
    }

    // ── 9. Actiuni rapide de sistem ───────────────────────────────
    fun openUrl(url: String) {
        val finalUrl = if (url.startsWith("http")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openSystemSettings(section: String = "") {
        val action = when (section.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    fun shareText(text: String, title: String = "Partajează din EVA-1") {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    // ── 10. Explorare fisiere din directorul aplicatiei & Downloads ──
    fun listFilesInDirectory(path: String = ""): List<File> {
        val dir = if (path.isBlank()) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } else {
            File(path)
        }
        return dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
    }

    // ── 11. Extrage coduri 2FA / OTP din ultimele SMS-uri ──────────
    fun extractLatest2faCodeFromSms(): String? {
        return try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                null,
                null,
                "date DESC LIMIT 10"
            ) ?: return null

            val otpRegex = Regex("\\b(\\d{4,8})\\b")
            cursor.use { c ->
                val bodyIdx = c.getColumnIndex("body")
                val addrIdx = c.getColumnIndex("address")
                while (c.moveToNext()) {
                    val body = c.getString(bodyIdx) ?: continue
                    val addr = c.getString(addrIdx) ?: "SMS"
                    if (body.contains("cod", ignoreCase = true) ||
                        body.contains("code", ignoreCase = true) ||
                        body.contains("verification", ignoreCase = true) ||
                        body.contains("parola", ignoreCase = true) ||
                        body.contains("OTP", ignoreCase = true)
                    ) {
                        val match = otpRegex.find(body)
                        if (match != null) {
                            return "${match.groupValues[1]} (de la $addr: \"$body\")"
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
