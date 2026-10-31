package com.eva1.companion.mesh

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

// ══════════════════════════════════════════════════════════════════════════════
// FAZA 3 — ALGORITMUL DE LOAD BALANCING SI TASK SCHEDULING DINAMIC
// ══════════════════════════════════════════════════════════════════════════════

@Serializable
data class LayerAssignment(
    val nodeId: String,
    val deviceName: String,
    val ipAddress: String,
    val port: Int,
    val startLayer: Int,
    val endLayer: Int,
    val normalizedWeight: Double,
    val estimatedVramMb: Long
) {
    val layerCount: Int get() = (endLayer - startLayer + 1).coerceAtLeast(0)
}

@Serializable
data class DistributedExecutionPlan(
    val sessionId: String,
    val modelName: String,
    val totalLayers: Int,
    val hiddenDim: Int,
    val stages: List<LayerAssignment>,
    val createdAt: Long = System.currentTimeMillis()
)

class SwarmLoadBalancer {

    fun createExecutionPlan(
        sessionId: String,
        modelName: String,
        totalLayers: Int,
        hiddenDim: Int,
        ramPerLayerMb: Long,
        allNodes: List<PeerCapability>
    ): DistributedExecutionPlan {
        if (allNodes.isEmpty()) {
            return DistributedExecutionPlan(
                sessionId = sessionId,
                modelName = modelName,
                totalLayers = totalLayers,
                hiddenDim = hiddenDim,
                stages = listOf(
                    LayerAssignment(
                        nodeId = "local-fallback",
                        deviceName = "Local Node",
                        ipAddress = "127.0.0.1",
                        port = 0,
                        startLayer = 0,
                        endLayer = (totalLayers - 1).coerceAtLeast(0),
                        normalizedWeight = 1.0,
                        estimatedVramMb = totalLayers * ramPerLayerMb
                    )
                )
            )
        }

        val eligibleNodes = allNodes.filter { node ->
            node.status != NodeStatus.OFFLINE &&
                node.status != NodeStatus.OVERHEATING &&
                node.availableRamMb >= ramPerLayerMb * 2
        }.sortedByDescending { it.computeScore() }

        val activeNodes = if (eligibleNodes.isEmpty()) {
            listOf(allNodes.first())
        } else {
            eligibleNodes
        }

        val scores = activeNodes.map { it.computeScore().coerceAtLeast(0.05) }
        val totalScore = scores.sum().coerceAtLeast(0.01)
        val weights = scores.map { it / totalScore }

        val rawLayerCounts = weights.mapIndexed { idx, w ->
            val maxByRam = (activeNodes[idx].availableRamMb / ramPerLayerMb.coerceAtLeast(1L)).toInt()
            val ideal = (w * totalLayers).roundToInt().coerceAtLeast(1)
            ideal.coerceAtMost(max(1, maxByRam))
        }.toMutableList()

        var currentSum = rawLayerCounts.sum()
        while (currentSum != totalLayers) {
            if (currentSum < totalLayers) {
                rawLayerCounts[0] += 1
                currentSum += 1
            } else {
                val idxToReduce = rawLayerCounts.indexOfLast { it > 1 }
                    .takeIf { it >= 0 }
                    ?: rawLayerCounts.indexOfLast { it > 0 }.takeIf { it >= 0 }
                    ?: break
                rawLayerCounts[idxToReduce] -= 1
                currentSum -= 1
            }
        }

        val stages = mutableListOf<LayerAssignment>()
        var cursor = 0
        activeNodes.forEachIndexed { idx, node ->
            val count = rawLayerCounts[idx]
            if (count > 0 && cursor < totalLayers) {
                val start = cursor
                val end = (cursor + count - 1).coerceAtMost(totalLayers - 1)
                stages.add(
                    LayerAssignment(
                        nodeId = node.nodeId,
                        deviceName = node.deviceName,
                        ipAddress = node.ipAddress,
                        port = node.port,
                        startLayer = start,
                        endLayer = end,
                        normalizedWeight = weights[idx],
                        estimatedVramMb = count * ramPerLayerMb
                    )
                )
                cursor = end + 1
            }
        }

        return DistributedExecutionPlan(
            sessionId = sessionId,
            modelName = modelName,
            totalLayers = totalLayers,
            hiddenDim = hiddenDim,
            stages = stages
        )
    }

    fun rebalanceAfterFailure(
        failedNodeId: String,
        currentPlan: DistributedExecutionPlan,
        remainingNodes: List<PeerCapability>,
        ramPerLayerMb: Long = 80L
    ): DistributedExecutionPlan {
        val healthy = remainingNodes.filterNot { it.nodeId == failedNodeId }
        return createExecutionPlan(
            sessionId = currentPlan.sessionId,
            modelName = currentPlan.modelName,
            totalLayers = currentPlan.totalLayers,
            hiddenDim = currentPlan.hiddenDim,
            ramPerLayerMb = ramPerLayerMb,
            allNodes = healthy
        )
    }
}
