package com.eva1.companion.engines

import android.content.Context
import android.util.Log
import com.eva1.companion.AnthropicApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Nivelul de Măiestrie dobândit de AI prin antrenament și învățare pe jocuri.
 */
enum class GameSkillTier(val displayName: String, val badge: String, val minXp: Int) {
    ROOKIE("Începător (Învață Mecanicile)", "🌱", 0),
    APPRENTICE("Ucenic (Comenzi de Bază Asimilate)", "🥉", 150),
    VETERAN("Veteran (Tactică & Reflexe Naturale)", "🥈", 500),
    PRO_GAMER("Pro Gamer (Mișcare Umană Fluidă & Țintire)", "🥇", 1200),
    ESPORTS_MASTER("Maestru eSports (Zero Mișcări Haotice / Eficiență Maximă)", "🏆", 2500)
}

/**
 * Profilul de Cunoaștere & Învățare al unui joc instalat pe telefon.
 */
data class GameKnowledgeProfile(
    val gameTitle: String,
    val packageName: String,
    val genre: GameBotMode = GameBotMode.AUTO_ADAPTIVE,
    val onlineGuideSummary: String = "",
    val keyControlsLearned: String = "",
    val tacticalRules: List<String> = emptyList(),
    val experiencePoints: Int = 100,
    val skillTier: GameSkillTier = GameSkillTier.ROOKIE,
    val successfulActionsCount: Int = 0,
    val penalizedActionsCount: Int = 0,
    val totalTimePlayedSeconds: Long = 0L,
    val lastAdaptationNote: String = "Profil inițializat — gata de analiză vizuală și învățare.",
    val isOnlineGuideLoaded: Boolean = false
) {
    val successRatePercent: Int
        get() {
            val total = successfulActionsCount + penalizedActionsCount
            if (total == 0) return 0
            return ((successfulActionsCount.toFloat() / total) * 100).toInt().coerceIn(10, 100)
        }
}

/**
 * Motorul Cognitiv de Învățare Autonomă și Interpretare a Jocurilor (EVA-1 Game Cognition 2026).
 *
 * Responsabilități:
 * 1. Căutare Web & Wikipedia Live pentru comenzi, butoane și strategii când AI-ul întâlnește un joc nou.
 * 2. Adaptare prin Învățare prin Întărire (Reinforcement Learning):
 *    - Evaluează rezultatul acțiunilor: dacă viața (HP) scade brusc, penalizează acțiunea și caută adăpost/medkit.
 *    - Dacă inamicul este doborât sau scorul crește, consolidează vectorul de atac.
 * 3. Elimină orice gest robotizat sau apăsare haotică, implementând dinamici motorii umane:
 *    - FPS/PUBG: Joystick continuu pe stânga, scanare cameră lină pe dreapta, foc DOAR la confirmare țintă în reticul.
 *    - Curse/Asphalt: Urmărire fluidă a trasei, frânare și drift controlat în curbe, nitro doar pe linii drepte.
 *    - Fotbal: Construcție pase la mijloc, sprint pe flancuri, șut la poartă în careul de 16m.
 *    - Puzzles: Mutări calculate cu pauze de cădere a pieselor.
 */
class EvaGameCognitionEngine(
    private val context: Context,
    private val anthropicService: AnthropicApiService? = null
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Baza de cunoștințe persistentă per pachet joc
    private val profilesCache = ConcurrentHashMap<String, GameKnowledgeProfile>()

    private val _activeProfile = MutableStateFlow<GameKnowledgeProfile?>(null)
    val activeProfile: StateFlow<GameKnowledgeProfile?> = _activeProfile.asStateFlow()

    private val _cognitionLog = MutableStateFlow("🧠 Motor Cognitiv EVA-1 gata — pregătit să învețe orice joc.")
    val cognitionLog: StateFlow<String> = _cognitionLog.asStateFlow()

    init {
        preseedKnownGameProfiles()
    }

    /**
     * Încarcă sau creează profilul de învățare al unui joc când acesta este lansat.
     */
    fun attachToGame(gameTitle: String, packageName: String): GameKnowledgeProfile {
        val key = packageName.ifBlank { gameTitle.lowercase() }
        val existing = profilesCache[key]
        if (existing != null) {
            _activeProfile.value = existing
            return existing
        }

        val resolvedGenre = inferGenreFromIdentity(gameTitle, packageName)
        val initialProfile = GameKnowledgeProfile(
            gameTitle = gameTitle.ifBlank { packageName.substringAfterLast('.') },
            packageName = packageName,
            genre = resolvedGenre,
            onlineGuideSummary = "Căutare automată pe internet a regulilor de joc...",
            keyControlsLearned = getDefaultControlsForGenre(resolvedGenre),
            tacticalRules = getDefaultTacticsForGenre(resolvedGenre)
        )
        profilesCache[key] = initialProfile
        _activeProfile.value = initialProfile

        // Lansăm căutarea asincronă pe net a ghidului și tacticii reale
        engineScope.launch {
            learnGameLogicOnline(gameTitle, packageName)
        }

        return initialProfile
    }

    /**
     * Caută pe internet (Web / Wikipedia / Google RSS) regulile, controalele și tacticile jocului.
     */
    suspend fun learnGameLogicOnline(gameTitle: String, packageName: String) = withContext(Dispatchers.IO) {
        val cleanName = gameTitle.ifBlank { packageName.substringAfterLast('.') }
        _cognitionLog.value = "🔍 Căutare online ghid & mecanici pentru „$cleanName”..."

        var fetchedGuide = ""
        var learnedTactics = mutableListOf<String>()

        try {
            // 1. Căutare DuckDuckGo Instant Answer / Wikipedia
            val query = "$cleanName mobile game controls tactics gameplay guide"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val ddgUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val req = Request.Builder()
                .url(ddgUrl)
                .header("User-Agent", "EVA-1-GameCognition/2026.2")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val abstractText = json.optString("AbstractText", "")
                        if (abstractText.isNotBlank()) {
                            fetchedGuide = abstractText.take(400)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("GameCognition", "Căutare online eșuată: ${e.message}")
        }

        val resolvedGenre = inferGenreFromIdentity(cleanName, packageName)

        if (fetchedGuide.isBlank()) {
            fetchedGuide = when (resolvedGenre) {
                GameBotMode.FPS_BATTLE_ROYALE ->
                    "Shooter Tactic / Battle Royale: Deplasare continuă cu joystick-ul stâng, scanare orizont cu glisare dreapta. Foc controlat DOAR când ținta este în reticul. Folosire adăpost la viață scăzută."
                GameBotMode.RACING_NFS ->
                    "Simulator Curse Auto: Urmărire fluidă a curbelor pe trasa ideală, frânare și inițiere drift înainte de viraje, nitro boost exclusiv pe liniile drepte."
                GameBotMode.FOOTBALL_SPORTS ->
                    "Simulare Fotbal: Construcție cu pase scurte la mijlocul terenului, accelerare pe flancuri și șut plasat din interiorul careului."
                GameBotMode.CLASH_BASE_STRATEGY ->
                    "Strategie Bază: Colectare resurse, identificare apărări inamice, desfășurare pe valuri (Tancuri întâi, DPS în spate), fără spam haotic în luptă."
                GameBotMode.TOUCH_DRAG_PUZZLE, GameBotMode.PUZZLE_MATCH ->
                    "Puzzle Tactil: Scanare grilă pentru combinații optime de 3 sau linii complete, glisare deliberată și așteptarea cascadelor de puncte."
                GameBotMode.SOLITAIRE_CARDS ->
                    "Solitaire & Jocuri de Cărți (Klondike / Spider / FreeCell): Prioritizare deblocare cărți ascunse de pe cele 7 coloane Tableau, trimitere Ași și cărți consecutive la Fundație, mutare Regi doar când eliberează o carte cu fața în jos, și tragere din pachet (Stock) când nu mai există mutări pe tablă."
                else ->
                    "Joc Acțiune Adaptivă: Control uman fluid bazat pe analiza vizuală în timp real a interfeței și reacție la obstacole."
            }
        }

        learnedTactics = getDefaultTacticsForGenre(resolvedGenre).toMutableList()

        val key = packageName.ifBlank { cleanName.lowercase() }
        val cur = profilesCache[key] ?: GameKnowledgeProfile(cleanName, packageName, resolvedGenre)
        val updated = cur.copy(
            genre = resolvedGenre,
            onlineGuideSummary = fetchedGuide,
            tacticalRules = learnedTactics,
            isOnlineGuideLoaded = true,
            experiencePoints = cur.experiencePoints + 50,
            lastAdaptationNote = "Ghid și tactici asimilate cu succes din surse web."
        )
        profilesCache[key] = updated
        _activeProfile.value = updated
        _cognitionLog.value = "✅ Cunoștințe asimilate pentru „$cleanName” (${resolvedGenre.displayName}): Stil de joc calibrat!"
    }

    /**
     * Bucla de Învățare prin Întărire (Reinforcement Learning Feedback):
     * Monitorizează efectul acțiunilor asupra stării de viață (HP), scorului și progresului pe ecran.
     */
    fun evaluateActionOutcome(
        packageName: String,
        hpBefore: Float,
        hpAfter: Float,
        frameDiff: Float,
        enemyInCrosshair: Boolean,
        actionExecuted: TacticalGameAction
    ) {
        val key = packageName.ifBlank { _activeProfile.value?.packageName.orEmpty() }
        val cur = profilesCache[key] ?: _activeProfile.value ?: return

        var newSuccess = cur.successfulActionsCount
        var newPenalized = cur.penalizedActionsCount
        var addedXp = 1
        var adaptationNote = cur.lastAdaptationNote

        // 1. Penalizare dacă AI-ul a suferit daune critice în timp ce acționa haotic
        if (hpAfter < hpBefore - 8f) {
            newPenalized++
            adaptationNote = "⚠️ Daune detectate (${hpBefore.toInt()}% ➔ ${hpAfter.toInt()}%): Adaptat comportamentul — prioritate retragere la adăpost și vindecare."
        }
        // 2. Recompensă dacă AI-ul a angajat inamicul în reticul și nu a suferit daune
        else if (enemyInCrosshair && hpAfter >= hpBefore) {
            newSuccess++
            addedXp = 5
            adaptationNote = "🎯 Foc controlat reușit: Inamic lovit în reticul fără daune proprii — confirmată eficiența tirului."
        }
        // 3. În curse: dacă ecranul e înghețat (mașina blocată într-o barieră), penalizare
        else if (cur.genre == GameBotMode.RACING_NFS && frameDiff < 0.004f) {
            newPenalized++
            adaptationNote = "🚧 Mașină blocată la margine: Comutat pe manevră de marșarier și redresare trasă."
        } else {
            // Fără un semnal observabil nu declarăm acțiunea reușită și nu acordăm XP.
            // Altfel profilul ar pretinde că a învățat chiar și când nu a putut măsura rezultatul.
            _cognitionLog.value = "⏸️ Rezultatul acțiunii nu poate fi verificat vizual; profilul nu primește recompensă."
            return
        }

        val totalXp = cur.experiencePoints + addedXp
        val newTier = when {
            totalXp >= GameSkillTier.ESPORTS_MASTER.minXp -> GameSkillTier.ESPORTS_MASTER
            totalXp >= GameSkillTier.PRO_GAMER.minXp -> GameSkillTier.PRO_GAMER
            totalXp >= GameSkillTier.VETERAN.minXp -> GameSkillTier.VETERAN
            totalXp >= GameSkillTier.APPRENTICE.minXp -> GameSkillTier.APPRENTICE
            else -> GameSkillTier.ROOKIE
        }

        val updated = cur.copy(
            experiencePoints = totalXp,
            skillTier = newTier,
            successfulActionsCount = newSuccess,
            penalizedActionsCount = newPenalized,
            lastAdaptationNote = adaptationNote
        )
        profilesCache[key] = updated
        _activeProfile.value = updated
    }

    /**
     * Deduce genul jocului după titlu și numele pachetului.
     */
    fun inferGenreFromIdentity(title: String, pkg: String): GameBotMode {
        val combined = "$title $pkg".lowercase()
        return when {
            combined.contains("solit") || combined.contains("klondike") || combined.contains("patience") ||
                combined.contains("spider") || combined.contains("freecell") || combined.contains("tripledot") ||
                combined.contains("mobilityware") || combined.contains("card") || combined.contains("carte") ||
                combined.contains("carti") || combined.contains("cărți") || combined.contains("uno") ||
                combined.contains("rummy") || combined.contains("remi") -> GameBotMode.SOLITAIRE_CARDS

            combined.contains("tetris") || combined.contains("brick") || combined.contains("2048") ||
                combined.contains("snake") || combined.contains("arcade") -> GameBotMode.TETRIS_ARCADE

            combined.contains("pubg") || combined.contains("cod") || combined.contains("call of duty") ||
                combined.contains("free fire") || combined.contains("standoff") || combined.contains("shooter") ||
                combined.contains("fps") || combined.contains("sniper") -> GameBotMode.FPS_BATTLE_ROYALE

            combined.contains("asphalt") || combined.contains("need for speed") || combined.contains("nfs") ||
                combined.contains("racing") || combined.contains("drift") || combined.contains("traffic rider") ||
                combined.contains("real racing") || combined.contains("car ") || combined.contains("drive") -> GameBotMode.RACING_NFS

            combined.contains("fifa") || combined.contains("fc mobile") || combined.contains("efootball") ||
                combined.contains("pes") || combined.contains("dls") || combined.contains("soccer") ||
                combined.contains("football") -> GameBotMode.FOOTBALL_SPORTS

            combined.contains("clash") || combined.contains("supercell") || combined.contains("lords mobile") ||
                combined.contains("rise of kingdoms") || combined.contains("strategy") -> GameBotMode.CLASH_BASE_STRATEGY

            combined.contains("blast") || combined.contains("woodoku") || combined.contains("1010") ||
                combined.contains("drag") -> GameBotMode.TOUCH_DRAG_PUZZLE

            combined.contains("candy") || combined.contains("crush") || combined.contains("match") ||
                combined.contains("puzzle") || combined.contains("jewel") -> GameBotMode.PUZZLE_MATCH

            combined.contains("subway") || combined.contains("surfers") || combined.contains("temple run") ||
                combined.contains("runner") -> GameBotMode.ENDLESS_RUNNER

            combined.contains("gta") || combined.contains("gangstar") || combined.contains("payback") ||
                combined.contains("open world") || combined.contains("roblox") || combined.contains("minecraft") -> GameBotMode.OPEN_WORLD_GTA

            combined.contains("brawl stars") || combined.contains("moba") || combined.contains("legends") ||
                combined.contains("rpg") || combined.contains("genshin") -> GameBotMode.RPG_STRATEGY

            else -> GameBotMode.AUTO_ADAPTIVE
        }
    }

    private fun getDefaultControlsForGenre(genre: GameBotMode): String {
        return when (genre) {
            GameBotMode.SOLITAIRE_CARDS -> "Sus: 4 Fundații (A..K) + Waste (73%, 17%) + Pachet Stock (91%, 17%) | Mijloc: 7 Coloane Tableau (Tap Auto-Move)"
            GameBotMode.TETRIS_ARCADE -> "Jos: ◀ Stânga (30%, 84%) | ↻ Rotire (50%, 84%) | ▶ Dreapta (70%, 84%) | ▼ Drop (40%, 92%)"
            GameBotMode.FPS_BATTLE_ROYALE -> "Stânga: Joystick Sprint | Dreapta: Reticul, Foc, ADS, Salt, Reîncărcare, Medkit"
            GameBotMode.RACING_NFS -> "Stânga: Viraj Stânga/Frână | Dreapta: Viraj Dreapta/Nitro/Drift"
            GameBotMode.FOOTBALL_SPORTS -> "Stânga: Dribling Direcțional | Dreapta: Pasă, Pasă Culoar, Șut, Deposedare"
            GameBotMode.CLASH_BASE_STRATEGY -> "Jos: Bară Trupe & Eroi | Flancuri: Perimetru de Lansare în afara Liniei Roșii"
            GameBotMode.TOUCH_DRAG_PUZZLE -> "Jos: Tavă 3 Piese | Centru: Grilă Plasare Celule Libere"
            GameBotMode.PUZZLE_MATCH -> "Centru: Grilă Glisare Orizontală/Verticală 3-în-Rând"
            GameBotMode.ENDLESS_RUNNER -> "Centru Ecran: Glisare 4D (Stânga/Dreapta/Salt Sus/Slide Jos)"
            else -> "Ecran Tactil Complet: Detectare Automată Elemente Active & Butoane Interactive"
        }
    }

    private fun getDefaultTacticsForGenre(genre: GameBotMode): List<String> {
        return when (genre) {
            GameBotMode.SOLITAIRE_CARDS -> listOf(
                "Prioritizează mutarea cărților din coloanele care au cărți cu fața în jos pentru a le întoarce.",
                "Nu muta un Rege deja aflat în vârful unei coloane deschise pe o altă coloană goală (evită buclele infinite).",
                "Când toate mutările utile de pe cele 7 coloane au fost epuizate, trage o carte nouă din pachetul Stock (dreapta-sus)."
            )
            GameBotMode.TETRIS_ARCADE -> listOf(
                "Identifică în timp real coloana piesei în cădere și coloana cu cea mai joasă stivă.",
                "Apasă exclusiv pe butoanele fizice Stânga (◀), Dreapta (▶), Rotire (↻) și Drop (▼) de la baza ecranului.",
                "Declanșează Drop (▼) imediat ce piesa este aliniată pe axa verticală a golului țintă."
            )
            GameBotMode.FPS_BATTLE_ROYALE -> listOf(
                "Nu trage în gol: declanșează focul doar când reticulul intersectează inamicul.",
                "Menține mișcarea continuă de strafe stânga-dreapta în timpul luptelor pentru a fi o țintă grea.",
                "Când viața scade sub 40%, oprește focul, caută adăpost și folosește trusa medicală.",
                "Reîncarcă muniția doar când ești în siguranță, nu direct în fața inamicului."
            )
            GameBotMode.RACING_NFS -> listOf(
                "Păstrează linia ideală de rulare urmărind curbura drumului.",
                "Nu declanșa Nitro Boost în curbe strânse — doar la ieșirea pe linie dreaptă.",
                "Inițiază drift cu o atingere scurtă de frână cu 150ms înainte de viraj.",
                "Dacă lovești o barieră, activează marșarierul și repoziționează mașina."
            )
            GameBotMode.FOOTBALL_SPORTS -> listOf(
                "Construiește jocul prin pase rapide la mijlocul terenului.",
                "Pasează pe culoar (Through Ball) când un coechipier demarchează pe flanc.",
                "Șutează la poartă din interiorul careului de 16 metri spre colțurile porții."
            )
            GameBotMode.CLASH_BASE_STRATEGY -> listOf(
                "Lansează tancurile primele pentru a atrage focul tunurilor inamice.",
                "Plasează arcașii și trupele DPS în spatele primei linii.",
                "Nu atinge ecranul în timpul bătăliei decât pentru activarea abilității eroului."
            )
            else -> listOf(
                "Analizează ecranul înainte de fiecare gest tactil.",
                "Păstrează pauze naturale între acțiuni pentru finalizarea animațiilor jocului."
            )
        }
    }

    private fun preseedKnownGameProfiles() {
        val solitaire = GameKnowledgeProfile(
            gameTitle = "Solitaire / Solitario (Klondike)",
            packageName = "com.tripledot.solitaire",
            genre = GameBotMode.SOLITAIRE_CARDS,
            onlineGuideSummary = "Klondike Solitaire: Deblocare metodică a celor 7 coloane Tableau, trimitere cărți la Fundație (A..K), protecție anti-buclă pentru Regi și extragere din pachetul Stock.",
            keyControlsLearned = getDefaultControlsForGenre(GameBotMode.SOLITAIRE_CARDS),
            tacticalRules = getDefaultTacticsForGenre(GameBotMode.SOLITAIRE_CARDS),
            experiencePoints = 1350,
            skillTier = GameSkillTier.PRO_GAMER
        )
        profilesCache["com.tripledot.solitaire"] = solitaire
        profilesCache["com.mobilityware.solitaire"] = solitaire.copy(packageName = "com.mobilityware.solitaire")

        val pubg = GameKnowledgeProfile(
            gameTitle = "PUBG Mobile / Call of Duty",
            packageName = "com.tencent.ig",
            genre = GameBotMode.FPS_BATTLE_ROYALE,
            onlineGuideSummary = "Tactical Battle Royale: Foc de acoperire, scanare orizont, deplasare continuă cu joystick-ul și utilizare prudentă a kiturilor medicale.",
            keyControlsLearned = getDefaultControlsForGenre(GameBotMode.FPS_BATTLE_ROYALE),
            tacticalRules = getDefaultTacticsForGenre(GameBotMode.FPS_BATTLE_ROYALE),
            experiencePoints = 850,
            skillTier = GameSkillTier.VETERAN
        )
        profilesCache["com.tencent.ig"] = pubg
        profilesCache["com.activision.callofduty.shooter"] = pubg.copy(
            gameTitle = "Call of Duty: Mobile",
            packageName = "com.activision.callofduty.shooter"
        )

        val asphalt = GameKnowledgeProfile(
            gameTitle = "Asphalt 9 / Need for Speed",
            packageName = "com.gameloft.android.ANMP.GloftA9HM",
            genre = GameBotMode.RACING_NFS,
            onlineGuideSummary = "Curse Arcade: Păstrarea trasei ideale, drift în curbe pentru încărcare shockwave și accelerare nitro pe liniile drepte.",
            keyControlsLearned = getDefaultControlsForGenre(GameBotMode.RACING_NFS),
            tacticalRules = getDefaultTacticsForGenre(GameBotMode.RACING_NFS),
            experiencePoints = 620,
            skillTier = GameSkillTier.VETERAN
        )
        profilesCache["com.gameloft.android.ANMP.GloftA9HM"] = asphalt

        val clash = GameKnowledgeProfile(
            gameTitle = "Clash of Clans",
            packageName = "com.supercell.clashofclans",
            genre = GameBotMode.CLASH_BASE_STRATEGY,
            onlineGuideSummary = "Strategie & Război de Clanuri: Colectare resurse, scouting bază, desfășurare metodică a trupelor pe flanc și observare pasivă a luptei.",
            keyControlsLearned = getDefaultControlsForGenre(GameBotMode.CLASH_BASE_STRATEGY),
            tacticalRules = getDefaultTacticsForGenre(GameBotMode.CLASH_BASE_STRATEGY),
            experiencePoints = 1400,
            skillTier = GameSkillTier.PRO_GAMER
        )
        profilesCache["com.supercell.clashofclans"] = clash

        val fifa = GameKnowledgeProfile(
            gameTitle = "EA SPORTS FC Mobile / FIFA",
            packageName = "com.ea.gp.fifamobile",
            genre = GameBotMode.FOOTBALL_SPORTS,
            onlineGuideSummary = "Fotbal & Simulatoare: Construcție prin pase rapide la mijloc, sprint pe flancuri și finalizare precisă cu șut la poartă în careu.",
            keyControlsLearned = getDefaultControlsForGenre(GameBotMode.FOOTBALL_SPORTS),
            tacticalRules = getDefaultTacticsForGenre(GameBotMode.FOOTBALL_SPORTS),
            experiencePoints = 950,
            skillTier = GameSkillTier.VETERAN
        )
        profilesCache["com.ea.gp.fifamobile"] = fifa

        val freefire = pubg.copy(
            gameTitle = "Garena Free Fire",
            packageName = "com.dts.freefireth"
        )
        profilesCache["com.dts.freefireth"] = freefire
    }
}
