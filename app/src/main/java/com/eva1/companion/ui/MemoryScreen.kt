package com.eva1.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.eva1.companion.*
import com.eva1.companion.ui.theme.*

@Composable
fun MemoryScreen(state: EvaUiState, onIntent: (EvaIntent) -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<MemoryCategory?>(null) }

    val filtered = remember(state.memories, selectedCategory) {
        if (selectedCategory == null) state.memories
        else state.memories.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Memorie Persistentă", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${state.memories.size} amintiri stocate • Auto-Genesis v2.${state.evolutionCount}",
                    color = EvaTextSecondary,
                    fontSize = 12.sp
                )
            }
            FilledTonalButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_memory_button"),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = EvaBlue.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = EvaBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Adaugă", color = EvaBlue, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Category Filter Chips (Scrollabil orizontal) ───────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickChip(
                label = "Toate",
                active = selectedCategory == null,
                activeColor = EvaBlue,
                onClick = { selectedCategory = null }
            )
            MemoryCategory.entries.forEach { cat ->
                QuickChip(
                    label = cat.label,
                    active = selectedCategory == cat,
                    activeColor = EvaPurple,
                    onClick = { selectedCategory = if (selectedCategory == cat) null else cat }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = EvaTextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Nicio amintire în această categorie", color = EvaTextSecondary)
                    Text(
                        "EVA-1 învață automat din conversații prin motorul Auto-Genesis",
                        color = EvaTextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { mem ->
                    MemoryCard(
                        entry = mem,
                        onDelete = { onIntent(EvaIntent.DeleteMemory(mem.id)) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { k, v, cat, imp ->
                onIntent(EvaIntent.AddMemory(k, v, cat, imp))
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MemoryCard(entry: MemoryEntry, onDelete: () -> Unit) {
    val catColor = when (entry.category) {
        MemoryCategory.USER_PREF -> EvaBlue
        MemoryCategory.FACT -> EvaCyan
        MemoryCategory.SKILL -> EvaGreen
        MemoryCategory.CONTEXT -> EvaPurple
        MemoryCategory.GOAL -> EvaAmber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EvaSurface)
            .border(0.8.dp, EvaBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(catColor.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(entry.category.label, color = catColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(entry.key, color = EvaTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(entry.value, color = EvaTextSecondary, fontSize = 13.sp)
        }
        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_memory_${entry.id}")) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Șterge amintirea", tint = EvaTextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, MemoryCategory, Float) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MemoryCategory.USER_PREF) }
    var importance by remember { mutableFloatStateOf(0.8f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EvaDarkNavy,
        title = { Text("Adaugă în Memoria EVA-1", color = EvaTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Cheie (ex: limbaj_preferat)") },
                    singleLine = true,
                    modifier = Modifier.testTag("memory_key_input")
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Valoare (ex: Kotlin & Python)") },
                    modifier = Modifier.testTag("memory_value_input")
                )
                Text("Categorie:", color = EvaTextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MemoryCategory.entries.take(3).forEach { cat ->
                        QuickChip(
                            label = cat.label,
                            active = category == cat,
                            activeColor = EvaBlue,
                            onClick = { category = cat }
                        )
                    }
                }
                Text("Importanță: ${(importance * 100).toInt()}%", color = EvaTextSecondary, fontSize = 12.sp)
                Slider(value = importance, onValueChange = { importance = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(key, value, category, importance) },
                enabled = key.isNotBlank() && value.isNotBlank(),
                modifier = Modifier.testTag("save_memory_confirm_button")
            ) {
                Text("Salvează")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anulează") }
        }
    )
}
