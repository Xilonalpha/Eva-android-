package com.eva1.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eva1.companion.*
import com.eva1.companion.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(state: EvaUiState, onIntent: (EvaIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text("Istoric Sesiuni", style = MaterialTheme.typography.titleLarge)
        Text(
            "${state.sessions.size} conversații salvate",
            color = EvaTextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))

        if (state.sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, tint = EvaTextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Nicio sesiune salvată încă", color = EvaTextSecondary)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sessions, key = { it.id }) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(EvaSurface)
                            .border(0.8.dp, EvaBorder, RoundedCornerShape(14.dp))
                            .clickable { onIntent(EvaIntent.LoadSession(session)) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.title, style = MaterialTheme.typography.titleMedium)
                            if (session.summary.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(session.summary, color = EvaTextSecondary, fontSize = 12.sp, maxLines = 2)
                            }
                            Spacer(Modifier.height(4.dp))
                            val dateStr = remember(session.createdAt) {
                                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(session.createdAt))
                            }
                            Text(
                                "${session.messages.size} mesaje • ${session.engine.displayName} • $dateStr",
                                color = EvaTextMuted,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { onIntent(EvaIntent.DeleteSession(session.id)) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Șterge sesiunea", tint = EvaTextMuted)
                        }
                    }
                }
            }
        }
    }
}
