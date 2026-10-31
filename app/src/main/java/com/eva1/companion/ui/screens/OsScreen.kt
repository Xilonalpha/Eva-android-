package com.eva1.companion.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eva1.companion.engines.EvaAccessibilityService
import com.eva1.companion.engines.EvaGameDecisionLogger
import com.eva1.companion.engines.DynamicGestureCommand
import com.eva1.companion.engines.DynamicGestureMotion
import com.eva1.companion.engines.LlmGestureMappingEngine
import com.eva1.companion.engines.ExternalGameScreenType
import com.eva1.companion.engines.ExternalGameUiState
import com.eva1.companion.engines.GameBotMode
import com.eva1.companion.engines.GameDecisionLogEntry
import com.eva1.companion.engines.GameDecisionType
import com.eva1.companion.engines.LearnedUserGesture
import com.eva1.companion.engines.GameBridgeTacticalFrequency
import com.eva1.companion.engines.LocalBridgeStatus
import com.eva1.companion.engines.LocalGameDecisionResult
import com.eva1.companion.engines.LocalLlmGameBridgeEngine
import com.eva1.companion.engines.MediaProjectionVisionEngine
import com.eva1.companion.engines.MultiModalGameVisionEngine
import com.eva1.companion.engines.MultiModalVisionAnalysis
import com.eva1.companion.engines.MultiModalVisionProvider
import com.eva1.companion.engines.DetectedVisionObject
import com.eva1.companion.engines.OfflineLlmEngine
import com.eva1.companion.engines.OsOrchestratorEngine
import com.eva1.companion.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OsScreen(osEngine: OsOrchestratorEngine) {
    val context = LocalContext.current
    var telemetry by remember { mutableStateOf(osEngine.getDeviceTelemetry()) }
    var shellCmd by remember { mutableStateOf("getprop ro.product.model && uname -a") }
    var shellOutput by remember { mutableStateOf("") }
    var appSearch by remember { mutableStateOf("") }
    var otpStatus by remember { mutableStateOf("") }

    // WhatsApp & YouTube Direct Controls
    var waRecipient by remember { mutableStateOf("") }
    var waMessage by remember { mutableStateOf("") }
    var waStatus by remember { mutableStateOf("") }
    var showWhatsAppConfirmation by remember { mutableStateOf(false) }
    var confirmedWaRecipient by remember { mutableStateOf("") }
    var confirmedWaMessage by remember { mutableStateOf("") }
    var ytQuery by remember { mutableStateOf("") }
    var ytStatus by remember { mutableStateOf("") }

    // Game Bot, MediaProjection & Screen Vision State
    val isGameBotRunning by osEngine.isGameBotRunning.collectAsState()
    val gameBotStatus by osEngine.gameBotStatus.collectAsState()
    val gameBotActions by osEngine.gameBotActionsCount.collectAsState()
    val activeGameMode by osEngine.activeGameMode.collectAsState()
    val liveGameState by osEngine.liveGameState.collectAsState()
    val visionReport by osEngine.lastScreenVisionReport.collectAsState()
    val whatsAppSendStatus by EvaAccessibilityService.whatsAppSendStatus.collectAsState()
    val youTubePlaybackStatus by osEngine.youTubePlaybackStatus.collectAsState()
    val isAccessibilityActive by EvaAccessibilityService.isServiceConnected.collectAsState()
    val currentAppUiState by EvaAccessibilityService.currentAppUiState.collectAsState()
    val isFloatingHudVisible by EvaAccessibilityService.isFloatingHudVisible.collectAsState()
    val isMediaProjectionActive by MediaProjectionVisionEngine.isMediaProjectionActive.collectAsState()
    val projectedFramesCount by MediaProjectionVisionEngine.framesCapturedCount.collectAsState()
    val streamFps by MediaProjectionVisionEngine.streamFps.collectAsState()
    val frameLatencyMs by MediaProjectionVisionEngine.lastFrameLatencyMs.collectAsState()
    val annotatedVisionBitmap by MediaProjectionVisionEngine.latestAnnotatedVisionBitmap.collectAsState()
    val latestVisionAnalysis by MediaProjectionVisionEngine.latestVisionAnalysis.collectAsState()
    val latestScreenAnalysis by MediaProjectionVisionEngine.latestScreenAnalysis.collectAsState()
    val activeGameProfile by osEngine.gameCognition.activeProfile.collectAsState()
    val cognitionLog by osEngine.gameCognition.cognitionLog.collectAsState()
    val recentDecisionLogs by EvaGameDecisionLogger.recentLogs.collectAsState()
    val selectedGameLlmModelId by osEngine.selectedGameLlmModelId.collectAsState()
    val learnedGestures by osEngine.learnedGestures.collectAsState()
    val bridgeConfig by osEngine.localLlmBridge.config.collectAsState()
    val bridgeStatus by osEngine.localLlmBridge.status.collectAsState()
    val latestBridgeDecision by osEngine.localLlmBridge.latestDecision.collectAsState()
    val latestDynamicGesture by osEngine.gestureMapper.latestDispatchedGesture.collectAsState()
    val recentDynamicGestures by osEngine.gestureMapper.recentGesturesHistory.collectAsState()
    val mmProvider by osEngine.multiModalVision.selectedProvider.collectAsState()
    val mmAnalysis by osEngine.multiModalVision.latestAnalysis.collectAsState()
    val mmIsAnalyzing by osEngine.multiModalVision.isAnalyzing.collectAsState()
    val mmFramesCount by osEngine.multiModalVision.framesAnalyzedCount.collectAsState()
    val activeDirective by osEngine.dualBrainEngine.activeDirective.collectAsState()
    val lastReflexAction by osEngine.dualBrainEngine.lastReflexAction.collectAsState()
    val isDualBrainActive by osEngine.dualBrainEngine.isDualBrainActive.collectAsState()
    val reflexCount by osEngine.dualBrainEngine.reflexActionsCount.collectAsState()
    val strategicCount by osEngine.dualBrainEngine.strategicDeliberationsCount.collectAsState()
    var selectedDecisionLogFilter by remember { mutableStateOf<GameDecisionType?>(null) }
    var showDiagnosticExplain by remember { mutableStateOf(false) }
    var showGestureRecordDialog by remember { mutableStateOf(false) }
    var newGestureLabel by remember { mutableStateOf("") }
    var newGestureTypeKey by remember { mutableStateOf("FIRE") }
    var newGestureX by remember { mutableStateOf("0.86") }
    var newGestureY by remember { mutableStateOf("0.72") }



    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            osEngine.startMediaProjection(result.resultCode, result.data!!)
        }
    }

    var targetGameInput by remember { mutableStateOf("") }
    var selectedGameMode by remember { mutableStateOf(activeGameMode) }

    val allApps = remember { osEngine.getInstalledApps() }
    val installedGames = remember(allApps) { osEngine.getInstalledGames() }
    val scope = rememberCoroutineScope()

    val filteredApps = remember(allApps, appSearch) {
        if (appSearch.isBlank()) allApps.take(20)
        else allApps.filter {
            it.appName.contains(appSearch, ignoreCase = true) ||
                it.packageName.contains(appSearch, ignoreCase = true)
        }.take(20)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. Header & Hardware Telemetry ─────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = EvaCyan, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("EVA-1 Deep OS Orchestrator", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${telemetry.model} • ${telemetry.androidVersion}",
                        color = EvaTextSecondary,
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = { telemetry = osEngine.getDeviceTelemetry() },
                    modifier = Modifier.testTag("refresh_telemetry_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reîmprospătează telemetria", tint = EvaCyan)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryBox(
                    title = "Baterie",
                    value = "${telemetry.batteryPercent}%",
                    sub = "${telemetry.batteryTempC}°C ${if (telemetry.isCharging) "⚡" else ""}",
                    color = EvaGreen,
                    modifier = Modifier.weight(1f)
                )
                TelemetryBox(
                    title = "Memorie RAM",
                    value = "${telemetry.ramUsedMb} MB",
                    sub = "din ${telemetry.ramTotalMb} MB",
                    color = EvaBlue,
                    modifier = Modifier.weight(1f)
                )
                TelemetryBox(
                    title = "Stocare Liberă",
                    value = "${"%.1f".format(telemetry.storageFreeGb)} GB",
                    sub = "din ${"%.0f".format(telemetry.storageTotalGb)} GB",
                    color = EvaPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── 2. Viziune Ecran în Timp Real (Live Screen Vision) ──────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvaSurface)
                    .border(
                        1.dp,
                        if (isAccessibilityActive) EvaGreen else EvaCyan,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = EvaCyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("👁️ Viziune Ecran în Timp Real (Live Screen Vision)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            if (isAccessibilityActive)
                                "🟢 Serviciu Global Activ — EVA vede orice aplicație și joc de pe telefon în timp real"
                            else
                                "🟡 Vedere internă activă — apasă „Activează Serviciu Global” pentru vedere în alte aplicații/jocuri",
                            color = if (isAccessibilityActive) EvaGreen else EvaTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { osEngine.inspectLiveScreen("Scanare declanșată din OS Control") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_live_screen_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EvaCyan)
                    ) {
                        Icon(Icons.Default.Scanner, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scanează Ecranul Acum", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { osEngine.openAccessibilitySettings() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_accessibility_settings_button")
                    ) {
                        Text("Activează Serviciu Global", fontSize = 11.sp, color = EvaCyan)
                    }
                }

                // Monitor în timp real al stării UI din alte aplicații (AccessibilityService)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF090D18))
                        .border(0.8.dp, if (isAccessibilityActive) EvaGreen.copy(alpha = 0.5f) else EvaBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📱", fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Stare UI Aplicație Externă:",
                                color = EvaTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isAccessibilityActive) EvaGreen.copy(alpha = 0.2f) else EvaRed.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (isAccessibilityActive) "Accessibility Activ" else "Serviciu Oprit",
                                color = if (isAccessibilityActive) EvaGreen else EvaRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val detectedPkg = currentAppUiState.packageName.ifBlank { "Nicio aplicație externă activă" }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pachet:", color = EvaTextSecondary, fontSize = 10.sp)
                        Text(detectedPkg.take(30), color = EvaCyan, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tip Ecran:", color = EvaTextSecondary, fontSize = 10.sp)
                        Text(currentAppUiState.screenType.displayName, color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    if (currentAppUiState.closePopupButton != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("❌", fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("Pop-up Detectat (X la ${currentAppUiState.closePopupButton?.centerX}, ${currentAppUiState.closePopupButton?.centerY}) — Auto-Dismiss Pregătit", color = EvaRed, fontSize = 9.5.sp)
                        }
                    }

                    if (currentAppUiState.startOrPlayButton != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("▶️", fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("Buton Start/Play Detectat (${currentAppUiState.startOrPlayButton?.text?.ifBlank { "Play" }})", color = EvaGreen, fontSize = 9.5.sp)
                        }
                    }

                    if (currentAppUiState.returnHomeButton != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏠", fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("Buton Continuare / Return Home Detectat", color = EvaCyan, fontSize = 9.5.sp)
                        }
                    }

                    if (currentAppUiState.hasCanvasOnlyDisplay) {
                        Text("🎮 Randare Grafică Directă (OpenGL/Vulkan) — Se folosește analiza de pixeli MediaProjection 60 FPS", color = EvaCyan, fontSize = 9.sp)
                    }

                    Text(
                        "Interpretare Decizie AI: ${currentAppUiState.decisionContextSummary}",
                        color = EvaTextSecondary,
                        fontSize = 9.sp,
                        lineHeight = 13.sp
                    )
                }


                if (visionReport.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .padding(10.dp)
                    ) {
                        Text(
                            visionReport,
                            color = EvaCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // ── 2.5. MediaProjection API & AI Vision Game UI Analyzer ───
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvaSurface)
                    .border(
                        1.dp,
                        if (isMediaProjectionActive) EvaGreen else EvaBlue,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CastConnected, contentDescription = null, tint = EvaCyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "📡 MediaProjection API & AI Vision Game Analyzer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            if (isMediaProjectionActive)
                                "🟢 Flux Video Live (${streamFps.coerceAtLeast(30)} FPS • ${frameLatencyMs}ms • $projectedFramesCount cadre) • Viziune AI Activă"
                            else
                                "🟡 Mod Hybrid (Accessibility Screenshot activ) — pornește MediaProjection pt. flux video 60-FPS în AI Vision",
                            color = if (isMediaProjectionActive) EvaGreen else EvaTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Monitor Video Live Adnotat (arată exact ce „vede” AI-ul pe fluxul MediaProjection în timp real)
                val liveBmp = annotatedVisionBitmap
                if (isMediaProjectionActive && liveBmp != null && !liveBmp.isRecycled) {
                    val imgBitmap = remember(liveBmp, projectedFramesCount) {
                        runCatching { liveBmp.asImageBitmap() }.getOrNull()
                    }
                    if (imgBitmap != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF050811))
                                .border(1.dp, EvaGreen.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "🎥 Flux Video MediaProjection în Timp Real (Vedere AI Adnotată)",
                                    color = EvaGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "${streamFps.coerceAtLeast(30)} FPS • ${frameLatencyMs}ms",
                                    color = EvaCyan,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Image(
                                bitmap = imgBitmap,
                                contentDescription = "Previzualizare flux video MediaProjection adnotat de AI Vision",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .testTag("media_projection_live_video_preview"),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (isMediaProjectionActive) {
                                osEngine.stopMediaProjection()
                            } else {
                                if (!com.eva1.companion.engines.MediaProjectionRequester.requestMediaProjection()) {
                                    runCatching {
                                        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toggle_media_projection_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMediaProjectionActive) EvaRed else EvaBlue
                        )
                    ) {
                        Icon(
                            if (isMediaProjectionActive) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isMediaProjectionActive) "Oprește MediaProjection" else "Activează MediaProjection",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                osEngine.triggerAiVisionFrameAnalysis()
                                osEngine.inspectLiveScreen("Analiză AI Vision declanșată manual")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("analyze_ai_vision_frame_button")
                    ) {
                        Text("Analizează UI Joc (AI Vision)", fontSize = 11.sp, color = EvaCyan)
                    }
                }

                // Telemetrie Vizuală Joc: Stare Joc + Bare de Viață (HP) + Butoane Detectate
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A0F1A))
                        .border(0.7.dp, EvaBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🎯 Stare Joc: ${latestVisionAnalysis.gameState.displayName}",
                            color = Color(latestVisionAnalysis.gameState.badgeColorHex),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            "${latestVisionAnalysis.frameWidth}x${latestVisionAnalysis.frameHeight}",
                            color = EvaTextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    VisionMetricBar(
                        label = "❤️ Health Bar Jucător (HP)",
                        percent = latestVisionAnalysis.healthBars.playerHpPercent,
                        barColor = if (latestVisionAnalysis.healthBars.isLowHealthCritical) EvaRed else EvaGreen
                    )
                    VisionMetricBar(
                        label = "👹 Health Bar Inamic / Boss",
                        percent = latestVisionAnalysis.healthBars.enemyOrBossHpPercent,
                        barColor = EvaRed
                    )
                    VisionMetricBar(
                        label = "⚡ Scut / Nitro / Elixir",
                        percent = latestVisionAnalysis.healthBars.secondaryBarPercent,
                        barColor = EvaCyan
                    )

                    Text(
                        text = latestVisionAnalysis.aiVisionDirective,
                        color = EvaCyan,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    val scene3d = latestScreenAnalysis?.scene3dAnalysis
                    if (scene3d != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.8.dp, EvaCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🧭 Viziune Scenă 3D Live: ${scene3d.sceneType.badge} ${scene3d.sceneType.labelRo}",
                                color = EvaCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Culoare Navigație: ◀ Stânga ${(scene3d.leftCorridorOpenness * 100).toInt()}% • ▲ Centru ${(scene3d.centerCorridorOpenness * 100).toInt()}% • ▶ Dreapta ${(scene3d.rightCorridorOpenness * 100).toInt()}%",
                                color = EvaTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = scene3d.tacticalSummaryRo,
                                color = EvaGreen,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    if (latestVisionAnalysis.detectedButtons.isNotEmpty()) {
                        Text(
                            "🔘 Butoane UI Joc Identificate (${latestVisionAnalysis.detectedButtons.size}):",
                            color = EvaTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        latestVisionAnalysis.detectedButtons.take(4).forEach { btn ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EvaSurface)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${btn.label} • (${btn.centerX.toInt()}, ${btn.centerY.toInt()})",
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "${(btn.confidence * 100).toInt()}% conf",
                                    color = EvaGreen,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 2.5 Conductă Viziune Multimodală (Gemini Vision & Local Vision Model) ──
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvaSurface)
                    .border(1.dp, EvaCyan.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
                    .testTag("multimodal_vision_card"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        tint = EvaCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "👁️ Conductă Viziune Multimodală (11 Modele Locale Offline)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Funcționează 100% OFFLINE direct pe procesorul telefonului cu cele 11 modele locale integrate — ZERO chei API necesare!",
                            color = EvaTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (mmIsAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = EvaCyan,
                            strokeWidth = 2.dp
                        )
                    }
                }

                // Selector Provider Multimodal
                Text(
                    "Motor Vizual Multimodal:",
                    color = EvaTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(MultiModalVisionProvider.values().toList()) { provider ->
                        val isSelected = mmProvider == provider
                        FilterChip(
                            selected = isSelected,
                            onClick = { osEngine.multiModalVision.selectProvider(provider) },
                            label = {
                                Text(
                                    "${provider.badge} ${provider.displayName}",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EvaCyan.copy(alpha = 0.25f),
                                selectedLabelColor = EvaCyan
                            )
                        )
                    }
                }

                // Telemetrie cadru multimodal
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF090E18))
                        .border(0.7.dp, EvaBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${mmAnalysis.providerUsed.badge} ${mmAnalysis.modelName}",
                            color = EvaCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            if (mmAnalysis.latencyMs > 0) "${mmAnalysis.latencyMs}ms • $mmFramesCount cadre" else "$mmFramesCount cadre analizate",
                            color = EvaTextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    VisionMetricBar(
                        label = "❤️ HP Jucător (Detectat de Viziune Multimodală)",
                        percent = mmAnalysis.playerHpPercent,
                        barColor = if (mmAnalysis.isLowHpCritical) EvaRed else EvaGreen
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "🎯 Reticul Țintă: ${if (mmAnalysis.hasTargetInCrosshair) "DA (Inamic Fixat!)" else "Liber"}",
                            color = if (mmAnalysis.hasTargetInCrosshair) EvaRed else EvaTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "👾 Inamici FOV: ${mmAnalysis.enemies.size}",
                            color = if (mmAnalysis.enemies.isNotEmpty()) EvaPurple else EvaTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    if (mmAnalysis.interactiveButtons.isNotEmpty()) {
                        Text(
                            "🔘 Butoane Tactile Detectate Dinamic (${mmAnalysis.interactiveButtons.size}):",
                            color = EvaTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        mmAnalysis.interactiveButtons.take(4).forEach { btn ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EvaSurface)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${btn.label} [${btn.category}]",
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "X:${(btn.normalizedX * 100).toInt()}% Y:${(btn.normalizedY * 100).toInt()}%",
                                    color = EvaCyan,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Decizie gestuală dinamică
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(EvaBlue.copy(alpha = 0.15f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = EvaCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                "Gest Dinamic Recomandat: ${mmAnalysis.recommendedGesture.motion} (${mmAnalysis.recommendedGesture.startX.toInt()}, ${mmAnalysis.recommendedGesture.startY.toInt()})",
                                color = EvaCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                mmAnalysis.recommendedGesture.reasoning,
                                color = EvaTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Buton analiză manuală imediată
                Button(
                    onClick = {
                        scope.launch {
                            osEngine.triggerAiVisionFrameAnalysis()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_multimodal_vision_analysis_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EvaCyan.copy(alpha = 0.85f))
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Declanșează Analiză Cadru cu Modelul Local (100% Offline)",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ── 2.8. Panou Arhitectură Dual-Brain (Sistem 1 Reflexe 16-60ms + Sistem 2 Strateg 5-10s) ──
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvaSurface)
                    .border(1.dp, if (isDualBrainActive) EvaGreen else EvaCyan, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🧠", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Creier AI Hibrid cu Două Viteze (Dual-Brain)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Sistem 1: Reflexe rapide (16-60ms) • Sistem 2: Strateg (5-10s)",
                                color = EvaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDualBrainActive) EvaGreen.copy(alpha = 0.2f) else EvaCyan.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isDualBrainActive) "ACTIV" else "PREGĂTIT",
                            color = if (isDualBrainActive) EvaGreen else EvaCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sistemul 1: Reflexe motorii
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚡", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Sistemul 1 (Reflexe Motorii • 30 FPS / ~35ms)",
                            color = EvaCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            lastReflexAction?.details ?: "Reflexe pe pixeli active (Foc în reticul, eschivă, sprint continuu)",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Text(
                            "Latență: ${lastReflexAction?.reflexLatencyMs ?: 35}ms • Total reflexe: $reflexCount",
                            color = EvaTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                // Sistemul 2: Strategul mare
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(activeDirective.posture.badge, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Sistemul 2 (Strategul • Model Local la 6.5s)",
                            color = EvaPurple,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            "Postură: ${activeDirective.posture.displayName}",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                        Text(
                            "Obiectiv: ${activeDirective.highLevelGoal}",
                            color = EvaTextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            "Model: ${activeDirective.modelName} • Cicluri strategice: $strategicCount",
                            color = EvaTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // ── 3. Autopilot Jocuri Mobile (Autonomous Game Bot) ────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvaSurface)
                    .border(
                        1.dp,
                        if (isGameBotRunning) EvaGreen else EvaPurple,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SportsEsports, contentDescription = null, tint = EvaPurple, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🎮 Autopilot Jocuri Mobile (Joacă în Locul Tău)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            gameBotStatus,
                            color = if (isGameBotRunning) EvaGreen else EvaTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (isGameBotRunning) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(EvaGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("$gameBotActions mutări", color = EvaGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Banner Viziune Meniu Jocuri Instalate (Pixel Screenshot + UI Tree)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isAccessibilityActive) EvaGreen.copy(alpha = 0.12f) else EvaRed.copy(alpha = 0.14f))
                        .border(
                            0.8.dp,
                            if (isAccessibilityActive) EvaGreen else EvaRed,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isAccessibilityActive) {
                            "🟢 Viziune Meniu & Pixel Screenshot ACTIVĂ: EVA-1 vede butoanele PLAY / START / BATTLE din meniul jocurilor instalate și joacă autonom."
                        } else {
                            "⚠️ Pentru ca EVA-1 să vadă meniul jocului instalat și să apese pe PLAY / START în alte aplicații, activează serviciul din Accesibilitate:"
                        },
                        color = if (isAccessibilityActive) EvaGreen else EvaTextPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isAccessibilityActive) {
                        Button(
                            onClick = { osEngine.openAccessibilitySettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = EvaCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Activează", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Panou Plutitor peste Jocuri (Floating AI Game HUD Overlay)
                if (isAccessibilityActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isFloatingHudVisible) EvaCyan.copy(alpha = 0.14f) else EvaBlack)
                            .border(
                                0.8.dp,
                                if (isFloatingHudVisible) EvaCyan else EvaBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isFloatingHudVisible) "🟢 Panou Plutitor peste Jocuri ACTIV" else "🕹️ Panou Plutitor peste Jocuri (Floating HUD)",
                                color = if (isFloatingHudVisible) EvaCyan else EvaTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Afișează bara flotantă EVA-1 direct peste orice joc deschis pe telefon (buton ▶️ Joacă Aici / ⏸ Pauză).",
                                color = EvaTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Button(
                            onClick = { osEngine.toggleFloatingGameHud(!isFloatingHudVisible) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFloatingHudVisible) EvaRed else EvaCyan
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("toggle_floating_game_hud_button")
                        ) {
                            Text(
                                text = if (isFloatingHudVisible) "Ascunde HUD" else "Afișează HUD",
                                color = if (isFloatingHudVisible) Color.White else Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Jocuri Instalate Detectate pe Telefon (1-Click Launch & Play)
                if (installedGames.isNotEmpty()) {
                    Text(
                        "📱 Jocuri detectate pe telefonul tău (${installedGames.size}) — apasă pentru selectare sau lansare directă:",
                        color = EvaCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(installedGames, key = { it.packageName }) { gameApp ->
                            val isSelected = targetGameInput.equals(gameApp.appName, ignoreCase = true) ||
                                targetGameInput.equals(gameApp.packageName, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) EvaGreen.copy(alpha = 0.22f) else EvaBlack)
                                    .border(
                                        0.9.dp,
                                        if (isSelected) EvaGreen else EvaCyan.copy(alpha = 0.5f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        targetGameInput = gameApp.appName
                                        val lowerPkg = "${gameApp.appName} ${gameApp.packageName}".lowercase()
                                        if (lowerPkg.contains("blast") || lowerPkg.contains("woodoku") || lowerPkg.contains("drag")) {
                                            selectedGameMode = GameBotMode.TOUCH_DRAG_PUZZLE
                                        } else if (lowerPkg.contains("tetris") || lowerPkg.contains("brick") || lowerPkg.contains("2048")) {
                                            selectedGameMode = GameBotMode.TETRIS_ARCADE
                                        }
                                        osEngine.startGameAutopilot(gameApp.packageName, selectedGameMode)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("🎮 ${gameApp.appName}", color = EvaTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("▶️ Joacă", color = EvaGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        item {
                            val isDemoSelected = targetGameInput.equals("demo", ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDemoSelected) EvaPurple.copy(alpha = 0.25f) else EvaBlack)
                                    .border(0.8.dp, if (isDemoSelected) EvaPurple else EvaBorder, RoundedCornerShape(10.dp))
                                    .clickable { targetGameInput = "demo" }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("🕹️ Mini-Joc Demo Intern", color = EvaTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = targetGameInput,
                    onValueChange = { targetGameInput = it },
                    placeholder = { Text("Nume joc de pe telefon (ex: Tetris, Subway Surfers, PUBG) sau lasă liber pt. auto-detecție...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("game_bot_target_input"),
                    singleLine = true
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GameBotMode.entries) { mode ->
                        val active = mode == selectedGameMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) EvaPurple.copy(alpha = 0.25f) else EvaBlack)
                                .border(0.8.dp, if (active) EvaPurple else EvaBorder, RoundedCornerShape(10.dp))
                                .clickable { selectedGameMode = mode }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(mode.displayName, color = if (active) EvaTextPrimary else EvaTextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                // ── SELECTOR MODEL LLM LOCAL PENTRU STRATEGIE JOCURI (11 MODELE) ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF060B14))
                        .border(1.dp, EvaPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖", fontSize = 15.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Model LLM Local pentru Strategie (${OfflineLlmEngine.AVAILABLE_MODELS.size} Modele)",
                                color = EvaPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text("Off-Line 100%", color = EvaGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "Alege care dintre cele 11 modele locale ia deciziile tactice când jocul este blocat:",
                        color = EvaTextSecondary,
                        fontSize = 10.sp
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(OfflineLlmEngine.AVAILABLE_MODELS) { model ->
                            val isSelected = model.id == selectedGameLlmModelId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) EvaPurple.copy(alpha = 0.35f) else EvaBlack)
                                    .border(
                                        if (isSelected) 1.dp else 0.6.dp,
                                        if (isSelected) EvaPurple else EvaBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { osEngine.selectGameLlmModel(model.id) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            model.displayName.take(24),
                                            color = if (isSelected) Color.White else EvaTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(model.sizeGb, color = EvaCyan, fontSize = 9.sp)
                                    }
                                    if (model.id == "Gemma-2-9B-Vision-Action-MLC") {
                                        Text("⭐ Recomandat Vision & Jocuri", color = Color(0xFFFFD54F), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    } else if (model.id.contains("DeepSeek")) {
                                        Text("⚡ Raționament & Tactică", color = EvaCyan, fontSize = 9.sp)
                                    } else if (model.id.contains("SmolLM2")) {
                                        Text("🚀 Viteză Instantanee (0.25GB)", color = EvaGreen, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Strategia jocurilor folosește exclusiv motorul offline integrat.
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("Strategie AI pe dispozitiv", color = EvaCyan, fontWeight = FontWeight.Bold)
                    Text("Fără server HTTP, Ollama, Termux sau porturi locale.", color = EvaTextSecondary)
                    Text("Model selectat: ${bridgeConfig.modelName}", color = EvaTextSecondary)
                    Text("Stare: ${bridgeStatus}", color = EvaTextSecondary)
                }

                // ── MAPARE DINAMICĂ GESTURI LLM (dispatchGesture pe Coordonate Reale) ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0C101D))
                        .border(1.dp, EvaCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .testTag("dynamic_gesture_monitor_card"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎯", fontSize = 15.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Translație Dinamică Coordonate LLM",
                                color = EvaCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(EvaCyan.copy(alpha = 0.2f))
                                .border(0.6.dp, EvaCyan, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Fără Bucle Fixe", color = EvaCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        "Convertește analiza spațială a ecranului în atingeri fizice reale (dispatchGesture) pe baza raționamentului LLM în timp real.",
                        color = EvaTextSecondary,
                        fontSize = 10.sp
                    )

                    latestDynamicGesture?.let { gesture ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF070A12))
                                .border(0.6.dp, EvaCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Mișcare: ", color = EvaTextSecondary, fontSize = 10.sp)
                                    Text(
                                        gesture.motion.name,
                                        color = EvaGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    "Durată: ${gesture.durationMs}ms ${if (gesture.repeatCount > 1) "x${gesture.repeatCount}" else ""}",
                                    color = EvaTextSecondary,
                                    fontSize = 9.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "De la: (${gesture.startX.toInt()}, ${gesture.startY.toInt()})",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (gesture.motion == DynamicGestureMotion.SWIPE || gesture.motion == DynamicGestureMotion.DRAG_AND_DROP) {
                                    Text(
                                        "Până la: (${gesture.endX.toInt()}, ${gesture.endY.toInt()})",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (gesture.reasoning.isNotBlank()) {
                                Text(
                                    "Raționament LLM: ${gesture.reasoning}",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    } ?: run {
                        Text(
                            "În așteptarea primei directive spațiale LLM...",
                            color = EvaTextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    if (recentDynamicGestures.isNotEmpty()) {
                        Text("Istoric Recente (dispatchGesture):", color = EvaTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        recentDynamicGestures.take(3).forEach { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF090C16))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (result.isDispatched) "✅" else "❌",
                                        fontSize = 10.sp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        result.command.motion.name,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "(${result.command.startX.toInt()}, ${result.command.startY.toInt()})",
                                        color = EvaTextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                                Text(
                                    "${result.executionLatencyMs}ms",
                                    color = if (result.isDispatched) EvaGreen else EvaRed,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }

                // ── IMITATION LEARNING: CLONARE GESTURI UTILIZATOR ──
                val currentCleanKey = targetGameInput.ifBlank { "pubg" }.lowercase()
                val gameGestures = learnedGestures.filter { currentCleanKey.contains(it.gameKey) || it.gameKey.contains(currentCleanKey) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090E18))
                        .border(1.dp, EvaGreen.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎮", fontSize = 15.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Învață din Stilul Meu (Imitation Learning)",
                                color = EvaGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(EvaGreen.copy(alpha = 0.2f))
                                .border(0.6.dp, EvaGreen, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${gameGestures.size} butoane/gesturi calibrate",
                                color = EvaGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        "AI-ul folosește pozițiile reale ale butoanelor tale în loc să ghicească coordonate la întâmplare:",
                        color = EvaTextSecondary,
                        fontSize = 10.sp
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(gameGestures, key = { it.id }) { g ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EvaBlack)
                                    .border(0.7.dp, EvaBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column {
                                        Text(g.actionLabel, color = EvaTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text("Coord: (${(g.targetXFrac * 100).toInt()}%, ${(g.targetYFrac * 100).toInt()}%) • ${g.motionType}", color = EvaCyan, fontSize = 9.sp)
                                    }
                                    IconButton(
                                        onClick = { osEngine.removeLearnedGesture(g.id) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Șterge", tint = EvaRed, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                newGestureLabel = ""
                                showGestureRecordDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EvaGreen.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).testTag("calibrate_gesture_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = EvaGreen, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Calibrează Buton Nou", color = EvaGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── MOTOR COGNITIV & ÎNVĂȚARE AUTONOMĂ JOCURI ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070D1A))
                        .border(1.dp, EvaCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🧠", fontSize = 15.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Auto-Învățare & Căutare Online Ghid",
                                color = EvaCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        activeGameProfile?.let { prof ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EvaPurple.copy(alpha = 0.25f))
                                    .border(0.6.dp, EvaPurple, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "${prof.skillTier.badge} ${prof.skillTier.displayName} • ${prof.experiencePoints} XP",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    activeGameProfile?.let { prof ->
                        Text(
                            "📖 Ghid Asimilat (${prof.gameTitle}): ${prof.onlineGuideSummary}",
                            color = EvaTextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Text(
                            "🎮 Comenzi Reale Învățate: ${prof.keyControlsLearned}",
                            color = EvaTextSecondary,
                            fontSize = 10.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "🎯 Rata Succes: ${prof.successRatePercent}% acțiuni eficiente",
                                color = EvaGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "🚫 Mișcări haotice: 0 (Eliminate)",
                                color = EvaCyan,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            "⚡ Recent adaptat: ${prof.lastAdaptationNote}",
                            color = Color(0xFFFFB74D),
                            fontSize = 10.sp
                        )
                    } ?: run {
                        Text(
                            cognitionLog,
                            color = EvaTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val target = targetGameInput.ifBlank { "PUBG Call of Duty Asphalt" }
                                scope.launch {
                                    osEngine.gameCognition.learnGameLogicOnline(target, "")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EvaPurple.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("learn_game_online_button")
                        ) {
                            Icon(Icons.Default.TravelExplore, contentDescription = null, tint = EvaCyan, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Caută Ghid & Reguli pe Net", color = EvaCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            osEngine.startGameAutopilot(targetGameInput, selectedGameMode)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_game_bot_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EvaGreen)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Joacă în Locul Meu", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (isGameBotRunning) {
                        Button(
                            onClick = { osEngine.stopGameAutopilot() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stop_game_bot_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EvaRed)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Oprește Autopilot", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // ── ARENA VIZUALĂ INTERACTIVĂ AI JOCURI LIVE (60 FPS) ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF060A14))
                        .border(1.dp, if (isGameBotRunning) EvaCyan else EvaBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🕹️ ${liveGameState.gameTitle}",
                            color = EvaCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Scor: ${liveGameState.score} • 💰 ${liveGameState.coins} • Combo x${liveGameState.combo}",
                            color = EvaGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Boss HP Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("BOSS HP:", color = EvaRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { (liveGameState.bossHpPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EvaRed,
                            trackColor = EvaSurfaceHigh
                        )
                        Text("${liveGameState.bossHpPercent.toInt()}%", color = EvaTextSecondary, fontSize = 10.sp)
                    }

                    // 3-Lane Interactive Game Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF04070F))
                            .border(0.8.dp, EvaBorder, RoundedCornerShape(10.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val laneW = w / 3f

                            // Liniile separatoare pentru cele 3 culoare
                            for (lane in 1..2) {
                                drawLine(
                                    color = Color(0xFF1E325C),
                                    start = Offset(lane * laneW, 0f),
                                    end = Offset(lane * laneW, h),
                                    strokeWidth = 2f
                                )
                            }

                            // Linii orizontale în mișcare (efect viteză 3D)
                            val scrollOffset = ((liveGameState.score * 4) % 45).toFloat()
                            var gy = scrollOffset
                            while (gy < h) {
                                drawLine(
                                    color = Color(0xFF11203D),
                                    start = Offset(0f, gy),
                                    end = Offset(w, gy),
                                    strokeWidth = 1f
                                )
                                gy += 45f
                            }

                            // Obstacole și Monede Bonus
                            for (obs in liveGameState.obstacles) {
                                val cx = obs.lane * laneW + laneW * 0.5f
                                val cy = obs.yProgress * h
                                if (obs.isCoin) {
                                    // Monedă Bonus (Auriu)
                                    drawCircle(
                                        color = Color(0xFFFFD600),
                                        radius = 13f,
                                        center = Offset(cx, cy)
                                    )
                                } else {
                                    // Obstacol periculos (Roșu Neon)
                                    drawRoundRect(
                                        color = Color(0xFFFF2E54),
                                        topLeft = Offset(cx - laneW * 0.32f, cy - 14f),
                                        size = Size(laneW * 0.64f, 28f),
                                        cornerRadius = CornerRadius(8f, 8f)
                                    )
                                }
                            }

                            // Jucătorul AI EVA-1 pe culoarul activ
                            val playerCx = liveGameState.playerLane * laneW + laneW * 0.5f
                            val playerCy = h * 0.82f
                            // Halo energetic
                            drawCircle(
                                color = Color(0x5500E5FF),
                                radius = 26f,
                                center = Offset(playerCx, playerCy)
                            )
                            // Navă / Avatar AI EVA-1
                            val shipPath = Path().apply {
                                moveTo(playerCx, playerCy - 20f)
                                lineTo(playerCx - 18f, playerCy + 16f)
                                lineTo(playerCx, playerCy + 8f)
                                lineTo(playerCx + 18f, playerCy + 16f)
                                close()
                            }
                            drawPath(path = shipPath, color = Color(0xFF00F5D4))
                        }
                    }

                    Text(
                        "🤖 Decizie AI: ${liveGameState.lastAiDecision}",
                        color = EvaCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Butoane de control manual / asistat în Arena Jocului
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { osEngine.manualMoveLane(-1) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⬅️ Stânga", fontSize = 11.sp, color = EvaCyan)
                        }
                        OutlinedButton(
                            onClick = { osEngine.manualJumpOrAttack() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⚡ Atac / Salt", fontSize = 11.sp, color = EvaGreen)
                        }
                        OutlinedButton(
                            onClick = { osEngine.manualMoveLane(1) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dreapta ➡️", fontSize = 11.sp, color = EvaCyan)
                        }
                    }
                }
            }
        }

        // ── 3.5. Diagnoză & Jurnal Decizii AI în Logcat (EVA_GAME_DECISION) ─────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvaSurface)
                    .border(1.dp, EvaCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = EvaCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "📋 Jurnal Decizii AI & Diagnoză Logcat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = EvaTextPrimary
                            )
                            Text(
                                "Filtru: adb logcat -s EVA_GAME_DECISION (${recentDecisionLogs.size} decizii)",
                                fontSize = 10.sp,
                                color = EvaTextSecondary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("EVA-1 Game Decision Logcat", EvaGameDecisionLogger.exportFormattedLogcatText())
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Logcat EVA_GAME_DECISION copiat în clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_logcat_decisions_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiază Logcat", tint = EvaGreen, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { EvaGameDecisionLogger.clearLogs() },
                            modifier = Modifier.testTag("clear_logcat_decisions_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Golește Jurnal", tint = EvaRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Toggle Explicație Diagnoză: Test Simplu vs Jocuri Complexe
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(EvaBlack)
                        .clickable { showDiagnosticExplain = !showDiagnosticExplain }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "🔍 De ce reușește pe Testul Simplu vs. Jocuri Complexe?",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EvaCyan
                    )
                    Icon(
                        if (showDiagnosticExplain) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = EvaCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showDiagnosticExplain) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF070E1A))
                            .border(0.8.dp, EvaCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "🎮 Testul Simplu Intern (Cyber Runner):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EvaGreen
                        )
                        Text(
                            "• Date deterministe 100% în RAM (lane, yProgress, isCoin).\n• Nicio eroare de Computer Vision, latență 0ms de randare, fără ferestre pop-up neprevăzute sau ecrane de încărcare ale serverului.",
                            fontSize = 10.sp,
                            color = EvaTextSecondary,
                            lineHeight = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "🏰 Jocuri Complexe Mobile (Clash of Clans, PUBG, etc.):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300)
                        )
                        Text(
                            "• Randare grafică directă GPU (OpenGL/Vulkan/Unity) fără noduri UI standard în ierarhia de Accesibilitate Android.\n• Necesită recunoaștere prin MediaProjection la 60 FPS, clasificare cromatică de butoane (ex: Atac, Find Match, Return Home), detectare dialoguri pop-up și gestionarea latenței de server.\n• Monitorizează în Logcat linia [DIAGNOSTIC / CAUZĂ POTENȚIALĂ EȘEC] la fiecare pas!",
                            fontSize = 10.sp,
                            color = EvaTextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Filtre Tip Joc
                val filteredLogs = remember(recentDecisionLogs, selectedDecisionLogFilter) {
                    if (selectedDecisionLogFilter == null) recentDecisionLogs
                    else recentDecisionLogs.filter { it.gameType == selectedDecisionLogFilter }
                }

                val complexCount = recentDecisionLogs.count { it.gameType == GameDecisionType.EXTERNAL_COMPLEX_GAME }
                val simpleCount = recentDecisionLogs.count { it.gameType == GameDecisionType.INTERNAL_SIMPLE_TEST }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedDecisionLogFilter == null,
                        onClick = { selectedDecisionLogFilter = null },
                        label = { Text("Toate (${recentDecisionLogs.size})", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedDecisionLogFilter == GameDecisionType.EXTERNAL_COMPLEX_GAME,
                        onClick = { selectedDecisionLogFilter = GameDecisionType.EXTERNAL_COMPLEX_GAME },
                        label = { Text("Jocuri Complexe ($complexCount)", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedDecisionLogFilter == GameDecisionType.INTERNAL_SIMPLE_TEST,
                        onClick = { selectedDecisionLogFilter = GameDecisionType.INTERNAL_SIMPLE_TEST },
                        label = { Text("Test Simplu ($simpleCount)", fontSize = 10.sp) }
                    )
                }

                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(EvaBlack)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nicio decizie înregistrată încă.\nPornește Autopilotul pe un joc sau în modul demo pentru a vizualiza în timp real ce elemente identifică AI-ul și ce decizii ia!",
                            color = EvaTextSecondary,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredLogs.take(8).forEach { entry ->
                            DecisionLogCard(entry)
                        }
                    }
                }
            }
        }

        // ── 4. WhatsApp Direct & YouTube Music Launcher ─────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvaSurface)
                    .border(1.dp, EvaBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("💬 Mesaj Rapid WhatsApp & 🎵 Muzică YouTube", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = waRecipient,
                        onValueChange = { waRecipient = it },
                        placeholder = { Text("Persoană / Tel (ex: Andrei / 0722...)") },
                        modifier = Modifier
                            .weight(0.45f)
                            .testTag("whatsapp_recipient_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = waMessage,
                        onValueChange = { waMessage = it },
                        placeholder = { Text("Mesaj WhatsApp...") },
                        modifier = Modifier
                            .weight(0.55f)
                            .testTag("whatsapp_message_input"),
                        singleLine = true
                    )
                }

                Button(
                    onClick = {
                        confirmedWaRecipient = waRecipient.trim()
                        confirmedWaMessage = waMessage.trim()
                        if (confirmedWaRecipient.isBlank() || confirmedWaMessage.isBlank()) {
                            waStatus = "⚠️ Completează destinatarul și mesajul înainte de confirmare."
                        } else {
                            showWhatsAppConfirmation = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_whatsapp_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EvaGreen)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verifică și trimite WhatsApp", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                if (showWhatsAppConfirmation) {
                    AlertDialog(
                        onDismissRequest = { showWhatsAppConfirmation = false },
                        title = { Text("Confirmă mesajul WhatsApp") },
                        text = { Text("Destinatar: $confirmedWaRecipient\n\nMesaj: $confirmedWaMessage\n\nEVA va încerca să apese Trimite numai dacă textul din WhatsApp coincide exact. Este necesar Accessibility EVA activ.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showWhatsAppConfirmation = false
                                waStatus = osEngine.sendConfirmedWhatsAppMessage(confirmedWaRecipient, confirmedWaMessage)
                            }) { Text("Confirm și trimit") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWhatsAppConfirmation = false }) { Text("Anulează") }
                        }
                    )
                }

                if (waStatus.isNotBlank()) {
                    Text(waStatus, color = EvaGreen, fontSize = 11.sp)
                }
                Text("Stare trimitere: $whatsAppSendStatus", color = EvaCyan, fontSize = 11.sp)

                HorizontalDivider(color = EvaBorder, modifier = Modifier.padding(vertical = 4.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ytQuery,
                        onValueChange = { ytQuery = it },
                        placeholder = { Text("Melodie / Artist pe YouTube (ex: Coldplay, Sub Pielea Mea)...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("youtube_query_input"),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            ytStatus = osEngine.playOnYouTube(ytQuery)
                        },
                        modifier = Modifier.testTag("play_youtube_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EvaRed)
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("YouTube", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Text("Verificare YouTube: $youTubePlaybackStatus", color = EvaCyan, fontSize = 11.sp)
                if (ytStatus.isNotBlank()) {
                    Text(ytStatus, color = EvaCyan, fontSize = 11.sp)
                }
            }
        }

        // ── 5. Quick OS Actions & 2FA SMS Extractor ────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvaSurface)
                    .border(1.dp, EvaBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚡ Acțiuni Sistem & Automatizare", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { osEngine.openSystemSettings("wifi") }, modifier = Modifier.weight(1f)) {
                        Text("Wi-Fi", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { osEngine.openSystemSettings("bluetooth") }, modifier = Modifier.weight(1f)) {
                        Text("Bluetooth", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { osEngine.openSystemSettings("battery") }, modifier = Modifier.weight(1f)) {
                        Text("Baterie", fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = {
                        val code = osEngine.extractLatest2faCodeFromSms()
                        otpStatus = code ?: "Nu s-a găsit niciun cod 2FA recent în SMS (sau permisiunea SMS nu e acordată)."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("extract_2fa_sms_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EvaAmber)
                ) {
                    Icon(Icons.Default.Sms, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Extrage Ultimul Cod 2FA / OTP din SMS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (otpStatus.isNotBlank()) {
                    Text(otpStatus, color = EvaCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // ── 6. Android Shell Terminal ──────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvaSurface)
                    .border(1.dp, EvaBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("💻 Terminal Shell Android (Local)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = shellCmd,
                        onValueChange = { shellCmd = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("os_shell_input"),
                        singleLine = true,
                        placeholder = { Text("Comandă shell (ex: df -h, top -n 1, getprop)") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                shellOutput = osEngine.executeShellCommand(shellCmd)
                            }
                        },
                        modifier = Modifier.testTag("os_shell_exec_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EvaGreen)
                    ) {
                        Text("Execută", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                if (shellOutput.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .padding(10.dp)
                    ) {
                        Text(
                            shellOutput.take(1500),
                            color = EvaGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // ── 7. App Launcher ────────────────────────────────────────
        item {
            Text("🚀 Lansator Rapid Aplicații (${allApps.size} instalate)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = appSearch,
                onValueChange = { appSearch = it },
                placeholder = { Text("Caută aplicație...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("os_app_search_input"),
                singleLine = true
            )
        }

        items(filteredApps, key = { it.packageName }) { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(EvaSurface)
                    .border(
                        0.6.dp,
                        if (app.isGame) EvaGreen.copy(alpha = 0.5f) else EvaBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { osEngine.launchApp(app.packageName) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (app.isGame) Icons.Default.SportsEsports else Icons.Default.Apps,
                    contentDescription = null,
                    tint = if (app.isGame) EvaGreen else EvaBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (app.isGame) {
                            Text(
                                "JOC",
                                color = EvaGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(app.packageName, color = EvaTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EvaGreen.copy(alpha = 0.18f))
                        .border(0.7.dp, EvaGreen, RoundedCornerShape(8.dp))
                        .clickable {
                            targetGameInput = app.appName
                            osEngine.startGameAutopilot(app.packageName, selectedGameMode)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("🎮 Joacă AI", color = EvaGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Deschide", tint = EvaCyan, modifier = Modifier.size(16.dp))
            }
        }
    }

    if (showGestureRecordDialog) {
        val currentKey = targetGameInput.ifBlank { "pubg" }.lowercase()
        AlertDialog(
            onDismissRequest = { showGestureRecordDialog = false },
            title = {
                Text("🎯 Calibrare Buton / Gest ($currentKey)", color = EvaGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Configurează coordonatele exacte ale butonului de pe ecranul telefonului tău:",
                        fontSize = 11.sp,
                        color = EvaTextSecondary
                    )
                    OutlinedTextField(
                        value = newGestureLabel,
                        onValueChange = { newGestureLabel = it },
                        placeholder = { Text("Ex: Foc Principal / Drop / Sprint") },
                        label = { Text("Nume Acțiune") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Preconfigurare rapidă:", fontSize = 10.sp, color = EvaCyan)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val presets = listOf(
                            Triple("Foc PUBG", "FIRE", 0.86f to 0.72f),
                            Triple("Reîncărcare", "RELOAD", 0.78f to 0.88f),
                            Triple("Lunetă ADS", "SCOPE", 0.93f to 0.56f),
                            Triple("Medkit", "HEAL", 0.74f to 0.88f),
                            Triple("Atac Clash", "ATTACK", 0.08f to 0.88f),
                            Triple("Find Match", "FIND_MATCH", 0.82f to 0.76f),
                            Triple("Return Home", "RETURN_HOME", 0.50f to 0.85f)
                        )
                        items(presets) { (label, key, coords) ->
                            AssistChip(
                                onClick = {
                                    newGestureLabel = label
                                    newGestureTypeKey = key
                                    newGestureX = coords.first.toString()
                                    newGestureY = coords.second.toString()
                                },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newGestureX,
                            onValueChange = { newGestureX = it },
                            label = { Text("X (0.01-0.99)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newGestureY,
                            onValueChange = { newGestureY = it },
                            label = { Text("Y (0.01-0.99)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val x = newGestureX.toFloatOrNull() ?: 0.5f
                        val y = newGestureY.toFloatOrNull() ?: 0.5f
                        val label = newGestureLabel.ifBlank { "Buton $newGestureTypeKey" }
                        osEngine.recordLearnedGesture(
                            gameKey = currentKey,
                            actionLabel = label,
                            actionTypeKey = newGestureTypeKey,
                            targetXFrac = x,
                            targetYFrac = y,
                            note = "Calibrat manual de utilizator"
                        )
                        showGestureRecordDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EvaGreen)
                ) {
                    Text("Salvează", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGestureRecordDialog = false }) {
                    Text("Anulează", color = EvaTextSecondary)
                }
            }
        )
    }

}

@Composable
fun TelemetryBox(title: String, value: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(EvaSurface)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = EvaTextSecondary, fontSize = 11.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(sub, color = EvaTextMuted, fontSize = 10.sp)
    }
}

@Composable
fun VisionMetricBar(label: String, percent: Float, barColor: Color) {
    val safePct = percent.coerceIn(0f, 100f)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = EvaTextSecondary, fontSize = 10.sp)
            Text("${safePct.toInt()}%", color = barColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1E293B))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safePct / 100f)
                    .fillMaxHeight()
                    .background(barColor)
            )
        }
    }
}

@Composable
fun DecisionLogCard(entry: GameDecisionLogEntry) {
    val isComplex = entry.gameType == GameDecisionType.EXTERNAL_COMPLEX_GAME
    val accentColor = if (isComplex) EvaCyan else EvaPurple

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF070B14))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(0.8.dp, accentColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (isComplex) "COMPLEX" else "TEST SIMPLU",
                        color = accentColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("Pas #${entry.step}", color = EvaTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("• ${entry.timestampFormatted}", color = EvaTextSecondary, fontSize = 10.sp)
            }
            Text(
                entry.gameTitle.take(18),
                color = EvaTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Elementele identificate de AI pe ecran
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("👁️ Identificat:", color = EvaCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (entry.identifiedElements.isEmpty()) {
                Text(
                    "Fără butoane UI evidente (analiză dinamică pixeli / ecran complet)",
                    color = EvaTextSecondary,
                    fontSize = 10.sp
                )
            } else {
                Text(
                    entry.identifiedElements.joinToString(", ") { "${it.label} (${it.x.toInt()}, ${it.y.toInt()})" },
                    color = EvaTextPrimary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }

        // Decizia luată și categoria de acțiune
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("🎯 Acțiune:", color = EvaGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                "[${entry.actionCategory}] ${entry.chosenAction}",
                color = EvaTextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Coordonate țintă și stare gest
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📍 Țintă: ${entry.coordinatesTarget}",
                color = EvaTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                if (entry.gestureSuccess) "✓ Gest acceptat de Android" else "✕ Gest respins",
                color = if (entry.gestureSuccess) EvaGreen else EvaRed,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Raționamentul AI
        if (entry.reasoning.isNotBlank()) {
            Text(
                "💡 Raționament: ${entry.reasoning}",
                color = EvaTextSecondary,
                fontSize = 10.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }

        // Notă de diagnoză / cauză eșec
        if (entry.whyFailedOrSucceededNote.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1B1405))
                    .border(0.8.dp, Color(0xFFFFB300).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "⚠️ Diagnoză: ${entry.whyFailedOrSucceededNote}",
                    color = Color(0xFFFFCC80),
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

