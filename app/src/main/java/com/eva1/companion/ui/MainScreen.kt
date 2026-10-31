package com.eva1.companion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eva1.companion.EvaIntent
import com.eva1.companion.EvaTab
import com.eva1.companion.EvaViewModel
import com.eva1.companion.quantum.QuantumContextScreen
import com.eva1.companion.ui.screens.*
import com.eva1.companion.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: EvaViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // BackHandler pentru inchiderea overlay-ului vocal, a sertarului de navigatie sau revenirea in Chat
    BackHandler(enabled = state.showVoiceOverlay || drawerState.isOpen || state.activeTab != EvaTab.CHAT) {
        when {
            state.showVoiceOverlay -> viewModel.onIntent(EvaIntent.ToggleVoiceOverlay(false))
            drawerState.isOpen -> scope.launch { drawerState.close() }
            else -> viewModel.onIntent(EvaIntent.SwitchTab(EvaTab.CHAT))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = EvaDarkNavy,
                drawerContentColor = EvaTextPrimary,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(
                            "EVA-1 v2.1.${state.evolutionCount}",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EvaCyan
                        )
                        Text(
                            "Companion AI Complet • 9 Motoare + Mod Siri",
                            fontSize = 12.sp,
                            color = EvaTextSecondary
                        )
                    }

                    HorizontalDivider(color = EvaBorder, modifier = Modifier.padding(vertical = 6.dp))

                    EvaTab.entries.forEach { tab ->
                        val selected = state.activeTab == tab
                        NavigationDrawerItem(
                            label = {
                                Column {
                                    Text(tab.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                    Text(tab.subtitle, fontSize = 10.sp, color = EvaTextMuted)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
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
                                    },
                                    contentDescription = tab.label
                                )
                            },
                            selected = selected,
                            onClick = {
                                viewModel.onIntent(EvaIntent.SwitchTab(tab))
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .testTag("drawer_item_${tab.name.lowercase()}"),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = EvaBlue.copy(alpha = 0.2f),
                                selectedIconColor = EvaCyan,
                                selectedTextColor = EvaTextPrimary,
                                unselectedIconColor = EvaTextSecondary,
                                unselectedTextColor = EvaTextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = EvaBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                EvaTopBar(
                    state = state,
                    onIntent = viewModel::onIntent,
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            },
            bottomBar = {
                EvaBottomBar(
                    activeTab = state.activeTab,
                    onTabSelect = {
                        if (state.showVoiceOverlay) {
                            viewModel.onIntent(EvaIntent.ToggleVoiceOverlay(false))
                        }
                        viewModel.onIntent(EvaIntent.SwitchTab(it))
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(EvaBlack)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ── Siri-Style Hands-Free Floating Status Bar ──────
                    AnimatedVisibility(
                        visible = state.voiceAssistantEnabled && !state.showVoiceOverlay,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut()
                    ) {
                        SiriHandsFreeStatusBar(
                            state = state,
                            onIntent = viewModel::onIntent
                        )
                    }

                    // ── Auto-Genesis Notification Banner ───────────────
                    AnimatedVisibility(
                        visible = state.genesisNotification != null,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut()
                    ) {
                        state.genesisNotification?.let { note ->
                            GenesisNotificationBanner(
                                message = note,
                                onDismiss = { viewModel.onIntent(EvaIntent.DismissNotification) }
                            )
                        }
                    }

                    // ── Error Banner ───────────────────────────────────
                    AnimatedVisibility(
                        visible = state.error != null,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut()
                    ) {
                        state.error?.let { err ->
                            ErrorBanner(
                                error = err,
                                onDismiss = { viewModel.onIntent(EvaIntent.DismissError) }
                            )
                        }
                    }

                    // ── Main Content per Tab (Toate cele 11 Ecrane) ────
                    Box(modifier = Modifier.weight(1f)) {
                        when (state.activeTab) {
                            EvaTab.CHAT -> ChatScreen(state = state, onIntent = viewModel::onIntent)
                            EvaTab.AGENT -> AgentScreen(state = state, onIntent = viewModel::onIntent)
                            EvaTab.CODE -> CodeScreen(state = state, onIntent = viewModel::onIntent)
                            EvaTab.MEDIA -> MediaScreen(state = state, onIntent = viewModel::onIntent)
                            EvaTab.MESH -> MeshScreen(
                                state = state,
                                swarmManager = viewModel.swarmMeshManager,
                                onIntent = viewModel::onIntent
                            )
                            EvaTab.QUANTUM -> QuantumContextScreen(
                                engine = viewModel.quantumEngine,
                                onAdoptPrecomputedToChat = { precomputed ->
                                    viewModel.onIntent(EvaIntent.SwitchTab(EvaTab.CHAT))
                                    viewModel.onIntent(EvaIntent.SendMessage(precomputed))
                                }
                            )
                            EvaTab.OFFLINE_LLM -> OfflineLlmScreen(
                                state = state,
                                engine = viewModel.offlineLlmEngine,
                                onIntent = viewModel::onIntent
                            )
                            EvaTab.OS -> OsScreen(osEngine = viewModel.osOrchestratorEngine)
                            EvaTab.MEMORY -> MemoryScreen(state = state, onIntent = viewModel::onIntent)
                            EvaTab.HISTORY -> HistoryScreen(state = state, onIntent = viewModel::onIntent)
                            EvaTab.SETTINGS -> SettingsScreen(state = state, onIntent = viewModel::onIntent)
                        }
                    }
                }

                // ── Siri-Style Interactive Voice Overlay ───────────────
                AnimatedVisibility(
                    visible = state.showVoiceOverlay,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SiriVoiceOverlay(
                        state = state,
                        onIntent = viewModel::onIntent
                    )
                }
            }
        }
    }
}
