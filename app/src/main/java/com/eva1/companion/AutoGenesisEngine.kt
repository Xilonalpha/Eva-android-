package com.eva1.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class GenesisAnalysisResult(
    val newMemories: List<MemoryEntry> = emptyList(),
    val updatedMemories: List<MemoryEntry> = emptyList(),
    val learnedInsights: List<String> = emptyList(),
    val systemPromptRefinement: String = "",
    val sessionSummary: String = "",
    val qualityScore: Float = 0.85f
)

class AutoGenesisEngine(
    private val mistral: MistralApiService
) {
    suspend fun analyzeSession(
        messages: List<Message>,
        existingMemories: List<MemoryEntry>,
        currentSystemPrompt: String
    ): GenesisAnalysisResult = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) {
            return@withContext GenesisAnalysisResult()
        }

        val userMessages = messages.filter { it.role == MessageRole.USER }
        val lastTopic = userMessages.lastOrNull()?.content?.take(90) ?: "conversație generală"

        val newMemories = mutableListOf<MemoryEntry>()
        val updatedMemories = mutableListOf<MemoryEntry>()
        val insights = mutableListOf<String>()

        fun upsertMemory(key: String, value: String, category: MemoryCategory, importance: Float) {
            val existing = existingMemories.firstOrNull { it.key.equals(key, ignoreCase = true) }
            if (existing != null) {
                if (existing.value != value) {
                    updatedMemories.add(
                        existing.copy(
                            value = value,
                            category = category,
                            importance = maxOf(existing.importance, importance),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            } else if (newMemories.none { it.key.equals(key, ignoreCase = true) }) {
                newMemories.add(
                    MemoryEntry(
                        key = key,
                        value = value,
                        category = category,
                        importance = importance
                    )
                )
            }
        }

        userMessages.forEach { msg ->
            val text = msg.content.trim()
            val lower = text.lowercase(Locale.ROOT)

            // 1. Extragere nume utilizator
            Regex("""(?:ma cheama|mă cheamă|numele meu este|numele meu e|eu sunt)\s+([A-ZĂÂÎȘȚa-zăâîșț]{2,25})""", RegexOption.IGNORE_CASE)
                .find(text)?.let { m ->
                    val candidate = m.groupValues[1].trim()
                    if (candidate.lowercase(Locale.ROOT) !in setOf("un", "o", "aici", "bine", "gata", "acasa", "acasă")) {
                        val formattedName = candidate.replaceFirstChar { it.uppercase() }
                        upsertMemory("nume_utilizator", formattedName, MemoryCategory.FACT, 0.98f)
                        insights.add("Utilizatorul se numește $formattedName.")
                    }
                }

            // 2. Extragere preferinte
            if (lower.contains("prefer") || lower.contains("îmi place") || lower.contains("imi place") || lower.contains("ador ")) {
                upsertMemory(
                    key = "preferinta_${text.hashCode().toUInt().toString(16).take(5)}",
                    value = text.take(140),
                    category = MemoryCategory.USER_PREF,
                    importance = 0.88f
                )
                insights.add("Preferință identificată: ${text.take(80)}")
            }

            // 3. Extragere obiective / proiecte
            if (lower.contains("vreau sa construiesc") || lower.contains("vreau să construiesc") ||
                lower.contains("obiectivul meu") || lower.contains("lucrez la") || lower.contains("proiectul meu")
            ) {
                upsertMemory(
                    key = "obiectiv_activ",
                    value = text.take(150),
                    category = MemoryCategory.GOAL,
                    importance = 0.92f
                )
                insights.add("Obiectiv activ detectat: ${text.take(80)}")
            }

            // 4. Extragere competente / limbaje folosite
            val techSkills = listOf("kotlin", "python", "javascript", "android", "compose", "rust", "c++", "sql", "linux", "ai")
                .filter { lower.contains(it) }
            if (techSkills.isNotEmpty()) {
                upsertMemory(
                    key = "tehnologii_utilizate",
                    value = techSkills.joinToString(", ") { it.uppercase() },
                    category = MemoryCategory.SKILL,
                    importance = 0.84f
                )
            }
        }

        if (newMemories.isEmpty() && updatedMemories.isEmpty() && userMessages.isNotEmpty()) {
            upsertMemory(
                key = "ultimul_subiect_discutat",
                value = lastTopic,
                category = MemoryCategory.CONTEXT,
                importance = 0.78f
            )
        }

        if (insights.isEmpty()) {
            insights.add("Utilizatorul preferă răspunsuri directe, aplicate și bine structurate în limba română.")
            insights.add("Subiect principal abordat în sesiune: $lastTopic")
        }

        val quality = (0.85f + (newMemories.size + updatedMemories.size) * 0.03f).coerceAtMost(0.98f)

        GenesisAnalysisResult(
            newMemories = newMemories,
            updatedMemories = updatedMemories,
            learnedInsights = insights.distinct(),
            systemPromptRefinement = "Personalizează răspunsurile folosind memoria activă și păstrează rigoarea tehnică 2026.",
            sessionSummary = "Sesiune analizată (${messages.size} mesaje) • Subiect: $lastTopic",
            qualityScore = quality
        )
    }

    suspend fun evolveSystemPrompt(
        currentPrompt: String,
        learnedInsights: List<String>,
        memories: List<MemoryEntry>,
        currentEvolutionCount: Int
    ): String = withContext(Dispatchers.IO) {
        val nextVer = currentEvolutionCount + 1
        val base = currentPrompt.substringBefore("\n\n[EVOLUȚIE AUTO-GENESIS").trim()
        val insightBlock = (learnedInsights + memories.take(5).map { "${it.key}: ${it.value}" })
            .distinct()
            .joinToString("\n") { "• $it" }

        """
        $base

        [EVOLUȚIE AUTO-GENESIS v2.$nextVer]
        $insightBlock
        """.trimIndent()
    }
}
