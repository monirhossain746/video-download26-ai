package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.analyzer.AnalyzedMediaResult
import com.example.analyzer.MediaAnalyzer
import com.example.analyzer.MediaFormatOption
import com.example.data.local.DownloadCategory
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsRepository
import com.example.download.DownloadCoordinator
import com.example.security.SafeFileManager
import com.example.security.UrlSecurityValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val urlInput: String = "",
    val detectedClipboardUrl: String? = null,
    val isAnalyzing: Boolean = false,
    val showAnalysisSheet: Boolean = false,
    val analysisResult: AnalyzedMediaResult? = null,
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val totalDownloadedBytes: Long = 0L,
    val currentTotalSpeedBps: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val recentlyPlayed: List<DownloadEntity> = emptyList(),
    val recentDownloads: List<DownloadEntity> = emptyList(),
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val clipboardAutoDetectEnabled: Boolean = true,
    val statusBannerMessage: String? = null
)

class HomeViewModel(
    private val downloadDao: DownloadDao,
    private val mediaAnalyzer: MediaAnalyzer,
    private val downloadCoordinator: DownloadCoordinator,
    private val safeFileManager: SafeFileManager,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val localState = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(
        localState,
        downloadDao.observeAllDownloads(),
        downloadDao.observeRecentlyPlayed(),
        settingsRepository.settingsFlow
    ) { local, allDownloads, recentlyPlayed, settings: AppSettings ->
        val active = allDownloads.filter { it.statusEnum.isActive }
        val completed = allDownloads.filter { it.statusEnum == DownloadStatus.COMPLETED }
        val totalBytes = completed.sumOf { it.totalBytes.coerceAtLeast(it.downloadedBytes) }
        val liveSpeed = active.filter { it.statusEnum == DownloadStatus.DOWNLOADING }.sumOf { it.speedBytesPerSec }
        local.copy(
            activeCount = active.size,
            completedCount = completed.size,
            totalDownloadedBytes = totalBytes,
            currentTotalSpeedBps = liveSpeed,
            availableStorageBytes = safeFileManager.getAvailableBytes(),
            recentlyPlayed = recentlyPlayed,
            recentDownloads = allDownloads.take(4),
            videoCount = completed.count { it.categoryEnum == DownloadCategory.VIDEO },
            audioCount = completed.count { it.categoryEnum == DownloadCategory.AUDIO },
            clipboardAutoDetectEnabled = settings.clipboardAutoDetect
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onUrlInputChange(newValue: String) {
        localState.update { it.copy(urlInput = newValue) }
    }

    fun onClipboardChecked(clipText: String?) {
        if (clipText.isNullOrBlank()) return
        val candidate = clipText.trim()
        if (candidate != localState.value.urlInput &&
            UrlSecurityValidator.looksLikeDownloadableMediaUrl(candidate)
        ) {
            localState.update { it.copy(detectedClipboardUrl = candidate) }
        }
    }

    fun dismissClipboardSuggestion() {
        localState.update { it.copy(detectedClipboardUrl = null) }
    }

    fun useClipboardSuggestion() {
        val url = localState.value.detectedClipboardUrl ?: return
        localState.update { it.copy(urlInput = url, detectedClipboardUrl = null) }
        analyzeUrl(url)
    }

    fun analyzeCurrentInput() {
        analyzeUrl(localState.value.urlInput)
    }

    fun analyzeUrl(rawUrl: String) {
        localState.update {
            it.copy(
                urlInput = rawUrl,
                isAnalyzing = true,
                showAnalysisSheet = true,
                analysisResult = null
            )
        }
        viewModelScope.launch {
            val result = mediaAnalyzer.analyze(rawUrl)
            localState.update {
                it.copy(
                    isAnalyzing = false,
                    analysisResult = result
                )
            }
        }
    }

    fun dismissAnalysisSheet() {
        localState.update { it.copy(showAnalysisSheet = false) }
    }

    fun startDownload(media: AnalyzedMediaResult.Success, format: MediaFormatOption) {
        viewModelScope.launch {
            downloadCoordinator.enqueueDownload(
                sourceUrl = media.originalUrl,
                title = media.title,
                thumbnailUrl = media.thumbnailUrl,
                format = format
            )
            localState.update {
                it.copy(
                    showAnalysisSheet = false,
                    statusBannerMessage = "Queued '${media.title}' (${format.resolutionLabel})"
                )
            }
        }
    }

    fun clearStatusBanner() {
        localState.update { it.copy(statusBannerMessage = null) }
    }

    fun pauseDownload(id: Long) = viewModelScope.launch { downloadCoordinator.pauseDownload(id) }
    fun resumeDownload(id: Long) = viewModelScope.launch { downloadCoordinator.resumeDownload(id) }
    fun retryDownload(id: Long) = viewModelScope.launch { downloadCoordinator.retryDownload(id) }
    fun cancelDownload(id: Long) = viewModelScope.launch { downloadCoordinator.cancelDownload(id) }

    class Factory(
        private val downloadDao: DownloadDao,
        private val mediaAnalyzer: MediaAnalyzer,
        private val downloadCoordinator: DownloadCoordinator,
        private val safeFileManager: SafeFileManager,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                downloadDao = downloadDao,
                mediaAnalyzer = mediaAnalyzer,
                downloadCoordinator = downloadCoordinator,
                safeFileManager = safeFileManager,
                settingsRepository = settingsRepository
            ) as T
        }
    }
}
