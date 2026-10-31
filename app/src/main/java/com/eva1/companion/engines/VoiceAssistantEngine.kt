package com.eva1.companion.engines

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.eva1.companion.VoiceAssistantState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

// ══════════════════════════════════════════════════════════════════════════════
// EVA-1 HANDS-FREE SIRI-STYLE VOICE ASSISTANT ENGINE ("HEI Siri")
//
// Arhitectură Single-Engine Anti-Suprapunere:
//   - Un singur SpeechRecognizer activ la nivel de aplicație (fără sesiuni duble).
//   - Mutex strict între Microfon (SpeechRecognizer) și Voce (EvaTtsEngine):
//     cât timp EVA vorbește sau procesează o comandă, microfonul este complet oprit
//     și orice runnable de repornire din Handler este anulat.
//   - Deduplicare comenzi (Debounce 1.6s) pentru a preveni dubla execuție pe
//     dispozitivele Android care emit `onResults` de două ori.
// ══════════════════════════════════════════════════════════════════════════════

class VoiceAssistantEngine(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onCommandRecognized: (String) -> Unit
) {
    companion object {
        private val WAKE_PREFIXES = listOf(
            "hey siri",
            "hei siri",
            "ok siri",
            "okay siri",
            "salut siri",
            "bună siri",
            "buna siri",
            "alo siri"
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private val sherpaWakeWordDetector = SherpaKwsWakeWordDetector(
        context = context.applicationContext,
        onDetected = { mainHandler.post { onSherpaWakeWordDetected() } },
        onUnavailable = { reason ->
            mainHandler.post {
                if (isEnabled && requireWakeWord && !isPausedForTtsSpeaking) {
                    _statusNote.value = "$reason Se folosește temporar recunoașterea Android ca rezervă."
                    startListeningLoop(forceRestart = true)
                }
            }
        }
    )

    private val _state = MutableStateFlow(VoiceAssistantState.OFF)
    val state: StateFlow<VoiceAssistantState> = _state.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _statusNote = MutableStateFlow("")
    val statusNote: StateFlow<String> = _statusNote.asStateFlow()

    @Volatile
    var isEnabled: Boolean = false
        private set

    @Volatile
    var requireWakeWord: Boolean = true

    @Volatile
    private var isAwaitingDirectCommandAfterWake: Boolean = false

    @Volatile
    private var isPausedForTtsSpeaking: Boolean = false

    @Volatile
    private var isListeningSessionActive: Boolean = false

    private var consecutiveErrors: Int = 0
    private var lastRecognizedNormalized: String = ""
    private var lastRecognizedTimestampMs: Long = 0L

    // ── Pornește / Oprește modul Hands-Free "Hei Siri" ──────────────
    fun setEnabled(enabled: Boolean, waitForWelcomeTts: Boolean = false) {
        mainHandler.removeCallbacksAndMessages(null)
        isEnabled = enabled
        if (enabled) {
            consecutiveErrors = 0
            isAwaitingDirectCommandAfterWake = !requireWakeWord
            if (waitForWelcomeTts) {
                isPausedForTtsSpeaking = true
                stopInternalRecognizer()
                _state.value = VoiceAssistantState.SPEAKING
                _statusNote.value = "🔊 EVA-1 confirmă activarea Hands-Free..."
            } else {
                isPausedForTtsSpeaking = false
                if (requireWakeWord) {
                    startSherpaWakeWordListening()
                } else {
                    startListeningLoop()
                }
            }
        } else {
            isAwaitingDirectCommandAfterWake = false
            isPausedForTtsSpeaking = false
            stopInternalRecognizer()
            sherpaWakeWordDetector.stop()
            _state.value = VoiceAssistantState.OFF
            _partialTranscript.value = ""
            _rmsLevel.value = 0f
            _statusNote.value = "Asistent vocal Hands-Free oprit."
        }
    }

    // ── Forțează intrarea directă în modul de ascultare comandă ────
    fun triggerImmediateCommandListening() {
        mainHandler.removeCallbacksAndMessages(null)
        if (!isEnabled) {
            isEnabled = true
        }
        isPausedForTtsSpeaking = false
        isAwaitingDirectCommandAfterWake = true
        consecutiveErrors = 0
        sherpaWakeWordDetector.stop()
        _state.value = VoiceAssistantState.LISTENING_COMMAND
        startListeningLoop(forceRestart = true)
    }

    // ── Suspendă microfonul cât timp EVA vorbește (evită auto-ecoul și suprapunerea) ──
    fun onEvaSpeakingStarted() {
        isPausedForTtsSpeaking = true
        _state.value = VoiceAssistantState.SPEAKING
        _rmsLevel.value = 0f
        _statusNote.value = "🔊 EVA-1 îți răspunde vocal (microfon în pauză anti-ecou)..."
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            stopInternalRecognizer()
            sherpaWakeWordDetector.stop()
        }
    }

    // ── Reia ascultarea automat DOAR după ce EVA a terminat complet de vorbit ──
    fun onEvaSpeakingFinished() {
        isPausedForTtsSpeaking = false
        mainHandler.removeCallbacksAndMessages(null)
        if (isEnabled) {
            mainHandler.postDelayed({
                if (isEnabled && !isPausedForTtsSpeaking && _state.value != VoiceAssistantState.PROCESSING) {
                    _state.value = if (isAwaitingDirectCommandAfterWake || !requireWakeWord) {
                        VoiceAssistantState.LISTENING_COMMAND
                    } else {
                        VoiceAssistantState.LISTENING_WAKE_WORD
                    }
                    if (requireWakeWord && !isAwaitingDirectCommandAfterWake) {
                        startSherpaWakeWordListening()
                    } else {
                        startListeningLoop()
                    }
                }
            }, 450L)
        } else {
            _state.value = VoiceAssistantState.OFF
        }
    }

    fun markProcessing() {
        mainHandler.removeCallbacksAndMessages(null)
        _state.value = VoiceAssistantState.PROCESSING
        _rmsLevel.value = 0f
        mainHandler.post {
            stopInternalRecognizer()
            sherpaWakeWordDetector.stop()
        }
    }

    // ── Procesează orice frază rostită (de la microfon sau simulator) ──
    fun handleRecognizedPhrase(rawPhrase: String, forceDirectCommand: Boolean = false) {
        val cleaned = rawPhrase.trim()
        if (cleaned.isEmpty()) return

        // Deduplicare: prevenim dubla execuție dacă SpeechRecognizer trimite același rezultat de 2 ori
        val normalized = cleaned.lowercase(Locale.forLanguageTag("ro-RO")).replace(Regex("\\s+"), " ")
        val now = SystemClock.elapsedRealtime()
        if (!forceDirectCommand && normalized == lastRecognizedNormalized && (now - lastRecognizedTimestampMs) < 1600L) {
            return
        }
        lastRecognizedNormalized = normalized
        lastRecognizedTimestampMs = now

        _partialTranscript.value = cleaned

        // Dacă nu cerem wake-word sau suntem deja după "Hei Siri" sau e comandă directă
        if (forceDirectCommand || !requireWakeWord || isAwaitingDirectCommandAfterWake) {
            val commandOnly = stripWakeWordIfPresent(cleaned).ifBlank { cleaned }
            isAwaitingDirectCommandAfterWake = !requireWakeWord
            isPausedForTtsSpeaking = true
            _state.value = VoiceAssistantState.PROCESSING
            mainHandler.removeCallbacksAndMessages(null)
            stopInternalRecognizer()
            sherpaWakeWordDetector.stop()
            onCommandRecognized(commandOnly)
            return
        }

        // Verificăm dacă fraza conține "Hei Siri" / "Siri"
        val lower = cleaned.lowercase(Locale.forLanguageTag("ro-RO"))
        val matchedPrefix = WAKE_PREFIXES.firstOrNull { prefix ->
            lower == prefix ||
                lower.startsWith("$prefix ") ||
                lower.startsWith("$prefix,") ||
                lower.contains(" $prefix ")
        }

        if (matchedPrefix != null) {
            val afterWake = extractCommandAfterWakeWord(cleaned, matchedPrefix)
            if (afterWake.length >= 2) {
                // Utilizatorul a spus și comanda în aceeași propoziție: "Hei Siri, deschide YouTube"
                isAwaitingDirectCommandAfterWake = false
                isPausedForTtsSpeaking = true
                _state.value = VoiceAssistantState.PROCESSING
                mainHandler.removeCallbacksAndMessages(null)
                stopInternalRecognizer()
                onCommandRecognized(afterWake)
            } else {
                // Utilizatorul a strigat doar "Hei Siri!" -> oprim microfonul cât timp EVA spune "Da, te ascult!"
                isAwaitingDirectCommandAfterWake = true
                isPausedForTtsSpeaking = true
                _state.value = VoiceAssistantState.SPEAKING
                mainHandler.removeCallbacksAndMessages(null)
                stopInternalRecognizer()
                onWakeWordDetected()
            }
        } else {
            // Nu s-a rostit numele "Siri" -> continuăm ascultarea în fundal
            _statusNote.value = "Auzit: \"$cleaned\" (Spune 'Hei Siri' înainte de comandă)"
        }
    }

    private fun stripWakeWordIfPresent(text: String): String {
        val lower = text.lowercase(Locale.forLanguageTag("ro-RO"))
        for (prefix in WAKE_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return text.drop(prefix.length).trimStart(' ', ',', '.', '!', '?', ':')
            }
        }
        return text
    }

    private fun extractCommandAfterWakeWord(text: String, matchedPrefix: String): String {
        val lower = text.lowercase(Locale.forLanguageTag("ro-RO"))
        val idx = lower.indexOf(matchedPrefix)
        if (idx < 0) return ""
        return text.substring(idx + matchedPrefix.length)
            .trimStart(' ', ',', '.', '!', '?', ':')
            .trim()
    }

    /** Starts the native Sherpa-ONNX KWS engine for the wake phrase only. */
    private fun startSherpaWakeWordListening() {
        mainHandler.post {
            if (!isEnabled || isPausedForTtsSpeaking || _state.value == VoiceAssistantState.PROCESSING) return@post
            stopInternalRecognizer()
            if (sherpaWakeWordDetector.start()) {
                isAwaitingDirectCommandAfterWake = false
                _state.value = VoiceAssistantState.LISTENING_WAKE_WORD
                _statusNote.value = "Sherpa-ONNX KWS activ: detectare locală HEY SIRI (fără internet, 16 kHz)."
            } else {
                _statusNote.value = "Sherpa KWS indisponibil; se folosește SpeechRecognizer ca rezervă."
                startListeningLoop(forceRestart = true)
            }
        }
    }

    private fun onSherpaWakeWordDetected() {
        if (!isEnabled || isPausedForTtsSpeaking) return
        sherpaWakeWordDetector.stop()
        isAwaitingDirectCommandAfterWake = true
        isPausedForTtsSpeaking = true
        _state.value = VoiceAssistantState.SPEAKING
        _partialTranscript.value = "Hey Siri"
        _statusNote.value = "Cuvânt de activare detectat local de Sherpa-ONNX."
        onWakeWordDetected()
    }

    private fun startListeningLoop(forceRestart: Boolean = false) {
        mainHandler.post {
            if (!isEnabled || isPausedForTtsSpeaking || _state.value == VoiceAssistantState.PROCESSING) {
                return@post
            }

            if (isListeningSessionActive && !forceRestart) {
                return@post
            }

            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasMicPermission) {
                disableListeningAfterFatalError("Acordă permisiunea RECORD_AUDIO pentru a activa microfonul.")
                return@post
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                disableListeningAfterFatalError("Recunoașterea vocală Android nu este disponibilă pe acest dispozitiv.")
                return@post
            }

            try {
                if (forceRestart && speechRecognizer != null) {
                    runCatching { speechRecognizer?.cancel() }
                    isListeningSessionActive = false
                }

                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                        setRecognitionListener(recognitionListener)
                    }
                }

                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ro-RO")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // Best-effort offline recognition; availability depends on the installed Android speech service.
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                }

                // startListening este asincron: onReadyForSpeech confirmă activarea reală.
                _statusNote.value = "Se inițializează recunoașterea vocală Android..."
                speechRecognizer?.startListening(recognizerIntent)
                isListeningSessionActive = true
            } catch (e: Exception) {
                isListeningSessionActive = false
                disableListeningAfterFatalError("Pornirea microfonului a eșuat: ${e.localizedMessage ?: "eroare necunoscută"}")
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            consecutiveErrors = 0
            isListeningSessionActive = true
            _state.value = if (isAwaitingDirectCommandAfterWake || !requireWakeWord) {
                VoiceAssistantState.LISTENING_COMMAND
            } else {
                VoiceAssistantState.LISTENING_WAKE_WORD
            }
            _statusNote.value = "🎙️ Microfon activ: recunoașterea vocală Android este pregătită."
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            if (!isPausedForTtsSpeaking) {
                _rmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _rmsLevel.value = 0f
            isListeningSessionActive = false
        }

        override fun onError(error: Int) {
            _rmsLevel.value = 0f
            isListeningSessionActive = false
            consecutiveErrors++
            // Erorile permanente nu se repară prin reporniri repetate.
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                error == SpeechRecognizer.ERROR_CLIENT &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                mainHandler.removeCallbacksAndMessages(null)
                disableListeningAfterFatalError("Microfon indisponibil: verifică permisiunea RECORD_AUDIO.")
                return
            }

            // Nu reporni la infinit când serviciul Android raportează o eroare permanentă.
            if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                consecutiveErrors >= 6) {
                disableListeningAfterFatalError("Recunoașterea vocală s-a oprit (cod Android $error). Verifică serviciul vocal și reactivează microfonul.")
                return
            }

            // Dacă eroarea este ERROR_RECOGNIZER_BUSY, distrugem instanța blocată înainte de următorul ciclu
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || consecutiveErrors >= 3) {
                stopInternalRecognizer()
            }

            val delayMs = if (consecutiveErrors > 4) 2600L else 650L
            mainHandler.removeCallbacksAndMessages(null)
            if (isEnabled && !isPausedForTtsSpeaking && _state.value != VoiceAssistantState.PROCESSING) {
                mainHandler.postDelayed({
                    if (isEnabled && !isPausedForTtsSpeaking && _state.value != VoiceAssistantState.PROCESSING) {
                        startListeningLoop()
                    }
                }, delayMs)
            }
        }

        override fun onResults(results: Bundle?) {
            _rmsLevel.value = 0f
            isListeningSessionActive = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()?.trim() ?: ""

            if (best.isNotEmpty() && !isPausedForTtsSpeaking) {
                handleRecognizedPhrase(best, forceDirectCommand = false)
            }

            // Repornim bucla de ascultare DOAR dacă nu am intrat în procesare sau răspuns vocal TTS
            if (isEnabled && !isPausedForTtsSpeaking && _state.value != VoiceAssistantState.PROCESSING && _state.value != VoiceAssistantState.SPEAKING) {
                mainHandler.removeCallbacksAndMessages(null)
                mainHandler.postDelayed({
                    if (isEnabled && !isPausedForTtsSpeaking && _state.value != VoiceAssistantState.PROCESSING && _state.value != VoiceAssistantState.SPEAKING) {
                        startListeningLoop()
                    }
                }, 400L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (isPausedForTtsSpeaking) return
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                ?: return
            if (partial.isNotEmpty()) {
                _partialTranscript.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** O eroare fatală oprește și modul logic, nu doar instanța SpeechRecognizer. */
    private fun disableListeningAfterFatalError(reason: String) {
        isEnabled = false
        isPausedForTtsSpeaking = false
        isAwaitingDirectCommandAfterWake = false
        mainHandler.removeCallbacksAndMessages(null)
        stopInternalRecognizer()
        sherpaWakeWordDetector.stop()
        _rmsLevel.value = 0f
        _state.value = VoiceAssistantState.OFF
        _statusNote.value = reason
    }

    private fun stopInternalRecognizer() {
        isListeningSessionActive = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun release() {
        isEnabled = false
        isPausedForTtsSpeaking = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            stopInternalRecognizer()
            sherpaWakeWordDetector.release()
        }
    }
}
