package com.example.ui.ai

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DownloadCategory
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadStatus
import com.example.ui.theme.GlassCard
import com.example.ui.theme.shimmerSkeleton
import com.example.ui.theme.vortexPrimaryGradient
import java.io.File

@Composable
fun AiTranscribeTab(
    viewModel: AiStudioViewModel,
    downloadDao: DownloadDao
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val completedDownloads by remember { downloadDao.observeCompletedDownloads() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val audioItems = remember(completedDownloads) {
        completedDownloads.filter { it.categoryEnum == DownloadCategory.AUDIO && it.statusEnum == DownloadStatus.COMPLETED }
    }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var copiedNotice by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionMessage = null
            viewModel.startMicRecording(context)
        } else {
            permissionMessage = "Microphone permission is required to record speech for transcription."
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard(glowBorder = state.isRecordingAudio) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Audio Transcription (gemini-3.5-transcribe)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Record directly from your microphone or tap any downloaded audio file below to transcribe it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            if (state.isRecordingAudio) {
                                viewModel.stopMicRecordingAndTranscribe()
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    permissionMessage = null
                                    viewModel.startMicRecording(context)
                                } else {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("transcribe_record_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .then(
                                    if (state.isRecordingAudio) {
                                        Modifier.background(MaterialTheme.colorScheme.error)
                                    } else {
                                        Modifier.background(vortexPrimaryGradient())
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (state.isRecordingAudio) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.isRecordingAudio) {
                                        "Stop & Transcribe (${state.recordingSeconds}s)"
                                    } else {
                                        "Record Microphone & Transcribe"
                                    },
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (permissionMessage != null) {
                        Text(
                            text = permissionMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Transcription Result Card
        item {
            when (val res = state.transcribeState) {
                AsyncActionState.Idle -> Unit
                AsyncActionState.Loading -> {
                    GlassCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Transcribing audio with Gemini 3.5…",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .shimmerSkeleton()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(18.dp)
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
                    GlassCard(glowBorder = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (copiedNotice) "Transcript (Copied!)" else "Transcript",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Transcript", res.data))
                                        copiedNotice = true
                                    },
                                    modifier = Modifier.testTag("copy_transcript_button")
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy transcript")
                                }
                            }
                            Text(
                                text = res.data,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Library Audio Files to Transcribe
        if (audioItems.isNotEmpty()) {
            item {
                Text(
                    text = "Transcribe From Audio Library",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(audioItems, key = { it.id }) { item ->
                GlassCard(
                    modifier = Modifier.clickable {
                        copiedNotice = false
                        viewModel.transcribeFile(
                            file = File(item.localFilePath),
                            mimeType = item.mimeType.ifBlank { "audio/mpeg" }
                        )
                    },
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AudioFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Tap to transcribe • ${item.mimeType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
