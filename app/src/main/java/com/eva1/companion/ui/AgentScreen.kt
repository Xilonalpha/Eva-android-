package com.eva1.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eva1.companion.*
import com.eva1.companion.ui.theme.*

@Composable
fun AgentScreen(state: EvaUiState, onIntent: (EvaIntent) -> Unit) {
    var goalInput by remember { mutableStateOf("") }

    val exampleGoals = listOf(
        "Verifică telemetria completă a telefonului (baterie, RAM, stocare), rulează uname -a în shell și fă un raport de diagnostic 2026",
        "Cercetează pe web cele mai bune modele și arhitecturi AI din 2026 și compilează un benchmark în Kotlin",
        "Generează o imagine futuristă cu un oraș cibernetic în anul 2026 și salveaz-o în Galeria Media",
        "Compune o melodie electronică ambientală și scrie un script de analiză în Code Studio",
        "Deschide setările Wi-Fi și salvează în memoria EVA-1 statusul rețelei curente"
    )

    val activeTools = listOf(
        "🌐 Web Search 2026",
        "📄 HTTP/URL Fetch",
        "⚙️ Compilator Cod Live",
        "💻 Terminal Shell Android",
        "📱 Telemetrie Hardware",
        "🚀 Lansator Aplicații",
        "🖼️ Generare Imagini Flux",
        "🎵 Sinteză Muzică WAV",
        "💾 Scriere Fișiere & Memorie"
    )

    // Un singur LazyColumn unificat pentru ca nimic să nu fie înghesuit pe verticală
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 1. Header ──────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(EvaPurple, EvaBlue))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Agent Autonom ReAct • Ediția 2026", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Fără limitări • Acces direct la Web, Cod, Shell, OS, Media & Memorie",
                        color = EvaCyan,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ── 2. Active Real Tools Strip ─────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeTools.forEach { toolName ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(EvaSurface)
                            .border(0.6.dp, EvaBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(toolName, color = EvaTextSecondary, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }

        // ── 3. Goal Input Card ─────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(EvaSurface)
                    .border(1.dp, EvaBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agent_goal_input"),
                    placeholder = {
                        Text(
                            "Cere orice Agentului EVA-1 (caută pe web în 2026, rulează cod/shell, verifică telefonul, deschide aplicații, generează imagini/muzică)...",
                            color = EvaTextMuted,
                            fontSize = 12.sp
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EvaPurple,
                        unfocusedBorderColor = EvaBorder,
                        focusedContainerColor = EvaBlack,
                        unfocusedContainerColor = EvaBlack,
                        focusedTextColor = EvaTextPrimary,
                        unfocusedTextColor = EvaTextPrimary
                    )
                )

                Spacer(Modifier.height(10.dp))

                val isRunning = state.currentTask?.status in listOf(TaskStatus.THINKING, TaskStatus.EXECUTING)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (goalInput.isNotBlank() && !isRunning) {
                                onIntent(EvaIntent.RunAgentTask(goalInput))
                                goalInput = ""
                            }
                        },
                        enabled = goalInput.isNotBlank() && !isRunning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("launch_agent_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EvaPurple)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Agentul execută uneltele...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Execută Obiectivul Autonom", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (isRunning) {
                        OutlinedButton(
                            onClick = { onIntent(EvaIntent.CancelAgentTask) },
                            modifier = Modifier.testTag("cancel_agent_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EvaRed)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Oprește Agentul")
                            Spacer(Modifier.width(4.dp))
                            Text("Stop")
                        }
                    }
                }
            }
        }

        // ── 4. Content: Active Task or Example Goals ───────────────
        val task = state.currentTask
        if (task != null) {
            item {
                AgentTaskCard(task = task)
            }
        } else {
            item {
                Text(
                    "Misiuni complexe recomandate (apasă pentru a testa uneltele reale):",
                    color = EvaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(exampleGoals) { ex ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(EvaSurface)
                        .border(0.8.dp, EvaBorder, RoundedCornerShape(12.dp))
                        .clickable { goalInput = ex }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EvaPurple, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(ex, color = EvaTextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }

        if (state.agentTasks.size > 1) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Sarcini anterioare:", color = EvaTextMuted, fontSize = 12.sp)
            }
            items(state.agentTasks.drop(1), key = { it.id }) { oldTask ->
                AgentTaskCard(task = oldTask)
            }
        }
    }
}

@Composable
fun AgentTaskCard(task: AgentTask) {
    val statusColor = when (task.status) {
        TaskStatus.COMPLETED -> EvaGreen
        TaskStatus.FAILED -> EvaRed
        TaskStatus.THINKING, TaskStatus.EXECUTING -> EvaAmber
        TaskStatus.IDLE -> EvaTextMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EvaSurface)
            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                task.goal,
                color = EvaTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                task.status.name,
                color = statusColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        task.steps.forEach { step ->
            StepItem(step = step)
        }

        if (task.finalResult.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(EvaBlack)
                    .border(1.dp, EvaGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EvaGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rezultat Final Consolidat", color = EvaGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    FormattedMessageText(content = task.finalResult, isUser = false)
                }
            }
        }
    }
}

@Composable
fun StepItem(step: AgentStep) {
    val actionColor = when (step.action) {
        "SEARCH", "WEB_FETCH" -> EvaCyan
        "EXECUTE_CODE", "CODE", "SHELL_EXEC" -> EvaPurple
        "DEVICE_TELEMETRY", "OPEN_APP", "OPEN_URL", "SYSTEM_SETTINGS" -> EvaBlueBright
        "GENERATE_IMAGE", "GENERATE_MUSIC" -> EvaGreen
        "MEMORIZE", "FILE_WRITE", "CLIPBOARD_WRITE" -> EvaAmber
        "COMPLETE" -> EvaGreen
        "ERROR" -> EvaRed
        else -> EvaBlue
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(EvaSurfaceHigh)
            .border(0.5.dp, EvaBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(actionColor.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "Pas ${step.stepNumber} • ${step.action}",
                    color = actionColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            if (step.actionInput.isNotBlank() && step.action != "COMPLETE") {
                Spacer(Modifier.width(6.dp))
                Text(
                    step.actionInput.lines().firstOrNull()?.take(50) ?: "",
                    color = EvaTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (step.durationMs > 0) {
                Text(
                    "${step.durationMs}ms",
                    color = EvaTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (step.thought.isNotBlank()) {
            Text("💭 ${step.thought}", color = EvaTextSecondary, fontSize = 12.sp)
        }

        if (step.observation.isNotBlank() && step.action != "COMPLETE") {
            Text(
                "📌 ${step.observation.take(500)}",
                color = EvaTextPrimary.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
