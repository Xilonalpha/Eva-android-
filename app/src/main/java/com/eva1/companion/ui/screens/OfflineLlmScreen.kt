package com.eva1.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eva1.companion.AiEngine
import com.eva1.companion.EvaIntent
import com.eva1.companion.EvaTab
import com.eva1.companion.EvaUiState
import com.eva1.companion.engines.GgufOnDeviceEngine
import com.eva1.companion.engines.LiteRtLocalLlmEngine
import com.eva1.companion.engines.OfflineLlmEngine
import com.eva1.companion.ui.theme.*
import java.util.Locale

@Composable
fun OfflineLlmScreen(
    state: EvaUiState,
    engine: OfflineLlmEngine,
    onIntent: (EvaIntent) -> Unit
) {
    val isLoaded by engine.isLoaded.collectAsState()
    val progress by engine.loadProgress.collectAsState()
    val currentModel by engine.currentModel.collectAsState()
    val tps by engine.tokensPerSecond.collectAsState()
    val liteRtStatus by engine.liteRtEngine.status.collectAsState()
    val liteRtProgress by engine.liteRtEngine.downloadProgress.collectAsState()
    val liteRtDownloading by engine.liteRtEngine.isDownloading.collectAsState()
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    val isLocalActiveInChat = state.selectedEngine == AiEngine.LOCAL_LLM

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Memory, contentDescription = null, tint = EvaGreen, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Motor AI local (LiteRT-LM + GGUF)", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Inferență locală reală; descarcă un model compatibil înainte de utilizare",
                    color = EvaTextSecondary,
                    fontSize = 11.sp
                )
            }
            if (tps > 0f) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EvaGreen.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("${"%.1f".format(Locale.US, tps)} tok/s", color = EvaGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // ── Card de Sinergie cu Chat-ul Principal ──────────────────
        Surface(
            color = if (isLocalActiveInChat) EvaGreen.copy(alpha = 0.14f) else EvaSurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isLocalActiveInChat) EvaGreen else EvaBorder,
                    RoundedCornerShape(12.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isLocalActiveInChat) Icons.Default.Verified else Icons.Default.Sync,
                    contentDescription = null,
                    tint = if (isLocalActiveInChat) EvaGreen else EvaCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLocalActiveInChat) "Activ în Chat: Local LLM (LiteRT-LM / GGUF)" else "Motor curent în Chat: ${state.selectedEngine.badge}",
                        color = EvaTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (isLocalActiveInChat)
                            "Chat-ul folosește modelul local selectat dacă runtime-ul și modelul sunt încărcate."
                        else
                            "Selectează și încarcă un model local compatibil pentru a-l folosi în Chat.",
                        color = EvaTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onIntent(EvaIntent.LoadOfflineModel(currentModel))
                        onIntent(EvaIntent.SwitchTab(EvaTab.CHAT))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLocalActiveInChat) EvaGreen else EvaBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("use_local_llm_in_chat_button")
                ) {
                    Text(
                        text = if (isLocalActiveInChat) "Mergi la Chat" else "Folosește în Chat",
                        color = if (isLocalActiveInChat) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Quick Test Conversație Naturală ────────────────────────
        Text(
            "Testează conversația naturală offline (1-Click):",
            color = EvaTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val quickPrompts = listOf(
                "Ce faci?",
                "Salut EVA, cum ești azi?",
                "Mă plictisesc, ce îmi recomanzi?",
                "Spune-mi o glumă bună",
                "Ce părere ai despre inteligența artificială locală?"
            )
            quickPrompts.forEach { qp ->
                AssistChip(
                    onClick = { onIntent(EvaIntent.AskOfflineModel(qp)) },
                    label = { Text(qp, fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = EvaGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }

        // ── Offline Chat Box (Sincronizat cu Chat-ul Principal) ────
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Scrie orice în mod natural (ex: Ce faci? / Cum îți merge? / Explică-mi...)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("offline_prompt_input"),
            maxLines = 4
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (prompt.isNotBlank() && !state.isOfflineGenerating) {
                        val q = prompt
                        prompt = ""
                        onIntent(EvaIntent.AskOfflineModel(q))
                    }
                },
                enabled = prompt.isNotBlank() && !state.isOfflineGenerating,
                modifier = Modifier
                    .weight(1f)
                    .testTag("run_offline_inference_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EvaPurple),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isOfflineGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Procesare locală...")
                } else {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rulează modelul local și sincronizează în Chat")
                }
            }
        }

        if (state.offlineLastReply.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(EvaSurface)
                    .border(1.dp, EvaGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        state.offlineLastReply,
                        color = EvaTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onIntent(EvaIntent.SwitchTab(EvaTab.CHAT)) }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = EvaCyan, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Vezi conversația în Chat", color = EvaCyan, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // ── Parametri Inferență Locală (Temperature & Top-P) ───────
        Surface(
            color = EvaSurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, EvaBorder, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    state.ggufStatusMessage,
                    color = EvaCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (state.isDownloadingGguf) {
                    LinearProgressIndicator(
                        progress = { (state.ggufDownloadProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = EvaGreen
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Temperatură: ${"%.2f".format(Locale.US, state.ggufTemperature)}",
                            fontSize = 11.sp,
                            color = EvaTextSecondary
                        )
                        Slider(
                            value = state.ggufTemperature,
                            onValueChange = { newTemp ->
                                onIntent(
                                    EvaIntent.UpdateGgufParams(
                                        temperature = newTemp,
                                        topP = state.ggufTopP,
                                        maxTokens = state.ggufMaxTokens
                                    )
                                )
                            },
                            valueRange = 0.1f..1.2f
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Top-P Sampling: ${"%.2f".format(Locale.US, state.ggufTopP)}",
                            fontSize = 11.sp,
                            color = EvaTextSecondary
                        )
                        Slider(
                            value = state.ggufTopP,
                            onValueChange = { newTopP ->
                                onIntent(
                                    EvaIntent.UpdateGgufParams(
                                        temperature = state.ggufTemperature,
                                        topP = newTopP,
                                        maxTokens = state.ggufMaxTokens
                                    )
                                )
                            },
                            valueRange = 0.5f..1.0f
                        )
                    }
                }
            }
        }

        // ── Modele LiteRT-LM reale, distincte de fișierele GGUF ──
        Text(
            "Modele noi LiteRT-LM (inferență nativă CPU/GPU):",
            color = EvaTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(liteRtStatus, color = EvaCyan, fontSize = 11.sp)
        if (liteRtDownloading) {
            LinearProgressIndicator(progress = { liteRtProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        LiteRtLocalModelsSection(
            currentModel = currentModel,
            onSelect = { id ->
                onIntent(EvaIntent.SelectOfflineModel(id))
            },
            onDownload = { id ->
                scope.launch {
                    val result = engine.downloadLiteRtModel(id)
                    if (result.isSuccess) {
                        onIntent(EvaIntent.SelectOfflineModel(id))
                        onIntent(EvaIntent.LoadOfflineModel(id))
                    }
                }
            },
            onLoad = { id ->
                onIntent(EvaIntent.SelectOfflineModel(id))
                onIntent(EvaIntent.LoadOfflineModel(id))
            }
        )

        // ── LISTA UNICĂ: Cele 11 Modele Locale EVA-1 (Fără Limitări) ──
        Text(
            "Modelele GGUF existente (runtime llama.cpp separat):",
            color = EvaTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        val downloadedIds = remember(state.ggufDownloadProgress, state.isDownloadingGguf, currentModel) {
            engine.ggufEngine.getDownloadedGgufModels()
        }
        OfflineLlmEngine.AVAILABLE_MODELS.forEach { m ->
            val selected = m.id == currentModel
            val isInstalled = downloadedIds.contains(m.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) EvaBlue.copy(alpha = 0.18f) else EvaSurface)
                    .border(1.dp, if (selected) EvaBlue else EvaBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        onIntent(EvaIntent.SelectOfflineModel(m.id))
                    }
                    .padding(10.dp)
                    .testTag("offline_model_${m.id}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onIntent(EvaIntent.SelectOfflineModel(m.id)) }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(m.sizeGb, color = EvaCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(m.description, color = EvaTextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(
                    onClick = { onIntent(EvaIntent.DownloadGgufModel(m.id)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("provision_gguf_${m.id}")
                ) {
                    Icon(
                        imageVector = if (isInstalled) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (isInstalled) EvaGreen else EvaCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isInstalled) "Activ" else "Descarcă",
                        fontSize = 10.sp,
                        color = if (isInstalled) EvaGreen else EvaCyan
                    )
                }
            }
        }

        // ── Load Model Button + Progress ───────────────────────────
        Button(
            onClick = { onIntent(EvaIntent.LoadOfflineModel(currentModel)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("load_offline_model_button"),
            colors = ButtonDefaults.buttonColors(containerColor = if (isLoaded) EvaGreen else EvaBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(if (isLoaded) Icons.Default.CheckCircle else Icons.Default.Download, contentDescription = null, tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                if (isLoaded) "Model Activ în Memorie & Chat (Reîncarcă)" else "Încarcă Modelul & Activează în Chat",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }

        if (progress.isNotBlank()) {
            Text(
                progress,
                color = EvaCyan,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}


@Composable
private fun LiteRtLocalModelsSection(
    currentModel: String,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onLoad: (String) -> Unit
) {
    LiteRtLocalLlmEngine.MODELS.forEach { model ->
        val selected = currentModel == model.id
        Surface(
            color = if (selected) EvaBlue.copy(alpha = 0.18f) else EvaSurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, if (selected) EvaBlue else EvaBorder, RoundedCornerShape(12.dp))
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = { onSelect(model.id) })
                    Column(Modifier.weight(1f)) {
                        Text(model.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(model.description, color = EvaTextSecondary, fontSize = 11.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onDownload(model.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Descarcă modelul", fontSize = 11.sp)
                    }
                    Button(onClick = { onLoad(model.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Încarcă", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
