package com.example.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.MusicModelOption
import com.example.analyzer.FormatUtils
import com.example.data.local.DownloadEntity
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.shimmerSkeleton
import com.example.ui.theme.vortexPrimaryGradient

@Composable
fun AiMusicTab(
    viewModel: AiStudioViewModel,
    onPlayTrack: (DownloadEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extra = LocalVortexColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Lyria Music Studio",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Compose original tracks with Lyria 3 Clip (up to 30s) or Lyria 3 Pro (full-length).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MusicModelOption.entries.forEach { option ->
                            FilterChip(
                                selected = state.selectedMusicModel == option,
                                onClick = { viewModel.selectMusicModel(option) },
                                label = { Text(option.displayName) },
                                modifier = Modifier.testTag("music_model_${option.modelId}")
                            )
                        }
                    }

                    Text(
                        text = state.selectedMusicModel.durationHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )

                    OutlinedTextField(
                        value = state.musicPrompt,
                        onValueChange = viewModel::onMusicPromptChange,
                        label = { Text("Genre, tempo, instruments, and mood") },
                        placeholder = { Text("Synthwave cyberpunk instrumental at 115 BPM with analog bass") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedBorderColor = extra.glassBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("music_prompt_input")
                    )

                    Button(
                        onClick = viewModel::generateMusic,
                        enabled = state.musicState !is AsyncActionState.Loading,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_music_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(vortexPrimaryGradient()),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compose Track", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        item {
            when (val res = state.musicState) {
                AsyncActionState.Idle -> Unit
                AsyncActionState.Loading -> {
                    GlassCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Composing with ${state.selectedMusicModel.displayName}…",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .shimmerSkeleton()
                            )
                        }
                    }
                }
                is AsyncActionState.Error -> {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = res.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                is AsyncActionState.Success -> {
                    val track = res.data
                    GlassCard(glowBorder = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = null,
                                    tint = extra.success
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Saved to Audio Library • ${FormatUtils.formatBytes(track.totalBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = { onPlayTrack(track) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("play_generated_music_button")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play in Vortex ExoPlayer")
                            }
                        }
                    }
                }
            }
        }
    }
}
