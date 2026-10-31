package com.eva1.companion.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eva1.companion.AiEngine
import com.eva1.companion.EvaIntent
import com.eva1.companion.EvaTab
import com.eva1.companion.EvaUiState
import com.eva1.companion.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ChatScreen(state: EvaUiState, onIntent: (EvaIntent) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.messages.size, state.isLoading) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // Zero-permission Android Photo Picker cu procesare pe Dispatchers.IO
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val base64 = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                    val bmp = BitmapFactory.decodeStream(stream)
                    stream.close()
                    val scaled = scaleBitmap(bmp, 1024)
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }.getOrNull()
            }
            if (base64 != null) {
                onIntent(EvaIntent.AttachImage(base64, "image/jpeg"))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Quick Capabilities Row (Scrollabil Orizontal) ──────────
        QuickOptionsBar(state = state, onIntent = onIntent)

        // ── Messages List ──────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                WelcomePlaceholder(state = state, onIntent = onIntent)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            onSpeak = { text -> onIntent(EvaIntent.SpeakMessage(text)) }
                        )
                    }
                    if (state.isLoading) {
                        item { TypingIndicator(engine = state.selectedEngine) }
                    }
                }
            }
        }

        // ── Attached Image Preview ─────────────────────────────────
        if (state.attachedImageBase64 != null) {
            AttachedImageBar(onClear = { onIntent(EvaIntent.ClearAttachment) })
        }

        // ── Input Bar ──────────────────────────────────────────────
        ChatInputBar(
            text = state.inputText,
            isLoading = state.isLoading,
            hasAttachment = state.attachedImageBase64 != null,
            onTextChange = { onIntent(EvaIntent.SetInput(it)) },
            onAttachImage = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onMicClick = { onIntent(EvaIntent.TriggerDirectVoiceListen) },
            onSend = {
                keyboard?.hide()
                onIntent(
                    EvaIntent.SendMessage(
                        content = state.inputText,
                        imageBase64 = state.attachedImageBase64,
                        imageMime = state.attachedImageMime
                    )
                )
            }
        )
    }
}

// ── Quick Options Bar (Scrollabil orizontal, fără înghesuire) ─────
@Composable
fun QuickOptionsBar(state: EvaUiState, onIntent: (EvaIntent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EvaDarkNavy.copy(alpha = 0.7f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickChip(
            label = if (state.selectedEngine == AiEngine.LOCAL_LLM) "⚡ Local LLM (Activ)" else "⚡ Local LLM",
            active = state.selectedEngine == AiEngine.LOCAL_LLM,
            activeColor = EvaGreen,
            onClick = {
                val target = if (state.selectedEngine == AiEngine.LOCAL_LLM) AiEngine.CLAUDE else AiEngine.LOCAL_LLM
                onIntent(EvaIntent.SwitchEngine(target))
            }
        )
        if (state.selectedEngine == AiEngine.CLAUDE) {
            QuickChip(
                label = "Gândire Extinsă",
                active = state.useExtendedThinking,
                activeColor = EvaPurple,
                onClick = { onIntent(EvaIntent.ToggleThinking(!state.useExtendedThinking)) }
            )
        }
        QuickChip(
            label = "Căutare Web",
            active = state.useWebSearch,
            activeColor = EvaCyan,
            onClick = { onIntent(EvaIntent.ToggleWebSearch(!state.useWebSearch)) }
        )
        QuickChip(
            label = if (state.autoSpeakReplies) "🔊 Voce ON" else "🔇 Voce OFF",
            active = state.autoSpeakReplies,
            activeColor = EvaGreen,
            onClick = { onIntent(EvaIntent.ToggleAutoSpeak(!state.autoSpeakReplies)) }
        )
        if (state.isTtsSpeaking) {
            QuickChip(
                label = "⏹️ Stop Voce",
                active = true,
                activeColor = EvaRed,
                onClick = { onIntent(EvaIntent.StopSpeaking) }
            )
        }
        QuickChip(
            label = "🗄️ ${state.memories.size} amintiri",
            active = false,
            activeColor = EvaBlue,
            onClick = { onIntent(EvaIntent.SwitchTab(EvaTab.MEMORY)) }
        )
    }
}

@Composable
fun QuickChip(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) activeColor.copy(alpha = 0.18f) else EvaSurface)
            .border(
                0.8.dp,
                if (active) activeColor else EvaBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = if (active) activeColor else EvaTextMuted,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

// ── Welcome Screen (Scrollabil vertical pe orice ecran) ───────────
@Composable
fun WelcomePlaceholder(state: EvaUiState, onIntent: (EvaIntent) -> Unit) {
    val suggestions = listOf(
        "🔍 Caută cele mai noi descoperiri AI din 2026",
        "🕒 Ce dată și ce oră este acum în 2026?",
        "💻 Scrie un script Kotlin pentru analiza datelor în timp real",
        "⚡ Planifică-mi un proiect software complex pas cu pas"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(EvaBlue, EvaPurple, EvaDarkNavy))),
            contentAlignment = Alignment.Center
        ) {
            Text("Siri", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(10.dp))

        Text("Bun venit în EVA-1 v2.0", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Companion AI cu 11 module active • ${state.selectedEngine.displayName}",
            color = EvaTextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(10.dp))

        // Quantum Context Live Status Card
        Surface(
            color = EvaSurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.8.dp, EvaPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { onIntent(EvaIntent.SwitchTab(EvaTab.QUANTUM)) }
                .testTag("welcome_quantum_card")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = EvaPurple)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Quantum Zero-UI: ${state.quantumStats.ZeroUIMode}",
                        color = EvaTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Intenție prezisă: ${state.quantumStats.predictedNeed}",
                        color = EvaCyan,
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = EvaTextMuted)
            }
        }

        Spacer(Modifier.height(12.dp))

        suggestions.forEach { suggestion ->
            val cleanPrompt = suggestion.substringAfter(" ")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EvaSurface)
                    .border(0.8.dp, EvaBorder, RoundedCornerShape(12.dp))
                    .clickable { onIntent(EvaIntent.SendMessage(cleanPrompt)) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(suggestion, color = EvaTextSecondary, fontSize = 13.sp)
            }
        }
    }
}

// ── Typing Indicator ──────────────────────────────────────────────
@Composable
fun TypingIndicator(engine: AiEngine) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(EvaSurface)
            .border(0.8.dp, EvaBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = when (engine) {
                AiEngine.CLAUDE -> EvaBlue
                AiEngine.MISTRAL -> EvaCyan
                AiEngine.LOCAL_LLM -> EvaGreen
            }
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${engine.displayName} procesează...",
            color = EvaTextSecondary,
            fontSize = 12.sp
        )
    }
}

// ── Attached Image Bar ─────────────────────────────────────────────
@Composable
fun AttachedImageBar(onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EvaSurface)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Image, contentDescription = null, tint = EvaCyan, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Imagine atașată (gata de analiză vizuală)", color = EvaCyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Elimină imaginea", tint = EvaTextMuted, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Chat Input Bar ─────────────────────────────────────────────────
@Composable
fun ChatInputBar(
    text: String,
    isLoading: Boolean,
    hasAttachment: Boolean,
    onTextChange: (String) -> Unit,
    onAttachImage: () -> Unit,
    onMicClick: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = EvaDarkNavy,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = EvaBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAttachImage,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("attach_image_button")
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = "Atașează imagine",
                    tint = if (hasAttachment) EvaCyan else EvaTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                placeholder = { Text("Scrie sau vorbește cu EVA-1...", color = EvaTextMuted, fontSize = 13.sp) },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EvaBlue,
                    unfocusedBorderColor = EvaBorder,
                    focusedContainerColor = EvaSurface,
                    unfocusedContainerColor = EvaSurface,
                    cursorColor = EvaBlue,
                    focusedTextColor = EvaTextPrimary,
                    unfocusedTextColor = EvaTextPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isLoading) onSend() })
            )

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(EvaSurfaceHigh)
                    .border(1.dp, EvaCyan.copy(alpha = 0.5f), CircleShape)
                    .testTag("chat_mic_button")
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Vorbește cu EVA-1",
                    tint = EvaCyan,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            val canSend = (text.isNotBlank() || hasAttachment) && !isLoading
            IconButton(
                onClick = { if (canSend) onSend() },
                enabled = canSend,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) Brush.linearGradient(listOf(EvaBlue, EvaPurple))
                        else Brush.linearGradient(listOf(EvaSurface, EvaSurface))
                    )
                    .testTag("send_message_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Trimite mesaj",
                    tint = if (canSend) Color.White else EvaTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun scaleBitmap(bmp: Bitmap, maxDim: Int): Bitmap {
    val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height, 1f)
    return Bitmap.createScaledBitmap(
        bmp,
        (bmp.width * ratio).toInt().coerceAtLeast(1),
        (bmp.height * ratio).toInt().coerceAtLeast(1),
        true
    )
}
