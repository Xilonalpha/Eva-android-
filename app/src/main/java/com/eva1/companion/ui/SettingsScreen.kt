package com.eva1.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eva1.companion.*
import com.eva1.companion.ui.theme.*

@Composable
fun SettingsScreen(state: EvaUiState, onIntent: (EvaIntent) -> Unit) {
    var promptDraft by remember(state.systemPrompt) { mutableStateOf(state.systemPrompt) }

    // Chei API initializate din starea persistenta DataStore
    var anthropicKey by remember(state.apiKeys.anthropicKey) { mutableStateOf(state.apiKeys.anthropicKey) }
    var mistralKey by remember(state.apiKeys.mistralKey) { mutableStateOf(state.apiKeys.mistralKey) }
    var jdoodleId by remember(state.apiKeys.jdoodleClientId) { mutableStateOf(state.apiKeys.jdoodleClientId) }
    var jdoodleSecret by remember(state.apiKeys.jdoodleClientSecret) { mutableStateOf(state.apiKeys.jdoodleClientSecret) }
    var elevenLabsKey by remember(state.apiKeys.elevenLabsKey) { mutableStateOf(state.apiKeys.elevenLabsKey) }
    var replicateKey by remember(state.apiKeys.replicateKey) { mutableStateOf(state.apiKeys.replicateKey) }
    var stabilityKey by remember(state.apiKeys.stabilityKey) { mutableStateOf(state.apiKeys.stabilityKey) }
    var geminiKey by remember(state.apiKeys.geminiKey) { mutableStateOf(state.apiKeys.geminiKey) }
    var showKeys by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Setări & Configurare EVA-1", style = MaterialTheme.typography.titleLarge)

        // ── 1. Chei API Principale (Salvate Persistent in DataStore) ───
        SettingsCard(title = "🔑 Chei API Cloud (Salvate Persistent)") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Cheile sunt salvate criptat local + citite automat din AI Studio Secrets (.env)",
                    color = EvaTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showKeys = !showKeys }) {
                    Icon(
                        if (showKeys) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Afișează sau ascunde cheile",
                        tint = EvaCyan
                    )
                }
            }

            ApiKeyField("Google Gemini API Key (Multimodal Vision)", geminiKey, showKeys) { geminiKey = it }
            ApiKeyField("Anthropic API Key (Claude Sonnet 4)", anthropicKey, showKeys) { anthropicKey = it }
            ApiKeyField("Mistral API Key (Mistral Large)", mistralKey, showKeys) { mistralKey = it }
            ApiKeyField("JDoodle Client ID (Compilator 20+ limbaje)", jdoodleId, showKeys) { jdoodleId = it }
            ApiKeyField("JDoodle Client Secret", jdoodleSecret, showKeys) { jdoodleSecret = it }
            ApiKeyField("ElevenLabs API Key (Voce TTS ultra-realistă)", elevenLabsKey, showKeys) { elevenLabsKey = it }
            ApiKeyField("Replicate API Key (MusicGen & Video AI)", replicateKey, showKeys) { replicateKey = it }
            ApiKeyField("Stability AI Key (Opțional — Pollinations e gratuit)", stabilityKey, showKeys) { stabilityKey = it }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    onIntent(
                        EvaIntent.SaveApiKeys(
                            StoredApiKeys(
                                anthropicKey = anthropicKey.trim(),
                                mistralKey = mistralKey.trim(),
                                jdoodleClientId = jdoodleId.trim(),
                                jdoodleClientSecret = jdoodleSecret.trim(),
                                elevenLabsKey = elevenLabsKey.trim(),
                                replicateKey = replicateKey.trim(),
                                stabilityKey = stabilityKey.trim(),
                                runwayKey = state.apiKeys.runwayKey,
                                sunoKey = state.apiKeys.sunoKey,
                                geminiKey = geminiKey.trim()
                            )
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_api_keys_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EvaGreen)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Salvează Cheile API Persistent", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // ── 2. AI Engine Selection & Modele Locale Fără Limitări ───
        SettingsCard(title = "🤖 Motor AI Principal & Modele Locale (Fără Limitări)") {
            AiEngine.entries.forEach { engine ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.selectedEngine == engine,
                        onClick = { onIntent(EvaIntent.SwitchEngine(engine)) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(engine.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(engine.modelId, color = EvaTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            if (state.selectedEngine == AiEngine.LOCAL_LLM) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EvaGreen.copy(alpha = 0.14f))
                        .border(1.dp, EvaGreen, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "✅ MODUL LOCAL LLM OMNI ACTIV: Orice acțiune din aplicație (Chat, Agent Autonom, Generare Poze 8K, Generare Videoclipuri MP4, Scriere Programe Complete în Code Studio, Mesaje WhatsApp, Muzică YouTube, Viziune Ecran în Timp Real și Autopilot Jocuri) este executată de modelul local selectat mai jos, fără limitări!",
                        color = EvaGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "📦 Alege Modelul Local Activ (${com.eva1.companion.engines.OfflineLlmEngine.AVAILABLE_MODELS.size} modele fără limitări):",
                color = EvaCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))

            com.eva1.companion.engines.OfflineLlmEngine.AVAILABLE_MODELS.forEach { localModel ->
                val isSelectedModel = state.offlineModelId == localModel.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelectedModel) EvaBlue.copy(alpha = 0.18f) else EvaBlack)
                        .border(
                            0.8.dp,
                            if (isSelectedModel) EvaCyan else EvaBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelectedModel,
                        onClick = {
                            onIntent(EvaIntent.LoadOfflineModel(localModel.id))
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(localModel.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EvaTextPrimary)
                        Text(localModel.description, color = EvaTextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(localModel.sizeGb, color = EvaCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // ── 3. Advanced Capabilities ───────────────────────────────
        SettingsCard(title = "⚡ Capabilități Avansate") {
            SettingToggleRow(
                title = "Gândire Extinsă (Extended Thinking)",
                subtitle = "Claude analizează profund problema înainte de a răspunde",
                checked = state.useExtendedThinking,
                onCheckedChange = { onIntent(EvaIntent.ToggleThinking(it)) }
            )
            HorizontalDivider(color = EvaBorder, modifier = Modifier.padding(vertical = 8.dp))
            SettingToggleRow(
                title = "Căutare Web în Timp Real",
                subtitle = "Acces live la internet prin DuckDuckGo Instant API",
                checked = state.useWebSearch,
                onCheckedChange = { onIntent(EvaIntent.ToggleWebSearch(it)) }
            )
            HorizontalDivider(color = EvaBorder, modifier = Modifier.padding(vertical = 8.dp))
            SettingToggleRow(
                title = "Motor Auto-Genesis (Auto-Evoluție)",
                subtitle = "EVA-1 își rafinează singur promptul și memoria după conversații",
                checked = state.autoGenesisEnabled,
                onCheckedChange = { onIntent(EvaIntent.ToggleAutoGenesis(it)) }
            )
        }

        // ── 3B. Android TextToSpeech (TTS) & Voice Assistant ────────
        SettingsCard(title = "🔊 Motor Vocal Android TextToSpeech (TTS)") {
            Text(
                state.ttsStatusLabel,
                color = EvaCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            SettingToggleRow(
                title = "Răspuns Vocal Automat (Auto-Speak)",
                subtitle = "EVA-1 rostește automat cu voce tare răspunsurile din Chat, Agent și Hands-Free",
                checked = state.autoSpeakReplies,
                onCheckedChange = { onIntent(EvaIntent.ToggleAutoSpeak(it)) }
            )

            HorizontalDivider(color = EvaBorder, modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "Viteză vorbire: ${String.format("%.2f", state.ttsSpeechRate)}x",
                color = EvaTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = state.ttsSpeechRate,
                onValueChange = { onIntent(EvaIntent.SetTtsSpeechRate(it)) },
                valueRange = 0.6f..1.8f,
                colors = SliderDefaults.colors(thumbColor = EvaCyan, activeTrackColor = EvaCyan)
            )

            Text(
                "Tonalitate voce (Pitch): ${String.format("%.2f", state.ttsPitch)}x",
                color = EvaTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = state.ttsPitch,
                onValueChange = { onIntent(EvaIntent.SetTtsPitch(it)) },
                valueRange = 0.6f..1.5f,
                colors = SliderDefaults.colors(thumbColor = EvaPurple, activeTrackColor = EvaPurple)
            )

            Text(
                "Pentru voce neurală românească offline, instalează motorul Sherpa-ONNX Piper ro_RO-mihai-medium (APK separat, ARM64). La următoarea pornire, EVA îl selectează automat dacă este instalat.",
                color = EvaTextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            OutlinedButton(
                onClick = { onIntent(EvaIntent.OpenSherpaTtsGuide) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = EvaCyan)
                Spacer(Modifier.width(8.dp))
                Text("Instalează / vezi vocea Piper românească", color = EvaCyan)
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onIntent(
                            EvaIntent.SpeakMessage(
                                "Salut! Sunt EVA-1, asistentul tău vocal inteligent din anul 2026. Motorul Android Text To Speech este activ și pregătit!"
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EvaBlue),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_tts_voice_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Testează Vocea", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (state.isTtsSpeaking) {
                    OutlinedButton(
                        onClick = { onIntent(EvaIntent.StopSpeaking) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = EvaRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Oprește", color = EvaRed, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── 4. Auto-Genesis Manual Trigger ─────────────────────────
        SettingsCard(title = "🧬 Auto-Genesis Engine (v2.${state.evolutionCount})") {
            Text(
                "Forțează acum o analiză a sesiunii curente pentru a extrage noi amintiri și a evolua promptul de sistem.",
                color = EvaTextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onIntent(EvaIntent.TriggerManualEvolution) },
                colors = ButtonDefaults.buttonColors(containerColor = EvaPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_evolution_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Declanșează Evoluția Acum")
            }
        }

        // ── 5. System Prompt Editor ────────────────────────────────
        SettingsCard(title = "📜 Prompt de Sistem (ADN-ul EVA-1)") {
            OutlinedTextField(
                value = promptDraft,
                onValueChange = { promptDraft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_prompt_input"),
                minLines = 5,
                maxLines = 10,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EvaBlue,
                    unfocusedBorderColor = EvaBorder,
                    focusedContainerColor = EvaBlack,
                    unfocusedContainerColor = EvaBlack
                )
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onIntent(EvaIntent.UpdateSystemPrompt(promptDraft)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_system_prompt_button")
            ) {
                Text("Salvează Promptul de Sistem")
            }
        }
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    showPlain: Boolean,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        visualTransformation = if (showPlain) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EvaCyan,
            unfocusedBorderColor = EvaBorder,
            focusedContainerColor = EvaBlack,
            unfocusedContainerColor = EvaBlack
        )
    )
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EvaSurface)
            .border(0.8.dp, EvaBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = EvaTextPrimary)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = EvaTextSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
