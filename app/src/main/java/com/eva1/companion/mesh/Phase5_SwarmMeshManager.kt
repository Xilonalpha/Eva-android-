package com.eva1.companion.mesh

import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// ══════════════════════════════════════════════════════════════════════════════
// FAZA 5 — ORCHESTRATORUL SUPREM SWARM MESH (INTEGRARE FAZE 1 -> 4 + BLE)
// ══════════════════════════════════════════════════════════════════════════════

class SwarmMeshManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val profiler = HardwareCapabilityProfiler(context)
    val discoveryService = WifiDirectDiscoveryService(context, profiler)
    val cryptoManager = SwarmCryptoManager()
    val loadBalancer = SwarmLoadBalancer()
    val stageExecutor = LocalTransformerStageExecutor()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _localCapability = MutableStateFlow(profiler.snapshotLocalCapability())
    val localCapability: StateFlow<PeerCapability> = _localCapability.asStateFlow()

    private val _currentPlan = MutableStateFlow<DistributedExecutionPlan?>(null)
    val currentPlan: StateFlow<DistributedExecutionPlan?> = _currentPlan.asStateFlow()

    private val _pipelineStatus = MutableStateFlow("Roi inactiv — apasă 'Pornește Roiul' pentru a uni telefoanele.")
    val pipelineStatus: StateFlow<String> = _pipelineStatus.asStateFlow()

    private val _lastInferenceMetrics = MutableStateFlow<SwarmInferenceMetrics?>(null)
    val lastInferenceMetrics: StateFlow<SwarmInferenceMetrics?> = _lastInferenceMetrics.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    lateinit var tcpTransport: SwarmTcpTransport
        private set

    lateinit var pipelineCoordinator: PipelineParallelCoordinator
        private set

    private var heartbeatJob: Job? = null

    init {
        tcpTransport = SwarmTcpTransport(
            localPort = 8988,
            crypto = cryptoManager,
            onPacketReceived = ::handleIncomingSwarmPacket,
            onLog = ::appendLog
        )

        pipelineCoordinator = PipelineParallelCoordinator(
            localNodeId = profiler.localNodeId,
            stageExecutor = stageExecutor,
            tcpTransport = tcpTransport,
            loadBalancer = loadBalancer,
            onProgress = { status ->
                _pipelineStatus.value = status
                appendLog(status)
            }
        )
    }

    fun startSwarm() {
        if (_isRunning.value) return
        _isRunning.value = true

        discoveryService.initialize()
        tcpTransport.startServer()
        discoveryService.broadcastLocalCapabilities()
        discoveryService.startDiscovery(::appendLog)

        heartbeatJob = scope.launch {
            while (isActive) {
                val localSnap = profiler.snapshotLocalCapability()
                _localCapability.value = localSnap
                discoveryService.broadcastLocalCapabilities()
                discoveryService.removeStalePeers(45_000L)
                recomputeExecutionPlan()
                delay(5000L)
            }
        }

        appendLog("Roiul EVA-1 a pornit. Nod local: ${_localCapability.value.deviceName} (Scor=${"%.2f".format(_localCapability.value.computeScore())})")
        recomputeExecutionPlan()
    }

    fun stopSwarm() {
        heartbeatJob?.cancel()
        tcpTransport.stopServer()
        _isRunning.value = false
        _pipelineStatus.value = "Roi oprit."
        appendLog("Roiul EVA-1 a fost oprit.")
    }

    // ── Simulare nod virtual pentru testare pe un singur dispozitiv ──
    fun addSimulatedPeerNode() {
        val virtualIdx = discoveryService.discoveredPeers.value.size + 1
        val names = listOf("Pixel 9 Pro XL", "Galaxy S25 Ultra", "OnePlus 13", "Xiaomi 15 Ultra")
        val chosenName = names[(virtualIdx - 1) % names.size]
        val virtualCap = PeerCapability(
            nodeId = "vnode-$virtualIdx-${UUID.randomUUID().toString().take(4)}",
            deviceName = "$chosenName (Nod Swarm #$virtualIdx)",
            ipAddress = "127.0.0.1",
            port = 8988,
            availableRamMb = Random.nextLong(4096L, 12288L),
            totalRamMb = 16384L,
            cpuCores = 8,
            cpuMaxFreqMhz = 3200L,
            batteryPercent = Random.nextInt(65, 99),
            isCharging = virtualIdx % 2 == 0,
            thermalStatus = 0,
            hasVulkanGpu = true,
            status = NodeStatus.IDLE,
            rttLatencyMs = Random.nextLong(4L, 14L)
        )
        discoveryService.updatePeerFromNetwork(virtualCap)
        appendLog("Nod Swarm adăugat în cluster: ${virtualCap.deviceName} (${virtualCap.availableRamMb}MB RAM liber, scor=${"%.2f".format(virtualCap.computeScore())})")
        recomputeExecutionPlan()
    }

    fun getAllClusterNodes(): List<PeerCapability> {
        val remote = discoveryService.discoveredPeers.value.values.toList()
        return listOf(_localCapability.value) + remote.filterNot { it.nodeId == profiler.localNodeId }
    }

    fun recomputeExecutionPlan(
        modelName: String = "Llama-3.2-3B-Swarm",
        totalLayers: Int = 32,
        hiddenDim: Int = 512,
        ramPerLayerMb: Long = 64L
    ): DistributedExecutionPlan {
        val nodes = getAllClusterNodes()
        val plan = loadBalancer.createExecutionPlan(
            sessionId = UUID.randomUUID().toString().take(8),
            modelName = modelName,
            totalLayers = totalLayers,
            hiddenDim = hiddenDim,
            ramPerLayerMb = ramPerLayerMb,
            allNodes = nodes
        )
        _currentPlan.value = plan
        return plan
    }

    fun executeDistributedPrompt(
        prompt: String,
        maxTokens: Int = 18,
        onComplete: (SwarmInferenceMetrics) -> Unit = {}
    ) {
        scope.launch {
            val plan = _currentPlan.value ?: recomputeExecutionPlan()
            appendLog("🚀 Pornesc inferența distribuită pe ${plan.stages.size} nod(uri) pentru: \"$prompt\"")

            val metrics = pipelineCoordinator.runDistributedInference(
                prompt = prompt,
                initialPlan = plan,
                availablePeersProvider = ::getAllClusterNodes,
                maxNewTokens = maxTokens
            )

            _lastInferenceMetrics.value = metrics
            _pipelineStatus.value = "Inferență completă în ${metrics.totalDurationMs}ms (${"%.1f".format(metrics.tokensPerSecond)} tok/s)"
            appendLog("✅ Rezultat Swarm (${metrics.totalDurationMs}ms): ${metrics.generatedText}")
            onComplete(metrics)
        }
    }

    private suspend fun handleIncomingSwarmPacket(header: SwarmPacketHeader, payload: ByteArray) {
        when (header.type) {
            PacketType.HANDSHAKE_PUBKEY -> {
                appendLog("🔑 Cheie publică ECDH primită de la nodul ${header.senderNodeId}")
            }

            PacketType.CAPABILITY_HEARTBEAT -> {
                val cap = runCatching { discoveryService.decodeCapabilityJson(payload.decodeToString()) }.getOrNull() ?: return
                discoveryService.updatePeerFromNetwork(cap)
                recomputeExecutionPlan()
            }

            PacketType.TENSOR_FORWARD -> {
                appendLog("⚡ [Worker] Procesez straturile ${header.layerFrom}..${header.layerTo} pentru sesiunea ${header.sessionId}")
                val inputTensor = TensorSerializer.bytesToFloatArray(payload)
                val outputTensor = stageExecutor.executeAssignedLayers(
                    inputActivation = inputTensor,
                    startLayer = header.layerFrom,
                    endLayer = header.layerTo
                )

                val peerIp = discoveryService.discoveredPeers.value[header.senderNodeId]?.ipAddress
                    ?.takeIf { it.isNotBlank() } ?: return
                val peerPort = discoveryService.discoveredPeers.value[header.senderNodeId]?.port ?: 8988

                val replyHeader = SwarmPacketHeader(
                    packetId = header.packetId,
                    type = PacketType.TENSOR_RESULT,
                    senderNodeId = profiler.localNodeId,
                    targetNodeId = header.senderNodeId,
                    sessionId = header.sessionId,
                    layerFrom = header.layerFrom,
                    layerTo = header.layerTo,
                    tensorShape = header.tensorShape
                )

                tcpTransport.sendPacket(
                    targetIp = peerIp,
                    targetPort = peerPort,
                    header = replyHeader,
                    rawPayload = TensorSerializer.floatArrayToBytes(outputTensor)
                )
            }

            PacketType.TENSOR_RESULT -> {
                pipelineCoordinator.onRemoteStageResultReceived(header.packetId, payload)
            }

            PacketType.PIPELINE_ABORT -> {
                appendLog("⚠️ Sesiunea ${header.sessionId} a fost anulată de nodul ${header.senderNodeId}")
            }

            PacketType.CHAT_MESSAGE -> {
                appendLog("💬 [${header.senderNodeId}]: ${payload.decodeToString()}")
            }
        }
    }

    private fun appendLog(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.update { (it + "[$ts] $msg").takeLast(80) }
    }
}
