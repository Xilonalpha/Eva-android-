package com.eva1.companion.engines

/** Direct in-process JNI inference; no HTTP server or localhost. */
internal object EvaNativeGguf {
    private val nextRequestId = java.util.concurrent.atomic.AtomicLong(0)
    fun newRequestId(): Long = nextRequestId.incrementAndGet()
    val available: Boolean = try {
        System.loadLibrary("eva_gguf")
        true
    } catch (_: UnsatisfiedLinkError) { false }

    /** Request cancellation from another thread while native inference is running. */
    external fun cancel(requestId: Long)

    /** Free the cached GGUF model when memory is needed elsewhere. */
    external fun release()

    external fun generate(requestId: Long, modelPath: String, prompt: String, maxTokens: Int, temperature: Float): String
}
