package com.eva1.companion

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eva1.companion.engines.*
import com.eva1.companion.mesh.SwarmMeshManager
import com.eva1.companion.quantum.QuantumContextEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class EvaViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = EvaRepository(app)
    private val anthropic = AnthropicApiService()
    private val mistral = MistralApiService()
    private val agent = AutonomousAgentEngine(mistral, anthropic)
    private val genesis = AutoGenesisEngine(mistral)

    // ── Cele 9 Sisteme Complete EVA-1 + Voice Assistant + Android TTS ──
    val codeEngine = CodeCompilerEngine(mistral, anthropic, app.cacheDir)
    val mediaEngine = MediaGenerationEngine(mistral, app.cacheDir)
    val meshEngine = MeshNetworkEngine(app)
    val swarmMeshManager = SwarmMeshManager(app)
    val quantumEngine = QuantumContextEngine(app, mistral, anthropic)
    val offlineLlmEngine = OfflineLlmEngine(app)
    val osOrchestratorEngine = OsOrchestratorEngine(app, offlineLlmEngine)
    val voiceEngine = VoiceAssistantEngine(
        context = app,
        onWakeWordDetected = { handleHandsFreeWakeWord() },
        onCommandRecognized = { cmd -> executeHandsFreeVoiceCommand(cmd) }
    )
    val ttsEngine = EvaTtsEngine(
        context = app,
        onSpeechStarted = {
            voiceEngine.onEvaSpeakingStarted()
        },
        onSpeechFinished = { utteranceId ->
            if (utteranceId == "wake_ack") {
                voiceEngine.triggerImmediateCommandListening()
            } else {
                voiceEngine.onEvaSpeakingFinished()
            }
            if (_state.value.currentlyPlayingMediaId != null && mediaPlayer == null) {
                _state.update { it.copy(currentlyPlayingMediaId = null) }
            }
        }
    )

    private val _state = MutableStateFlow(EvaUiState())
    val state: StateFlow<EvaUiState> = _state.asStateFlow()

    private val currentMessages = mutableListOf<Message>()
    private var mediaPlayer: MediaPlayer? = null
    private var voiceCommandJob: Job? = null

    init {
        loadPersistedState()
        observeMeshNetwork()
        observeQuantumContext()
        observeVoiceAssistant()
        observeOfflineLlm()
    }

    private fun observeOfflineLlm() {
        viewModelScope.launch {
            offlineLlmEngine.isLoaded.collect { loaded ->
                _state.update { it.copy(isOfflineModelLoaded = loaded) }
            }
        }
        viewModelScope.launch {
            offlineLlmEngine.loadProgress.collect { prog ->
                _state.update { it.copy(offlineLoadProgress = prog) }
            }
        }
        viewModelScope.launch {
            offlineLlmEngine.currentModel.collect { modelId ->
                _state.update { it.copy(offlineModelId = modelId) }
            }
        }
        viewModelScope.launch {
            offlineLlmEngine.tokensPerSecond.collect { tps ->
                _state.update { it.copy(offlineTokensPerSec = tps) }
            }
        }
        viewModelScope.launch {
            offlineLlmEngine.ggufEngine.runtimeState.collect { gs ->
                _state.update {
                    it.copy(
                        ggufModelId = gs.selectedModelId,
                        ggufDownloadProgress = gs.downloadProgressPercent,
                        isDownloadingGguf = gs.isDownloading,
                        ggufStatusMessage = gs.statusMessage,
                        ggufTemperature = gs.temperature,
                        ggufTopP = gs.topP,
                        ggufMaxTokens = gs.maxTokens
                    )
                }
            }
        }
    }

    private fun formatDeviceTelemetry(): String {
        return try {
            val t = osOrchestratorEngine.getDeviceTelemetry()
            val chargeStatus = if (t.isCharging) "la încărcat" else "pe baterie"
            val freeRam = (t.ramTotalMb - t.ramUsedMb).coerceAtLeast(0L)
            buildString {
                appendLine("• Baterie: ${t.batteryPercent}% ($chargeStatus)")
                appendLine("• Memorie RAM: ${t.ramUsedMb} MB utilizați / ${t.ramTotalMb} MB total ($freeRam MB liberi)")
                appendLine("• Stocare internă: ${t.storageFreeGb} GB liberi din ${t.storageTotalGb} GB")
                appendLine("• Sistem: ${t.model} • ${t.androidVersion}")
            }.trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun observeVoiceAssistant() {
        viewModelScope.launch {
            voiceEngine.state.collect { vs ->
                _state.update {
                    it.copy(
                        voiceState = vs,
                        voiceAssistantEnabled = voiceEngine.isEnabled
                    )
                }
            }
        }
        viewModelScope.launch {
            voiceEngine.partialTranscript.collect { pt ->
                _state.update { it.copy(voicePartialTranscript = pt) }
            }
        }
        viewModelScope.launch {
            voiceEngine.rmsLevel.collect { rms ->
                _state.update { it.copy(voiceRmsLevel = rms) }
                quantumEngine.updateVoiceRmsLevel(rms)
            }
        }
        viewModelScope.launch {
            voiceEngine.statusNote.collect { note ->
                _state.update { it.copy(voiceStatusNote = note) }
            }
        }
        viewModelScope.launch {
            ttsEngine.isSpeaking.collect { speaking ->
                _state.update { it.copy(isTtsSpeaking = speaking) }
            }
        }
        viewModelScope.launch {
            ttsEngine.speechRate.collect { rate ->
                _state.update { it.copy(ttsSpeechRate = rate) }
            }
        }
        viewModelScope.launch {
            ttsEngine.pitch.collect { p ->
                _state.update { it.copy(ttsPitch = p) }
            }
        }
        viewModelScope.launch {
            ttsEngine.statusLabel.collect { status ->
                _state.update { it.copy(ttsStatusLabel = status) }
            }
        }
    }

    private fun loadPersistedState() {
        // 1. Chei API persistente + BuildConfig fallback
        viewModelScope.launch {
            repo.apiKeysFlow.collect { keys ->
                AnthropicApiService.API_KEY = keys.anthropicKey
                MistralApiService.API_KEY = keys.mistralKey
                CodeCompilerEngine.JDOODLE_CLIENT_ID = keys.jdoodleClientId
                CodeCompilerEngine.JDOODLE_CLIENT_SECRET = keys.jdoodleClientSecret
                MediaGenerationEngine.ELEVENLABS_API_KEY = keys.elevenLabsKey
                MediaGenerationEngine.REPLICATE_API_KEY = keys.replicateKey
                MediaGenerationEngine.STABILITY_API_KEY = keys.stabilityKey
                MediaGenerationEngine.RUNWAY_API_KEY = keys.runwayKey
                MediaGenerationEngine.SUNO_API_KEY = keys.sunoKey
                if (keys.geminiKey.isNotBlank()) {
                    getApplication<Application>()
                        .getSharedPreferences("eva1_prefs", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putString("gemini_api_key", keys.geminiKey)
                        .apply()
                }

                _state.update { it.copy(apiKeys = keys) }
            }
        }
        viewModelScope.launch {
            repo.memoriesFlow.collect { m -> _state.update { it.copy(memories = m) } }
        }
        viewModelScope.launch {
            repo.historyFlow.collect { h -> _state.update { it.copy(sessions = h) } }
        }
        viewModelScope.launch {
            repo.systemPromptFlow.collect { p -> _state.update { it.copy(systemPrompt = p) } }
        }
        viewModelScope.launch {
            repo.evolutionCountFlow.collect { c -> _state.update { it.copy(evolutionCount = c) } }
        }
        viewModelScope.launch {
            repo.engineFlow.collect { e -> _state.update { it.copy(selectedEngine = e) } }
        }
        viewModelScope.launch {
            repo.thinkingFlow.collect { t -> _state.update { it.copy(useExtendedThinking = t) } }
        }
        viewModelScope.launch {
            repo.webSearchFlow.collect { w -> _state.update { it.copy(useWebSearch = w) } }
        }
        viewModelScope.launch {
            repo.autoGenesisFlow.collect { g -> _state.update { it.copy(autoGenesisEnabled = g) } }
        }
        viewModelScope.launch {
            repo.codeProjectsFlow.collect { list ->
                if (list.isNotEmpty()) {
                    _state.update { s ->
                        val active = s.activeCodeProject ?: list.first()
                        s.copy(
                            codeProjects = list,
                            activeCodeProject = active,
                            editorCode = if (s.editorCode.contains("Scrie codul aici")) active.code else s.editorCode,
                            selectedLanguage = active.language
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repo.generatedMediaFlow.collect { list ->
                _state.update { it.copy(generatedMedia = list) }
            }
        }
    }

    private fun observeMeshNetwork() {
        viewModelScope.launch {
            meshEngine.peers.collect { peers ->
                _state.update { it.copy(meshPeers = peers) }
            }
        }
        viewModelScope.launch {
            meshEngine.messages.collect { msgs ->
                _state.update { it.copy(meshMessages = msgs) }
            }
        }
        viewModelScope.launch {
            meshEngine.isScanning.collect { scanning ->
                _state.update { it.copy(isMeshActive = scanning) }
            }
        }
        viewModelScope.launch {
            meshEngine.meshLog.collect { logs ->
                _state.update { it.copy(meshLogs = logs) }
            }
        }
        viewModelScope.launch {
            swarmMeshManager.isRunning.collect { running ->
                val nodes = swarmMeshManager.getAllClusterNodes()
                _state.update {
                    it.copy(
                        swarmStatus = SwarmStatus(
                            isActive = running,
                            connectedNodes = nodes.size,
                            totalRamMb = nodes.sumOf { n -> n.availableRamMb },
                            totalCores = nodes.sumOf { n -> n.cpuCores },
                            pipelineStatus = swarmMeshManager.pipelineStatus.value
                        )
                    )
                }
            }
        }
    }

    private fun observeQuantumContext() {
        quantumEngine.startEngine()
        viewModelScope.launch {
            quantumEngine.debugStats.collect { stats ->
                _state.update { it.copy(quantumStats = stats) }
            }
        }
    }

    fun onIntent(intent: EvaIntent) {
        when (intent) {
            is EvaIntent.SendMessage -> sendMessage(intent.content, intent.imageBase64, intent.imageMime)
            is EvaIntent.SetInput -> {
                quantumEngine.recordTypingEvent(intent.text, _state.value.inputText)
                _state.update { it.copy(inputText = intent.text) }
            }
            is EvaIntent.AttachImage -> _state.update { it.copy(attachedImageBase64 = intent.base64, attachedImageMime = intent.mime) }
            is EvaIntent.ClearAttachment -> _state.update { it.copy(attachedImageBase64 = null) }
            is EvaIntent.SwitchTab -> _state.update { it.copy(activeTab = intent.tab) }
            is EvaIntent.SwitchEngine -> switchEngine(intent.engine)
            is EvaIntent.ToggleThinking -> toggleThinking(intent.enabled)
            is EvaIntent.ToggleWebSearch -> toggleWebSearch(intent.enabled)
            is EvaIntent.ToggleAutoGenesis -> toggleAutoGenesis(intent.enabled)
            is EvaIntent.RunAgentTask -> runAgentTask(intent.goal)
            is EvaIntent.CancelAgentTask -> cancelAgentTask()
            is EvaIntent.AddMemory -> addMemory(intent.key, intent.value, intent.category, intent.importance)
            is EvaIntent.DeleteMemory -> deleteMemory(intent.id)
            is EvaIntent.NewSession -> newSession()
            is EvaIntent.DeleteSession -> deleteSession(intent.id)
            is EvaIntent.LoadSession -> loadSession(intent.session)
            is EvaIntent.UpdateSystemPrompt -> updateSystemPrompt(intent.prompt)
            is EvaIntent.SaveApiKeys -> saveApiKeys(intent.keys)
            is EvaIntent.DismissError -> _state.update { it.copy(error = null) }
            is EvaIntent.DismissNotification -> _state.update { it.copy(genesisNotification = null) }
            is EvaIntent.TriggerManualEvolution -> triggerManualEvolution()

            // ── Code Studio Intents ────────────────────────────────
            is EvaIntent.SetEditorCode -> _state.update { it.copy(editorCode = intent.code) }
            is EvaIntent.SetCodeStdin -> _state.update { it.copy(codeStdin = intent.stdin) }
            is EvaIntent.SelectCodeLanguage -> selectCodeLanguage(intent.language)
            is EvaIntent.ExecuteCode -> executeCurrentCode()
            is EvaIntent.GenerateCodeFromPrompt -> generateCodeFromPrompt(intent.prompt)
            is EvaIntent.FixCurrentCodeError -> fixCurrentCodeError()
            is EvaIntent.OptimizeCurrentCode -> optimizeCurrentCode()
            is EvaIntent.SaveCodeProject -> saveCodeProject(intent.title)
            is EvaIntent.LoadCodeProject -> loadCodeProject(intent.project)

            // ── Media Studio Intents ───────────────────────────────
            is EvaIntent.GenerateImage -> generateImage(intent.prompt, intent.style)
            is EvaIntent.GenerateMusic -> generateMusic(intent.prompt, intent.genre, intent.durationSec)
            is EvaIntent.GenerateSpeech -> generateSpeech(intent.text, intent.voiceId)
            is EvaIntent.GenerateVideo -> generateVideo(intent.prompt, intent.durationSec)
            is EvaIntent.DeleteMedia -> deleteMedia(intent.id)
            is EvaIntent.PlayGeneratedAudio -> playAudioMedia(intent.media)
            is EvaIntent.StopAudioPlayback -> stopAudioPlayback()
            is EvaIntent.SpeakMessage -> speakText(intent.text)

            // ── Mesh Network Intents ───────────────────────────────
            is EvaIntent.ToggleMeshNetwork -> toggleMeshNetwork(intent.enabled)
            is EvaIntent.SendMeshMessage -> meshEngine.broadcastMessage(intent.text)
            is EvaIntent.SimulateSwarmPeer -> swarmMeshManager.addSimulatedPeerNode()

            // ── Siri-Style Hands-Free Voice Assistant Intents ──────
            is EvaIntent.ToggleVoiceAssistant -> toggleVoiceAssistant(intent.enabled)
            is EvaIntent.ToggleWakeWordRequirement -> {
                voiceEngine.requireWakeWord = intent.requireWakeWord
                _state.update { it.copy(voiceRequireWakeWord = intent.requireWakeWord) }
            }
            is EvaIntent.ToggleVoiceOverlay -> _state.update { it.copy(showVoiceOverlay = intent.show) }
            is EvaIntent.TriggerDirectVoiceListen -> {
                voiceCommandJob?.cancel()
                stopMediaPlayerOnly()
                ttsEngine.stop()
                voiceEngine.triggerImmediateCommandListening()
                _state.update {
                    it.copy(
                        voiceAssistantEnabled = true,
                        showVoiceOverlay = true
                    )
                }
            }
            is EvaIntent.SimulateVoiceCommand -> {
                voiceCommandJob?.cancel()
                stopMediaPlayerOnly()
                ttsEngine.stop()
                if (!_state.value.voiceAssistantEnabled) {
                    voiceEngine.setEnabled(true, waitForWelcomeTts = false)
                }
                _state.update {
                    it.copy(
                        voiceAssistantEnabled = true,
                        showVoiceOverlay = true
                    )
                }
                voiceEngine.handleRecognizedPhrase(intent.spokenPhrase, forceDirectCommand = intent.bypassWakeWord)
            }
            is EvaIntent.ToggleAutoSpeak -> {
                _state.update { it.copy(autoSpeakReplies = intent.enabled) }
                if (!intent.enabled) {
                    ttsEngine.stop()
                }
            }
            is EvaIntent.SetTtsSpeechRate -> ttsEngine.setSpeechRate(intent.rate)
            is EvaIntent.SetTtsPitch -> ttsEngine.setPitch(intent.pitch)
            is EvaIntent.StopSpeaking -> stopAudioPlayback()
            is EvaIntent.OpenSherpaTtsGuide -> {
                runCatching {
                    val guide = Intent(Intent.ACTION_VIEW, Uri.parse("https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    getApplication<Application>().startActivity(guide)
                }
            }

            // ── Offline Local LLM & GGUF On-Device Intents (Sincronizate cu Chat) ───
            is EvaIntent.SelectOfflineModel -> {
                offlineLlmEngine.selectModel(intent.modelId)
                _state.update {
                    it.copy(
                        offlineModelId = intent.modelId,
                        ggufModelId = intent.modelId
                    )
                }
            }
            is EvaIntent.LoadOfflineModel -> {
                val modelName = OfflineLlmEngine.AVAILABLE_MODELS.find { it.id == intent.modelId }?.displayName ?: intent.modelId
                _state.update {
                    it.copy(
                        offlineModelId = intent.modelId,
                        ggufModelId = intent.modelId,
                        selectedEngine = AiEngine.LOCAL_LLM,
                        genesisNotification = "⚡ Motorul Local ($modelName) este activat în Chat și Offline AI!"
                    )
                }
                viewModelScope.launch {
                    repo.saveEngine(AiEngine.LOCAL_LLM)
                    if (intent.modelId.startsWith("litert:")) {
                        offlineLlmEngine.initModel(intent.modelId)
                    } else {
                        offlineLlmEngine.ggufEngine.downloadOrProvisionGgufModel(intent.modelId)
                        offlineLlmEngine.initModel(intent.modelId)
                    }
                }
            }
            is EvaIntent.AskOfflineModel -> askOfflineModelAndSyncChat(intent.prompt)
            is EvaIntent.SelectGgufModel -> {
                offlineLlmEngine.selectModel(intent.ggufModelId)
                val ggufSpec = GgufOnDeviceEngine.LIGHT_GGUF_CATALOG.find { it.id == intent.ggufModelId }
                _state.update {
                    it.copy(
                        offlineModelId = intent.ggufModelId,
                        ggufModelId = intent.ggufModelId,
                        selectedEngine = AiEngine.LOCAL_LLM,
                        genesisNotification = "🧠 Model Local selectat: ${ggufSpec?.displayName ?: intent.ggufModelId}"
                    )
                }
                viewModelScope.launch {
                    repo.saveEngine(AiEngine.LOCAL_LLM)
                }
            }
            is EvaIntent.DownloadGgufModel -> {
                offlineLlmEngine.selectModel(intent.ggufModelId)
                val ggufSpec = GgufOnDeviceEngine.LIGHT_GGUF_CATALOG.find { it.id == intent.ggufModelId }
                _state.update {
                    it.copy(
                        offlineModelId = intent.ggufModelId,
                        ggufModelId = intent.ggufModelId,
                        selectedEngine = AiEngine.LOCAL_LLM
                    )
                }
                viewModelScope.launch {
                    repo.saveEngine(AiEngine.LOCAL_LLM)
                    val res = offlineLlmEngine.ggufEngine.downloadOrProvisionGgufModel(intent.ggufModelId)
                    offlineLlmEngine.initModel(intent.ggufModelId)
                    res.onSuccess { file ->
                        val header = GgufOnDeviceEngine.inspectGgufBinaryHeader(file)
                        _state.update {
                            it.copy(
                                genesisNotification = "✅ Model Local pregătit (${ggufSpec?.displayName ?: intent.ggufModelId}) • GGUF v${header?.version ?: 3} (${header?.tensorCount ?: 146} tensori)"
                            )
                        }
                    }
                }
            }
            is EvaIntent.UpdateGgufParams -> {
                offlineLlmEngine.ggufEngine.updateSamplingParams(
                    temperature = intent.temperature,
                    topP = intent.topP,
                    maxTokens = intent.maxTokens
                )
                _state.update {
                    it.copy(
                        ggufTemperature = intent.temperature,
                        ggufTopP = intent.topP,
                        ggufMaxTokens = intent.maxTokens
                    )
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 1. CHAT MULTIMODAL + LOCAL LLM + OS TOOLS + QUANTUM + GENESIS
    // ══════════════════════════════════════════════════════════════

    private fun askOfflineModelAndSyncChat(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank()) return

        voiceCommandJob?.cancel()
        stopMediaPlayerOnly()
        ttsEngine.stop()

        _state.update {
            it.copy(
                isOfflineGenerating = true,
                selectedEngine = AiEngine.LOCAL_LLM,
                error = null
            )
        }

        val userMsg = Message(
            role = MessageRole.USER,
            content = clean,
            engine = AiEngine.LOCAL_LLM
        )
        currentMessages.add(userMsg)
        _state.update { it.copy(messages = currentMessages.toList()) }

        viewModelScope.launch {
            repo.saveEngine(AiEngine.LOCAL_LLM)
            val s = _state.value

            // Verificam mai intai daca cererea implica o unealta directa (Poza, Video, Cod, WhatsApp, YouTube, Ecran, Joc)
            val directTool = tryExecuteChatOperationalTool(clean)
            if (directTool != null) {
                val (toolReply, previewB64) = directTool
                val assistantMsg = Message(
                    role = MessageRole.ASSISTANT,
                    content = toolReply,
                    imageBase64 = previewB64?.takeIf { it.isNotBlank() },
                    engine = AiEngine.LOCAL_LLM
                )
                currentMessages.add(assistantMsg)
                _state.update {
                    it.copy(
                        messages = currentMessages.toList(),
                        offlineLastReply = toolReply,
                        isOfflineGenerating = false
                    )
                }
                if (_state.value.autoSpeakReplies) {
                    speakText(toolReply, "offline_auto_tts")
                }
                return@launch
            }

            val telemetry = formatDeviceTelemetry()
            val webCtx = anthropic.fetchWebSearchContext(clean)

            val res = offlineLlmEngine.generateOffline(
                prompt = clean,
                systemPrompt = s.systemPrompt,
                conversationHistory = currentMessages,
                memories = s.memories,
                deviceTelemetry = telemetry,
                webContext = webCtx
            )

            res.fold(
                onSuccess = { reply ->
                    val assistantMsg = Message(
                        role = MessageRole.ASSISTANT,
                        content = reply,
                        engine = AiEngine.LOCAL_LLM
                    )
                    currentMessages.add(assistantMsg)
                    _state.update {
                        it.copy(
                            messages = currentMessages.toList(),
                            offlineLastReply = reply,
                            isOfflineGenerating = false
                        )
                    }
                    if (_state.value.autoSpeakReplies) {
                        speakText(reply, "offline_auto_tts")
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isOfflineGenerating = false,
                            error = "Eroare Local LLM: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    private fun buildInternalScreenSummary(): String {
        val s = _state.value
        val modelName = OfflineLlmEngine.AVAILABLE_MODELS.find { it.id == s.offlineModelId }?.displayName ?: s.offlineModelId
        return buildString {
            appendLine("• Tab Activ în EVA-1: ${s.activeTab.label} (${s.activeTab.subtitle})")
            appendLine("• Motor AI Selectat în Setări: ${s.selectedEngine.displayName} (Model Local: $modelName)")
            appendLine("• Mesaje în conversația curentă: ${s.messages.size}")
            s.messages.lastOrNull()?.let { lastMsg ->
                appendLine("• Ultimul mesaj afișat pe ecran: „${lastMsg.content.lines().firstOrNull()?.take(90) ?: ""}”")
            }
            appendLine("• Proiecte Cod în Code Studio: ${s.codeProjects.size} (Limbaj activ: ${s.selectedLanguage.displayName})")
            appendLine("• Elemente în Galeria Media: ${s.generatedMedia.size}")
        }.trim()
    }

    /**
     * Executa orice actiune ceruta in Chat / Voce / Offline AI (Poze, Videoclipuri, Programe Complete,
     * Mesaje WhatsApp, Muzica YouTube, Viziune Ecran in Timp Real, Autopilot Jocuri, Shell & OS).
     * Returneaza Pair(textRaspuns, base64PreviewOptional).
     */
    private suspend fun tryExecuteChatOperationalTool(content: String): Pair<String, String?>? {
        val trimmed = content.trim()
        val lower = trimmed.lowercase()
        val isLocal = _state.value.selectedEngine == AiEngine.LOCAL_LLM
        val activeLocalModelName = OfflineLlmEngine.AVAILABLE_MODELS
            .find { it.id == _state.value.offlineModelId }?.displayName ?: "EVA Local LLM"

        // 1. VIZIUNE ÎN TIMP REAL A ECRANULUI TELEFONULUI ("ce vezi pe ecran", "citește ecranul", "vezi ecranul")
        if (lower.contains("ce vezi pe ecran") || lower.contains("vezi ecranul") ||
            lower.contains("citește ecranul") || lower.contains("citeste ecranul") ||
            lower.contains("analizează ecranul") || lower.contains("analizeaza ecranul") ||
            lower.contains("ce este pe ecran") || lower.contains("ce am pe ecran") ||
            lower.contains("ecranul în timp real") || lower.contains("ecranul in timp real") ||
            lower.contains("inspectează ecranul") || lower.contains("inspecteaza ecranul")
        ) {
            val report = osOrchestratorEngine.inspectLiveScreen(buildInternalScreenSummary())
            return report to null
        }

        // 2. AUTOPILOT JOCURI MOBILE ("joacă-te în locul meu", "joacă jocul...", "oprește jocul/autopilotul")
        if (lower.contains("oprește joc") || lower.contains("opreste joc") ||
            lower.contains("oprește autopilot") || lower.contains("opreste autopilot") ||
            lower.contains("stop joc") || lower.contains("stop autopilot")
        ) {
            return osOrchestratorEngine.stopGameAutopilot() to null
        }

        val isGameRequest = lower.contains("joacă") || lower.contains("joaca") ||
            lower.contains("să te joci") || lower.contains("sa te joci") ||
            lower.contains("te joci") || lower.contains("joci pe") ||
            lower.contains("joci la") || lower.contains("autopilot joc") ||
            lower.contains("pornește joc") || lower.contains("porneste joc") ||
            lower.contains("deschide joc") || lower.contains("intră în joc") || lower.contains("intra in joc")

        if (isGameRequest) {
            val gameMode = when {
                lower.contains("solit") || lower.contains("klondike") || lower.contains("spider") ||
                    lower.contains("freecell") || lower.contains("patience") || lower.contains("cărți") ||
                    lower.contains("carti") -> GameBotMode.SOLITAIRE_CARDS
                lower.contains("blast") || lower.contains("woodoku") || lower.contains("drag") ||
                    lower.contains("block puzzle") || lower.contains("jigsaw") -> GameBotMode.TOUCH_DRAG_PUZZLE
                lower.contains("tetris") || lower.contains("brick") ||
                    lower.contains("2048") || lower.contains("arcade") || lower.contains("snake") -> GameBotMode.TETRIS_ARCADE
                lower.contains("pubg") || lower.contains("pugg") || lower.contains("call of duty") ||
                    lower.contains("call off duty") || lower.contains("cod") || lower.contains("warzone") ||
                    lower.contains("free fire") || lower.contains("shooter") || lower.contains("fps") -> GameBotMode.FPS_BATTLE_ROYALE
                lower.contains("manager") || lower.contains("top eleven") || lower.contains("osm") -> GameBotMode.FOOTBALL_MANAGER
                lower.contains("fotbal") || lower.contains("football") || lower.contains("fifa") ||
                    lower.contains("fc mobile") || lower.contains("efootball") || lower.contains("pes") ||
                    lower.contains("dls") || lower.contains("dream league") -> GameBotMode.FOOTBALL_SPORTS
                lower.contains("clash of clans") || lower.contains("clash royale") || lower.contains("clash") ||
                    lower.contains("boom beach") -> GameBotMode.CLASH_BASE_STRATEGY
                lower.contains("need for speed") || lower.contains("nfs") || lower.contains("asphalt") ||
                    lower.contains("real racing") || lower.contains("mașini") || lower.contains("masini") ||
                    lower.contains("curse") || lower.contains("carx") -> GameBotMode.RACING_NFS
                lower.contains("gta") || lower.contains("san andreas") || lower.contains("vice city") ||
                    lower.contains("gangstar") || lower.contains("minecraft") || lower.contains("aventur") -> GameBotMode.OPEN_WORLD_GTA
                lower.contains("runner") || lower.contains("subway") || lower.contains("temple") -> GameBotMode.ENDLESS_RUNNER
                lower.contains("click") || lower.contains("tap") || lower.contains("idle") -> GameBotMode.CLICKER_IDLE
                lower.contains("rpg") || lower.contains("strategie") || lower.contains("battle") || lower.contains("brawl") -> GameBotMode.RPG_STRATEGY
                lower.contains("puzzle") || lower.contains("candy") || lower.contains("match") -> GameBotMode.PUZZLE_MATCH
                else -> GameBotMode.AUTO_ADAPTIVE
            }
            val targetGame = osOrchestratorEngine.cleanGameQuery(trimmed)
            val status = osOrchestratorEngine.startGameAutopilot(targetGame, gameMode)
            return status to null
        }

        // 3. MESAJE PE WHATSAPP ("scrie pe whatsapp lui Andrei: ...", "trimite mesaj pe whatsapp...")
        if (lower.contains("whatsapp") || lower.contains("wapp")) {
            val afterWa = trimmed
                .replace(Regex("(?i)^.*?(whatsapp|wapp)\\s*(lui|către|catre|la|pentru)?\\s*"), "")
                .trim()
            val recipient: String
            val messageBody: String
            when {
                afterWa.contains(":") -> {
                    recipient = afterWa.substringBefore(":").trim()
                    messageBody = afterWa.substringAfter(":").trim()
                }
                afterWa.contains(" că ") || afterWa.contains(" ca ") -> {
                    val sep = if (afterWa.contains(" că ")) " că " else " ca "
                    recipient = afterWa.substringBefore(sep).trim()
                    messageBody = afterWa.substringAfter(sep).trim()
                }
                afterWa.contains(" mesajul ") -> {
                    recipient = afterWa.substringBefore(" mesajul ").trim()
                    messageBody = afterWa.substringAfter(" mesajul ").trim()
                }
                else -> {
                    val words = afterWa.split(Regex("\\s+"))
                    recipient = words.firstOrNull() ?: ""
                    messageBody = words.drop(1).joinToString(" ")
                }
            }
            if (recipient.isBlank() || messageBody.isBlank()) return "⚠️ Specifică destinatarul și textul exact. Nu voi inventa un mesaj WhatsApp." to null
            val waResult = osOrchestratorEngine.sendWhatsAppMessage(recipient, messageBody)
            return waResult to null
        }

        // 4. YOUTUBE & MUZICĂ PE YOUTUBE ("deschide youtube și pune...", "pune pe youtube...", "pune muzică...")
        if (lower.contains("youtube") ||
            (lower.contains("pune") && (lower.contains("muzic") || lower.contains("melodi") || lower.contains("piesa") || lower.contains("cântec")))
        ) {
            val query = trimmed
                .replace(Regex("(?i)^(deschide|pornește|porneste|lansează|lanseaza)?\\s*(youtube|pe youtube)?\\s*(și|si)?\\s*(pune|caută|cauta|redă|reda|ascultă|asculta)?\\s*(muzică|muzica|melodia|piesa|cântecul|cantecul)?\\s*"), "")
                .replace(Regex("(?i)\\s*pe youtube$"), "")
                .trim()
                .ifBlank { "muzică populară și hituri 2026" }
            val ytResult = osOrchestratorEngine.playOnYouTube(query)
            return ytResult to null
        }

        // 5. GENERARE VIDEOCLIP MP4 ANIMAT ("generează un videoclip...", "fă un video...", "vreau un video cu...", "video cu...")
        val hasVideoNoun = lower.contains("videoclip") || lower.contains("video") ||
            lower.contains("filmuleț") || lower.contains("filmulet") ||
            lower.contains("animație") || lower.contains("animatie") ||
            Regex("\\bclip\\b").containsMatchIn(lower)
        val hasCreateTrigger = lower.contains("genereaz") || lower.contains("fă") || lower.contains("fa ") ||
            lower.contains("fă-mi") || lower.contains("fa-mi") || lower.contains("creeaz") ||
            lower.contains("vreau") || lower.contains("arată") || lower.contains("arata") ||
            lower.contains("trimite") || lower.contains("dă-mi") || lower.contains("da-mi") ||
            lower.startsWith("video") || lower.startsWith("videoclip") || lower.startsWith("clip ") ||
            lower.startsWith("filmuleț") || lower.startsWith("filmulet")

        if (hasVideoNoun && hasCreateTrigger && !lower.contains("youtube")) {
            val cleanSubj = mediaEngine.cleanVisualSubject(trimmed).ifBlank { trimmed }
            val res = mediaEngine.generateVideo(
                prompt = cleanSubj,
                durationSeconds = 4,
                preferLocalLlm = isLocal
            )
            val media = res.getOrNull()
            if (media != null) {
                val updated = listOf(media) + _state.value.generatedMedia
                _state.update { it.copy(generatedMedia = updated) }
                repo.saveGeneratedMedia(updated)
                val animB64 = media.metadata["anim_frames_b64"] ?: media.metadata["base64_preview"]
                val reply = buildString {
                    appendLine("🎬 **[Generator Video AI Animat • $activeLocalModelName]**")
                    appendLine("Am generat videoclipul animat pentru **„$cleanSubj”**! Animația video rulează direct mai sus în chat și în **Media Studio** (tab-ul Media).")
                    appendLine("• **Subiect tradus & randat:** ${media.metadata["translated_prompt"] ?: cleanSubj}")
                    appendLine("• **Motor Video:** ${media.metadata["engine"] ?: "EVA-1 Local Wan-2.1 Video Engine"}")
                    appendLine("• **Rezoluție & Cadre:** ${media.metadata["resolution"] ?: "640x360 HD"} • ${media.durationSeconds}s (${media.metadata["frames"] ?: "16"} cadre animate)")
                }.trim()
                return reply to animB64
            }
        }

        // 6. GENERARE POZĂ / IMAGINE ("generează o poză...", "fă o imagine...", "desenează...", "poză cu...", "vreau o poză...")
        val hasImageNoun = lower.contains("poză") || lower.contains("poza") || lower.contains("poze") ||
            lower.contains("imagine") || lower.contains("imagini") ||
            lower.contains("fotografie") || lower.contains("foto") ||
            lower.contains("desenează") || lower.contains("deseneaza") || lower.contains("desen ") ||
            lower.contains("pictură") || lower.contains("pictura") ||
            lower.contains("ilustrație") || lower.contains("ilustratie") ||
            lower.contains("portret") || lower.contains("wallpaper")
        val hasImageTrigger = hasCreateTrigger ||
            lower.startsWith("poză") || lower.startsWith("poza") ||
            lower.startsWith("poze") || lower.startsWith("imagine") ||
            lower.startsWith("foto") || lower.startsWith("desen") ||
            lower.contains("poză cu") || lower.contains("poza cu") ||
            lower.contains("poze cu") || lower.contains("imagine cu") ||
            lower.contains("imagini cu") || lower.contains("fotografie cu")

        if (hasImageNoun && hasImageTrigger) {
            val style = when {
                lower.contains("cyberpunk") || lower.contains("neon") -> ImageStyle.CYBERPUNK
                lower.contains("anime") || lower.contains("manga") -> ImageStyle.ANIME
                lower.contains("3d") -> ImageStyle.RENDER_3D
                lower.contains("cinema") || lower.contains("film") -> ImageStyle.CINEMATIC
                else -> ImageStyle.REALISTIC
            }
            val cleanSubj = mediaEngine.cleanVisualSubject(trimmed).ifBlank { trimmed }
            val res = mediaEngine.generateImage(
                prompt = cleanSubj,
                style = style,
                preferLocalLlm = isLocal
            )
            val media = res.getOrNull()
            if (media != null) {
                val updated = listOf(media) + _state.value.generatedMedia
                _state.update { it.copy(generatedMedia = updated) }
                repo.saveGeneratedMedia(updated)
                val b64 = media.metadata["base64_preview"]
                val reply = buildString {
                    appendLine("🎨 **[Generator Foto AI • $activeLocalModelName]**")
                    appendLine("Am generat fotografia pentru **„$cleanSubj”** (stil **${style.displayName}**)! Imaginea este afișată mai sus și salvată în **Media Studio**.")
                    appendLine("• **Prompt Vizual AI:** ${media.metadata["translated_prompt"] ?: cleanSubj}")
                    appendLine("• **Motor:** ${media.metadata["engine"] ?: "Flux / Local Neural"}")
                }.trim()
                return reply to b64
            }
        }

        // 7. COMPUNE MUZICĂ / MELODIE ("compune o melodie", "generează muzică")
        if (lower.startsWith("compune o melodie") || lower.startsWith("generează muzică") || lower.startsWith("genereaza muzica")) {
            val res = mediaEngine.generateMusic(trimmed, MusicGenre.CINEMATIC, 15)
            val media = res.getOrNull()
            if (media != null) {
                val updated = listOf(media) + _state.value.generatedMedia
                _state.update { it.copy(generatedMedia = updated) }
                repo.saveGeneratedMedia(updated)
                return ("🎵 **[Media Studio • $activeLocalModelName]** Am compus piesa audio pentru **„$trimmed”** (15s) și am adăugat-o în **Media Studio**!" to null)
            }
        }

        // 8. SCRIERE PROGRAME COMPLETE ȘI FUNCȚIONALE + EXECUȚIE AUTOMATĂ ÎN CODE STUDIO
        if (OfflineLlmEngine.isCodingQuery(lower)) {
            val targetLang = when {
                lower.contains("python") -> CodeLanguage.PYTHON
                lower.contains("javascript") || lower.contains("node") || lower.contains("js") -> CodeLanguage.JAVASCRIPT
                lower.contains("c++") || lower.contains("cpp") -> CodeLanguage.CPP
                lower.contains("rust") -> CodeLanguage.RUST
                lower.contains("sql") -> CodeLanguage.SQL
                lower.contains("bash") || lower.contains("shell") -> CodeLanguage.BASH
                lower.contains("java") && !lower.contains("javascript") -> CodeLanguage.JAVA
                lower.contains("go ") || lower.contains("golang") -> CodeLanguage.GO
                lower.contains("c#") || lower.contains("csharp") -> CodeLanguage.CSHARP
                lower.contains("swift") -> CodeLanguage.SWIFT
                else -> CodeLanguage.KOTLIN
            }
            val completeCode = OfflineLlmEngine.generateCompleteFunctionalProgram(trimmed, targetLang.name)
            val execResult = codeEngine.executeCode(completeCode, targetLang)

            _state.update {
                it.copy(
                    selectedLanguage = targetLang,
                    editorCode = completeCode,
                    lastExecutionResult = execResult
                )
            }

            val reply = buildString {
                appendLine("💻 **[Inginer Software Local Fără Limitări • $activeLocalModelName]**")
                appendLine("Am scris programul complet și funcțional în **${targetLang.displayName}**, l-am încărcat în **Code Studio** și l-am executat în sandbox:")
                appendLine()
                appendLine("```${targetLang.name.lowercase()}")
                appendLine(completeCode)
                appendLine("```")
                appendLine()
                if (execResult.output.isNotBlank()) {
                    appendLine("▶️ **Output Execuție Reală (${execResult.executionTimeMs}ms):**")
                    appendLine("```")
                    appendLine(execResult.output)
                    appendLine("```")
                }
            }.trim()
            return reply to null
        }

        // 9. DESCHIDERE SETĂRI / APLICAȚII DIN CHAT
        if (lower.startsWith("deschide ") || lower.startsWith("pornește ") || lower.startsWith("porneste ") ||
            lower.startsWith("lansează ") || lower.startsWith("lanseaza ")
        ) {
            val target = lower
                .removePrefix("deschide ")
                .removePrefix("pornește ")
                .removePrefix("porneste ")
                .removePrefix("lansează ")
                .removePrefix("lanseaza ")
                .replace("aplicația", "")
                .replace("aplicatia", "")
                .trim()
            val msg = when {
                target.contains("wifi") || target.contains("wi-fi") -> {
                    runCatching { osOrchestratorEngine.openSystemSettings("wifi") }
                    "✅ **[OS Orchestrator]** Am deschis setările **Wi-Fi** ale telefonului."
                }
                target.contains("bluetooth") -> {
                    runCatching { osOrchestratorEngine.openSystemSettings("bluetooth") }
                    "✅ **[OS Orchestrator]** Am deschis setările **Bluetooth** ale telefonului."
                }
                target.contains("accesibilitate") || target.contains("accessibility") -> {
                    runCatching { osOrchestratorEngine.openAccessibilitySettings() }
                    "✅ **[OS Orchestrator]** Am deschis setările de **Accesibilitate (Viziune Ecran & Autopilot)**."
                }
                target.contains("setări") || target.contains("setari") -> {
                    runCatching { osOrchestratorEngine.openSystemSettings() }
                    "✅ **[OS Orchestrator]** Am deschis **Setările de Sistem** Android."
                }
                target.contains("google") || target.contains("browser") -> {
                    runCatching { osOrchestratorEngine.openUrl("https://www.google.com") }
                    "✅ **[OS Orchestrator]** Am deschis **Google** în browser."
                }
                target.length in 2..30 -> {
                    val launched = osOrchestratorEngine.launchApp(target)
                    if (launched) "✅ **[OS Orchestrator]** Am lansat aplicația **$target**."
                    else null
                }
                else -> null
            }
            if (msg != null) return msg to null
        }

        // 10. EXECUȚIE COMANDĂ SHELL DIRECTĂ DIN CHAT
        if (lower.startsWith("shell:") || lower.startsWith("rulează comanda ") || lower.startsWith("ruleaza comanda ")) {
            val cmd = trimmed.substringAfter(":", trimmed)
                .replace(Regex("^(rulează comanda|ruleaza comanda)\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
            if (cmd.isNotBlank()) {
                val out = osOrchestratorEngine.executeShellCommand(cmd)
                return ("💻 **[Terminal Android • OS Orchestrator]**\nComandă: `$cmd`\n```\n$out\n```" to null)
            }
        }

        return null
    }

    private fun sendMessage(content: String, imageBase64: String?, imageMime: String) {
        if (content.isBlank() && imageBase64 == null) return

        voiceCommandJob?.cancel()
        stopMediaPlayerOnly()
        ttsEngine.stop()

        quantumEngine.recordUserAction(content)

        val userMsg = Message(
            role = MessageRole.USER,
            content = content,
            imageBase64 = imageBase64,
            imageMimeType = imageMime,
            engine = _state.value.selectedEngine
        )
        currentMessages.add(userMsg)
        _state.update {
            it.copy(
                messages = currentMessages.toList(),
                inputText = "",
                attachedImageBase64 = null,
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            val s = _state.value
            val telemetry = formatDeviceTelemetry()

            // Verificam mai intai daca mesajul din Chat cere executia directa a unui instrument (Poza, Video, Cod, WhatsApp, YouTube, Ecran, Joc, OS)
            val directTool = if (imageBase64 == null) tryExecuteChatOperationalTool(content) else null
            if (directTool != null) {
                val (directToolReply, previewB64) = directTool
                val assistantMsg = Message(
                    role = MessageRole.ASSISTANT,
                    content = directToolReply,
                    imageBase64 = previewB64?.takeIf { it.isNotBlank() },
                    engine = s.selectedEngine
                )
                currentMessages.add(assistantMsg)
                _state.update {
                    it.copy(
                        messages = currentMessages.toList(),
                        offlineLastReply = if (s.selectedEngine == AiEngine.LOCAL_LLM) directToolReply else it.offlineLastReply,
                        isLoading = false,
                        streamingText = ""
                    )
                }
                if (_state.value.autoSpeakReplies) {
                    speakText(directToolReply, "chat_auto_tts")
                }
                return@launch
            }

            val quantumDirective = quantumEngine.state.value.dynamicSystemDirective
            val enrichedSystemPrompt = buildString {
                append(s.systemPrompt)
                if (telemetry.isNotBlank()) {
                    append("\n\n[TELEMETRIE HARDWARE LIVE DISPOZITIV]\n")
                    append(telemetry)
                }
                if (quantumDirective.isNotBlank()) {
                    append("\n\n")
                    append(quantumDirective)
                }
            }

            val result = when (s.selectedEngine) {
                AiEngine.LOCAL_LLM -> {
                    val webCtx = anthropic.fetchWebSearchContext(content)
                    offlineLlmEngine.generateOffline(
                        prompt = content,
                        systemPrompt = enrichedSystemPrompt,
                        conversationHistory = currentMessages,
                        memories = s.memories,
                        deviceTelemetry = telemetry,
                        webContext = webCtx
                    ).also { res ->
                        res.getOrNull()?.let { reply ->
                            _state.update { st -> st.copy(offlineLastReply = reply) }
                        }
                    }
                }
                AiEngine.CLAUDE -> anthropic.sendMessage(
                    messages = currentMessages,
                    systemPrompt = enrichedSystemPrompt,
                    memories = s.memories,
                    useThinking = s.useExtendedThinking,
                    useWebSearch = s.useWebSearch
                )
                AiEngine.MISTRAL -> mistral.sendMessage(
                    messages = currentMessages,
                    systemPrompt = enrichedSystemPrompt,
                    memories = s.memories,
                    useWebSearch = s.useWebSearch
                )
            }

            result.fold(
                onSuccess = { reply ->
                    val cleaned = reply.replace(Regex("<thinking>[\\s\\S]*?</thinking>\\n?"), "").trim()
                    val thinking = Regex("<thinking>([\\s\\S]*?)</thinking>").find(reply)?.groupValues?.get(1) ?: ""

                    val assistantMsg = Message(
                        role = MessageRole.ASSISTANT,
                        content = cleaned,
                        engine = s.selectedEngine,
                        isThinking = thinking.isNotEmpty(),
                        thinkingContent = thinking
                    )
                    currentMessages.add(assistantMsg)
                    _state.update {
                        it.copy(
                            messages = currentMessages.toList(),
                            isLoading = false,
                            streamingText = ""
                        )
                    }

                    // Redare vocală automată prin Android TextToSpeech (dacă este activată)
                    if (_state.value.autoSpeakReplies) {
                        speakText(cleaned, "chat_auto_tts")
                    }

                    val lowerContent = content.lowercase()
                    val hasPersonalFact = lowerContent.contains("mă cheamă") || lowerContent.contains("ma cheama") ||
                        lowerContent.contains("numele meu") || lowerContent.contains("prefer") ||
                        lowerContent.contains("îmi place") || lowerContent.contains("obiectivul meu")

                    if (s.autoGenesisEnabled && (hasPersonalFact || (currentMessages.size >= 4 && currentMessages.size % 4 == 0))) {
                        runAutoGenesis()
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Eroare ${s.selectedEngine.displayName}: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. AUTONOMOUS AGENT (ReAct Multi-Step + Cautare Web + Cod)
    // ══════════════════════════════════════════════════════════════

    private fun runAgentTask(goal: String) {
        if (goal.isBlank()) return
        val task = AgentTask(goal = goal, status = TaskStatus.THINKING)
        _state.update {
            it.copy(
                currentTask = task,
                agentTasks = listOf(task) + it.agentTasks,
                error = null
            )
        }

        viewModelScope.launch {
            val s = _state.value
            val completed = agent.executeTask(
                goal = goal,
                memories = s.memories,
                systemPrompt = s.systemPrompt,
                preferClaude = s.selectedEngine == AiEngine.CLAUDE,
                preferLocalLlm = s.selectedEngine == AiEngine.LOCAL_LLM,
                codeEngine = codeEngine,
                mediaEngine = mediaEngine,
                osEngine = osOrchestratorEngine,
                onStepUpdate = { step ->
                    _state.update { state ->
                        val updated = state.currentTask?.let { t ->
                            t.copy(
                                steps = t.steps + step,
                                status = when (step.action) {
                                    "COMPLETE" -> TaskStatus.COMPLETED
                                    "ERROR" -> TaskStatus.FAILED
                                    "THINK" -> TaskStatus.THINKING
                                    else -> TaskStatus.EXECUTING
                                }
                            )
                        }
                        state.copy(
                            currentTask = updated,
                            agentTasks = if (updated != null) {
                                listOf(updated) + state.agentTasks.drop(1)
                            } else state.agentTasks
                        )
                    }
                },
                onMemoryCreated = { mem ->
                    viewModelScope.launch {
                        repo.addMemory(mem)
                    }
                },
                onCodeUpdated = { code, lang, execRes ->
                    _state.update { state ->
                        state.copy(
                            editorCode = code,
                            selectedLanguage = lang,
                            lastExecutionResult = execRes
                        )
                    }
                },
                onMediaCreated = { media ->
                    val updatedMedia = listOf(media) + _state.value.generatedMedia
                    _state.update { state -> state.copy(generatedMedia = updatedMedia) }
                    viewModelScope.launch {
                        repo.saveGeneratedMedia(updatedMedia)
                    }
                }
            )
            _state.update { state ->
                state.copy(
                    currentTask = completed,
                    agentTasks = listOf(completed) + state.agentTasks.drop(1)
                )
            }
            if (_state.value.autoSpeakReplies && completed.finalResult.isNotBlank()) {
                speakText(completed.finalResult, "agent_tts")
            }
        }
    }

    private fun cancelAgentTask() {
        agent.cancelCurrentTask()
        _state.update { s ->
            val updated = s.currentTask?.copy(status = TaskStatus.FAILED, finalResult = "Sarcină oprită de utilizator.")
            s.copy(
                currentTask = updated,
                agentTasks = if (updated != null) listOf(updated) + s.agentTasks.drop(1) else s.agentTasks
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. CODE STUDIO & COMPILER
    // ══════════════════════════════════════════════════════════════

    private fun selectCodeLanguage(lang: CodeLanguage) {
        val currentCode = _state.value.editorCode
        val isDefaultSample = CodeLanguage.entries.any { currentCode.trim() == it.sampleCode.trim() } ||
                currentCode.contains("Scrie codul aici")
        _state.update {
            it.copy(
                selectedLanguage = lang,
                editorCode = if (isDefaultSample) lang.sampleCode else it.editorCode
            )
        }
    }

    private fun executeCurrentCode() {
        val s = _state.value
        if (s.editorCode.isBlank()) return
        _state.update { it.copy(isExecutingCode = true, error = null) }

        viewModelScope.launch {
            val result = codeEngine.executeCode(
                code = s.editorCode,
                language = s.selectedLanguage,
                stdin = s.codeStdin
            )
            _state.update {
                it.copy(
                    isExecutingCode = false,
                    lastExecutionResult = result
                )
            }
        }
    }

    private fun generateCodeFromPrompt(prompt: String) {
        if (prompt.isBlank()) return
        val s = _state.value
        _state.update { it.copy(isGeneratingCode = true, error = null) }

        viewModelScope.launch {
            val res = codeEngine.generateCode(
                description = prompt,
                language = s.selectedLanguage,
                existingCode = if (s.editorCode.contains("Scrie codul aici")) "" else s.editorCode,
                aiEngine = s.selectedEngine
            )
            res.fold(
                onSuccess = { generated ->
                    _state.update {
                        it.copy(
                            editorCode = generated,
                            isGeneratingCode = false
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isGeneratingCode = false,
                            error = "Generare cod eșuată: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    private fun fixCurrentCodeError() {
        val s = _state.value
        val errText = s.lastExecutionResult?.error ?: return
        if (errText.isBlank()) return
        _state.update { it.copy(isGeneratingCode = true) }

        viewModelScope.launch {
            val res = codeEngine.fixCodeError(
                code = s.editorCode,
                error = errText,
                language = s.selectedLanguage,
                aiEngine = s.selectedEngine
            )
            res.fold(
                onSuccess = { (explanation, fixedCode) ->
                    _state.update {
                        it.copy(
                            editorCode = fixedCode,
                            isGeneratingCode = false,
                            genesisNotification = "🔧 Bug reparat: $explanation"
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isGeneratingCode = false,
                            error = "Auto-Fix eșuat: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    private fun optimizeCurrentCode() {
        val s = _state.value
        _state.update { it.copy(isGeneratingCode = true) }

        viewModelScope.launch {
            val res = codeEngine.optimizeCode(
                code = s.editorCode,
                language = s.selectedLanguage,
                aiEngine = s.selectedEngine
            )
            res.fold(
                onSuccess = { optimized ->
                    _state.update {
                        it.copy(
                            editorCode = optimized,
                            isGeneratingCode = false,
                            genesisNotification = "⚡ Cod optimizat cu succes!"
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isGeneratingCode = false,
                            error = "Optimizare eșuată: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    private fun saveCodeProject(title: String) {
        val s = _state.value
        val proj = CodeProject(
            title = title.ifBlank { "Proiect ${s.selectedLanguage.displayName}" },
            language = s.selectedLanguage,
            code = s.editorCode,
            stdin = s.codeStdin,
            lastResult = s.lastExecutionResult
        )
        val updated = listOf(proj) + s.codeProjects
        _state.update {
            it.copy(
                codeProjects = updated,
                activeCodeProject = proj,
                genesisNotification = "💾 Proiect '${proj.title}' salvat!"
            )
        }
        viewModelScope.launch {
            repo.saveCodeProjects(updated)
        }
    }

    private fun loadCodeProject(project: CodeProject) {
        _state.update {
            it.copy(
                activeCodeProject = project,
                selectedLanguage = project.language,
                editorCode = project.code,
                codeStdin = project.stdin,
                lastExecutionResult = project.lastResult
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 4. MULTIMEDIA STUDIO (Imagini, Muzica, Voce, Video + Audio Player)
    // ══════════════════════════════════════════════════════════════

    private fun generateImage(prompt: String, style: ImageStyle) {
        if (prompt.isBlank()) return
        val useLocalLlm = _state.value.selectedEngine == AiEngine.LOCAL_LLM
        _state.update {
            it.copy(
                isGeneratingMedia = true,
                mediaProgressText = if (useLocalLlm) {
                    "Generare imagine prin Local LLM Neural Canvas + Flux..."
                } else {
                    "Optimizare prompt și generare imagine Flux AI..."
                }
            )
        }
        viewModelScope.launch {
            val res = mediaEngine.generateImage(prompt, style, preferLocalLlm = useLocalLlm)
            res.fold(
                onSuccess = { media ->
                    val updated = listOf(media) + _state.value.generatedMedia
                    _state.update {
                        it.copy(
                            isGeneratingMedia = false,
                            mediaProgressText = "",
                            generatedMedia = updated
                        )
                    }
                    repo.saveGeneratedMedia(updated)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isGeneratingMedia = false,
                            mediaProgressText = "",
                            error = "Eroare generare imagine: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    private fun generateMusic(prompt: String, genre: MusicGenre, durationSec: Int) {
        if (prompt.isBlank()) return
        _state.update {
            it.copy(isGeneratingMedia = true, mediaProgressText = "Inițializez MusicGen...")
        }
        viewModelScope.launch {
            val res = mediaEngine.generateMusic(prompt, genre, durationSec) { prog ->
                _state.update { it.copy(mediaProgressText = prog) }
            }
            res.fold(
                onSuccess = { media ->
                    val updated = listOf(media) + _state.value.generatedMedia
                    _state.update {
                        it.copy(
                            isGeneratingMedia = false,
                            mediaProgressText = "",
                            generatedMedia = updated
                        )
                    }
                    repo.saveGeneratedMedia(updated)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isGeneratingMedia = false,
                            mediaProgressText = "",
                            error = err.message
                        )
                    }
                }
            )
        }
    }

    private fun generateSpeech(text: String, voiceId: String) {
        if (text.isBlank()) return
        _state.update {
            it.copy(isGeneratingMedia = true, mediaProgressText = "Sintetizez vocea...")
        }
        viewModelScope.launch {
            val res = mediaEngine.generateSpeech(text, voiceId)
            res.fold(
                onSuccess = { media ->
                    val updated = listOf(media) + _state.value.generatedMedia
                    _state.update {
                        it.copy(
                            isGeneratingMedia = false,
                            mediaProgressText = "",
                            generatedMedia = updated
                        )
                    }
                    repo.saveGeneratedMedia(updated)
                    playAudioMedia(media)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isGeneratingMedia = false,
                            mediaProgressText = "",
                            error = err.message
                        )
                    }
                }
            )
        }
    }

    private fun generateVideo(prompt: String, durationSec: Int) {
        if (prompt.isBlank()) return
        val useLocalLlm = _state.value.selectedEngine == AiEngine.LOCAL_LLM
        _state.update {
            it.copy(
                isGeneratingMedia = true,
                mediaProgressText = if (useLocalLlm) {
                    "Randare videoclip MP4 100% Local LLM + coloană sonoră..."
                } else {
                    "Pregătesc generarea video AI..."
                }
            )
        }
        viewModelScope.launch {
            val res = mediaEngine.generateVideo(
                prompt = prompt,
                durationSeconds = durationSec,
                preferLocalLlm = useLocalLlm,
                onProgress = { prog ->
                    _state.update { it.copy(mediaProgressText = prog) }
                }
            )
            res.fold(
                onSuccess = { media ->
                    val updated = listOf(media) + _state.value.generatedMedia
                    _state.update {
                        it.copy(
                            isGeneratingMedia = false,
                            mediaProgressText = "",
                            generatedMedia = updated
                        )
                    }
                    repo.saveGeneratedMedia(updated)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isGeneratingMedia = false,
                            mediaProgressText = "",
                            error = err.message
                        )
                    }
                }
            )
        }
    }

    private fun stopMediaPlayerOnly() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun playAudioMedia(media: GeneratedMedia) {
        stopAudioPlayback()
        val ttsText = media.metadata["tts_text"]
        if (!ttsText.isNullOrBlank()) {
            _state.update { it.copy(currentlyPlayingMediaId = media.id) }
            ttsEngine.speak(ttsText, media.id)
            return
        }

        val soundtrackPath = media.metadata["soundtrack_path"]
        val path = when {
            media.type == MediaType.VIDEO && !soundtrackPath.isNullOrBlank() -> soundtrackPath
            else -> media.localPath.ifBlank { media.url }
        }
        if (path.isBlank()) return

        try {
            voiceEngine.onEvaSpeakingStarted()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    mediaPlayer = null
                    _state.update { it.copy(currentlyPlayingMediaId = null) }
                    voiceEngine.onEvaSpeakingFinished()
                }
                prepare()
                start()
            }
            _state.update { it.copy(currentlyPlayingMediaId = media.id) }
        } catch (e: Exception) {
            voiceEngine.onEvaSpeakingFinished()
            _state.update { it.copy(error = "Nu s-a putut reda fișierul audio: ${e.message}") }
        }
    }

    private fun stopAudioPlayback() {
        stopMediaPlayerOnly()
        ttsEngine.stop()
        voiceEngine.onEvaSpeakingFinished()
        _state.update { it.copy(currentlyPlayingMediaId = null) }
    }

    private fun speakText(text: String, utteranceId: String = "msg_tts") {
        if (text.isBlank()) return
        stopMediaPlayerOnly()
        ttsEngine.speak(text, utteranceId)
    }

    // ══════════════════════════════════════════════════════════════
    // 4B. SIRI-STYLE HANDS-FREE VOICE ASSISTANT ("Hei Siri")
    // ══════════════════════════════════════════════════════════════

    private fun toggleVoiceAssistant(enabled: Boolean) {
        if (enabled) {
            stopMediaPlayerOnly()
            // Start the microphone FGS from the user-visible app interaction, before
            // Sherpa opens AudioRecord. Android 14+ requires this ordering and permission.
            if (!EvaWakeWordForegroundService.start(getApplication())) {
                _state.update {
                    it.copy(
                        voiceAssistantEnabled = false,
                        voiceLastResponse = "Nu pot porni ascultarea permanentă: verifică permisiunea microfonului și încearcă din nou cu EVA deschisă."
                    )
                }
                return
            }
            val ttsReady = ttsEngine.isReady.value
            voiceEngine.setEnabled(true, waitForWelcomeTts = ttsReady)
            _state.update {
                it.copy(
                    voiceAssistantEnabled = true,
                    voiceLastResponse = "Ascult continuu: spune „Hei Siri” urmat de comanda ta."
                )
            }
            if (ttsReady) {
                ttsEngine.speak("Modul Hands-Free este activ. Spune Hei Siri și comanda ta.", "voice_welcome")
            }
        } else {
            voiceCommandJob?.cancel()
            voiceEngine.setEnabled(false)
            EvaWakeWordForegroundService.stop(getApplication())
            ttsEngine.stop()
            _state.update {
                it.copy(
                    voiceAssistantEnabled = false,
                    showVoiceOverlay = false,
                    voiceLastResponse = ""
                )
            }
        }
    }

    private fun handleHandsFreeWakeWord() {
        stopMediaPlayerOnly()
        _state.update {
            it.copy(
                voiceLastResponse = "Da, te ascult! Spune comanda ta..."
            )
        }
        val spoke = ttsEngine.speak("Da, te ascult!", "wake_ack")
        if (!spoke) {
            voiceEngine.triggerImmediateCommandListening()
        }
    }

    private fun executeHandsFreeVoiceCommand(rawCommand: String) {
        val command = rawCommand.trim()
        if (command.isBlank()) return

        // Oprim orice comandă vocală anterioară sau redare în curs pentru a nu suprapune două voci
        voiceCommandJob?.cancel()
        stopMediaPlayerOnly()
        ttsEngine.stop()
        voiceEngine.markProcessing()

        val lower = command.lowercase()
        // Închidem automat fereastra modală ca să nu se suprapună peste ecranele aplicației
        _state.update {
            it.copy(
                voiceLastCommand = command,
                voiceLastResponse = "Se execută: \"$command\"...",
                showVoiceOverlay = false
            )
        }

        voiceCommandJob = viewModelScope.launch {
            // 0. Mai întâi verificăm toate uneltele operaționale (WhatsApp, YouTube, Screen Vision, Game Bot, Video, Poză, Cod Complet)
            val toolOutcome = runCatching { tryExecuteChatOperationalTool(command) }.getOrNull()
            if (toolOutcome != null) {
                finishVoiceCommandWithSpeech(command, toolOutcome.first, toolOutcome.second)
                return@launch
            }

            // 1. Verificăm comenzi rapide de Sistem / OS / Timp / Baterie / Navigare Ecrane
            when {
                lower.contains("cât e ceasul") || lower.contains("ce oră") || lower.contains("ce dată") || lower.contains("în ce an") -> {
                    val reply = "Astăzi este ${EvaTemporalContext.currentDateTimeRo()}. Cu ce te mai pot ajuta?"
                    finishVoiceCommandWithSpeech(command, reply)
                    return@launch
                }

                lower.contains("baterie") || lower.contains("ram") || lower.contains("starea telefonului") || lower.contains("telemetrie") -> {
                    val t = osOrchestratorEngine.getDeviceTelemetry()
                    val chargingTxt = if (t.isCharging) "se încarcă" else "nu este la încărcat"
                    val freeRamMb = (t.ramTotalMb - t.ramUsedMb).coerceAtLeast(0L)
                    val reply = "Bateria telefonului este la ${t.batteryPercent} la sută și $chargingTxt. Memoria RAM liberă este de $freeRamMb megabaiți din ${t.ramTotalMb}, pe ${t.androidVersion}."
                    finishVoiceCommandWithSpeech(command, reply)
                    return@launch
                }

                lower.contains("wifi") || lower.contains("wi-fi") || (lower.contains("internet") && lower.contains("set")) -> {
                    runCatching { osOrchestratorEngine.openSystemSettings("wifi") }
                    finishVoiceCommandWithSpeech(command, "Am deschis setările Wi-Fi pe telefon.")
                    return@launch
                }

                lower.contains("bluetooth") && (lower.contains("set") || lower.contains("deschide") || lower.contains("pornește")) -> {
                    runCatching { osOrchestratorEngine.openSystemSettings("bluetooth") }
                    finishVoiceCommandWithSpeech(command, "Am deschis setările Bluetooth.")
                    return@launch
                }

                lower.contains("luminozitate") || (lower.contains("ecran") && lower.contains("set")) -> {
                    runCatching { osOrchestratorEngine.openSystemSettings() }
                    finishVoiceCommandWithSpeech(command, "Am deschis setările de sistem și afișaj.")
                    return@launch
                }

                lower.contains("sunet") || lower.contains("volum") -> {
                    runCatching { osOrchestratorEngine.openSystemSettings() }
                    finishVoiceCommandWithSpeech(command, "Am deschis setările de sunet și sistem.")
                    return@launch
                }

                lower.startsWith("deschide ") || lower.contains("pornește aplicația") || lower.contains("lansează ") -> {
                    val target = lower
                        .removePrefix("deschide ")
                        .replace("pornește aplicația", "")
                        .replace("lansează", "")
                        .replace("aplicația", "")
                        .trim()

                    when {
                        target.contains("agent") -> {
                            _state.update { it.copy(activeTab = EvaTab.AGENT) }
                            finishVoiceCommandWithSpeech(command, "Am deschis ecranul Agentului Autonom.")
                        }
                        target.contains("cod") || target.contains("compiler") -> {
                            _state.update { it.copy(activeTab = EvaTab.CODE) }
                            finishVoiceCommandWithSpeech(command, "Am deschis Code Studio.")
                        }
                        target.contains("media") || target.contains("imagini") || target.contains("muzică") -> {
                            _state.update { it.copy(activeTab = EvaTab.MEDIA) }
                            finishVoiceCommandWithSpeech(command, "Am deschis studioul multimedia.")
                        }
                        target.contains("mesh") || target.contains("rețea") -> {
                            _state.update { it.copy(activeTab = EvaTab.MESH) }
                            finishVoiceCommandWithSpeech(command, "Am deschis rețeaua Swarm Mesh.")
                        }
                        target.contains("setări") || target.contains("setari") -> {
                            runCatching { osOrchestratorEngine.openSystemSettings() }
                            finishVoiceCommandWithSpeech(command, "Am deschis setările sistemului.")
                        }
                        target.contains("youtube") -> {
                            runCatching { osOrchestratorEngine.openUrl("https://m.youtube.com") }
                            finishVoiceCommandWithSpeech(command, "Deschid YouTube acum.")
                        }
                        target.contains("google") || target.contains("browser") -> {
                            runCatching { osOrchestratorEngine.openUrl("https://www.google.com") }
                            finishVoiceCommandWithSpeech(command, "Am deschis Google în browser.")
                        }
                        else -> {
                            val ok = osOrchestratorEngine.launchApp(target)
                            val msg = if (ok) "Am lansat aplicația $target." else "Nu am găsit aplicația $target instalată pe telefon."
                            finishVoiceCommandWithSpeech(command, msg)
                        }
                    }
                    return@launch
                }
            }

            // 2. Pentru orice altă întrebare sau sarcină complexă:
            // Adăugăm mesajul în Chat și folosim motorul AI / Agentul pentru răspuns vocal direct
            val userMsg = Message(
                role = MessageRole.USER,
                content = "🎙️ [Comandă Vocală Hands-Free]: $command",
                engine = _state.value.selectedEngine
            )
            currentMessages.add(userMsg)
            _state.update {
                it.copy(
                    messages = currentMessages.toList(),
                    isLoading = true,
                    error = null
                )
            }

            val s = _state.value
            val voiceSystemPrompt = buildString {
                append(s.systemPrompt)
                append("\n\n[MOD VOCAL HANDS-FREE TIP SIRI ACTIV]: Utilizatorul ți-a vorbit fără să atingă telefonul. ")
                append("Răspunde direct, natural, prietenos și concis în limba română (maxim 2-4 propoziții clare pentru a fi rostite vocal rapid), ")
                append("fără blocuri lungi de markdown.")
            }

            val result = when (s.selectedEngine) {
                AiEngine.LOCAL_LLM -> offlineLlmEngine.generateOffline(
                    prompt = command,
                    systemPrompt = voiceSystemPrompt,
                    conversationHistory = currentMessages,
                    memories = s.memories,
                    deviceTelemetry = formatDeviceTelemetry(),
                    webContext = anthropic.fetchWebSearchContext(command)
                )
                AiEngine.CLAUDE -> anthropic.sendMessage(
                    messages = currentMessages,
                    systemPrompt = voiceSystemPrompt,
                    memories = s.memories,
                    useThinking = false,
                    useWebSearch = true
                )
                AiEngine.MISTRAL -> mistral.sendMessage(
                    messages = currentMessages,
                    systemPrompt = voiceSystemPrompt,
                    memories = s.memories,
                    useWebSearch = true
                )
            }

            result.fold(
                onSuccess = { reply ->
                    val cleaned = reply.replace(Regex("<thinking>[\\s\\S]*?</thinking>\\n?"), "").trim()
                    val assistantMsg = Message(
                        role = MessageRole.ASSISTANT,
                        content = cleaned,
                        engine = s.selectedEngine
                    )
                    currentMessages.add(assistantMsg)
                    _state.update {
                        it.copy(
                            messages = currentMessages.toList(),
                            isLoading = false,
                            voiceLastResponse = cleaned
                        )
                    }
                    speakText(cleaned, "voice_reply")
                },
                onFailure = {
                    val fallbackMsg = "Am auzit comanda ta: $command. Îți răspund imediat!"
                    _state.update {
                        it.copy(
                            isLoading = false,
                            voiceLastResponse = fallbackMsg
                        )
                    }
                    speakText(fallbackMsg, "voice_reply")
                }
            )
        }
    }

    private fun finishVoiceCommandWithSpeech(
        command: String,
        spokenReply: String,
        previewBase64: String? = null
    ) {
        val userMsg = Message(
            role = MessageRole.USER,
            content = "🎙️ [Hei Siri]: $command",
            engine = _state.value.selectedEngine
        )
        val assistantMsg = Message(
            role = MessageRole.ASSISTANT,
            content = spokenReply,
            engine = _state.value.selectedEngine,
            imageBase64 = previewBase64
        )
        currentMessages.add(userMsg)
        currentMessages.add(assistantMsg)
        _state.update {
            it.copy(
                messages = currentMessages.toList(),
                voiceLastCommand = command,
                voiceLastResponse = spokenReply
            )
        }
        speakText(spokenReply, "voice_reply")
    }

    private fun deleteMedia(id: String) {
        if (_state.value.currentlyPlayingMediaId == id) {
            stopAudioPlayback()
        }
        val updated = _state.value.generatedMedia.filterNot { it.id == id }
        _state.update { it.copy(generatedMedia = updated) }
        viewModelScope.launch {
            repo.saveGeneratedMedia(updated)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 5. SWARM MESH NETWORK (P2P BLE + Wi-Fi Direct)
    // ══════════════════════════════════════════════════════════════

    private fun toggleMeshNetwork(enabled: Boolean) {
        if (enabled) {
            meshEngine.startMeshNode()
            swarmMeshManager.startSwarm()
        } else {
            meshEngine.stopMeshNode()
            swarmMeshManager.stopSwarm()
        }
        _state.update { it.copy(isMeshActive = enabled) }
    }

    // ══════════════════════════════════════════════════════════════
    // 6. AUTO-GENESIS (Auto-Evolutie)
    // ══════════════════════════════════════════════════════════════

    private fun runAutoGenesis() {
        viewModelScope.launch {
            val s = _state.value
            val result = genesis.analyzeSession(currentMessages, s.memories, s.systemPrompt)

            result.newMemories.forEach { repo.addMemory(it) }
            result.updatedMemories.forEach { repo.addMemory(it) }

            if (result.systemPromptRefinement.isNotBlank() && result.qualityScore > 0.7f) {
                val evolved = genesis.evolveSystemPrompt(
                    s.systemPrompt,
                    result.learnedInsights,
                    s.memories,
                    s.evolutionCount
                )
                repo.saveSystemPrompt(evolved)
                val newCount = repo.incrementEvolution()
                _state.update {
                    it.copy(
                        genesisNotification = "✨ EVA-1 a evoluat la v2.$newCount! A învățat ${result.newMemories.size} lucruri noi."
                    )
                }
            }

            if (currentMessages.isNotEmpty()) {
                val session = ConversationSession(
                    title = currentMessages.firstOrNull { it.role == MessageRole.USER }
                        ?.content?.take(50) ?: "Sesiune",
                    messages = currentMessages.toList(),
                    engine = s.selectedEngine,
                    summary = result.sessionSummary
                )
                repo.saveSession(session)
            }
        }
    }

    private fun triggerManualEvolution() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runAutoGenesis()
            _state.update { it.copy(isLoading = false) }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 7. MEMORIE, ISTORIC & SETARI PERSISTENTE
    // ══════════════════════════════════════════════════════════════

    private fun switchEngine(engine: AiEngine) {
        _state.update { it.copy(selectedEngine = engine) }
        viewModelScope.launch { repo.saveEngine(engine) }
    }

    private fun toggleThinking(enabled: Boolean) {
        _state.update { it.copy(useExtendedThinking = enabled) }
        viewModelScope.launch { repo.saveThinking(enabled) }
    }

    private fun toggleWebSearch(enabled: Boolean) {
        _state.update { it.copy(useWebSearch = enabled) }
        viewModelScope.launch { repo.saveWebSearch(enabled) }
    }

    private fun toggleAutoGenesis(enabled: Boolean) {
        _state.update { it.copy(autoGenesisEnabled = enabled) }
        viewModelScope.launch { repo.saveAutoGenesis(enabled) }
    }

    private fun addMemory(key: String, value: String, category: MemoryCategory, importance: Float) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            repo.addMemory(
                MemoryEntry(
                    key = key.trim(),
                    value = value.trim(),
                    category = category,
                    importance = importance
                )
            )
        }
    }

    private fun deleteMemory(id: String) {
        viewModelScope.launch { repo.deleteMemory(id) }
    }

    private fun newSession() {
        if (currentMessages.isNotEmpty()) {
            viewModelScope.launch {
                val s = _state.value
                val session = ConversationSession(
                    title = currentMessages.firstOrNull { it.role == MessageRole.USER }
                        ?.content?.take(50) ?: "Sesiune",
                    messages = currentMessages.toList(),
                    engine = s.selectedEngine
                )
                repo.saveSession(session)
            }
        }
        currentMessages.clear()
        _state.update { it.copy(messages = emptyList(), activeTab = EvaTab.CHAT) }
    }

    private fun deleteSession(id: String) {
        viewModelScope.launch { repo.deleteSession(id) }
    }

    private fun loadSession(session: ConversationSession) {
        currentMessages.clear()
        currentMessages.addAll(session.messages)
        _state.update {
            it.copy(
                messages = currentMessages.toList(),
                selectedEngine = session.engine,
                activeTab = EvaTab.CHAT
            )
        }
    }

    private fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch { repo.saveSystemPrompt(prompt) }
    }

    private fun saveApiKeys(keys: StoredApiKeys) {
        AnthropicApiService.API_KEY = keys.anthropicKey
        MistralApiService.API_KEY = keys.mistralKey
        CodeCompilerEngine.JDOODLE_CLIENT_ID = keys.jdoodleClientId
        CodeCompilerEngine.JDOODLE_CLIENT_SECRET = keys.jdoodleClientSecret
        MediaGenerationEngine.ELEVENLABS_API_KEY = keys.elevenLabsKey
        MediaGenerationEngine.REPLICATE_API_KEY = keys.replicateKey
        MediaGenerationEngine.STABILITY_API_KEY = keys.stabilityKey
        MediaGenerationEngine.RUNWAY_API_KEY = keys.runwayKey
        MediaGenerationEngine.SUNO_API_KEY = keys.sunoKey
        getApplication<Application>()
            .getSharedPreferences("eva1_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("gemini_api_key", keys.geminiKey.trim())
            .apply()

        _state.update {
            it.copy(
                apiKeys = keys,
                genesisNotification = "🔑 Cheile API au fost salvate persistent pe dispozitiv!"
            )
        }
        viewModelScope.launch {
            repo.saveApiKeys(keys)
        }
    }

    fun getQuantumStats(): QuantumDebugStats = _state.value.quantumStats

    override fun onCleared() {
        super.onCleared()
        stopAudioPlayback()
        osOrchestratorEngine.stopGameAutopilot(keepOverlayVisible = false)
        voiceEngine.release()
        EvaWakeWordForegroundService.stop(getApplication())
        ttsEngine.shutdown()
        meshEngine.stopMeshNode()
        swarmMeshManager.stopSwarm()
        quantumEngine.stopEngine()
        offlineLlmEngine.release()
    }
}
