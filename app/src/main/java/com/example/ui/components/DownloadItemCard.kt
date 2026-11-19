package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.analyzer.FormatUtils
import com.example.data.local.DownloadCategory
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.vortexPrimaryGradient

@Composable
fun DownloadItemCard(
    item: DownloadEntity,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onPlayOrOpen: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = LocalVortexColors.current
    val status = item.statusEnum
    val animatedProgress by animateFloatAsState(
        targetValue = item.progressFraction,
        label = "dl_progress_${item.id}"
    )

    val categoryIcon = when (item.categoryEnum) {
        DownloadCategory.VIDEO -> Icons.Rounded.Videocam
        DownloadCategory.AUDIO -> Icons.Rounded.MusicNote
        DownloadCategory.DOCUMENT -> Icons.Rounded.Description
        DownloadCategory.APK -> Icons.Rounded.Android
        DownloadCategory.OTHER -> Icons.Rounded.Folder
    }

    val statusColor = when (status) {
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.tertiary
        DownloadStatus.QUEUED -> MaterialTheme.colorScheme.secondary
        DownloadStatus.PAUSED -> extra.warning
        DownloadStatus.COMPLETED -> extra.success
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_card_${item.id}"),
        glowBorder = status == DownloadStatus.DOWNLOADING
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = item.categoryEnum.label,
                        tint = statusColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(item.resolutionLabel)
                            append(" • ")
                            append(
                                if (item.totalBytes > 0L) {
                                    "${FormatUtils.formatBytes(item.downloadedBytes)} / ${FormatUtils.formatBytes(item.totalBytes)}"
                                } else {
                                    FormatUtils.formatBytes(item.downloadedBytes)
                                }
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                        IconButton(
                            onClick = { onPause(item.id) },
                            modifier = Modifier.testTag("pause_btn_${item.id}")
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = "Pause download")
                        }
                        IconButton(
                            onClick = { onCancel(item.id) },
                            modifier = Modifier.testTag("cancel_btn_${item.id}")
                        ) {
                            Icon(Icons.Rounded.Cancel, contentDescription = "Cancel download")
                        }
                    }

                    DownloadStatus.PAUSED -> {
                        IconButton(
                            onClick = { onResume(item.id) },
                            modifier = Modifier.testTag("resume_btn_${item.id}")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume download", tint = extra.success)
                        }
                        IconButton(
                            onClick = { onCancel(item.id) },
                            modifier = Modifier.testTag("cancel_btn_${item.id}")
                        ) {
                            Icon(Icons.Rounded.Cancel, contentDescription = "Cancel download")
                        }
                    }

                    DownloadStatus.FAILED -> {
                        IconButton(
                            onClick = { onRetry(item.id) },
                            modifier = Modifier.testTag("retry_btn_${item.id}")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Retry download", tint = MaterialTheme.colorScheme.tertiary)
                        }
                        IconButton(
                            onClick = { onCancel(item.id) },
                            modifier = Modifier.testTag("cancel_btn_${item.id}")
                        ) {
                            Icon(Icons.Rounded.Cancel, contentDescription = "Remove download")
                        }
                    }

                    DownloadStatus.COMPLETED -> {
                        IconButton(
                            onClick = { onPlayOrOpen(item) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                .testTag("open_completed_btn_${item.id}")
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Play or open file",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    DownloadStatus.CANCELLED -> Unit
                }
            }

            if (status != DownloadStatus.COMPLETED && status != DownloadStatus.CANCELLED) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0.03f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(vortexPrimaryGradient())
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val statusLabel = when (status) {
                        DownloadStatus.DOWNLOADING -> "${(animatedProgress * 100).toInt()}% • ${FormatUtils.formatSpeed(item.speedBytesPerSec)}"
                        DownloadStatus.QUEUED -> "Queued for slot…"
                        DownloadStatus.PAUSED -> if (item.errorMessage == "PAUSED_NETWORK") "Paused (waiting for network)" else "Paused (${(animatedProgress * 100).toInt()}%)"
                        DownloadStatus.FAILED -> item.errorMessage ?: "Download failed"
                        else -> ""
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (status == DownloadStatus.DOWNLOADING && item.etaSeconds > 0L) {
                        Text(
                            text = "ETA ${FormatUtils.formatEta(item.etaSeconds)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
