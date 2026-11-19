package com.example.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadCategory
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.download.DownloadCoordinator
import com.example.security.SafeFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class LibrarySortOrder(val label: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)"),
    NAME_ASC("Name (A–Z)")
}

data class LibraryUiState(
    val selectedCategory: DownloadCategory? = null,
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.DATE_DESC,
    val items: List<DownloadEntity> = emptyList(),
    val selectedInfoItem: DownloadEntity? = null,
    val selectedInfoSha256: String? = null,
    val renameTarget: DownloadEntity? = null,
    val deleteTarget: DownloadEntity? = null,
    val apkSafetyNoticeTarget: DownloadEntity? = null,
    val feedbackMessage: String? = null
)

class LibraryViewModel(
    downloadDao: DownloadDao,
    private val coordinator: DownloadCoordinator,
    private val safeFileManager: SafeFileManager
) : ViewModel() {

    private val localState = MutableStateFlow(LibraryUiState())

    val uiState: StateFlow<LibraryUiState> = combine(
        localState,
        downloadDao.observeCompletedDownloads()
    ) { local, completed ->
        val filtered = completed
            .filter { item ->
                local.selectedCategory == null || item.categoryEnum == local.selectedCategory
            }
            .filter { item ->
                local.searchQuery.isBlank() ||
                    item.title.contains(local.searchQuery, ignoreCase = true) ||
                    item.fileName.contains(local.searchQuery, ignoreCase = true)
            }

        val sorted = when (local.sortOrder) {
            LibrarySortOrder.DATE_DESC -> filtered.sortedByDescending { it.updatedAt }
            LibrarySortOrder.DATE_ASC -> filtered.sortedBy { it.updatedAt }
            LibrarySortOrder.SIZE_DESC -> filtered.sortedByDescending { it.totalBytes.coerceAtLeast(it.downloadedBytes) }
            LibrarySortOrder.SIZE_ASC -> filtered.sortedBy { it.totalBytes.coerceAtLeast(it.downloadedBytes) }
            LibrarySortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
        }

        local.copy(items = sorted)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun selectCategory(category: DownloadCategory?) {
        localState.update { it.copy(selectedCategory = category) }
    }

    fun onSearchQueryChange(query: String) {
        localState.update { it.copy(searchQuery = query) }
    }

    fun setSortOrder(order: LibrarySortOrder) {
        localState.update { it.copy(sortOrder = order) }
    }

    fun showFileInfo(item: DownloadEntity) {
        localState.update { it.copy(selectedInfoItem = item, selectedInfoSha256 = "Calculating…") }
        viewModelScope.launch(Dispatchers.IO) {
            val hash = safeFileManager.computeSha256(File(item.localFilePath)) ?: "Unavailable"
            localState.update { it.copy(selectedInfoSha256 = hash) }
        }
    }

    fun dismissFileInfo() {
        localState.update { it.copy(selectedInfoItem = null, selectedInfoSha256 = null) }
    }

    fun requestRename(item: DownloadEntity) {
        localState.update { it.copy(renameTarget = item) }
    }

    fun dismissRename() {
        localState.update { it.copy(renameTarget = null) }
    }

    fun confirmRename(newName: String) {
        val target = localState.value.renameTarget ?: return
        viewModelScope.launch {
            val ok = coordinator.renameDownload(target, newName)
            localState.update {
                it.copy(
                    renameTarget = null,
                    feedbackMessage = if (ok) "Renamed successfully." else "Could not rename file (name may already exist)."
                )
            }
        }
    }

    fun requestDelete(item: DownloadEntity) {
        localState.update { it.copy(deleteTarget = item) }
    }

    fun dismissDelete() {
        localState.update { it.copy(deleteTarget = null) }
    }

    fun confirmDelete() {
        val target = localState.value.deleteTarget ?: return
        viewModelScope.launch {
            coordinator.deleteDownloadRecord(target)
            localState.update {
                it.copy(deleteTarget = null, feedbackMessage = "Removed '${target.title}'.")
            }
        }
    }

    fun showApkSafetyNotice(item: DownloadEntity) {
        localState.update { it.copy(apkSafetyNoticeTarget = item) }
    }

    fun dismissApkSafetyNotice() {
        localState.update { it.copy(apkSafetyNoticeTarget = null) }
    }

    fun exportToPublicDownloads(item: DownloadEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = safeFileManager.exportToPublicDownloads(File(item.localFilePath), item.mimeType)
            localState.update {
                it.copy(
                    feedbackMessage = if (ok) {
                        "Exported '${item.fileName}' to Downloads/Vortex"
                    } else {
                        "Export failed or unsupported on this Android version."
                    }
                )
            }
        }
    }

    fun clearFeedback() {
        localState.update { it.copy(feedbackMessage = null) }
    }

    class Factory(
        private val downloadDao: DownloadDao,
        private val coordinator: DownloadCoordinator,
        private val safeFileManager: SafeFileManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(downloadDao, coordinator, safeFileManager) as T
        }
    }
}
