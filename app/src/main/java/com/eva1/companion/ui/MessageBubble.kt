package com.eva1.companion.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eva1.companion.AiEngine
import com.eva1.companion.Message
import com.eva1.companion.MessageRole
import com.eva1.companion.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: Message,
    onSpeak: (String) -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER
    val clipboard = LocalClipboardManager.current
    var showThinking by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    val formattedTime = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        when (message.engine) {
                            AiEngine.CLAUDE -> Brush.linearGradient(listOf(EvaBlue, EvaPurple))
                            AiEngine.MISTRAL -> Brush.linearGradient(listOf(EvaCyan, EvaBlue))
                            AiEngine.LOCAL_LLM -> Brush.linearGradient(listOf(EvaGreen, EvaCyan))
                        }
                    )
                    .align(Alignment.Top),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (message.engine) {
                        AiEngine.CLAUDE -> "C"
                        AiEngine.MISTRAL -> "M"
                        AiEngine.LOCAL_LLM -> "L"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (message.isThinking && message.thinkingContent.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(EvaPurple.copy(alpha = 0.12f))
                        .border(0.5.dp, EvaPurple.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { showThinking = !showThinking }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = EvaPurple, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Procesul de gândire", color = EvaPurple, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (showThinking) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Afișează gândirea",
                        tint = EvaPurple,
                        modifier = Modifier.size(14.dp)
                    )
                }

                AnimatedVisibility(visible = showThinking) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(EvaSurface)
                            .border(0.5.dp, EvaBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            message.thinkingContent,
                            color = EvaTextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            if (message.imageBase64 != null) {
                val isAnimatedVideo = message.imageBase64.contains("|||")
                val frameBitmaps = remember(message.imageBase64) {
                    val parts = message.imageBase64.split("|||").filter { it.isNotBlank() }
                    parts.mapNotNull { b64 ->
                        runCatching {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }.getOrNull()
                    }
                }
                if (frameBitmaps.isNotEmpty()) {
                    var frameIdx by remember { mutableIntStateOf(0) }
                    var isPlayingAnim by remember { mutableStateOf(true) }

                    if (isAnimatedVideo && frameBitmaps.size > 1 && isPlayingAnim) {
                        LaunchedEffect(frameBitmaps.size, isPlayingAnim) {
                            while (isPlayingAnim) {
                                kotlinx.coroutines.delay(150L)
                                frameIdx = (frameIdx + 1) % frameBitmaps.size
                            }
                        }
                    }

                    val currentBmp = frameBitmaps[frameIdx.coerceIn(0, frameBitmaps.lastIndex)]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isAnimatedVideo) 190.dp else 210.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                if (isAnimatedVideo) EvaCyan else EvaBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = isAnimatedVideo) {
                                isPlayingAnim = !isPlayingAnim
                            }
                    ) {
                        Image(
                            bitmap = currentBmp,
                            contentDescription = if (isAnimatedVideo) "Videoclip AI Animat" else "Fotografie AI Generată",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isAnimatedVideo) {
                                    if (isPlayingAnim) "🎬 VIDEO AI LIVE • Cadru ${frameIdx + 1}/${frameBitmaps.size}"
                                    else "⏸️ VIDEO AI PAUZĂ (Apasă pt. Play)"
                                } else {
                                    "🎨 FOTO AI HD"
                                },
                                color = if (isAnimatedVideo) EvaCyan else EvaGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 18.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        )
                    )
                    .background(
                        if (isUser) {
                            Brush.linearGradient(listOf(EvaBlue, EvaViolet))
                        } else {
                            Brush.linearGradient(listOf(EvaSurface, EvaSurfaceHigh))
                        }
                    )
                    .then(
                        if (!isUser) Modifier.border(0.5.dp, EvaBorder, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                        else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                SelectionContainer {
                    FormattedMessageText(
                        content = message.content,
                        isUser = isUser
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formattedTime,
                    color = EvaTextMuted,
                    fontSize = 10.sp
                )
                if (!isUser) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copiază mesajul",
                        tint = if (copied) EvaGreen else EvaTextMuted,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable {
                                clipboard.setText(AnnotatedString(message.content))
                                copied = true
                            }
                            .testTag("copy_message_button_${message.id}")
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Ascultă mesajul",
                        tint = EvaCyan,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onSpeak(message.content) }
                            .testTag("speak_message_button_${message.id}")
                    )
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(EvaSurfaceHigh)
                    .border(1.dp, EvaBorder, CircleShape)
                    .align(Alignment.Top),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = EvaTextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Renderizare text cu suport pentru code blocks (optimizat cu remember) ──
@Composable
fun FormattedMessageText(content: String, isUser: Boolean) {
    val parts = remember(content) { content.split("```") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { idx, part ->
            if (idx % 2 == 1) {
                val lines = part.lines()
                val lang = lines.firstOrNull()?.trim() ?: ""
                val code = if (lang.isNotEmpty() && !lang.contains(" ")) {
                    lines.drop(1).joinToString("\n")
                } else part

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(EvaBlack)
                        .border(0.5.dp, EvaBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        if (lang.isNotEmpty()) {
                            Text(lang, color = EvaCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            code.trim(),
                            color = EvaGreen,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )
                    }
                }
            } else if (part.isNotBlank()) {
                Text(
                    part.trim(),
                    color = if (isUser) Color.White else EvaTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
