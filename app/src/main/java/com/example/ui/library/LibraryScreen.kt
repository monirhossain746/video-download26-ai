package com.example.ui.library

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.analyzer.FormatUtils
import com.example.data.local.DownloadCategory
import com.example.data.local.DownloadEntity
import com.example.security.SafeFileManager
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalVortexColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    safeFileManager: SafeFileManager,
    initialCategory: DownloadCategory? = null,
    onPlayMedia: (DownloadEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(initialCategory) {
        viewModel.selectCategory(initialCategory)
    }

    LaunchedEffect(state.feedbackMessage) {
        val msg = state.feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val openOrPlay: (DownloadEntity) -> Unit = { item ->
        when (item.categoryEnum) {
            DownloadCategory.VIDEO, DownloadCategory.AUDIO -> onPlayMedia(item)
            DownloadCategory.APK -> viewModel.showApkSafetyNotice(item)
            else -> {
                runCatching {
                    val uri = safeFileManager.getContentUri(File(item.localFilePath))
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, item.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open with"))
                }
            }
        }
    }

    val shareItem: (DownloadEntity) -> Unit = { item ->
        runCatching {
            val uri = safeFileManager.getContentUri(File(item.localFilePath))
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${item.fileName}"))
        }
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
                        text = "Download Library",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${state.items.size} files in vault • ${state.sortOrder.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("library_sort_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort files")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        LibrarySortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Search videos, music, docs, APKs…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .testTag("library_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All Downloads") },
                        modifier = Modifier.testTag("chip_cat_all")
                    )
                }
                items(DownloadCategory.entries) { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick = { viewModel.selectCategory(cat) },
                        label = { Text(cat.label) },
                        modifier = Modifier.testTag("chip_cat_${cat.name.lowercase()}")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.items.isEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        EmptyStateCard(
                            icon = Icons.Rounded.Folder,
                            title = "No Files Found",
                            subtitle = if (state.searchQuery.isNotBlank()) {
                                "No downloaded files match '${state.searchQuery}'."
                            } else {
                                "Completed downloads for this category will appear here."
                            }
                        )
                    }
                } else {
                    items(state.items, key = { it.id }) { item ->
                        LibraryFileRow(
                            item = item,
                            onPrimaryClick = { openOrPlay(item) },
                            onShare = { shareItem(item) },
                            onExport = { viewModel.exportToPublicDownloads(item) },
                            onRename = { viewModel.requestRename(item) },
                            onInfo = { viewModel.showFileInfo(item) },
                            onDelete = { viewModel.requestDelete(item) }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Rename Dialog
    state.renameTarget?.let { target ->
        var text by remember(target) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = viewModel::dismissRename,
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("New file title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmRename(text) }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRename) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete File?") },
            text = { Text("Permanently remove '${target.fileName}' from your device?") },
            confirmButton = {
                Button(onClick = viewModel::confirmDelete) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text("Cancel")
                }
            }
        )
    }

    // File Info Dialog
    state.selectedInfoItem?.let { info ->
        val dateStr = remember(info.updatedAt) {
            SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(info.updatedAt))
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissFileInfo,
            title = { Text(info.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("File: ${info.fileName}", style = MaterialTheme.typography.bodyMedium)
                    Text("Category: ${info.categoryEnum.label}", style = MaterialTheme.typography.bodyMedium)
                    Text("Size: ${FormatUtils.formatBytes(info.totalBytes)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Resolution: ${info.resolutionLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text("MIME: ${info.mimeType}", style = MaterialTheme.typography.bodyMedium)
                    Text("Saved: $dateStr", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "SHA-256: ${state.selectedInfoSha256 ?: "Calculating…"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::dismissFileInfo) {
                    Text("Close")
                }
            }
        )
    }

    // APK Safety Notice (never auto-install APKs!)
    state.apkSafetyNoticeTarget?.let { apk ->
        AlertDialog(
            onDismissRequest = viewModel::dismissApkSafetyNotice,
            icon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
            title = { Text("APK Security Guard") },
            text = {
                Text(
                    "Vortex never auto-installs downloaded APK files (${apk.fileName}) to protect your device. " +
                        "You can inspect its SHA-256 checksum in File Info or share/export it to your system Downloads folder."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissApkSafetyNotice()
                    viewModel.showFileInfo(apk)
                }) {
                    Text("Inspect SHA-256")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissApkSafetyNotice) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun LibraryFileRow(
    item: DownloadEntity,
    onPrimaryClick: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    val extra = LocalVortexColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    val icon = when (item.categoryEnum) {
        DownloadCategory.VIDEO -> Icons.Rounded.Videocam
        DownloadCategory.AUDIO -> Icons.Rounded.MusicNote
        DownloadCategory.DOCUMENT -> Icons.Rounded.Description
        DownloadCategory.APK -> Icons.Rounded.Android
        DownloadCategory.OTHER -> Icons.Rounded.Folder
    }

    val accent = when (item.categoryEnum) {
        DownloadCategory.VIDEO -> MaterialTheme.colorScheme.tertiary
        DownloadCategory.AUDIO -> MaterialTheme.colorScheme.primary
        DownloadCategory.DOCUMENT -> MaterialTheme.colorScheme.secondary
        DownloadCategory.APK -> extra.warning
        DownloadCategory.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPrimaryClick)
            .testTag("library_item_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = item.categoryEnum.label, tint = accent)
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
                    text = "${item.resolutionLabel} • ${FormatUtils.formatBytes(item.totalBytes.coerceAtLeast(item.downloadedBytes))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onPrimaryClick) {
                Icon(
                    imageVector = if (item.categoryEnum == DownloadCategory.VIDEO || item.categoryEnum == DownloadCategory.AUDIO) {
                        Icons.Rounded.PlayArrow
                    } else {
                        Icons.Rounded.OpenInNew
                    },
                    contentDescription = "Open or play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More file actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        text = { Text("Share") },
                        onClick = { menuExpanded = false; onShare() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.DownloadForOffline, null) },
                        text = { Text("Save to Public Downloads") },
                        onClick = { menuExpanded = false; onExport() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                        text = { Text("Rename") },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Info, null) },
                        text = { Text("File Info & SHA-256") },
                        onClick = { menuExpanded = false; onInfo() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}
