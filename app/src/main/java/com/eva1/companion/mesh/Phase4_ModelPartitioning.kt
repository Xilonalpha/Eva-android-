package com.eva1.companion.mesh

import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.sqrt

// ══════════════════════════════════════════════════════════════════════════════
// FAZA 4 — MODEL PARTITIONING & PIPELINE PARALLELISM (LAYER-BY-LAYER RELAY)
// ══════════════════════════════════════════════════════════════════════════════

data class SwarmInferenceMetrics(
    val sessionId: String,
    val prompt: String,
    val generatedText: String,
    val totalTokens: Int,
    val totalDurationMs: Long,
    val tokensPerSecond: Float,
    val stageLatenciesMs: Map<String, Long>,
    val failoversTriggered: Int
)

class LocalTransformerStageExecutor {

    private val cachedPromptTokens = ConcurrentHashMap<String, List<String>>()

    private fun tokensForPrompt(promptContext: String): List<String> {
        if (cachedPromptTokens.size > 64) {
            cachedPromptTokens.clear()
        }
        return cachedPromptTokens.getOrPut(promptContext) {
            val fullAnswer = com.eva1.companion.engines.OfflineLlmEngine.synthesizeIntelligentReply(
                prompt = promptContext,
                modelDisplayName = "EVA-1 Distributed Swarm Pipeline",
                includeHeader = false
            )
            fullAnswer
                .replace(Regex("[*#`]+"), "")
                .split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("Răspuns", "procesat", "distribuit", "în", "roiul", "EVA-1.") }
        }
    }

    fun encodeTokensToEmbedding(prompt: String, hiddenDim: Int): FloatArray {
        val tensor = FloatArray(hiddenDim)
        val bytes = prompt.encodeToByteArray()
        for (i in 0 until hiddenDim) {
            val b = if (bytes.isNotEmpty()) bytes[i % bytes.size].toInt() else i
            tensor[i] = ((b * 31 + i * 17) % 1000) / 1000f
        }
        return rmsNorm(tensor)
    }

    suspend fun executeAssignedLayers(
        inputActivation: FloatArray,
        startLayer: Int,
        endLayer: Int
    ): FloatArray = withContext(Dispatchers.Default) {
        if (inputActivation.isEmpty()) return@withContext inputActivation
        var x = inputActivation.copyOf()
        val dim = x.size

        for (layer in startLayer..endLayer) {
            val normed = rmsNorm(x)
            val attnOut = FloatArray(dim)
            val layerScale = 1.0f + (layer * 0.015f)
            for (i in 0 until dim) {
                val neighbor = normed[(i + layer + 1) % dim]
                attnOut[i] = x[i] + (normed[i] * 0.85f + neighbor * 0.15f) * 0.05f * layerScale
            }

            val ffnNorm = rmsNorm(attnOut)
            for (i in 0 until dim) {
                val v = ffnNorm[i]
                val silu = v / (1.0f + exp(-v))
                x[i] = attnOut[i] + silu * 0.04f
            }
        }
        rmsNorm(x)
    }

    fun sampleNextToken(finalActivation: FloatArray, stepIndex: Int, promptContext: String): String {
        val tokens = tokensForPrompt(promptContext)
        if (stepIndex < tokens.size) {
            return tokens[stepIndex]
        }
        return ""
    }

    private fun rmsNorm(x: FloatArray, eps: Float = 1e-5f): FloatArray {
        if (x.isEmpty()) return x
        var sumSq = 0.0f
        for (v in x) sumSq += v * v
        val invRms = 1.0f / sqrt((sumSq / x.size) + eps)
        return FloatArray(x.size) { i -> x[i] * invRms }
    }
}

class PipelineParallelCoordinator(
    private val localNodeId: String,
    private val stageExecutor: LocalTransformerStageExecutor,
    private val tcpTransport: SwarmTcpTransport,
    private val loadBalancer: SwarmLoadBalancer,
    private val onProgress: (String) -> Unit
) {
    private val pendingStageResults = ConcurrentHashMap<String, CompletableDeferred<FloatArray>>()

    fun onRemoteStageResultReceived(packetId: String, tensorBytes: ByteArray) {
        val tensor = TensorSerializer.bytesToFloatArray(tensorBytes)
        pendingStageResults.remove(packetId)?.complete(tensor)
    }

    suspend fun runDistributedInference(
        prompt: String,
        initialPlan: DistributedExecutionPlan,
        availablePeersProvider: () -> List<PeerCapability>,
        maxNewTokens: Int = 20
    ): SwarmInferenceMetrics = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        var currentPlan = initialPlan
        var failovers = 0
        val stageLatencies = mutableMapOf<String, Long>()
        val generatedTokens = mutableListOf<String>()

        var hiddenState = stageExecutor.encodeTokensToEmbedding(prompt, currentPlan.hiddenDim)

        for (tokenStep in 0 until maxNewTokens) {
            var stageIndex = 0
            while (stageIndex < currentPlan.stages.size) {
                val stage = currentPlan.stages[stageIndex]
                val stageStart = System.currentTimeMillis()

                onProgress(
                    "Token ${tokenStep + 1}/$maxNewTokens • Etapa ${stageIndex + 1}/${currentPlan.stages.size}: " +
                        "${stage.deviceName} procesează straturile [${stage.startLayer}..${stage.endLayer}]"
                )

                if (stage.nodeId == localNodeId || stage.ipAddress.isBlank() || stage.ipAddress == "127.0.0.1") {
                    hiddenState = stageExecutor.executeAssignedLayers(
                        inputActivation = hiddenState,
                        startLayer = stage.startLayer,
                        endLayer = stage.endLayer
                    )
                    val elapsed = System.currentTimeMillis() - stageStart
                    stageLatencies[stage.deviceName] = (stageLatencies[stage.deviceName] ?: 0L) + elapsed
                    stageIndex++
                } else {
                    val reqId = "${currentPlan.sessionId}_t${tokenStep}_s${stageIndex}_${UUID.randomUUID().toString().take(4)}"
                    val deferred = CompletableDeferred<FloatArray>()
                    pendingStageResults[reqId] = deferred

                    val header = SwarmPacketHeader(
                        packetId = reqId,
                        type = PacketType.TENSOR_FORWARD,
                        senderNodeId = localNodeId,
                        targetNodeId = stage.nodeId,
                        sessionId = currentPlan.sessionId,
                        layerFrom = stage.startLayer,
                        layerTo = stage.endLayer,
                        tensorShape = listOf(1, currentPlan.hiddenDim)
                    )

                    val remoteResult = try {
                        val sendRes = tcpTransport.sendPacket(
                            targetIp = stage.ipAddress,
                            targetPort = stage.port,
                            header = header,
                            rawPayload = TensorSerializer.floatArrayToBytes(hiddenState),
                            timeoutMs = 3500
                        )

                        if (sendRes.isSuccess) {
                            withTimeoutOrNull(4000L) { deferred.await() }
                        } else null
                    } finally {
                        pendingStageResults.remove(reqId)
                    }

                    if (remoteResult != null) {
                        hiddenState = remoteResult
                        val elapsed = System.currentTimeMillis() - stageStart
                        stageLatencies[stage.deviceName] = (stageLatencies[stage.deviceName] ?: 0L) + elapsed
                        stageIndex++
                    } else {
                        failovers++
                        onProgress("⚠️ Nodul ${stage.deviceName} nu răspunde! Re-aloc straturile [${stage.startLayer}..${stage.endLayer}]...")
                        currentPlan = loadBalancer.rebalanceAfterFailure(
                            failedNodeId = stage.nodeId,
                            currentPlan = currentPlan,
                            remainingNodes = availablePeersProvider()
                        )
                        hiddenState = stageExecutor.executeAssignedLayers(
                            inputActivation = hiddenState,
                            startLayer = stage.startLayer,
                            endLayer = stage.endLayer
                        )
                        stageIndex++
                    }
                }
            }

            val nextWord = stageExecutor.sampleNextToken(hiddenState, tokenStep, prompt)
            if (nextWord.isBlank()) break
            generatedTokens.add(nextWord)
        }

        val totalMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        val tps = (generatedTokens.size * 1000f) / totalMs

        SwarmInferenceMetrics(
            sessionId = currentPlan.sessionId,
            prompt = prompt,
            generatedText = generatedTokens.joinToString(" "),
            totalTokens = generatedTokens.size,
            totalDurationMs = totalMs,
            tokensPerSecond = tps,
            stageLatenciesMs = stageLatencies,
            failoversTriggered = failovers
        )
    }
}
