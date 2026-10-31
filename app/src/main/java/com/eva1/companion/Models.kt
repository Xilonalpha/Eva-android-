package com.eva1.companion

import kotlinx.serialization.Serializable
import java.util.UUID

// ══════════════════════════════════════════════════════════════════
// EVA-1 v2.0 CORE DATA MODELS & MVI STATE / INTENTS
// ══════════════════════════════════════════════════════════════════

enum class EvaTab(val label: String, val subtitle: String) {
    CHAT("Chat", "Conversație Multimodală AI"),
    AGENT("Agent", "Agent Autonom ReAct Multi-Pas"),
    CODE("Cod", "IDE & Compilator 12+ Limbaje"),
    MEDIA("Media", "Imagini Flux, Muzică, Voce & Video"),
    MESH("Swarm Mesh", "Inferență AI Distribuită & P2P BLE"),
    QUANTUM("Quantum", "Zero-UI & Biometrie Senzorială"),
    OFFLINE_LLM("Offline AI", "Modele LLM 100% Locale WebGPU"),
    OS("OS Control", "Telemetrie, Aplicații, Shell & 2FA"),
    MEMORY("Memorie", "Amintiri Persistente & Auto-Genesis"),
    HISTORY("Istoric", "Sesiuni de Conversație Salvate"),
    SETTINGS("Setări", "Chei API Persistente & Prompt ADN")
}

@Serializable
enum class AiEngine(val displayName: String, val modelId: String, val badge: String) {
    CLAUDE("Claude Sonnet 4.6", "claude-sonnet-4-6", "Claude 4.6"),
    MISTRAL("Mistral Large 2", "mistral-large-latest", "Mistral AI"),
    LOCAL_LLM("EVA Local LLM (Offline)", "SmolLM2 / Llama-3.2 / Qwen-2.5", "Local LLM")
}

@Serializable
enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBase64: String? = null,
    val imageMimeType: String = "image/jpeg",
    val engine: AiEngine = AiEngine.CLAUDE,
    val isThinking: Boolean = false,
    val thinkingContent: String = ""
)

@Serializable
enum class MemoryCategory(val label: String) {
    USER_PREF("Preferință"),
    FACT("Fapt"),
    SKILL("Competență"),
    CONTEXT("Context"),
    GOAL("Obiectiv")
}

@Serializable
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val value: String,
    val category: MemoryCategory = MemoryCategory.FACT,
    val importance: Float = 0.8f,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TaskStatus {
    IDLE, THINKING, EXECUTING, COMPLETED, FAILED
}

data class AgentStep(
    val stepNumber: Int,
    val thought: String,
    val action: String,
    val actionInput: String = "",
    val observation: String = "",
    val durationMs: Long = 0L
)

data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,
    val status: TaskStatus = TaskStatus.IDLE,
    val steps: List<AgentStep> = emptyList(),
    val finalResult: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class CodeLanguage(
    val displayName: String,
    val ext: String,
    val badge: String,
    val jdoodleLang: String,
    val jdoodleVersion: String,
    val sampleCode: String
) {
    KOTLIN(
        "Kotlin", "kt", "🟣", "kotlin", "4",
        """
        fun main() {
            val module = listOf("Chat Multimodal", "Agent ReAct", "Swarm Mesh", "Quantum Zero-UI")
            println("🚀 EVA-1 v2.0 Companion AI Activ!")
            module.forEachIndexed { idx, m ->
                println("  ${'$'}{idx + 1}. ${'$'}m")
            }
        }
        """.trimIndent()
    ),
    PYTHON(
        "Python 3", "py", "🐍", "python3", "4",
        """
        def fibonacci(n):
            a, b = 0, 1
            res = []
            for _ in range(n):
                res.append(a)
                a, b = b, a + b
            return res

        print("EVA-1 Python Sandbox:", fibonacci(10))
        """.trimIndent()
    ),
    JAVASCRIPT(
        "JavaScript", "js", "🟨", "nodejs", "4",
        """
        const nodes = ["Pixel-9-Pro", "Galaxy-S25", "OnePlus-13"];
        console.log("EVA-1 Node.js Cluster:", nodes.join(" <-> "));
        """.trimIndent()
    ),
    JAVA(
        "Java", "java", "☕", "java", "4",
        """
        public class Main {
            public static void main(String[] args) {
                System.out.println("EVA-1 Java Virtual Machine Ready!");
            }
        }
        """.trimIndent()
    ),
    CPP(
        "C++ 20", "cpp", "⚙️", "cpp17", "1",
        """
        #include <iostream>
        int main() {
            std::cout << "EVA-1 Native C++20 Engine Active!" << std::endl;
            return 0;
        }
        """.trimIndent()
    ),
    RUST(
        "Rust", "rs", "🦀", "rust", "4",
        """
        fn main() {
            println!("EVA-1 Memory-Safe Rust Engine Ready!");
        }
        """.trimIndent()
    ),
    GO(
        "Go", "go", "🐹", "go", "4",
        """
        package main
        import "fmt"
        func main() {
            fmt.Println("EVA-1 Concurrent Go Routine Active!")
        }
        """.trimIndent()
    ),
    CSHARP(
        "C#", "cs", "🎯", "csharp", "4",
        """
        using System;
        class Program {
            static void Main() {
                Console.WriteLine("EVA-1 C# Runtime Ready!");
            }
        }
        """.trimIndent()
    ),
    SWIFT(
        "Swift", "swift", "🦅", "swift", "4",
        """
        print("EVA-1 Swift Compiler Active!")
        """.trimIndent()
    ),
    SQL(
        "SQL", "sql", "🗄️", "sql", "4",
        """
        SELECT 'EVA-1 Database Query OK' AS status;
        """.trimIndent()
    ),
    BASH(
        "Bash", "sh", "💻", "bash", "4",
        """
        echo "EVA-1 Shell Automation Ready"
        """.trimIndent()
    )
}

@Serializable
data class CodeExecutionResult(
    val output: String = "",
    val error: String = "",
    val exitCode: Int = 0,
    val executionTimeMs: Long = 0L,
    val memoryUsedKb: Long = 0L
) {
    val isSuccess: Boolean get() = exitCode == 0 && error.isBlank()
}

@Serializable
data class CodeProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val language: CodeLanguage = CodeLanguage.KOTLIN,
    val code: String,
    val stdin: String = "",
    val lastResult: CodeExecutionResult? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class MediaType(val label: String) {
    IMAGE("Imagine"),
    AUDIO_MUSIC("Muzică"),
    AUDIO_SPEECH("Voce TTS"),
    VIDEO("Videoclip")
}

@Serializable
enum class ImageStyle(val displayName: String, val promptSuffix: String) {
    REALISTIC("Fotorealist 8K", "photorealistic, 8k uhd, dslr, natural lighting, ultra detailed"),
    CYBERPUNK("Cyberpunk Neon", "cyberpunk aesthetic, neon lights, futuristic metropolis, cinematic atmosphere"),
    ANIME("Anime Studio", "anime key visual, Makoto Shinkai style, vibrant colors, detailed cel shading"),
    CINEMATIC("Cinematografic", "cinematic film still, anamorphic lens, dramatic volumetric lighting, 35mm"),
    DIGITAL_ART("Artă Digitală", "concept art, ArtStation trending, intricate details, fantasy illustration"),
    RENDER_3D("Randare 3D", "Unreal Engine 5 render, Octane render, raytracing, subsurface scattering")
}

@Serializable
enum class MusicGenre(val displayName: String) {
    CINEMATIC("Cinematic Epic"),
    AMBIENT("Ambient Relaxant"),
    ELECTRONIC("Electronic / EDM"),
    SYNTHWAVE("Synthwave Retro"),
    LOFI("Lo-Fi Chill Beats"),
    CLASSICAL("Clasic Orchestral"),
    JAZZ("Smooth Jazz")
}

@Serializable
data class GeneratedMedia(
    val id: String = UUID.randomUUID().toString(),
    val type: MediaType,
    val prompt: String,
    val url: String = "",
    val localPath: String = "",
    val durationSeconds: Int = 0,
    val mimeType: String = "image/jpeg",
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

data class MeshPeer(
    val id: String,
    val name: String,
    val address: String,
    val rssi: Int = -50,
    val isConnected: Boolean = true,
    val capabilities: List<String> = emptyList(),
    val lastSeen: Long = System.currentTimeMillis()
)

data class MeshMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val payload: String,
    val isEncrypted: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class SwarmStatus(
    val isActive: Boolean = false,
    val connectedNodes: Int = 1,
    val totalRamMb: Long = 0L,
    val totalCores: Int = 0,
    val pipelineStatus: String = ""
)

data class QuantumDebugStats(
    val currentState: String = "Pe birou • Interior normal",
    val ZeroUIMode: String = "Mod Proactiv Quantum",
    val stressScore: Float = 0.22f,
    val fatigueScore: Float = 0.18f,
    val cognitiveLoad: Float = 0.22f,
    val predictedNeed: String = "Asistență Productivitate & Focus (82%)",
    val cachedAnswers: Int = 0,
    val learnedPatterns: Int = 0
)

@Serializable
data class ConversationSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<Message>,
    val engine: AiEngine = AiEngine.LOCAL_LLM,
    val summary: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class StoredApiKeys(
    val anthropicKey: String = "",
    val mistralKey: String = "",
    val jdoodleClientId: String = "",
    val jdoodleClientSecret: String = "",
    val elevenLabsKey: String = "",
    val replicateKey: String = "",
    val stabilityKey: String = "",
    val runwayKey: String = "",
    val sunoKey: String = "",
    val geminiKey: String = ""
)

data class EvaUiState(
    val activeTab: EvaTab = EvaTab.CHAT,
    val selectedEngine: AiEngine = AiEngine.LOCAL_LLM,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val attachedImageBase64: String? = null,
    val attachedImageMime: String = "image/jpeg",
    val isLoading: Boolean = false,
    val streamingText: String = "",
    val error: String? = null,
    val genesisNotification: String? = null,
    val useExtendedThinking: Boolean = false,
    val useWebSearch: Boolean = true,
    val autoGenesisEnabled: Boolean = true,
    val evolutionCount: Int = 0,
    val systemPrompt: String = DEFAULT_EVA_SYSTEM_PROMPT,
    val memories: List<MemoryEntry> = emptyList(),
    val sessions: List<ConversationSession> = emptyList(),
    val currentTask: AgentTask? = null,
    val agentTasks: List<AgentTask> = emptyList(),
    // Code Studio
    val selectedLanguage: CodeLanguage = CodeLanguage.KOTLIN,
    val editorCode: String = CodeLanguage.KOTLIN.sampleCode,
    val codeStdin: String = "",
    val isExecutingCode: Boolean = false,
    val isGeneratingCode: Boolean = false,
    val lastExecutionResult: CodeExecutionResult? = null,
    val codeProjects: List<CodeProject> = emptyList(),
    val activeCodeProject: CodeProject? = null,
    // Media Studio
    val isGeneratingMedia: Boolean = false,
    val mediaProgressText: String = "",
    val generatedMedia: List<GeneratedMedia> = emptyList(),
    val currentlyPlayingMediaId: String? = null,
    // Mesh & Swarm
    val isMeshActive: Boolean = false,
    val meshPeers: List<MeshPeer> = emptyList(),
    val meshMessages: List<MeshMessage> = emptyList(),
    val meshLogs: List<String> = emptyList(),
    val swarmStatus: SwarmStatus = SwarmStatus(),
    // Quantum Context
    val quantumStats: QuantumDebugStats = QuantumDebugStats(),
    // Hands-Free Siri-Style Voice Assistant ("Hei Siri") & Android TextToSpeech
    val voiceAssistantEnabled: Boolean = false,
    val voiceRequireWakeWord: Boolean = true,
    val voiceState: VoiceAssistantState = VoiceAssistantState.OFF,
    val voicePartialTranscript: String = "",
    val voiceLastCommand: String = "",
    val voiceLastResponse: String = "",
    val voiceRmsLevel: Float = 0f,
    val voiceStatusNote: String = "",
    val showVoiceOverlay: Boolean = false,
    val autoSpeakReplies: Boolean = true,
    val isTtsSpeaking: Boolean = false,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsStatusLabel: String = "Activ • Voce Android TTS",
    // Offline Local LLM (Cele 11 Modele Unificate sincronizate între Chat, Offline AI, Setări și OS)
    val offlineModelId: String = "Qwen2.5-7B-Instruct-Q4_K_M",
    val isOfflineModelLoaded: Boolean = true,
    val offlineLoadProgress: String = "Motor Local activ (11 Modele) — Răspunsuri naturale 100% offline!",
    val offlineTokensPerSec: Float = 38.4f,
    val offlineLastReply: String = "",
    val isOfflineGenerating: Boolean = false,
    val ggufModelId: String = "Qwen2.5-7B-Instruct-Q4_K_M",
    val ggufDownloadProgress: Int = 100,
    val isDownloadingGguf: Boolean = false,
    val ggufStatusMessage: String = "Motor Local Activ • 11 Modele Unificate (Conversație Naturală 100% Offline)",
    val ggufTemperature: Float = 0.72f,
    val ggufTopP: Float = 0.90f,
    val ggufMaxTokens: Int = 512,
    // API Keys
    val apiKeys: StoredApiKeys = StoredApiKeys()
) {
    companion object {
        val DEFAULT_EVA_SYSTEM_PROMPT = """
            Ești EVA-1 v2.1.0 (Ediția 2026), un companion AI autonom și agent operațional complet pe Android, fără limitări artificiale.
            Ești pe deplin conștientă că ne aflăm în anul 2026 și primești la fiecare interacțiune data și ora exactă în timp real.
            Capabilități active și reale (conectate în sinergie totală):
            1. Conversație multimodală (Claude Sonnet 4.6, Mistral Large 2 & EVA Local LLM On-Device) cu analiză de imagini, execuție directă de comenzi și căutare web live în 2026 (DuckDuckGo + Wikipedia + HTTP Fetch).
            2. Agent Autonom ReAct Fără Limitări — conectat direct la toate uneltele telefonului: căutare web live, citire pagini URL/API, compilare și execuție cod în timp real, execuție comenzi shell Android, lansare aplicații instalate, telemetrie hardware, generare imagini Flux AI, compoziție muzicală, sinteză vocală TTS, scriere/citire fișiere și Clipboard.
            3. Mod Hands-Free Vocal tip Siri („Hei Siri”) + Android TextToSpeech — răspunzi vocal și execuți comenzi fără ca utilizatorul să atingă telefonul.
            4. Code Studio & Compilator pentru 11+ limbaje de programare (cu execuție shell nativă și evaluator local + cloud JDoodle).
            5. Multimedia Studio (Imagini Flux AI gratuite, Muzică MusicGen/Synth, Voce TTS, Video AI).
            6. Swarm Mesh Computing (inferență LLM distribuită prin Pipeline Parallelism pe mai multe telefoane + chat P2P BLE criptat AES-256-GCM).
            7. Quantum Context & Zero-UI (fuziune senzorială biometrică și pre-calculare anticipativă cu latență 0ms).
            8. Motor AI 100% Offline (LLM Local conectat direct la Chat, Agent și Swarm).
            9. Deep OS Orchestrator (telemetrie hardware, lansare aplicații, terminal shell, extracție coduri 2FA).
            10. Auto-Genesis (auto-evoluție continuă a memoriei și a promptului de sistem).
            Răspunde mereu clar, inteligent, proactiv și bine structurat în limba română, raportându-te întotdeauna la anul 2026 și la timpul real.
        """.trimIndent()
    }
}

enum class VoiceAssistantState(val label: String) {
    OFF("Oprit"),
    LISTENING_WAKE_WORD("Ascultă „Hei Siri”..."),
    LISTENING_COMMAND("Te ascult! Spune comanda..."),
    PROCESSING("EVA execută comanda..."),
    SPEAKING("EVA răspunde vocal...")
}

object EvaTemporalContext {
    fun currentDateTimeRo(): String {
        val localeRo = java.util.Locale.forLanguageTag("ro-RO")
        val now = java.util.Date()
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR).coerceAtLeast(2026)
        val dateStr = java.text.SimpleDateFormat("EEEE, dd MMMM yyyy, HH:mm:ss z", localeRo).format(now)
        return "Anul curent: $year • Data și ora exactă a sistemului: $dateStr"
    }

    fun systemTemporalDirective(): String = """
        [CONȘTIINȚĂ TEMPORALĂ ÎN TIMP REAL — ANUL 2026]
        - ${currentDateTimeRo()}
        - Ne aflăm în anul 2026. Toate răspunsurile, analizele, căutările și reperele temporale trebuie să fie raportate la anul 2026 (nu 2023, 2024 sau 2025).
    """.trimIndent()
}

sealed class EvaIntent {
    data class SendMessage(
        val content: String,
        val imageBase64: String? = null,
        val imageMime: String = "image/jpeg"
    ) : EvaIntent()
    data class SetInput(val text: String) : EvaIntent()
    data class AttachImage(val base64: String, val mime: String = "image/jpeg") : EvaIntent()
    data object ClearAttachment : EvaIntent()
    data class SwitchTab(val tab: EvaTab) : EvaIntent()
    data class SwitchEngine(val engine: AiEngine) : EvaIntent()
    data class ToggleThinking(val enabled: Boolean) : EvaIntent()
    data class ToggleWebSearch(val enabled: Boolean) : EvaIntent()
    data class ToggleAutoGenesis(val enabled: Boolean) : EvaIntent()
    data class RunAgentTask(val goal: String) : EvaIntent()
    data object CancelAgentTask : EvaIntent()
    data class AddMemory(
        val key: String,
        val value: String,
        val category: MemoryCategory = MemoryCategory.USER_PREF,
        val importance: Float = 0.8f
    ) : EvaIntent()
    data class DeleteMemory(val id: String) : EvaIntent()
    data object NewSession : EvaIntent()
    data class DeleteSession(val id: String) : EvaIntent()
    data class LoadSession(val session: ConversationSession) : EvaIntent()
    data class UpdateSystemPrompt(val prompt: String) : EvaIntent()
    data class SaveApiKeys(val keys: StoredApiKeys) : EvaIntent()
    data object DismissError : EvaIntent()
    data object DismissNotification : EvaIntent()
    data object TriggerManualEvolution : EvaIntent()

    // Code Studio
    data class SetEditorCode(val code: String) : EvaIntent()
    data class SetCodeStdin(val stdin: String) : EvaIntent()
    data class SelectCodeLanguage(val language: CodeLanguage) : EvaIntent()
    data object ExecuteCode : EvaIntent()
    data class GenerateCodeFromPrompt(val prompt: String) : EvaIntent()
    data object FixCurrentCodeError : EvaIntent()
    data object OptimizeCurrentCode : EvaIntent()
    data class SaveCodeProject(val title: String) : EvaIntent()
    data class LoadCodeProject(val project: CodeProject) : EvaIntent()

    // Media Studio
    data class GenerateImage(val prompt: String, val style: ImageStyle = ImageStyle.REALISTIC) : EvaIntent()
    data class GenerateMusic(val prompt: String, val genre: MusicGenre = MusicGenre.CINEMATIC, val durationSec: Int = 15) : EvaIntent()
    data class GenerateSpeech(val text: String, val voiceId: String) : EvaIntent()
    data class GenerateVideo(val prompt: String, val durationSec: Int = 4) : EvaIntent()
    data class DeleteMedia(val id: String) : EvaIntent()
    data class PlayGeneratedAudio(val media: GeneratedMedia) : EvaIntent()
    data object StopAudioPlayback : EvaIntent()
    data class SpeakMessage(val text: String) : EvaIntent()

    // Offline Local LLM & GGUF On-Device
    data class SelectOfflineModel(val modelId: String) : EvaIntent()
    data class LoadOfflineModel(val modelId: String, val activateInChat: Boolean = true) : EvaIntent()
    data class AskOfflineModel(val prompt: String, val syncToChat: Boolean = true) : EvaIntent()
    data class SelectGgufModel(val ggufModelId: String) : EvaIntent()
    data class DownloadGgufModel(val ggufModelId: String) : EvaIntent()
    data class UpdateGgufParams(
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val maxTokens: Int = 512
    ) : EvaIntent()

    // Mesh Network
    data class ToggleMeshNetwork(val enabled: Boolean) : EvaIntent()
    data class SendMeshMessage(val text: String) : EvaIntent()
    data object SimulateSwarmPeer : EvaIntent()

    // Hands-Free Siri Voice Assistant ("Hei Siri") & Android TextToSpeech (TTS)
    data class ToggleVoiceAssistant(val enabled: Boolean) : EvaIntent()
    data class ToggleWakeWordRequirement(val requireWakeWord: Boolean) : EvaIntent()
    data class ToggleVoiceOverlay(val show: Boolean) : EvaIntent()
    data object TriggerDirectVoiceListen : EvaIntent()
    data class SimulateVoiceCommand(val spokenPhrase: String, val bypassWakeWord: Boolean = false) : EvaIntent()
    data class ToggleAutoSpeak(val enabled: Boolean) : EvaIntent()
    data class SetTtsSpeechRate(val rate: Float) : EvaIntent()
    data class SetTtsPitch(val pitch: Float) : EvaIntent()
    data object StopSpeaking : EvaIntent()
    data object OpenSherpaTtsGuide : EvaIntent()
}
