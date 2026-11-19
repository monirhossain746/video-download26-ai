package com.example.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.analyzer.FormatUtils
import com.example.data.local.DownloadCategory
import com.example.data.local.DownloadEntity
import com.example.ui.components.DownloadItemCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MediaAnalysisSheet
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.vortexPrimaryGradient

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateDownloads: (Int) -> Unit,
    onNavigateAiStudio: (Int) -> Unit,
    onNavigateBrowser: () -> Unit,
    onNavigateLibrary: (DownloadCategory?) -> Unit,
    onNavigateSettings: () -> Unit,
    onOpenMedia: (DownloadEntity) -> Unit,
    onStreamUrl: (String, String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val extra = LocalVortexColors.current

    LaunchedEffect(state.clipboardAutoDetectEnabled) {
        if (state.clipboardAutoDetectEnabled) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            viewModel.onClipboardChecked(clipText)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Header Card with Generated Artwork & Live Stats
            item {
                GlassCard(
                    glowBorder = true,
                    contentPadding = PaddingValues(0.dp),
                    elevation = 10.dp
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_banner),
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(if (extra.isDark) 0.42f else 0.16f),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                        )

                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_vortex_icon),
                                        contentDescription = "Vortex Emblem",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Vortex",
                                            style = MaterialTheme.typography.headlineMedium
                                        )
                                        Text(
                                            text = stringResource(R.string.tagline),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(extra.success.copy(alpha = 0.16f))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Security,
                                            contentDescription = "TLS HTTPS Protected",
                                            tint = extra.success,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "TLS Vault",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extra.success
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = onNavigateSettings,
                                        modifier = Modifier.testTag("header_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = "Open Settings",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Animated Download Statistics Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatPill(
                                    label = "Active",
                                    value = state.activeCount.toString(),
                                    subValue = if (state.currentTotalSpeedBps > 0L) {
                                        FormatUtils.formatSpeed(state.currentTotalSpeedBps)
                                    } else "Idle",
                                    accent = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f)
                                )
                                StatPill(
                                    label = "Completed",
                                    value = state.completedCount.toString(),
                                    subValue = FormatUtils.formatBytes(state.totalDownloadedBytes),
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                StatPill(
                                    label = "Free Space",
                                    value = FormatUtils.formatBytes(state.availableStorageBytes),
                                    subValue = "Scoped Vault",
                                    accent = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Local Clipboard Suggestion Banner
            item {
                AnimatedVisibility(visible = state.detectedClipboardUrl != null) {
                    val detected = state.detectedClipboardUrl.orEmpty()
                    GlassCard(glowBorder = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Media URL Detected on Clipboard",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = detected,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = { viewModel.useClipboardSuggestion() }) {
                                Text("Analyze")
                            }
                            IconButton(onClick = { viewModel.dismissClipboardSuggestion() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
            }

            // Video URL Input Box + Paste + Analyze
            item {
                GlassCard {
                    Column {
                        Text(
                            text = "Analyze & Download Media",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Supports HTTPS direct video, audio, HLS streams, PDF, ZIP & APK files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = state.urlInput,
                            onValueChange = viewModel::onUrlInputChange,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.hint_paste_url),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { viewModel.analyzeCurrentInput() }
                            ),
                            trailingIcon = {
                                if (state.urlInput.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onUrlInputChange("") }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear URL")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                                unfocusedBorderColor = extra.glassBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_url_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    if (text.isNotBlank()) {
                                        viewModel.onUrlInputChange(text.trim())
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("paste_link_button")
                            ) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_paste))
                            }

                            Button(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.analyzeCurrentInput()
                                },
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .weight(1.45f)
                                    .height(50.dp)
                                    .testTag("analyze_video_button")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(vortexPrimaryGradient()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.btn_analyze),
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Action Dashboard Tiles (Active, Completed, History, Browser, Video Library, Audio, Settings)
            item {
                Text(
                    text = "Command Center",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardTile(
                            icon = Icons.Rounded.Downloading,
                            title = "Active",
                            subtitle = "${state.activeCount} running",
                            accent = MaterialTheme.colorScheme.tertiary,
                            onClick = { onNavigateDownloads(0) },
                            modifier = Modifier.weight(1f).testTag("tile_active_downloads")
                        )
                        DashboardTile(
                            icon = Icons.Rounded.TaskAlt,
                            title = "Completed",
                            subtitle = "${state.completedCount} saved",
                            accent = extra.success,
                            onClick = { onNavigateDownloads(1) },
                            modifier = Modifier.weight(1f).testTag("tile_completed_downloads")
                        )
                        DashboardTile(
                            icon = Icons.Rounded.History,
                            title = "History",
                            subtitle = "All logs",
                            accent = MaterialTheme.colorScheme.secondary,
                            onClick = { onNavigateDownloads(2) },
                            modifier = Modifier.weight(1f).testTag("tile_history")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardTile(
                            icon = Icons.Rounded.Language,
                            title = "Browser",
                            subtitle = "Media scan",
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = onNavigateBrowser,
                            modifier = Modifier.weight(1f).testTag("tile_browser")
                        )
                        DashboardTile(
                            icon = Icons.Rounded.VideoLibrary,
                            title = "Videos",
                            subtitle = "${state.videoCount} clips",
                            accent = MaterialTheme.colorScheme.tertiary,
                            onClick = { onNavigateLibrary(DownloadCategory.VIDEO) },
                            modifier = Modifier.weight(1f).testTag("tile_video_library")
                        )
                        DashboardTile(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "Audio",
                            subtitle = "${state.audioCount} tracks",
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = { onNavigateLibrary(DownloadCategory.AUDIO) },
                            modifier = Modifier.weight(1f).testTag("tile_audio_library")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardTile(
                            icon = Icons.Rounded.Folder,
                            title = "All Files",
                            subtitle = "Vault manager",
                            accent = MaterialTheme.colorScheme.secondary,
                            onClick = { onNavigateLibrary(null) },
                            modifier = Modifier.weight(1f).testTag("tile_all_library")
                        )
                        DashboardTile(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "AI Studio",
                            subtitle = "Chat, Voice, Image",
                            accent = MaterialTheme.colorScheme.tertiary,
                            onClick = { onNavigateAiStudio(0) },
                            modifier = Modifier.weight(1f).testTag("tile_ai_studio")
                        )
                        DashboardTile(
                            icon = Icons.Rounded.Settings,
                            title = "Settings",
                            subtitle = "Engine & security",
                            accent = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onNavigateSettings,
                            modifier = Modifier.weight(1f).testTag("tile_settings")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiQuickChip("Chat", modifier = Modifier.weight(1f).testTag("home_ai_chat")) { onNavigateAiStudio(0) }
                        AiQuickChip("Voice", modifier = Modifier.weight(1f).testTag("home_ai_voice")) { onNavigateAiStudio(1) }
                        AiQuickChip("Transcribe", modifier = Modifier.weight(1f).testTag("home_ai_transcribe")) { onNavigateAiStudio(2) }
                        AiQuickChip("Image", modifier = Modifier.weight(1f).testTag("home_ai_image")) { onNavigateAiStudio(3) }
                        AiQuickChip("Music", modifier = Modifier.weight(1f).testTag("home_ai_music")) { onNavigateAiStudio(4) }
                    }
                }
            }

            // Recently Played Carousel
            if (state.recentlyPlayed.isNotEmpty()) {
                item {
                    Text(
                        text = "Recently Played",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.recentlyPlayed, key = { it.id }) { item ->
                            GlassCard(
                                modifier = Modifier
                                    .width(200.dp)
                                    .clickable { onOpenMedia(item) }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = "Resume playback",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Resume • ${FormatUtils.formatDuration(item.lastPlaybackPositionMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Activity Preview
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Downloads",
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = { onNavigateDownloads(0) }) {
                        Text("See all")
                    }
                }
            }

            if (state.recentDownloads.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Downloading,
                        title = "Ready for High-Speed Downloads",
                        subtitle = "Paste an HTTPS media link above or browse sites with the built-in browser to detect downloadable media."
                    )
                }
            } else {
                items(state.recentDownloads, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        onPause = viewModel::pauseDownload,
                        onResume = viewModel::resumeDownload,
                        onRetry = viewModel::retryDownload,
                        onCancel = viewModel::cancelDownload,
                        onPlayOrOpen = onOpenMedia
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (state.showAnalysisSheet) {
        MediaAnalysisSheet(
            isAnalyzing = state.isAnalyzing,
            result = state.analysisResult,
            onDismiss = viewModel::dismissAnalysisSheet,
            onStartDownload = viewModel::startDownload,
            onStreamPlay = { streamUrl, title ->
                viewModel.dismissAnalysisSheet()
                onStreamUrl(streamUrl, title)
            }
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    subValue: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val extra = LocalVortexColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(extra.glassSurface.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DashboardTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.clickable(onClick = onClick),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AiQuickChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val extra = LocalVortexColors.current
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(extra.glassSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
