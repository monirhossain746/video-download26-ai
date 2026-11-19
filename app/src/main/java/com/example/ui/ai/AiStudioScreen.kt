package com.example.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity

private enum class AiStudioSection(
    val title: String,
    val icon: ImageVector,
    val tag: String
) {
    CHAT("Chat", Icons.Rounded.Chat, "ai_tab_chat"),
    VOICE("Live Voice", Icons.Rounded.RecordVoiceOver, "ai_tab_voice"),
    TRANSCRIBE("Transcribe", Icons.Rounded.GraphicEq, "ai_tab_transcribe"),
    IMAGES("Images", Icons.Rounded.Image, "ai_tab_images"),
    MUSIC("Music", Icons.Rounded.MusicNote, "ai_tab_music")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudioScreen(
    chatViewModel: AiChatViewModel,
    studioViewModel: AiStudioViewModel,
    downloadDao: DownloadDao,
    initialTabIndex: Int = 0,
    tabRequestNonce: Int = 0,
    onPlayTrack: (DownloadEntity) -> Unit
) {
    var selectedTab by rememberSaveable {
        mutableIntStateOf(initialTabIndex.coerceIn(0, AiStudioSection.entries.lastIndex))
    }

    LaunchedEffect(tabRequestNonce, initialTabIndex) {
        if (tabRequestNonce > 0) {
            selectedTab = initialTabIndex.coerceIn(0, AiStudioSection.entries.lastIndex)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(
                    text = "Vortex AI Studio",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Chat, Live Voice, Transcription, Image Editing & Lyria Music",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                AiStudioSection.entries.forEachIndexed { index, section ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = section.title
                            )
                        },
                        text = { Text(section.title) },
                        modifier = Modifier.testTag(section.tag)
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (AiStudioSection.entries[selectedTab]) {
                    AiStudioSection.CHAT -> AiChatTab(viewModel = chatViewModel)
                    AiStudioSection.VOICE -> AiVoiceTab(viewModel = studioViewModel)
                    AiStudioSection.TRANSCRIBE -> AiTranscribeTab(
                        viewModel = studioViewModel,
                        downloadDao = downloadDao
                    )
                    AiStudioSection.IMAGES -> AiImageTab(viewModel = studioViewModel)
                    AiStudioSection.MUSIC -> AiMusicTab(
                        viewModel = studioViewModel,
                        onPlayTrack = onPlayTrack
                    )
                }
            }
        }
    }
}
