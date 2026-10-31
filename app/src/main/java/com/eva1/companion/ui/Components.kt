package com.eva1.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eva1.companion.AiEngine
import com.eva1.companion.EvaIntent
import com.eva1.companion.EvaTab
import com.eva1.companion.EvaUiState
import com.eva1.companion.VoiceAssistantState
import com.eva1.companion.ui.theme.*

// ── Top Bar + Banda Orizontală cu Toate cele 11 Ecrane ────────────
@Composable
fun EvaTopBar(
    state: EvaUiState,
    onIntent: (EvaIntent) -> Unit,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onIntent(EvaIntent.ToggleVoiceAssistant(true))
        } else {
            onIntent(EvaIntent.ToggleVoiceOverlay(true))
        }
    }

    Surface(
        color = EvaDarkNavy,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("drawer_menu_button")
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Deschide meniul principal",
                        tint = EvaTextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(EvaBlue, EvaPurple))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("E1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "EVA-1",
                            color = EvaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(EvaPurple.copy(alpha = 0.25f))
                                .border(0.5.dp, EvaPurple, RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "v2.${state.evolutionCount}",
                                color = EvaPurple,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        "${state.activeTab.label} • ${state.selectedEngine.badge}",
                        color = EvaTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(4.dp))

                // ── Butonul Siri-Style "Hei Siri" Hands-Free ─────────
                HandsFreeTopButton(
                    state = state,
                    onClick = {
                        if (state.voiceAssistantEnabled) {
                            onIntent(EvaIntent.ToggleVoiceOverlay(!state.showVoiceOverlay))
                        } else {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                onIntent(EvaIntent.ToggleVoiceAssistant(true))
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                )

                Spacer(Modifier.width(4.dp))

                EngineChip(
                    selected = state.selectedEngine,
                    onToggle = {
                        val next = when (state.selectedEngine) {
                            AiEngine.CLAUDE -> AiEngine.MISTRAL
                            AiEngine.MISTRAL -> AiEngine.LOCAL_LLM
                            AiEngine.LOCAL_LLM -> AiEngine.CLAUDE
                        }
                        onIntent(EvaIntent.SwitchEngine(next))
                    }
                )

                IconButton(
                    onClick = { onIntent(EvaIntent.NewSession) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("new_session_button")
                ) {
                    Icon(
                        Icons.Default.AddComment,
                        contentDescription = "Sesiune nouă",
                        tint = EvaTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Banda Orizontală Rapidă cu TOATE cele 11 Ecrane (100% Accesibile) ──
            EvaAllScreensStrip(
                activeTab = state.activeTab,
                onTabSelect = { tab ->
                    if (state.showVoiceOverlay) {
                        onIntent(EvaIntent.ToggleVoiceOverlay(false))
                    }
                    onIntent(EvaIntent.SwitchTab(tab))
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(EvaBorder)
            )
        }
    }
}

@Composable
fun EvaAllScreensStrip(
    activeTab: EvaTab,
    onTabSelect: (EvaTab) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080D1A))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(EvaTab.entries) { tab ->
            val selected = tab == activeTab
            val icon = when (tab) {
                EvaTab.CHAT -> Icons.AutoMirrored.Filled.Chat
                EvaTab.AGENT -> Icons.Default.Psychology
                EvaTab.CODE -> Icons.Default.Code
                EvaTab.MEDIA -> Icons.Default.Palette
                EvaTab.MESH -> Icons.Default.Hub
                EvaTab.QUANTUM -> Icons.Default.Bolt
                EvaTab.OFFLINE_LLM -> Icons.Default.Memory
                EvaTab.OS -> Icons.Default.DeveloperBoard
                EvaTab.MEMORY -> Icons.Default.Storage
                EvaTab.HISTORY -> Icons.Default.History
                EvaTab.SETTINGS -> Icons.Default.Settings
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) Brush.linearGradient(listOf(EvaBlue.copy(alpha = 0.35f), EvaPurple.copy(alpha = 0.35f)))
                        else Brush.linearGradient(listOf(EvaSurface, EvaSurface))
                    )
                    .border(
                        width = if (selected) 1.dp else 0.6.dp,
                        color = if (selected) EvaCyan else EvaBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onTabSelect(tab) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("top_strip_tab_${tab.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = if (selected) EvaCyan else EvaTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = tab.label,
                    color = if (selected) Color.White else EvaTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun HandsFreeTopButton(
    state: EvaUiState,
    onClick: () -> Unit
) {
    val active = state.voiceAssistantEnabled
    val (bgBrush, borderColor, labelText) = when (state.voiceState) {
        VoiceAssistantState.LISTENING_COMMAND -> Triple(
            Brush.linearGradient(listOf(EvaGreen.copy(alpha = 0.35f), EvaCyan.copy(alpha = 0.35f))),
            EvaGreen,
            "Ascult"
        )
        VoiceAssistantState.SPEAKING -> Triple(
            Brush.linearGradient(listOf(EvaPurple.copy(alpha = 0.35f), EvaBlue.copy(alpha = 0.35f))),
            EvaPurple,
            "Voce"
        )
        VoiceAssistantState.PROCESSING -> Triple(
            Brush.linearGradient(listOf(EvaAmber.copy(alpha = 0.30f), EvaPurple.copy(alpha = 0.30f))),
            EvaAmber,
            "AI..."
        )
        VoiceAssistantState.LISTENING_WAKE_WORD -> Triple(
            Brush.linearGradient(listOf(EvaCyan.copy(alpha = 0.25f), EvaBlue.copy(alpha = 0.25f))),
            EvaCyan,
            "Siri ON"
        )
        VoiceAssistantState.OFF -> Triple(
            Brush.linearGradient(listOf(EvaSurfaceHigh, EvaSurfaceHigh)),
            EvaBorder,
            "Hei Siri"
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("hei_eva_voice_button"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (active) Icons.Default.Mic else Icons.Default.MicNone,
            contentDescription = "Mod Vocal Hands-Free Hei Siri",
            tint = if (active) EvaCyan else EvaTextSecondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = labelText,
            color = if (active) Color.White else EvaTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ── Bara Compactă Hands-Free (Când modul Siri e activ în fundal) ──
@Composable
fun SiriHandsFreeStatusBar(
    state: EvaUiState,
    onIntent: (EvaIntent) -> Unit
) {
    val statusColor = when (state.voiceState) {
        VoiceAssistantState.LISTENING_COMMAND -> EvaGreen
        VoiceAssistantState.SPEAKING -> EvaPurple
        VoiceAssistantState.PROCESSING -> EvaAmber
        VoiceAssistantState.LISTENING_WAKE_WORD -> EvaCyan
        VoiceAssistantState.OFF -> EvaTextMuted
    }

    Surface(
        color = Color(0xFF10192E),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, statusColor.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .clickable { onIntent(EvaIntent.ToggleVoiceOverlay(true)) }
            .testTag("siri_handsfree_status_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.voicePartialTranscript.isNotBlank()) {
                    "„${state.voicePartialTranscript}”"
                } else {
                    state.voiceStatusNote
                },
                color = EvaTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Panou Siri",
                color = EvaCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EvaCyan.copy(alpha = 0.15f))
                    .clickable { onIntent(EvaIntent.ToggleVoiceOverlay(true)) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = { onIntent(EvaIntent.ToggleVoiceAssistant(false)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Oprește Hands-Free",
                    tint = EvaRed,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// ── Siri-Style Interactive Voice Assistant Overlay (Scrollabil & Non-Blocant) ──
@Composable
fun SiriVoiceOverlay(
    state: EvaUiState,
    onIntent: (EvaIntent) -> Unit
) {
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onIntent(EvaIntent.ToggleVoiceAssistant(true))
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "siri_orb")
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val dynamicScale = (basePulse + (state.voiceRmsLevel * 0.35f)).coerceIn(0.88f, 1.45f)

    val (orbPrimary, orbSecondary, stateBadge) = when (state.voiceState) {
        VoiceAssistantState.LISTENING_COMMAND -> Triple(EvaGreen, EvaCyan, "🟢 TE ASCULT ACTIV — SPUNE COMANDA")
        VoiceAssistantState.SPEAKING -> Triple(EvaPurple, EvaBlue, "🔊 EVA-1 ÎȚI RĂSPUNDE VOCAL")
        VoiceAssistantState.PROCESSING -> Triple(EvaAmber, EvaPurple, "⚡ SE EXECUTĂ COMANDA TA...")
        VoiceAssistantState.LISTENING_WAKE_WORD -> Triple(EvaCyan, EvaBlue, "🎙️ MOD HANDS-FREE: STRIGĂ „HEY SIRI”")
        VoiceAssistantState.OFF -> Triple(EvaBlue, EvaPurple, "⏸️ ASISTENTUL VOCAL ESTE OPRIT")
    }

    // Apăsarea pe fundalul întunecat închide panoul ca să nu blocheze niciodată ecranele
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onIntent(EvaIntent.ToggleVoiceOverlay(false)) }
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = EvaDarkNavy,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .heightIn(max = 620.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Previne închiderea la click în interiorul cardului
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(orbPrimary, orbSecondary)),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = orbPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "EVA-1 Hands-Free (Mod Siri)",
                                color = EvaTextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Control vocal fără atingerea telefonului",
                                color = EvaTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = { onIntent(EvaIntent.ToggleVoiceOverlay(false)) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("close_voice_overlay_button")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Închide panoul vocal",
                            tint = EvaTextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Sfera Siri / EVA-1 Animată ──────────────────────
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(116.dp)
                        .clickable {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasMic) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                onIntent(EvaIntent.TriggerDirectVoiceListen)
                            }
                        }
                        .testTag("siri_orb_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(106.dp)
                            .scale(dynamicScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        orbPrimary.copy(alpha = 0.42f),
                                        orbSecondary.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(orbPrimary, orbSecondary, EvaPurple)
                                )
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (state.voiceState) {
                                VoiceAssistantState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
                                VoiceAssistantState.LISTENING_COMMAND -> Icons.Default.Mic
                                VoiceAssistantState.PROCESSING -> Icons.Default.AutoAwesome
                                else -> Icons.Default.SettingsVoice
                            },
                            contentDescription = "Ascultare vocală",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Badge stare
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(orbPrimary.copy(alpha = 0.15f))
                        .border(1.dp, orbPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stateBadge,
                        color = orbPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = state.voiceStatusNote,
                    color = EvaTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                // Transcript live + Răspunsul vocal al EVEI
                Surface(
                    color = EvaSurfaceHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val shownInput = state.voicePartialTranscript.ifBlank {
                            state.voiceLastCommand.ifBlank {
                                "Spune: „Hei Siri, cât e ceasul?”, „Hei Siri, deschide Wi-Fi” sau „Hei Siri, câtă baterie mai am?”"
                            }
                        }
                        Text(
                            text = "🗣️ Tu:",
                            color = EvaCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = shownInput,
                            color = EvaTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (state.voiceLastResponse.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = EvaBorder)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "🔊 Răspuns EVA-1:",
                                color = EvaPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.voiceLastResponse,
                                color = EvaTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Comutatoare Hands-Free & Cuvânt de trezire ("Hei Siri")
                Surface(
                    color = EvaSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Ascultare Hands-Free Continuă",
                                    color = EvaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Ascultă în fundal cât timp folosești aplicația",
                                    color = EvaTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = state.voiceAssistantEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        val hasMic = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasMic) {
                                            onIntent(EvaIntent.ToggleVoiceAssistant(true))
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    } else {
                                        onIntent(EvaIntent.ToggleVoiceAssistant(false))
                                    }
                                },
                                modifier = Modifier.testTag("handsfree_master_switch")
                            )
                        }

                        HorizontalDivider(color = EvaBorder, modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Activare prin „Hei Siri” / „OK SIRI”",
                                    color = EvaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (state.voiceRequireWakeWord)
                                        "Execută când strigi „Hei Siri” (ca Siri)"
                                    else
                                        "Ascultare directă: execută orice propoziție",
                                    color = EvaTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = state.voiceRequireWakeWord,
                                onCheckedChange = { onIntent(EvaIntent.ToggleWakeWordRequirement(it)) },
                                modifier = Modifier.testTag("wake_word_switch")
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Testare Rapidă Comenzi Vocale ──
                Text(
                    text = "⚡ Simulare rapidă comandă vocală „Hei Siri”:",
                    color = EvaTextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))

                val sampleCommands = listOf(
                    "Hei Siri, cât e ceasul și în ce an suntem?",
                    "Hei Siri, câtă baterie și memorie RAM mai am?",
                    "Hei Siri, deschide setările Wi-Fi",
                    "Hei Siri, generează o imagine cu un oraș futurist în 2026",
                    "Hei Siri, compune o melodie electronică",
                    "Hei Siri, scrie un cod Python care calculează numerele prime"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sampleCommands.forEach { phrase ->
                        AssistChip(
                            onClick = { onIntent(EvaIntent.SimulateVoiceCommand(phrase)) },
                            label = {
                                Text(
                                    phrase,
                                    fontSize = 11.sp,
                                    color = EvaCyan
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = EvaCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onIntent(EvaIntent.ToggleVoiceOverlay(false)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Închide panoul", color = EvaTextSecondary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                onIntent(EvaIntent.TriggerDirectVoiceListen)
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EvaBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ascultă acum", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Engine Toggle Chip ────────────────────────────────────────────
@Composable
fun EngineChip(selected: AiEngine, onToggle: () -> Unit) {
    val (bg, border, textColor) = when (selected) {
        AiEngine.CLAUDE -> Triple(EvaBlue.copy(alpha = 0.18f), EvaBlue, EvaBlueBright)
        AiEngine.MISTRAL -> Triple(EvaCyan.copy(alpha = 0.15f), EvaCyan, EvaCyan)
        AiEngine.LOCAL_LLM -> Triple(EvaGreen.copy(alpha = 0.18f), EvaGreen, EvaGreen)
    }
    val label = when (selected) {
        AiEngine.CLAUDE -> "Claude"
        AiEngine.MISTRAL -> "Mistral"
        AiEngine.LOCAL_LLM -> "Local LLM"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("engine_toggle_chip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(textColor)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

// ── Bottom Navigation (Top 5 Primary Tabs + Drawer for all 11) ─────
@Composable
fun EvaBottomBar(activeTab: EvaTab, onTabSelect: (EvaTab) -> Unit) {
    val primaryTabs = listOf(
        EvaTab.CHAT,
        EvaTab.AGENT,
        EvaTab.CODE,
        EvaTab.MEDIA,
        EvaTab.QUANTUM
    )
    NavigationBar(
        containerColor = EvaDarkNavy,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = EvaBorder)
    ) {
        primaryTabs.forEach { tab ->
            val selected = tab == activeTab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelect(tab) },
                modifier = Modifier.testTag("bottom_tab_${tab.name.lowercase()}"),
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            EvaTab.CHAT -> Icons.AutoMirrored.Filled.Chat
                            EvaTab.AGENT -> Icons.Default.Psychology
                            EvaTab.CODE -> Icons.Default.Code
                            EvaTab.MEDIA -> Icons.Default.AutoAwesome
                            EvaTab.QUANTUM -> Icons.Default.Bolt
                            else -> Icons.Default.Settings
                        },
                        contentDescription = tab.label,
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = {
                    Text(
                        tab.label,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EvaBlue,
                    selectedTextColor = EvaBlue,
                    unselectedIconColor = EvaTextMuted,
                    unselectedTextColor = EvaTextMuted,
                    indicatorColor = EvaBlue.copy(alpha = 0.15f)
                )
            )
        }
    }
}

// ── Auto-Genesis Notification Banner ─────────────────────────────
@Composable
fun GenesisNotificationBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = Color(0xFF1A2E1A),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, EvaGreen.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                color = EvaGreen,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("dismiss_notification_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Închide notificarea", tint = EvaGreen, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// ── Error Banner ──────────────────────────────────────────────────
@Composable
fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Surface(
        color = Color(0xFF2E1218),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, EvaRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = "Eroare", tint = EvaRed, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                error,
                color = EvaRed,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("dismiss_error_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Închide eroarea", tint = EvaRed, modifier = Modifier.size(14.dp))
            }
        }
    }
}
