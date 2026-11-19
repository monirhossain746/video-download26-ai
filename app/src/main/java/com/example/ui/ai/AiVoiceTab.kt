package com.example.ui.ai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.vortexPrimaryGradient

private val VOICE_OPTIONS = listOf("FENRIR", "PUCK", "KORE", "AOEDE")

@Composable
fun AiVoiceTab(viewModel: AiStudioViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extra = LocalVortexColors.current
    var permissionError by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionError = null
            viewModel.startLiveVoiceSession()
        } else {
            permissionError = "Microphone permission is required for real-time voice conversations."
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state.isLiveConnected) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard(glowBorder = state.isLiveConnected) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Gemini Live Voice (gemini-3.8-live)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bidirectional low-latency voice conversation with live transcription.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VOICE_OPTIONS) { voice ->
                            FilterChip(
                                selected = state.liveVoiceName == voice,
                                onClick = { if (!state.isLiveConnected) viewModel.selectLiveVoice(voice) },
                                enabled = !state.isLiveConnected && !state.isLiveConnecting,
                                label = { Text(voice) },
                                modifier = Modifier.testTag("voice_option_$voice")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                if (state.isLiveConnected) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                                }
                            )
                            .clickable(enabled = !state.isLiveConnecting) {
                                if (state.isLiveConnected) {
                                    viewModel.stopLiveVoiceSession()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        permissionError = null
                                        viewModel.startLiveVoiceSession()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                            .testTag("live_voice_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            state.isLiveConnecting -> CircularProgressIndicator(color = Color.White)
                            state.isLiveConnected -> Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = "End live voice session",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Start live voice session",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when {
                            state.isLiveConnecting -> "Connecting to Gemini Live…"
                            state.isLiveConnected -> "Live Session Active — Tap to End"
                            else -> "Tap Microphone to Start Talking"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.isLiveConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        val combinedError = permissionError ?: state.liveError
        item {
            AnimatedVisibility(visible = combinedError != null) {
                GlassCard(contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = combinedError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Live Transcript",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (state.liveTranscripts.isEmpty()) {
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(vortexPrimaryGradient()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Real-time spoken dialogue captions will stream here during an active voice session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.liveTranscripts, key = { it.id }) { line ->
                GlassCard(contentPadding = PaddingValues(12.dp)) {
                    Column {
                        Text(
                            text = line.speaker,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (line.speaker == "You") MaterialTheme.colorScheme.tertiary else extra.glowPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
