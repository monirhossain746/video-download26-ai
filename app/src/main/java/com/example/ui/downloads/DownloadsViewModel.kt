package com.example.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.download.DownloadCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val selectedTab: Int = 0,
    val activeItems: List<DownloadEntity> = emptyList(),
    val completedItems: List<DownloadEntity> = emptyList(),
    val historyItems: List<DownloadEntity> = emptyList(),
    val aggregateSpeedBps: Long = 0L
)

class DownloadsViewModel(
    private val downloadDao: DownloadDao,
    private val coordinator: DownloadCoordinator
) : ViewModel() {

    private val selectedTab = MutableStateFlow(0)

    val uiState: StateFlow<DownloadsUiState> = combine(
        selectedTab,
        downloadDao.observeAllDownloads()
    ) { tab, all ->
        val active = all.filter {
            it.statusEnum == DownloadStatus.DOWNLOADING ||
                it.statusEnum == DownloadStatus.QUEUED ||
                it.statusEnum == DownloadStatus.PAUSED
        }
        val completed = all.filter { it.statusEnum == DownloadStatus.COMPLETED }
        val speed = active.filter { it.statusEnum == DownloadStatus.DOWNLOADING }.sumOf { it.speedBytesPerSec }
        DownloadsUiState(
            selectedTab = tab,
            activeItems = active,
            completedItems = completed,
            historyItems = all,
            aggregateSpeedBps = speed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DownloadsUiState()
    )

    fun selectTab(index: Int) {
        selectedTab.value = index.coerceIn(0, 2)
    }

    fun pause(id: Long) = viewModelScope.launch { coordinator.pauseDownload(id) }
    fun resume(id: Long) = viewModelScope.launch { coordinator.resumeDownload(id) }
    fun retry(id: Long) = viewModelScope.launch { coordinator.retryDownload(id) }
    fun cancel(id: Long) = viewModelScope.launch { coordinator.cancelDownload(id) }
    fun clearHistory() = viewModelScope.launch { downloadDao.clearHistory() }

    class Factory(
        private val downloadDao: DownloadDao,
        private val coordinator: DownloadCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DownloadsViewModel(downloadDao, coordinator) as T
        }
    }
}
