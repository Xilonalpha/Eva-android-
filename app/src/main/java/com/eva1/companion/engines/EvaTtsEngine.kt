package com.eva1.companion.engines

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

// ══════════════════════════════════════════════════════════════════════════════
// EVA-1 NATIVE ANDROID TEXT-TO-SPEECH (TTS) ENGINE — SINGLE-VOICE SYNCHRONIZED
//
// Capabilități:
//   - Sinteză vocală nativă Android (TextToSpeech) cu suport pentru limba română
//     (ro-RO) și fallback automat pe limba sistemului / engleză.
//   - Protecție strictă împotriva suprapunerii vocilor (Single-Voice Mutex):
//     orice apel nou `speak()` oprește instantaneu lotul anterior și ignoră
//     callback-urile `onDone`/`onError`/`onStop` ale mesajelor anulate, astfel
//     încât microfonul Hands-Free nu pornește niciodată în timp ce EVA vorbește.
//   - Control în timp real pentru Viteză (Speech Rate), Tonalitate (Pitch) și
//     sincronizare cu microfonul Hands-Free (Echo Prevention).
// ══════════════════════════════════════════════════════════════════════════════

class EvaTtsEngine(
    private val context: Context,
    private val onSpeechStarted: (String) -> Unit = {},
    private val onSpeechFinished: (String) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var tts: TextToSpeech? = null
    private var activeEngineLabel: String = "Android TTS"
    private var fallbackWatchdogJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private data class PendingSpeech(val text: String, val utteranceTag: String)
    private val pendingQueue = ArrayDeque<PendingSpeech>()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _statusLabel = MutableStateFlow("Inițializare motor vocal Android TTS...")
    val statusLabel: StateFlow<String> = _statusLabel.asStateFlow()

    @Volatile
    private var activeBatchPrefix: String = ""

    @Volatile
    private var activeLastUtteranceId: String = ""

    @Volatile
    private var activeUtteranceTag: String = ""

    init {
        initializeTts()
    }

    private fun initializeTts() {
        // Sherpa-ONNX Piper este oferit ca motor Android TTS separat de proiectul upstream.
        // Dacă utilizatorul îl instalează, EVA îl selectează automat; altfel folosește motorul
        // TTS Android deja instalat. Nu pretindem că biblioteca nativă Sherpa este inclusă în APK.
        val sherpaPackage = "com.k2fsa.sherpa.onnx.tts.engine"
        val sherpaInstalled = runCatching {
            context.packageManager.queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                0
            ).any { it.serviceInfo?.packageName == sherpaPackage }
        }.getOrDefault(false)

        if (sherpaInstalled && createTtsInstance(sherpaPackage, "Sherpa-ONNX Piper (offline)")) return
        createTtsInstance(null, "Android TextToSpeech")
    }

    private fun createTtsInstance(enginePackage: String?, label: String): Boolean {
        return try {
            activeEngineLabel = label
            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    configureEngine()
                } else if (enginePackage != null) {
                    // Dacă serviciul Sherpa este instalat, dar modelul/vocea nu este configurată,
                    // revenim transparent la motorul de sistem.
                    runCatching { tts?.shutdown() }
                    tts = null
                    if (!createTtsInstance(null, "Android TextToSpeech (fallback)")) {
                        _isReady.value = false
                        _statusLabel.value = "Niciun motor TTS disponibil (status=$status)"
                    }
                } else {
                    _isReady.value = false
                    _statusLabel.value = "Serviciul Android TTS indisponibil (status=$status)"
                }
            }
            tts = if (enginePackage == null) {
                TextToSpeech(context.applicationContext, listener)
            } else {
                TextToSpeech(context.applicationContext, listener, enginePackage)
            }
            true
        } catch (e: Exception) {
            if (enginePackage != null) {
                tts = null
                false
            } else {
                _isReady.value = false
                _statusLabel.value = "Eroare inițializare TTS: ${e.localizedMessage ?: "necunoscută"}"
                false
            }
        }
    }

    /** Deschide selectorul Android unde utilizatorul poate selecta motorul Sherpa-ONNX instalat. */
    fun openSystemTtsSettings(): Boolean = runCatching {
        val intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun configureEngine() {
        val engine = tts ?: return

        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(attrs)
        } catch (_: Exception) {}

        val roLocale = Locale.forLanguageTag("ro-RO")
        val roResult = engine.setLanguage(roLocale)
        val chosenLocaleLabel = if (roResult != TextToSpeech.LANG_MISSING_DATA && roResult != TextToSpeech.LANG_NOT_SUPPORTED) {
            // Preferăm o voce românească instalată local; evităm vocile care cer rețea când există alternativă offline.
            val localRomanianVoice = engine.voices
                ?.filter { it.locale.language.equals("ro", ignoreCase = true) && !it.isNetworkConnectionRequired }
                ?.maxWithOrNull(compareBy<android.speech.tts.Voice> { it.quality }.thenBy { -it.latency })
            if (localRomanianVoice != null) {
                engine.voice = localRomanianVoice
                "Română (${localRomanianVoice.name}, offline)"
            } else {
                val anyRomanianVoice = engine.voices
                    ?.firstOrNull { it.locale.language.equals("ro", ignoreCase = true) }
                if (anyRomanianVoice != null) engine.voice = anyRomanianVoice
                "Română (ro-RO; vocea disponibilă în sistem)"
            }
        } else {
            val defLocale = Locale.getDefault()
            val defResult = engine.setLanguage(defLocale)
            if (defResult != TextToSpeech.LANG_MISSING_DATA && defResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                "${defLocale.displayLanguage} (${defLocale.toLanguageTag()})"
            } else {
                engine.setLanguage(Locale.US)
                "English (en-US Fallback)"
            }
        }

        engine.setSpeechRate(_speechRate.value)
        engine.setPitch(_pitch.value)

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val id = utteranceId ?: return
                val currentPrefix = activeBatchPrefix
                if (currentPrefix.isNotEmpty() && id.startsWith(currentPrefix)) {
                    if (!_isSpeaking.value) {
                        _isSpeaking.value = true
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: return
                if (id == activeLastUtteranceId) {
                    completeCurrentSpeech(activeUtteranceTag.ifEmpty { id })
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                // Dacă redarea a fost întreruptă de un nou apel speak(QUEUE_FLUSH), NU declanșăm
                // onSpeechFinished pentru vechiul mesaj ca să nu pornim microfonul peste noua voce!
                val id = utteranceId ?: return
                if (!interrupted && id == activeLastUtteranceId) {
                    completeCurrentSpeech(activeUtteranceTag.ifEmpty { id })
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val id = utteranceId ?: return
                if (id == activeLastUtteranceId) {
                    completeCurrentSpeech(activeUtteranceTag.ifEmpty { id })
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                val id = utteranceId ?: return
                if (id == activeLastUtteranceId) {
                    completeCurrentSpeech(activeUtteranceTag.ifEmpty { id })
                }
            }
        })

        _isReady.value = true
        _statusLabel.value = "Activ • $activeEngineLabel • Voce Unic-Sincronizată ($chosenLocaleLabel)"

        // Redăm doar ultimul mesaj aflat în așteptare (evităm redarea în cascadă a mesajelor vechi)
        val lastPending = synchronized(pendingQueue) {
            val last = pendingQueue.lastOrNull()
            pendingQueue.clear()
            last
        }
        if (lastPending != null) {
            speak(lastPending.text, lastPending.utteranceTag)
        }
    }

    private fun completeCurrentSpeech(finishedTag: String) {
        fallbackWatchdogJob?.cancel()
        abandonAudioFocus()
        _isSpeaking.value = false
        onSpeechFinished(finishedTag)
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = clamped
        runCatching { tts?.setSpeechRate(clamped) }
    }

    fun setPitch(newPitch: Float) {
        val clamped = newPitch.coerceIn(0.5f, 1.6f)
        _pitch.value = clamped
        runCatching { tts?.setPitch(clamped) }
    }

    /**
     * Rostește textul specificat prin motorul nativ Android TextToSpeech.
     * Orice redare anterioară este oprită instantaneu fără a elibera microfonul prematur.
     */
    fun speak(rawText: String, utteranceTag: String = "msg_tts"): Boolean {
        val cleaned = sanitizeForSpeech(rawText)
        if (cleaned.isBlank()) return false

        // Anulăm watchdog-ul anterior și păstrăm doar ultimul mesaj în coadă dacă motorul se inițializează
        fallbackWatchdogJob?.cancel()

        if (!_isReady.value || tts == null) {
            synchronized(pendingQueue) {
                pendingQueue.clear()
                pendingQueue.addLast(PendingSpeech(cleaned, utteranceTag))
            }
            if (tts == null) {
                initializeTts()
            }
            return false
        }

        val engine = tts ?: return false

        // 1. Notificăm IMEDIAT VoiceAssistantEngine că EVA vorbește, ca microfonul să fie oprit
        val batchToken = UUID.randomUUID().toString().take(8)
        val batchPrefix = "${utteranceTag}_${batchToken}_"
        activeBatchPrefix = batchPrefix
        activeUtteranceTag = utteranceTag
        _isSpeaking.value = true
        onSpeechStarted(utteranceTag)

        // 2. Oprim orice redare TTS anterioară înainte de a trimite noul calup
        runCatching { engine.stop() }
        requestAudioFocus()

        engine.setSpeechRate(_speechRate.value)
        engine.setPitch(_pitch.value)

        val chunks = if (utteranceTag == "wake_ack") {
            listOf(cleaned)
        } else {
            splitIntoChunks(cleaned, maxChunkLen = 280)
        }
        if (chunks.isEmpty()) {
            completeCurrentSpeech(utteranceTag)
            return false
        }

        val lastId = "${batchPrefix}${chunks.lastIndex}"
        activeLastUtteranceId = lastId

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        chunks.forEachIndexed { index, sentence ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val chunkId = "${batchPrefix}$index"
            engine.speak(sentence, queueMode, params, chunkId)
        }

        scheduleWatchdog(cleaned.length, lastId, utteranceTag)
        return true
    }

    fun stop() {
        fallbackWatchdogJob?.cancel()
        synchronized(pendingQueue) {
            pendingQueue.clear()
        }
        val previousTag = activeUtteranceTag
        activeBatchPrefix = ""
        activeLastUtteranceId = ""
        activeUtteranceTag = ""
        runCatching { tts?.stop() }
        abandonAudioFocus()
        if (_isSpeaking.value) {
            _isSpeaking.value = false
            onSpeechFinished(previousTag)
        }
    }

    private fun scheduleWatchdog(totalChars: Int, expectedLastId: String, expectedTag: String) {
        fallbackWatchdogJob?.cancel()
        fallbackWatchdogJob = scope.launch {
            val rateFactor = (1.0f / _speechRate.value.coerceAtLeast(0.5f))
            val estimatedMs = ((totalChars * 75L * rateFactor).toLong() + 1200L).coerceIn(2500L, 32_000L)
            delay(estimatedMs)
            if (_isSpeaking.value && activeLastUtteranceId == expectedLastId) {
                completeCurrentSpeech(expectedTag)
            }
        }
    }

    fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("<thinking>[\\s\\S]*?</thinking>"), " ")
            .replace(Regex("```[\\s\\S]*?```"), " Am generat codul complet pe ecran. ")
            .replace(Regex("https?://\\S+"), " link web ")
            .replace(Regex("[*_#`~>|•]"), " ")
            .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(1200)
    }

    private fun splitIntoChunks(text: String, maxChunkLen: Int): List<String> {
        if (text.length <= maxChunkLen) return listOf(text)
        val rawSentences = text.split(Regex("(?<=[.!?\\n])\\s+"))
        val result = mutableListOf<String>()
        var current = StringBuilder()

        for (s in rawSentences) {
            val trimmed = s.trim()
            if (trimmed.isEmpty()) continue
            if (current.length + trimmed.length + 1 <= maxChunkLen) {
                if (current.isNotEmpty()) current.append(" ")
                current.append(trimmed)
            } else {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                if (trimmed.length <= maxChunkLen) {
                    current.append(trimmed)
                } else {
                    result.addAll(splitLongSentenceAtWords(trimmed, maxChunkLen))
                }
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun splitLongSentenceAtWords(sentence: String, maxChunkLen: Int): List<String> {
        val words = sentence.split(Regex("\\s+")).filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + word.length + 1 > maxChunkLen) {
                chunks += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    private fun requestAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }
}
