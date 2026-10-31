package com.eva1.companion.quantum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════════════════════
// UI COMPLET PENTRU QUANTUM CONTEXT & ZERO-UI ENGINE
// ══════════════════════════════════════════════════════════════════════════════

private val QuantumBg = Color(0xFF080B12)
private val QuantumCard = Color(0xFF121826)
private val NeonViolet = Color(0xFFB388FF)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonEmerald = Color(0xFF00E676)
private val NeonRose = Color(0xFFFF5252)

@Composable
fun QuantumContextScreen(
    engine: QuantumContextEngine,
    onAdoptPrecomputedToChat: (String) -> Unit = {}
) {
    val qState by engine.state.collectAsState()
    val t = qState.telemetry

    DisposableEffect(Unit) {
        engine.startEngine()
        onDispose { }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(QuantumBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. HEADER: Mod Zero-UI Activ ──────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuantumCard)
                    .border(1.dp, NeonViolet.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(NeonEmerald)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "QUANTUM CONTEXT & ZERO-UI",
                            color = NeonViolet,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = "Încredere: ${qState.confidencePercent}%",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = qState.zeroUiMode.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = qState.zeroUiMode.description,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }
        }

        // ── 2. RASPUNS PRE-CALCULAT (ZERO LATENCY CACHE) ──────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuantumCard)
                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚡ Nevoie Anticipată:",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = qState.primaryNeed.displayTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { engine.precomputeResponseInBackground() },
                        modifier = Modifier.testTag("quantum_refresh_precompute_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recalculează răspuns", tint = NeonCyan)
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (qState.isPrecomputing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = NeonCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "EVA-1 pre-generează răspunsul în fundal (Zero Latency)...",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                } else if (qState.precomputedResponse.isNotBlank()) {
                    Text(
                        text = qState.precomputedResponse,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onAdoptPrecomputedToChat(qState.precomputedResponse) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quantum_adopt_response_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = "Folosește Răspunsul", tint = Color.Black)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Folosește Instant în Conversație (0ms latență)",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "Apasă iconița de reîmprospătare pentru a pre-calcula răspunsul optim.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ── 3. TELEMETRIE BIOMETRICA SI COGNITIVA ─────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuantumCard)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Biometrie Comportamentală & Senzori Fizici",
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(12.dp))

                BiometricBar("Nivel Concentrare (Focus)", t.focusScore, NeonEmerald)
                Spacer(Modifier.height(8.dp))
                BiometricBar("Nivel Stres (Micro-Tremur & Tastare)", t.stressScore, NeonRose)
                Spacer(Modifier.height(8.dp))
                BiometricBar("Oboseală Circadiană & Vizuală", t.fatigueScore, NeonViolet)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(10.dp))

                Text("• Stare Fizică: ${t.motionState.label}", color = Color.White, fontSize = 12.sp)
                Text("• Iluminare: ${t.lightingCondition.label} (${t.luxLevel.toInt()} lux)", color = Color.White, fontSize = 12.sp)
                Text("• Acustică: ${t.ambientEnvironment.label} (${t.ambientDecibels.toInt()} dB)", color = Color.White, fontSize = 12.sp)
                Text("• Cadență tastare: ${t.typingCadenceWpm.toInt()} WPM • Micro-tremur: ${"%.2f".format(t.microTremorIndex)}", color = Color.White, fontSize = 12.sp)
            }
        }

        // ── 4. DISTRIBUTIA PROBABILITATILOR BAYESIENE ─────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuantumCard)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Matricea Probabilităților de Intenție (Bayes + Markov)",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(10.dp))

                qState.needProbabilities.entries
                    .sortedByDescending { it.value }
                    .forEach { (need, prob) ->
                        val pct = (prob * 100).toInt()
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(need.displayTitle, color = Color.White, fontSize = 12.sp)
                                Text("$pct%", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { prob.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = NeonViolet,
                                trackColor = Color.White.copy(alpha = 0.08f)
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun BiometricBar(label: String, score: Int, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            Text("$score / 100", color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}
