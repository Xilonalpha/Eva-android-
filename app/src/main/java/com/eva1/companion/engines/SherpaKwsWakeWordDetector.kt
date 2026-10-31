package com.eva1.companion.engines

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getKeywordsFile
import com.k2fsa.sherpa.onnx.getKwsModelConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Native, on-device Sherpa-ONNX keyword spotter.
 *
 * Audio is captured as 16 kHz / mono / PCM16 and processed in 100 ms chunks.
 * No network calls are made. The detector is deliberately stopped while EVA
 * listens to a full command or speaks, so AudioRecord and SpeechRecognizer/TTS
 * never compete for the microphone.
 *
 * The selected upstream English GigaSpeech KWS model accepts the phrase
 * "HEY SIRI" as a custom stream keyword. Accent/noise performance must still
 * be validated on the target handset before describing it as production-grade.
 */
class SherpaKwsWakeWordDetector(
    private val context: Context,
    private val onDetected: () -> Unit,
    private val onUnavailable: (String) -> Unit
) {
    companion object {
        private const val TAG = "EvaSherpaKWS"
        private const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = 1_600 // 100 ms
        private const val MODEL_TYPE_GIGASPEECH_ENGLISH = 1
        private const val WAKE_PHRASE = "HEY SIRI"
        private const val MIN_RETRIGGER_GAP_MS = 2_000L
    }

    private val running = AtomicBoolean(false)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var spotter: KeywordSpotter? = null

    fun isRunning(): Boolean = running.get()

    /** Returns false only when permission/assets are unavailable immediately. */
    fun start(): Boolean {
        if (running.get()) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onUnavailable("Permisiunea microfonului lipsește.")
            return false
        }
        if (!hasPackagedModel()) {
            onUnavailable("Modelul Sherpa KWS lipsește din assets. Rulează tools/prepare_sherpa_kws.sh.")
            return false
        }
        if (!running.compareAndSet(false, true)) return true

        worker = thread(name = "EVA-Sherpa-KWS", start = true) {
            runDetector()
        }
        return true
    }

    private fun hasPackagedModel(): Boolean = try {
        val modelDir = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
        context.assets.open("$modelDir/tokens.txt").use { }
        context.assets.open("$modelDir/encoder-epoch-12-avg-2-chunk-16-left-64.onnx").use { }
        context.assets.open("$modelDir/decoder-epoch-12-avg-2-chunk-16-left-64.onnx").use { }
        context.assets.open("$modelDir/joiner-epoch-12-avg-2-chunk-16-left-64.onnx").use { }
        true
    } catch (_: Exception) {
        false
    }

    private fun runDetector() {
        var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
        var detected = false
        try {
            val modelConfig = requireNotNull(getKwsModelConfig(MODEL_TYPE_GIGASPEECH_ENGLISH)) {
                "Configurația modelului KWS English nu este disponibilă"
            }.apply {
                numThreads = 1
                provider = "cpu"
            }
            val config = KeywordSpotterConfig(
                featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig,
                keywordsFile = getKeywordsFile(MODEL_TYPE_GIGASPEECH_ENGLISH),
                maxActivePaths = 4,
                keywordsScore = 1.5f,
                keywordsThreshold = 0.28f,
                numTrailingBlanks = 2
            )
            val localSpotter = KeywordSpotter(assetManager = context.assets, config = config)
            spotter = localSpotter
            stream = localSpotter.createStream(WAKE_PHRASE)
            check(stream.ptr != 0L) { "Sherpa nu a putut crea fluxul pentru HEY SIRI" }

            val minBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBytes > 0) { "AudioRecord a returnat o dimensiune de buffer invalidă: $minBytes" }

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBytes * 2, CHUNK_SAMPLES * 2 * 4)
            )
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "Android nu a putut inițializa AudioRecord"
            }
            audioRecord = recorder
            recorder.startRecording()
            Log.i(TAG, "Sherpa KWS started: 16 kHz mono, CPU, one inference thread")

            val shortBuffer = ShortArray(CHUNK_SAMPLES)
            var lastTriggerAt = 0L
            while (running.get()) {
                val count = recorder.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) continue
                val samples = FloatArray(count) { index -> shortBuffer[index] / 32768.0f }
                stream.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
                while (localSpotter.isReady(stream)) {
                    localSpotter.decode(stream)
                    val phrase = localSpotter.getResult(stream).keyword
                    if (phrase.isNotBlank()) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastTriggerAt >= MIN_RETRIGGER_GAP_MS) {
                            lastTriggerAt = now
                            detected = true
                            Log.i(TAG, "Wake phrase detected by Sherpa KWS: $phrase")
                            running.set(false)
                            break
                        }
                        localSpotter.reset(stream)
                    }
                }
                if (detected) break
            }
        } catch (t: Throwable) {
            if (running.get()) {
                Log.e(TAG, "Sherpa KWS unavailable", t)
                onUnavailable(t.message ?: "Eroare la inițializarea Sherpa KWS")
            }
        } finally {
            running.set(false)
            runCatching { stream?.release() }
            runCatching { audioRecord?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop(); it.release() } }
            audioRecord = null
            runCatching { spotter?.release() }
            spotter = null
            if (detected) onDetected()
        }
    }

    fun stop() {
        running.set(false)
        runCatching {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            }
        }
        // AudioRecord.stop() unblocks read(); native objects are released by worker finally.
    }

    fun release() {
        stop()
        worker = null
    }
}
