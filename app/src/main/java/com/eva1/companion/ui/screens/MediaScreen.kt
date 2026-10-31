package com.eva1.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eva1.companion.*
import com.eva1.companion.engines.MediaGenerationEngine
import com.eva1.companion.ui.theme.*
import java.io.File

@Composable
fun MediaScreen(state: EvaUiState, onIntent: (EvaIntent) -> Unit) {
    var selectedType by remember { mutableStateOf(MediaType.IMAGE) }
    var prompt by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(ImageStyle.REALISTIC) }
    var selectedGenre by remember { mutableStateOf(MusicGenre.CINEMATIC) }
    var durationSec by remember { mutableIntStateOf(15) }
    var selectedVoice by remember { mutableStateOf(MediaGenerationEngine.VOICE_RACHEL) }

    // Un singur LazyColumn unificat pentru tot ecranul Multimedia Studio
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 1. Header ──────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = EvaPurple, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("EVA-1 Multimedia Studio", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Imagini Flux AI • Muzică MusicGen / Synth • Voce TTS • Video AI",
                        color = EvaTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ── 2. Media Type Selector ─────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaType.entries.forEach { type ->
                    val active = type == selectedType
                    val (icon, label) = when (type) {
                        MediaType.IMAGE -> "🖼️" to "Imagini"
                        MediaType.AUDIO_MUSIC -> "🎵" to "Muzică"
                        MediaType.AUDIO_SPEECH -> "🗣️" to "Voce"
                        MediaType.VIDEO -> "🎬" to "Video"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) EvaPurple.copy(alpha = 0.25f) else EvaSurface)
                            .border(1.dp, if (active) EvaPurple else EvaBorder, RoundedCornerShape(12.dp))
                            .clickable { selectedType = type }
                            .padding(vertical = 8.dp)
                            .testTag("media_type_${type.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$icon $label",
                            color = if (active) EvaTextPrimary else EvaTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // ── 3. Options Specific to Selected Media Type ─────────────
        item {
            when (selectedType) {
                MediaType.IMAGE -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ImageStyle.entries) { style ->
                            val active = style == selectedStyle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) EvaCyan.copy(alpha = 0.2f) else EvaSurface)
                                    .border(0.8.dp, if (active) EvaCyan else EvaBorder, RoundedCornerShape(12.dp))
                                    .clickable { selectedStyle = style }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(style.displayName, color = if (active) EvaCyan else EvaTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
                MediaType.AUDIO_MUSIC -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(MusicGenre.entries) { genre ->
                            val active = genre == selectedGenre
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) EvaGreen.copy(alpha = 0.2f) else EvaSurface)
                                    .border(0.8.dp, if (active) EvaGreen else EvaBorder, RoundedCornerShape(12.dp))
                                    .clickable { selectedGenre = genre }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(genre.displayName, color = if (active) EvaGreen else EvaTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
                MediaType.AUDIO_SPEECH -> {
                    val voices = listOf(
                        MediaGenerationEngine.VOICE_RACHEL to "Rachel (Feminin Cald)",
                        MediaGenerationEngine.VOICE_BELLA to "Bella (Feminin Expresiv)",
                        MediaGenerationEngine.VOICE_ADAM to "Adam (Masculin Profund)",
                        MediaGenerationEngine.VOICE_ANTONI to "Antoni (Masculin Clar)"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(voices) { (id, name) ->
                            val active = id == selectedVoice
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) EvaAmber.copy(alpha = 0.2f) else EvaSurface)
                                    .border(0.8.dp, if (active) EvaAmber else EvaBorder, RoundedCornerShape(12.dp))
                                    .clickable { selectedVoice = id }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(name, color = if (active) EvaAmber else EvaTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
                MediaType.VIDEO -> {
                    Text(
                        "🎬 Generare Video AI (4 secunde, 24 FPS, Zeroscope v2 XL)",
                        color = EvaCyan,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ── 4. Prompt Input + Generate Button ──────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvaSurface)
                    .border(1.dp, EvaBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("media_prompt_input"),
                    placeholder = {
                        Text(
                            when (selectedType) {
                                MediaType.IMAGE -> "Descrie imaginea (ex: 'Un oraș futurist pe Marte la apus')..."
                                MediaType.AUDIO_MUSIC -> "Descrie melodia (ex: 'Pian emoționant cu vioară și ploaie')..."
                                MediaType.AUDIO_SPEECH -> "Scrie textul pe care vrei să îl rostească EVA-1..."
                                MediaType.VIDEO -> "Descrie scena video (ex: 'Valurile oceanului sub aurora boreală')..."
                            },
                            color = EvaTextMuted,
                            fontSize = 12.sp
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EvaPurple,
                        unfocusedBorderColor = EvaBorder,
                        focusedContainerColor = EvaBlack,
                        unfocusedContainerColor = EvaBlack
                    )
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (prompt.isNotBlank() && !state.isGeneratingMedia) {
                            when (selectedType) {
                                MediaType.IMAGE -> onIntent(EvaIntent.GenerateImage(prompt, selectedStyle))
                                MediaType.AUDIO_MUSIC -> onIntent(EvaIntent.GenerateMusic(prompt, selectedGenre, durationSec))
                                MediaType.AUDIO_SPEECH -> onIntent(EvaIntent.GenerateSpeech(prompt, selectedVoice))
                                MediaType.VIDEO -> onIntent(EvaIntent.GenerateVideo(prompt, 4))
                            }
                            prompt = ""
                        }
                    },
                    enabled = prompt.isNotBlank() && !state.isGeneratingMedia,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_media_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EvaPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isGeneratingMedia) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(state.mediaProgressText.ifBlank { "Generare în curs..." }, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generează ${selectedType.label}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── 5. Generated Media Gallery ─────────────────────────────
        if (state.generatedMedia.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Collections, contentDescription = null, tint = EvaTextMuted, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Galeria ta multimedia este goală", color = EvaTextSecondary, fontSize = 13.sp)
                        Text("Imaginile Flux AI sunt 100% gratuite și nu necesită cheie API!", color = EvaGreen, fontSize = 11.sp)
                    }
                }
            }
        } else {
            items(state.generatedMedia, key = { it.id }) { media ->
                MediaItemCard(
                    media = media,
                    isPlaying = state.currentlyPlayingMediaId == media.id,
                    onPlay = { onIntent(EvaIntent.PlayGeneratedAudio(media)) },
                    onStop = { onIntent(EvaIntent.StopAudioPlayback) },
                    onDelete = { onIntent(EvaIntent.DeleteMedia(media.id)) }
                )
            }
        }
    }
}

@Composable
fun MediaItemCard(
    media: GeneratedMedia,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EvaSurface)
            .border(1.dp, EvaBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (media.type) {
                    MediaType.IMAGE -> "🖼️ Imagine AI"
                    MediaType.AUDIO_MUSIC -> "🎵 Melodie AI"
                    MediaType.AUDIO_SPEECH -> "🗣️ Voce Sintetizată"
                    MediaType.VIDEO -> "🎬 Videoclip AI"
                },
                color = EvaCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Șterge", tint = EvaTextMuted, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(media.prompt, color = EvaTextPrimary, fontSize = 13.sp)

        if (media.type == MediaType.IMAGE) {
            Spacer(Modifier.height(8.dp))
            val imgSource = if (media.localPath.isNotBlank() && File(media.localPath).exists()) {
                File(media.localPath)
            } else {
                media.url
            }
            AsyncImage(
                model = imgSource,
                contentDescription = media.prompt,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.8.dp, EvaBorder, RoundedCornerShape(12.dp))
            )
        } else if (media.type == MediaType.AUDIO_MUSIC || media.type == MediaType.AUDIO_SPEECH) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(EvaBlack)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (isPlaying) onStop() else onPlay() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isPlaying) EvaRed else EvaGreen)
                        .testTag("play_audio_${media.id}")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Oprește" else "Redă",
                        tint = Color.Black
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        media.metadata["engine"] ?: "Audio generat",
                        color = EvaGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isPlaying) "În curs de redare..." else "Apasă Play pentru audiție",
                        color = EvaTextMuted,
                        fontSize = 10.sp
                    )
                }
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = EvaGreen)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            val framePathsRaw = media.metadata["frame_paths"].orEmpty()
            val frameFiles = remember(framePathsRaw, media.metadata["preview_path"]) {
                val paths = framePathsRaw.split("|")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && File(it).exists() }
                if (paths.isNotEmpty()) {
                    paths.map { File(it) }
                } else {
                    val previewPath = media.metadata["preview_path"].orEmpty()
                    if (previewPath.isNotBlank() && File(previewPath).exists()) listOf(File(previewPath)) else emptyList()
                }
            }
            var isVideoAnimating by remember { mutableStateOf(true) }
            var currentFrameIdx by remember { mutableIntStateOf(0) }

            if (frameFiles.size > 1 && (isVideoAnimating || isPlaying)) {
                LaunchedEffect(frameFiles.size, isVideoAnimating, isPlaying) {
                    while (isVideoAnimating || isPlaying) {
                        kotlinx.coroutines.delay(125L)
                        currentFrameIdx = (currentFrameIdx + 1) % frameFiles.size
                    }
                }
            }

            if (frameFiles.isNotEmpty()) {
                val activeFile = frameFiles[currentFrameIdx.coerceIn(0, frameFiles.lastIndex)]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, EvaCyan, RoundedCornerShape(12.dp))
                        .clickable { isVideoAnimating = !isVideoAnimating }
                ) {
                    AsyncImage(
                        model = activeFile,
                        contentDescription = media.prompt,
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
                            text = if (isVideoAnimating || isPlaying) {
                                "🎬 REDARE VIDEO AI • Cadru ${currentFrameIdx + 1}/${frameFiles.size}"
                            } else {
                                "⏸️ PAUZĂ VIDEO (Apasă pe video pt. Play)"
                            },
                            color = EvaCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(EvaBlack)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = EvaCyan)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        media.metadata["engine"] ?: "EVA-1 Local Video Synthesizer",
                        color = EvaGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${media.localPath.substringAfterLast("/")} • ${media.durationSeconds}s (${frameFiles.size.coerceAtLeast(1)} cadre animate)",
                        color = EvaTextMuted,
                        fontSize = 10.sp
                    )
                }
                IconButton(
                    onClick = {
                        isVideoAnimating = !isVideoAnimating
                        if (!media.metadata["soundtrack_path"].isNullOrBlank()) {
                            if (isPlaying) onStop() else onPlay()
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isVideoAnimating || isPlaying) EvaRed else EvaCyan)
                ) {
                    Icon(
                        imageVector = if (isVideoAnimating || isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Redă sau oprește videoclipul",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}
