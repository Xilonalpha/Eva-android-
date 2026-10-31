package com.eva1.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eva1.companion.*
import com.eva1.companion.ui.theme.*

@Composable
fun CodeScreen(state: EvaUiState, onIntent: (EvaIntent) -> Unit) {
    var aiPrompt by remember { mutableStateOf("") }
    var showStdin by remember { mutableStateOf(false) }
    var showSavedProjects by remember { mutableStateOf(false) }
    var projectTitle by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 1. Header + Language Selector ──────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = EvaCyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("EVA-1 Code Studio & Compiler", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Compilare cloud & sandbox în ${CodeLanguage.entries.size} limbaje • AI Generator & Auto-Fix",
                    color = EvaTextSecondary,
                    fontSize = 11.sp
                )
            }
            if (state.codeProjects.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showSavedProjects = !showSavedProjects },
                    modifier = Modifier.testTag("toggle_saved_projects_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${state.codeProjects.size}", fontSize = 11.sp)
                }
            }
        }

        // ── Saved Projects Drawer ──────────────────────────────────
        if (showSavedProjects && state.codeProjects.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.codeProjects, key = { it.id }) { proj ->
                    Surface(
                        color = EvaSurfaceHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .border(0.8.dp, EvaBorder, RoundedCornerShape(10.dp))
                            .clickable { onIntent(EvaIntent.LoadCodeProject(proj)) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(proj.language.badge, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(proj.title, fontSize = 12.sp, color = EvaTextPrimary)
                        }
                    }
                }
            }
        }

        // ── 2. Language Chips Row ──────────────────────────────────
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(CodeLanguage.entries) { lang ->
                val selected = lang == state.selectedLanguage
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) EvaBlue.copy(alpha = 0.25f) else EvaSurface)
                        .border(1.dp, if (selected) EvaBlue else EvaBorder, RoundedCornerShape(14.dp))
                        .clickable { onIntent(EvaIntent.SelectCodeLanguage(lang)) }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                        .testTag("lang_chip_${lang.name.lowercase()}")
                ) {
                    Text(
                        "${lang.badge} ${lang.displayName}",
                        color = if (selected) EvaBlueBright else EvaTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // ── 3. AI Code Generator Bar ───────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EvaSurface)
                .border(1.dp, EvaPurple.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EvaPurple, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = aiPrompt,
                onValueChange = { aiPrompt = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_code_prompt_input"),
                placeholder = {
                    Text(
                        "Cere AI-ului să scrie cod (ex: 'algoritm Dijkstra', 'calculator științific')...",
                        fontSize = 12.sp,
                        color = EvaTextMuted
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EvaPurple,
                    unfocusedBorderColor = EvaBorder,
                    focusedContainerColor = EvaBlack,
                    unfocusedContainerColor = EvaBlack
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (aiPrompt.isNotBlank()) {
                        onIntent(EvaIntent.GenerateCodeFromPrompt(aiPrompt))
                        aiPrompt = ""
                    }
                },
                enabled = aiPrompt.isNotBlank() && !state.isGeneratingCode,
                modifier = Modifier.testTag("generate_code_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EvaPurple),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (state.isGeneratingCode) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Generează", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── 4. Code Editor Area ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 360.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF050810))
                .border(1.dp, EvaBorder, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EvaDarkNavy)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "main.${state.selectedLanguage.ext}",
                    color = EvaCyan,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { onIntent(EvaIntent.OptimizeCurrentCode) },
                    enabled = !state.isGeneratingCode,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = EvaAmber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Optimizează", color = EvaAmber, fontSize = 11.sp)
                }
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(state.editorCode)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiază codul", tint = EvaTextSecondary, modifier = Modifier.size(15.dp))
                }
                IconButton(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Salvează proiectul", tint = EvaGreen, modifier = Modifier.size(15.dp))
                }
            }

            val vScroll = rememberScrollState()
            val hScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
                    .padding(8.dp)
            ) {
                val lineCount = state.editorCode.lines().size.coerceAtLeast(1)
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    for (i in 1..lineCount) {
                        Text(
                            text = i.toString().padStart(3, ' '),
                            color = EvaTextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 19.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                ) {
                    BasicTextField(
                        value = state.editorCode,
                        onValueChange = { onIntent(EvaIntent.SetEditorCode(it)) },
                        textStyle = TextStyle(
                            color = Color(0xFFE2F1FF),
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 19.sp
                        ),
                        cursorBrush = SolidColor(EvaCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 200.dp)
                            .testTag("code_editor_input")
                    )
                }
            }
        }

        // ── 5. Action Buttons Row (Run, Stdin, Fix Error) ──────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onIntent(EvaIntent.ExecuteCode) },
                enabled = !state.isExecutingCode && state.editorCode.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("run_code_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EvaGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isExecutingCode) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EvaBlack)
                    Spacer(Modifier.width(8.dp))
                    Text("Compilare & Rulare...", color = EvaBlack, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = EvaBlack)
                    Spacer(Modifier.width(6.dp))
                    Text("Compilează & Rulează", color = EvaBlack, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = { showStdin = !showStdin },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Input (stdin)", fontSize = 12.sp)
            }

            if (state.lastExecutionResult != null && !state.lastExecutionResult.isSuccess) {
                Button(
                    onClick = { onIntent(EvaIntent.FixCurrentCodeError) },
                    colors = ButtonDefaults.buttonColors(containerColor = EvaRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("auto_fix_code_button")
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Repară cu AI", fontSize = 12.sp)
                }
            }
        }

        if (showStdin) {
            OutlinedTextField(
                value = state.codeStdin,
                onValueChange = { onIntent(EvaIntent.SetCodeStdin(it)) },
                label = { Text("Date de intrare (stdin) pentru program") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }

        // ── 6. Console Output Area ─────────────────────────────────
        state.lastExecutionResult?.let { res ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF060A12))
                    .border(
                        1.dp,
                        if (res.isSuccess) EvaGreen.copy(alpha = 0.5f) else EvaRed.copy(alpha = 0.6f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (res.isSuccess) EvaGreen else EvaRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (res.isSuccess) "Execuție reușită (exit 0)" else "Eroare compilare / execuție",
                        color = if (res.isSuccess) EvaGreen else EvaRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${res.executionTimeMs}ms",
                        color = EvaTextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (res.isSuccess) res.output.ifBlank { "(Fără output în consolă)" } else res.error,
                    color = if (res.isSuccess) EvaGreen else EvaRed,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = EvaDarkNavy,
            title = { Text("Salvează Proiectul de Cod") },
            text = {
                OutlinedTextField(
                    value = projectTitle,
                    onValueChange = { projectTitle = it },
                    label = { Text("Titlu proiect") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onIntent(EvaIntent.SaveCodeProject(projectTitle))
                    showSaveDialog = false
                    projectTitle = ""
                }) {
                    Text("Salvează")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Anulează") }
            }
        )
    }
}
