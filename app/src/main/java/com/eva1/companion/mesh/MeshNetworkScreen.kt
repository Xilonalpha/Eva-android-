package com.eva1.companion.mesh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eva1.companion.MeshMessage
import com.eva1.companion.MeshPeer

// ══════════════════════════════════════════════════════════════════════════════
// UI COMPLET PENTRU EVA-1 SWARM MESH COMPUTING + P2P BLE CHAT
// ══════════════════════════════════════════════════════════════════════════════

private val BgDark = Color(0xFF0A0E17)
private val CardDark = Color(0xFF131B2E)
private val AccentCyan = Color(0xFF00E5FF)
private val AccentGreen = Color(0xFF00E676)
private val AccentAmber = Color(0xFFFFAB00)
private val AccentPurple = Color(0xFFB388FF)

@Composable
fun MeshNetworkScreen(
    swarmManager: SwarmMeshManager,
    blePeers: List<MeshPeer> = emptyList(),
    bleMessages: List<MeshMessage> = emptyList(),
    onSendBleMessage: (String) -> Unit = {}
) {
    val isRunning by swarmManager.isRunning.collectAsState()
    val localCap by swarmManager.localCapability.collectAsState()
    val peersMap by swarmManager.discoveryService.discoveredPeers.collectAsState()
    val plan by swarmManager.currentPlan.collectAsState()
    val pipelineStatus by swarmManager.pipelineStatus.collectAsState()
    val lastMetrics by swarmManager.lastInferenceMetrics.collectAsState()
    val logs by swarmManager.logs.collectAsState()

    var promptText by remember { mutableStateOf("Explică cum funcționează inferența AI distribuită pe mai multe telefoane.") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var p2pChatInput by remember { mutableStateOf("") }

    val allNodes = remember(localCap, peersMap) {
        listOf(localCap) + peersMap.values.filterNot { it.nodeId == localCap.nodeId }
    }
    val totalClusterRamMb = allNodes.sumOf { it.availableRamMb }
    val totalClusterCores = allNodes.sumOf { it.cpuCores }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        // ── HEADER: Status Cluster & Buton Start/Stop ─────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EVA-1 SWARM MESH",
                    color = AccentCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${allNodes.size} nod(uri) • ${totalClusterRamMb}MB RAM • $totalClusterCores nuclee CPU",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { swarmManager.addSimulatedPeerNode() },
                    modifier = Modifier.testTag("add_virtual_peer_button"),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "Adaugă Nod", tint = AccentPurple, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+Nod", color = AccentPurple, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (isRunning) swarmManager.stopSwarm() else swarmManager.startSwarm()
                    },
                    modifier = Modifier.testTag("toggle_swarm_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFD50000) else AccentGreen
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Oprește Roiul" else "Pornește Roiul",
                        tint = Color.Black
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isRunning) "Oprește" else "Pornește",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── NAVIGATION TABS ───────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardDark,
            contentColor = AccentCyan,
            edgePadding = 0.dp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            listOf("1. Noduri", "2. Partiționare", "3. Inferență", "4. P2P Chat", "5. Telemetrie").forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    modifier = Modifier.testTag("swarm_tab_$idx"),
                    text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            0 -> NodesClusterTab(allNodes, localCap.nodeId)
            1 -> PartitionPlanTab(plan, onRecompute = { swarmManager.recomputeExecutionPlan() })
            2 -> DistributedInferenceTab(
                prompt = promptText,
                onPromptChange = { promptText = it },
                status = pipelineStatus,
                metrics = lastMetrics,
                onRun = { swarmManager.executeDistributedPrompt(promptText) }
            )
            3 -> BleEncryptedChatTab(
                blePeers = blePeers,
                bleMessages = bleMessages,
                input = p2pChatInput,
                onInputChange = { p2pChatInput = it },
                onSend = {
                    if (p2pChatInput.isNotBlank()) {
                        onSendBleMessage(p2pChatInput)
                        p2pChatInput = ""
                    }
                }
            )
            4 -> LogsTelemetryTab(logs)
        }
    }
}

@Composable
private fun NodesClusterTab(nodes: List<PeerCapability>, localId: String) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(nodes, key = { it.nodeId }) { node ->
            val score = node.computeScore()
            val isLocal = node.nodeId == localId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .border(
                        width = 1.dp,
                        color = if (isLocal) AccentCyan else Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (node.status) {
                                        NodeStatus.IDLE, NodeStatus.COMPUTING -> AccentGreen
                                        NodeStatus.OVERHEATING, NodeStatus.LOW_BATTERY -> AccentAmber
                                        else -> Color.Red
                                    }
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isLocal) "${node.deviceName} (TU / Master)" else node.deviceName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = "Scor: ${"%.2f".format(score)}",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "RAM Liber: ${node.availableRamMb} / ${node.totalRamMb} MB • CPU: ${node.cpuCores} nuclee @ ${node.cpuMaxFreqMhz}MHz",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
                Text(
                    text = "Baterie: ${node.batteryPercent}% ${if (node.isCharging) "(Încărcare)" else ""} • Vulkan GPU: ${if (node.hasVulkanGpu) "DA" else "NU"} • RTT: ${node.rttLatencyMs}ms",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun PartitionPlanTab(plan: DistributedExecutionPlan?, onRecompute: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Model: ${plan?.modelName ?: "N/A"} (${plan?.totalLayers ?: 32} straturi)",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onRecompute, modifier = Modifier.testTag("recompute_plan_button")) {
                Icon(Icons.Default.Refresh, contentDescription = "Recalculează", tint = AccentCyan)
                Spacer(Modifier.width(4.dp))
                Text("Recalculează", color = AccentCyan)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (plan == null) {
            Text("Niciun plan activ. Pornește Roiul.", color = Color.Gray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(plan.stages) { stage ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardDark)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stage.deviceName, color = AccentPurple, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Straturi [${stage.startLayer} .. ${stage.endLayer}] (${stage.layerCount} straturi)",
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { stage.normalizedWeight.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AccentCyan,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Cotă putere: ${(stage.normalizedWeight * 100).toInt()}% • VRAM estimat: ${stage.estimatedVramMb} MB",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributedInferenceTab(
    prompt: String,
    onPromptChange: (String) -> Unit,
    status: String,
    metrics: SwarmInferenceMetrics?,
    onRun: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            label = { Text("Prompt pentru Roiul AI Distribuit", color = AccentCyan) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("swarm_prompt_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
            ),
            maxLines = 3
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onRun,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("run_swarm_inference_button"),
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
        ) {
            Icon(Icons.Default.Memory, contentDescription = "Execută Pipeline", tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Execută prin Pipeline Parallelism pe Roi",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = status,
            color = AccentAmber,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(12.dp))

        metrics?.let { m ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .padding(14.dp)
            ) {
                Text(
                    text = "Răspuns Generat Colectiv:",
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = m.generatedText,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Viteză: ${"%.1f".format(m.tokensPerSecond)} tokeni/sec • Timp total: ${m.totalDurationMs}ms • Failovers: ${m.failoversTriggered}",
                    color = AccentCyan,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun BleEncryptedChatTab(
    blePeers: List<MeshPeer>,
    bleMessages: List<MeshMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Chat P2P Criptat AES-256-GCM (${blePeers.size} noduri BLE în rază)",
            color = AccentGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (bleMessages.isEmpty()) {
                item {
                    Text(
                        "Niciun mesaj P2P încă. Trimite un mesaj criptat către nodurile din apropiere.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
            items(bleMessages, key = { it.id }) { msg ->
                val isSelf = msg.senderId == "SELF"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelf) AccentCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(msg.senderName, color = if (isSelf) AccentCyan else AccentPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("🔒 AES-256-GCM", color = AccentGreen, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(msg.payload, color = Color.White, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("Scrie mesaj P2P fără internet...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ble_chat_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AccentCyan
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AccentCyan)
                    .testTag("ble_chat_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Trimite mesaj P2P", tint = Color.Black)
            }
        }
    }
}

@Composable
private fun LogsTelemetryTab(logs: List<String>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(logs.reversed()) { line ->
            Text(
                text = line,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
