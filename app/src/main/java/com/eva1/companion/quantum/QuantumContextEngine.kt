package com.eva1.companion.quantum

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.eva1.companion.AiEngine
import com.eva1.companion.AnthropicApiService
import com.eva1.companion.Message
import com.eva1.companion.MessageRole
import com.eva1.companion.MistralApiService
import com.eva1.companion.QuantumDebugStats
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.Calendar
import kotlin.math.*

// ══════════════════════════════════════════════════════════════════════════════
// EVA-1 QUANTUM CONTEXT & ZERO-UI ENGINE
//
// Arhitectura completa in 5 straturi:
//   STRATUL 1 — Colectare Senzori Fizici (Accelerometru, Lumina, Proximitate,
//               Giroscop, Microfon Ambiental, Baterie, Retea, Ceas Biologic)
//   STRATUL 2 — Inferenta Biometrica si Cognitiva (Stres, Oboseala, Focus,
//               Stare Fizica, Mediu Acustic)
//   STRATUL 3 — Matricea de Probabilitati Bayesiene + Lant Markov Temporal
//               pentru Predictia Intentiei inainte ca utilizatorul sa scrie
//   STRATUL 4 — Pre-Generare Asincrona (Zero-Latency Cache)
//   STRATUL 5 — Interfata Zero-UI Adaptiva (Ghost Mode, Whisper Mode,
//               Focus Shield, Proactive Whisper)
// ══════════════════════════════════════════════════════════════════════════════

enum class PhysicalMotionState(val label: String) {
    STILL_DESK("Pe birou / Nemișcat"),
    IN_HAND_STABLE("În mână (Stabil)"),
    WALKING("În mers"),
    RUNNING_OR_VEHICLE("Alergare / Vehicul"),
    FACE_DOWN_DND("Cu fața în jos (Nu deranja)"),
    IN_POCKET("În buzunar")
}

enum class AmbientEnvironment(val label: String) {
    DEEP_SILENCE("Liniște profundă (<30 dB)"),
    QUIET_ROOM("Cameră liniștită (30-45 dB)"),
    OFFICE_CONVERSATION("Birou / Conversație (45-65 dB)"),
    NOISY_CROWD("Zgomot intens / Stradă (>65 dB)")
}

enum class LightingCondition(val label: String) {
    PITCH_DARK("Întuneric total (0-5 lux)"),
    DIM_INDOOR("Lumină difuză (5-100 lux)"),
    NORMAL_INDOOR("Interior normal (100-500 lux)"),
    BRIGHT_DAYLIGHT("Lumină puternică (>500 lux)")
}

enum class ZeroUiMode(val title: String, val description: String) {
    GHOST_SILENT(
        "Mod Fantomă (Zero Intruziune)",
        "Telefonul este cu fața în jos sau în buzunar. EVA-1 tace și pregătește totul în fundal."
    ),
    FOCUS_SHIELD(
        "Scut de Concentrare",
        "Utilizatorul lucrează intens. Răspunsuri ultra-scurte, fără întreruperi inutile."
    ),
    EMPATHIC_CALM(
        "Mod Empatic & Calm",
        "Nivel ridicat de stres sau oboseală detectat. Ton cald, blând, odihnitor."
    ),
    HANDS_FREE_VOICE(
        "Mod În Mișcare (Hands-Free)",
        "Utilizatorul merge sau conduce. Răspunsuri directe, potrivite pentru redare vocală."
    ),
    PROACTIVE_COMPANION(
        "Mod Proactiv Quantum",
        "Condiții optime. EVA-1 anticipează următoarea întrebare și o pre-calculează."
    )
}

enum class PredictedUserNeed(val code: String, val displayTitle: String, val defaultPrompt: String) {
    MORNING_BRIEFING(
        "MORNING_BRIEFING",
        "Briefing de Dimineață & Planificare Zi",
        "Fă-mi un plan scurt, energizant și clar pentru ziua de azi, cu 3 priorități esențiale."
    ),
    DEEP_WORK_ASSIST(
        "DEEP_WORK_ASSIST",
        "Asistență Productivitate & Focus",
        "Sunt în mijlocul unei sesiuni de lucru concentrat. Dă-mi o tehnică rapidă de deblocare mentală și organizare."
    ),
    STRESS_DECOMPRESSION(
        "STRESS_DECOMPRESSION",
        "Decompresie Stres & Resetare Mentală",
        "Simt puțină tensiune. Ghidează-mă printr-un exercițiu de respirație de 60 de secunde și o perspectivă calmă."
    ),
    COMMUTE_SUMMARY(
        "COMMUTE_SUMMARY",
        "Idei Rapide în Mișcare",
        "Sunt în mișcare. Spune-mi pe scurt o idee fascinantă din tehnologie/știință pe care să o rețin în 30 de secunde."
    ),
    EVENING_REFLECTION(
        "EVENING_REFLECTION",
        "Reflecție de Seară & Sinteză",
        "Ajută-mă să închei ziua liniștit: 3 întrebări scurte de reflecție și pregătire pentru un somn odihnitor."
    ),
    CREATIVE_SPARK(
        "CREATIVE_SPARK",
        "Scânteie Creativă & Brainstorming",
        "Propune-mi 3 concepte inovatoare sau direcții creative pe care le putem construi împreună acum."
    )
}

data class BiometricTelemetry(
    val motionState: PhysicalMotionState = PhysicalMotionState.IN_HAND_STABLE,
    val ambientEnvironment: AmbientEnvironment = AmbientEnvironment.QUIET_ROOM,
    val lightingCondition: LightingCondition = LightingCondition.NORMAL_INDOOR,
    val luxLevel: Float = 250f,
    val ambientDecibels: Float = 38f,
    val microTremorIndex: Float = 0.12f,
    val typingCadenceWpm: Float = 42f,
    val backspaceErrorRate: Float = 0.05f,
    val stressScore: Int = 22,
    val fatigueScore: Int = 18,
    val focusScore: Int = 78,
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val isWifiConnected: Boolean = true,
    val hourOfDay: Int = 12
)

data class QuantumPredictionState(
    val telemetry: BiometricTelemetry = BiometricTelemetry(),
    val zeroUiMode: ZeroUiMode = ZeroUiMode.PROACTIVE_COMPANION,
    val primaryNeed: PredictedUserNeed = PredictedUserNeed.DEEP_WORK_ASSIST,
    val confidencePercent: Int = 82,
    val needProbabilities: Map<PredictedUserNeed, Float> = emptyMap(),
    val precomputedResponse: String = "",
    val isPrecomputing: Boolean = false,
    val lastPrecomputedFor: PredictedUserNeed? = null,
    val dynamicSystemDirective: String = ""
)

class QuantumContextEngine(
    private val context: Context,
    private val mistral: MistralApiService,
    private val anthropic: AnthropicApiService
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val prefs = context.getSharedPreferences("eva1_quantum_patterns", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(QuantumPredictionState())
    val state: StateFlow<QuantumPredictionState> = _state.asStateFlow()

    private val _debugStats = MutableStateFlow(QuantumDebugStats())
    val debugStats: StateFlow<QuantumDebugStats> = _debugStats.asStateFlow()

    @Volatile private var currentLux = 220f
    @Volatile private var isNearProximity = false
    @Volatile private var accelX = 0f
    @Volatile private var accelY = 0f
    @Volatile private var accelZ = 9.8f
    @Volatile private var gyroMagnitude = 0f
    @Volatile private var currentDb = 35f

    private val accelHistory = ArrayDeque<Float>(40)
    private val keystrokeTimestamps = ArrayDeque<Long>(50)
    @Volatile private var totalKeystrokes = 0
    @Volatile private var backspaceCount = 0

    // Matrice Markov persistenta: Ora (0..23) -> Frecventa fiecarei nevoi
    private val hourlyHabitMatrix = Array(24) { mutableMapOf<PredictedUserNeed, Int>() }

    private var isRunning = false
    private var audioMonitorJob: Job? = null
    private var fusionLoopJob: Job? = null
    private var precomputeJob: Job? = null

    init {
        loadPersistedPatterns()
        recomputeQuantumState()
    }

    // ── Incarca si salveaza matricea Markov temporala ─────────────
    private fun loadPersistedPatterns() {
        synchronized(hourlyHabitMatrix) {
            for (hour in 0..23) {
                PredictedUserNeed.entries.forEach { need ->
                    val count = prefs.getInt("h_${hour}_${need.code}", 0)
                    if (count > 0) {
                        hourlyHabitMatrix[hour][need] = count
                    }
                }
            }
        }
    }

    private fun savePersistedPatterns() {
        val editor = prefs.edit()
        synchronized(hourlyHabitMatrix) {
            for (hour in 0..23) {
                hourlyHabitMatrix[hour].forEach { (need, count) ->
                    editor.putInt("h_${hour}_${need.code}", count)
                }
            }
        }
        editor.apply()
    }

    // ── Porneste colectarea senzorilor si bucla predictiva ────────
    fun startEngine() {
        if (isRunning) return
        isRunning = true

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        startAmbientNoiseSampling()

        fusionLoopJob = scope.launch {
            while (isActive) {
                recomputeQuantumState()
                delay(3000L)
            }
        }
    }

    fun stopEngine() {
        isRunning = false
        sensorManager.unregisterListener(this)
        audioMonitorJob?.cancel()
        fusionLoopJob?.cancel()
        precomputeJob?.cancel()
    }

    fun recordTypingEvent(newText: String, previousText: String) {
        val now = System.currentTimeMillis()
        synchronized(keystrokeTimestamps) {
            keystrokeTimestamps.addLast(now)
            if (keystrokeTimestamps.size > 40) keystrokeTimestamps.removeFirst()
        }

        totalKeystrokes++
        if (newText.length < previousText.length) {
            backspaceCount++
        }
    }

    fun recordUserAction(actualPrompt: String) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val matchedNeed = classifyPromptToNeed(actualPrompt)
        synchronized(hourlyHabitMatrix) {
            val map = hourlyHabitMatrix[hour]
            map[matchedNeed] = (map[matchedNeed] ?: 0) + 1
        }
        savePersistedPatterns()
        recomputeQuantumState()
    }

    // ── 1. Citire Senzori Fizici ──────────────────────────────────
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
                val mag = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
                synchronized(accelHistory) {
                    accelHistory.addLast(mag)
                    if (accelHistory.size > 35) accelHistory.removeFirst()
                }
            }
            Sensor.TYPE_LIGHT -> {
                currentLux = event.values[0].coerceAtLeast(0f)
            }
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange
                isNearProximity = event.values[0] < min(5f, maxRange)
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                gyroMagnitude = sqrt(gx * gx + gy * gy + gz * gz)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Volatile
    private var audioHardwareAvailable = true

    private fun isRunningInEmulatorOrVirtualEnv(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hw = Build.HARDWARE.lowercase()
        val prod = Build.PRODUCT.lowercase()
        return fp.contains("generic") ||
            fp.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            hw.contains("ranchu") ||
            hw.contains("goldfish") ||
            prod.contains("sdk") ||
            prod.contains("emulator")
    }

    /**
     * Primește nivelul RMS pasiv direct de la VoiceAssistantEngine atunci când microfonul
     * este activ, evitând deschiderea unui al doilea flux AudioRecord concurent pe microfon.
     */
    fun updateVoiceRmsLevel(rmsNormalized: Float) {
        if (rmsNormalized > 0.05f) {
            currentDb = (32f + rmsNormalized * 48f).coerceIn(28f, 88f)
        }
    }

    private fun startAmbientNoiseSampling() {
        audioMonitorJob?.cancel()
        audioMonitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                // Estimare acustică neinvazivă (fără a deschide AudioRecord concurent peste SpeechRecognizer)
                val motionDbBoost = (computeAccelerometerVariance() * 12f).coerceIn(0f, 25f)
                val typingDbBoost = if (computeTypingWpm() > 45f) 6f else 0f
                currentDb = (34f + motionDbBoost + typingDbBoost).coerceIn(24f, 78f)
                delay(10_000L)
            }
        }
    }

    // ── 2. Fuziune Senzoriala si Inferenta Cognitiva ──────────────
    private fun recomputeQuantumState() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val variance = computeAccelerometerVariance()
        val motionState = when {
            accelZ < -8.0f && isNearProximity -> PhysicalMotionState.FACE_DOWN_DND
            isNearProximity && currentLux < 10f -> PhysicalMotionState.IN_POCKET
            variance < 0.04f && abs(accelZ) > 9.0f -> PhysicalMotionState.STILL_DESK
            variance < 0.35f -> PhysicalMotionState.IN_HAND_STABLE
            variance < 2.2f -> PhysicalMotionState.WALKING
            else -> PhysicalMotionState.RUNNING_OR_VEHICLE
        }

        val lighting = when {
            currentLux < 5f -> LightingCondition.PITCH_DARK
            currentLux < 100f -> LightingCondition.DIM_INDOOR
            currentLux < 500f -> LightingCondition.NORMAL_INDOOR
            else -> LightingCondition.BRIGHT_DAYLIGHT
        }

        val ambientEnv = when {
            currentDb < 32f -> AmbientEnvironment.DEEP_SILENCE
            currentDb < 48f -> AmbientEnvironment.QUIET_ROOM
            currentDb < 65f -> AmbientEnvironment.OFFICE_CONVERSATION
            else -> AmbientEnvironment.NOISY_CROWD
        }

        val wpm = computeTypingWpm()
        val errorRate = if (totalKeystrokes > 5) {
            (backspaceCount.toFloat() / totalKeystrokes.toFloat()).coerceIn(0f, 1f)
        } else 0.04f

        val tremorIndex = (variance * 0.6f + gyroMagnitude * 0.4f).coerceIn(0f, 1f)

        val rawStress = (errorRate * 120f) + (tremorIndex * 45f) +
            (if (currentDb > 65f) 18f else 0f) +
            (if (wpm > 75f) 15f else 0f)
        val stressScore = rawStress.roundToInt().coerceIn(5, 98)

        val circadianFatigue = when (hour) {
            in 0..5 -> 75
            in 6..9 -> 20
            in 10..13 -> 12
            in 14..16 -> 38
            in 17..20 -> 30
            else -> 58
        }
        val lightingFatigue = if (lighting == LightingCondition.PITCH_DARK) 20 else 0
        val fatigueScore = (circadianFatigue + lightingFatigue).coerceIn(5, 98)

        val focusScore = (100 - (stressScore * 0.45f + fatigueScore * 0.35f).roundToInt()).coerceIn(10, 99)

        val bm = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bm?.getIntExtra(BatteryManager.EXTRA_LEVEL, 80) ?: 80
        val scale = bm?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batPct = (level * 100) / scale.coerceAtLeast(1)
        val isCharging = (bm?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val telemetry = BiometricTelemetry(
            motionState = motionState,
            ambientEnvironment = ambientEnv,
            lightingCondition = lighting,
            luxLevel = currentLux,
            ambientDecibels = currentDb,
            microTremorIndex = tremorIndex,
            typingCadenceWpm = wpm,
            backspaceErrorRate = errorRate,
            stressScore = stressScore,
            fatigueScore = fatigueScore,
            focusScore = focusScore,
            batteryPercent = batPct,
            isCharging = isCharging,
            isWifiConnected = isWifi,
            hourOfDay = hour
        )

        val zeroUiMode = when {
            motionState == PhysicalMotionState.FACE_DOWN_DND ||
                motionState == PhysicalMotionState.IN_POCKET -> ZeroUiMode.GHOST_SILENT
            motionState == PhysicalMotionState.WALKING ||
                motionState == PhysicalMotionState.RUNNING_OR_VEHICLE -> ZeroUiMode.HANDS_FREE_VOICE
            stressScore >= 62 || fatigueScore >= 70 -> ZeroUiMode.EMPATHIC_CALM
            focusScore >= 75 && motionState == PhysicalMotionState.STILL_DESK -> ZeroUiMode.FOCUS_SHIELD
            else -> ZeroUiMode.PROACTIVE_COMPANION
        }

        val probabilities = computeNeedProbabilities(telemetry)
        val topEntry = probabilities.maxByOrNull { it.value }
        val primaryNeed = topEntry?.key ?: PredictedUserNeed.DEEP_WORK_ASSIST
        val confidencePct = ((topEntry?.value ?: 0.5f) * 100).roundToInt().coerceIn(35, 99)

        val directive = buildDynamicSystemDirective(telemetry, zeroUiMode, primaryNeed)

        _state.update { prev ->
            prev.copy(
                telemetry = telemetry,
                zeroUiMode = zeroUiMode,
                primaryNeed = primaryNeed,
                confidencePercent = confidencePct,
                needProbabilities = probabilities,
                dynamicSystemDirective = directive
            )
        }

        val totalPatterns = synchronized(hourlyHabitMatrix) {
            hourlyHabitMatrix.sumOf { it.values.sum() }
        }
        _debugStats.value = QuantumDebugStats(
            currentState = "${motionState.label} • ${lighting.label}",
            ZeroUIMode = zeroUiMode.title,
            stressScore = stressScore / 100f,
            fatigueScore = fatigueScore / 100f,
            cognitiveLoad = (100 - focusScore) / 100f,
            predictedNeed = "${primaryNeed.displayTitle} ($confidencePct%)",
            cachedAnswers = if (_state.value.precomputedResponse.isNotBlank()) 1 else 0,
            learnedPatterns = totalPatterns
        )

        val current = _state.value
        if (confidencePct >= 60 &&
            current.lastPrecomputedFor != primaryNeed &&
            !current.isPrecomputing &&
            zeroUiMode != ZeroUiMode.GHOST_SILENT
        ) {
            // Folosim motorul local (zero cost API și latență instantanee) pentru pre-calcularea din bucla automată de fundal
            precomputeResponseInBackground(primaryNeed, directive, AiEngine.LOCAL_LLM)
        }
    }

    // ── 3. Modelul Bayesian pentru Predictia Nevoii ───────────────
    private fun computeNeedProbabilities(t: BiometricTelemetry): Map<PredictedUserNeed, Float> {
        val rawScores = mutableMapOf<PredictedUserNeed, Float>()

        rawScores[PredictedUserNeed.MORNING_BRIEFING] = when (t.hourOfDay) {
            in 6..10 -> 0.85f
            in 11..12 -> 0.30f
            else -> 0.05f
        }

        rawScores[PredictedUserNeed.DEEP_WORK_ASSIST] =
            (if (t.hourOfDay in 9..18) 0.55f else 0.20f) +
                (if (t.focusScore > 65) 0.35f else 0f) +
                (if (t.motionState == PhysicalMotionState.STILL_DESK || t.motionState == PhysicalMotionState.IN_HAND_STABLE) 0.20f else 0f)

        rawScores[PredictedUserNeed.STRESS_DECOMPRESSION] =
            (t.stressScore / 100f) * 0.95f + (if (t.backspaceErrorRate > 0.15f) 0.30f else 0f)

        rawScores[PredictedUserNeed.COMMUTE_SUMMARY] =
            if (t.motionState == PhysicalMotionState.WALKING || t.motionState == PhysicalMotionState.RUNNING_OR_VEHICLE) 0.95f else 0.08f

        rawScores[PredictedUserNeed.EVENING_REFLECTION] =
            (if (t.hourOfDay in 20..23 || t.hourOfDay in 0..3) 0.80f else 0.05f) +
                (if (t.lightingCondition == LightingCondition.PITCH_DARK || t.lightingCondition == LightingCondition.DIM_INDOOR) 0.25f else 0f)

        rawScores[PredictedUserNeed.CREATIVE_SPARK] =
            if (t.focusScore > 70 && t.stressScore < 35) 0.60f else 0.25f

        synchronized(hourlyHabitMatrix) {
            val habitCounts = hourlyHabitMatrix[t.hourOfDay]
            val totalHabitsAtHour = habitCounts.values.sum().coerceAtLeast(1)
            habitCounts.forEach { (need, count) ->
                val bonus = (count.toFloat() / totalHabitsAtHour) * 0.45f
                rawScores[need] = (rawScores[need] ?: 0.1f) + bonus
            }
        }

        val sum = rawScores.values.sum().coerceAtLeast(0.01f)
        return rawScores.mapValues { (_, v) -> (v / sum).coerceIn(0.01f, 0.99f) }
    }

    // ── 4. Pre-Generare Asincrona in Fundal (Zero Latency) ────────
    fun precomputeResponseInBackground(
        need: PredictedUserNeed = _state.value.primaryNeed,
        directive: String = _state.value.dynamicSystemDirective,
        aiEngine: AiEngine = AiEngine.MISTRAL
    ) {
        precomputeJob?.cancel()
        precomputeJob = scope.launch(Dispatchers.IO) {
            _state.update { it.copy(isPrecomputing = true) }

            val sysPrompt = """
                Ești EVA-1, motorul cognitiv anticipativ (Quantum Context Engine).
                Pregătești în avans un răspuns proactiv, direct și valoros pentru utilizator,
                înainte ca acesta să apese vreun buton.
                
                $directive
                
                Răspunde în limba română, concis (maxim 120 de cuvinte), cald și direct aplicabil.
            """.trimIndent()

            val msgs = listOf(Message(role = MessageRole.USER, content = need.defaultPrompt))

            val result = when (aiEngine) {
                AiEngine.MISTRAL -> mistral.sendMessage(msgs, sysPrompt, emptyList(), false)
                AiEngine.CLAUDE -> anthropic.sendMessage(msgs, sysPrompt, emptyList(), false, false)
                AiEngine.LOCAL_LLM -> Result.success(
                    com.eva1.companion.engines.OfflineLlmEngine.synthesizeIntelligentReply(
                        prompt = need.defaultPrompt,
                        modelDisplayName = AiEngine.LOCAL_LLM.displayName,
                        includeHeader = false
                    )
                )
            }

            result.onSuccess { text ->
                _state.update {
                    it.copy(
                        precomputedResponse = text.trim(),
                        isPrecomputing = false,
                        lastPrecomputedFor = need
                    )
                }
                _debugStats.update { it.copy(cachedAnswers = 1) }
            }.onFailure {
                _state.update { it.copy(isPrecomputing = false) }
            }
        }
    }

    // ── 5. Directiva de Sistem Dinamica ───────────────────────────
    private fun buildDynamicSystemDirective(
        t: BiometricTelemetry,
        mode: ZeroUiMode,
        need: PredictedUserNeed
    ): String = """
        [TELEMETRIE QUANTUM ÎN TIMP REAL]
        - Mișcare fizică: ${t.motionState.label}
        - Iluminare ambientală: ${t.lightingCondition.label} (${t.luxLevel.toInt()} lux)
        - Mediu acustic: ${t.ambientEnvironment.label} (${t.ambientDecibels.toInt()} dB)
        - Scor Stres Biometric: ${t.stressScore}/100 | Oboseală: ${t.fatigueScore}/100 | Focus: ${t.focusScore}/100
        - Mod Interfață Zero-UI: ${mode.title} — ${mode.description}
        - Intenție anticipată: ${need.displayTitle}
        
        [INSTRUCȚIUNI ADAPTIVE OBLIGATORII PENTRU EVA-1]
        ${when (mode) {
            ZeroUiMode.GHOST_SILENT -> "Fii ultra-concis (maxim 2 propoziții)."
            ZeroUiMode.FOCUS_SHIELD -> "Răspunde structurat, cu bullet points scurte, direct la subiect, fără introduceri lungi."
            ZeroUiMode.EMPATHIC_CALM -> "Folosește un ton foarte cald, empatic, încurajator și relaxant. Evită presiunea."
            ZeroUiMode.HANDS_FREE_VOICE -> "Formulează răspunsul natural pentru a fi ascultat audio în mers, în cel mult 3-4 propoziții clare."
            ZeroUiMode.PROACTIVE_COMPANION -> "Oferă un răspuns complet, proactiv și sugerează inteligent următorul pas."
        }}
    """.trimIndent()

    private fun computeAccelerometerVariance(): Float {
        val snapshot = synchronized(accelHistory) {
            if (accelHistory.size < 4) return 0.05f
            accelHistory.toFloatArray()
        }
        val mean = snapshot.average().toFloat()
        var sumSq = 0f
        for (v in snapshot) {
            val d = v - mean
            sumSq += d * d
        }
        return sumSq / snapshot.size
    }

    private fun computeTypingWpm(): Float {
        val (size, spanMs) = synchronized(keystrokeTimestamps) {
            if (keystrokeTimestamps.size < 5) return 40f
            val span = (keystrokeTimestamps.last() - keystrokeTimestamps.first()).coerceAtLeast(1000L)
            keystrokeTimestamps.size to span
        }
        val charsPerMinute = (size * 60_000f) / spanMs
        return (charsPerMinute / 5f).coerceIn(10f, 130f)
    }

    private fun classifyPromptToNeed(prompt: String): PredictedUserNeed {
        val p = prompt.lowercase()
        return when {
            p.contains("plan") || p.contains("diminea") || p.contains("azi") -> PredictedUserNeed.MORNING_BRIEFING
            p.contains("stres") || p.contains("obosit") || p.contains("calm") -> PredictedUserNeed.STRESS_DECOMPRESSION
            p.contains("cod") || p.contains("lucr") || p.contains("proiect") -> PredictedUserNeed.DEEP_WORK_ASSIST
            p.contains("sear") || p.contains("somn") || p.contains("noapte") -> PredictedUserNeed.EVENING_REFLECTION
            p.contains("idee") || p.contains("creativ") || p.contains("invent") -> PredictedUserNeed.CREATIVE_SPARK
            else -> PredictedUserNeed.DEEP_WORK_ASSIST
        }
    }
}
