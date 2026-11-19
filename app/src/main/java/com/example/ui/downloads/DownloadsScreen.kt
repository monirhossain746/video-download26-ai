package com.example.ui.downloads

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.analyzer.FormatUtils
import com.example.data.local.DownloadEntity
import com.example.ui.components.DownloadItemCard
import com.example.ui.components.EmptyStateCard

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    initialTab: Int = 0,
    onOpenMedia: (DownloadEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialTab) {
        viewModel.selectTab(initialTab)
    }

    val tabs = listOf(
        "Active (${state.activeItems.size})",
        "Completed (${state.completedItems.size})",
        "History (${state.historyItems.size})"
    )

    val displayedItems = when (state.selectedTab) {
        0 -> state.activeItems
        1 -> state.completedItems
        else -> state.historyItems
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Download Manager",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = if (state.aggregateSpeedBps > 0L) {
                            "Live Speed: ${FormatUtils.formatSpeed(state.aggregateSpeedBps)}"
                        } else {
                            "HTTP Range Engine • Background Ready"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (state.selectedTab == 2 && state.historyItems.isNotEmpty()) {
                    IconButton(
                        onClick = viewModel::clearHistory,
                        modifier = Modifier.testTag("clear_download_history_btn")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear download history")
                    }
                }
            }

            TabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(text = title, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.testTag("downloads_tab_$index")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (displayedItems.isEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        val (icon, title, subtitle) = when (state.selectedTab) {
                            0 -> Triple(
                                Icons.Rounded.Downloading,
                                "No Active Downloads",
                                "Queued, running, and paused downloads appear here with live KB/s–GB/s telemetry."
                            )
                            1 -> Triple(
                                Icons.Rounded.DownloadDone,
                                "No Completed Downloads Yet",
                                "Finished videos, audio tracks, and documents will be available here and in your Library."
                            )
                            else -> Triple(
                                Icons.Rounded.History,
                                "Download History Is Empty",
                                "Your completed, paused, and failed download records appear here."
                            )
                        }
                        EmptyStateCard(icon = icon, title = title, subtitle = subtitle)
                    }
                } else {
                    items(displayedItems, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onPause = viewModel::pause,
                            onResume = viewModel::resume,
                            onRetry = viewModel::retry,
                            onCancel = viewModel::cancel,
                            onPlayOrOpen = onOpenMedia
                        )
                    }
                }
            }
        }
    }
}
