package com.example.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.analyzer.AnalyzedMediaResult
import com.example.analyzer.MediaAnalyzer
import com.example.analyzer.MediaFormatOption
import com.example.data.local.BrowserHistoryDao
import com.example.data.local.BrowserHistoryEntity
import com.example.download.DownloadCoordinator
import com.example.security.UrlSecurityValidator
import com.example.security.UrlValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder

data class BrowserUiState(
    val addressInput: String = DEFAULT_HOME_URL,
    val currentUrl: String = DEFAULT_HOME_URL,
    val pageTitle: String = "Vortex Secure Browser",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isPrivateMode: Boolean = false,
    val detectedMediaUrls: List<String> = emptyList(),
    val history: List<BrowserHistoryEntity> = emptyList(),
    val showHistorySheet: Boolean = false,
    val showDetectedSheet: Boolean = false,
    val isAnalyzing: Boolean = false,
    val showAnalysisSheet: Boolean = false,
    val analysisResult: AnalyzedMediaResult? = null,
    val securityAlert: String? = null
) {
    companion object {
        const val DEFAULT_HOME_URL = "https://duckduckgo.com/"
    }
}

class BrowserViewModel(
    private val historyDao: BrowserHistoryDao,
    private val mediaAnalyzer: MediaAnalyzer,
    private val coordinator: DownloadCoordinator
) : ViewModel() {

    private val localState = MutableStateFlow(BrowserUiState())

    val uiState: StateFlow<BrowserUiState> = combine(
        localState,
        historyDao.observeHistory()
    ) { local, history ->
        local.copy(history = history)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BrowserUiState()
    )

    fun onAddressInputChange(value: String) {
        localState.update { it.copy(addressInput = value) }
    }

    fun resolveNavigationUrl(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val isUrlLike = trimmed.contains('.') && !trimmed.contains(' ')
        val candidate = if (isUrlLike) {
            if (trimmed.startsWith("https://")) trimmed
            else if (trimmed.startsWith("http://")) {
                localState.update {
                    it.copy(securityAlert = "Cleartext HTTP upgraded/blocked for security. Using HTTPS.")
                }
                "https://" + trimmed.removePrefix("http://")
            } else {
                "https://$trimmed"
            }
        } else {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            "https://duckduckgo.com/?q=$encoded"
        }

        return when (val check = UrlSecurityValidator.validate(candidate, checkPlatformPolicy = false)) {
            is UrlValidationResult.Valid -> {
                localState.update { it.copy(addressInput = check.normalizedUrl, securityAlert = null) }
                check.normalizedUrl
            }
            is UrlValidationResult.Invalid -> {
                localState.update { it.copy(securityAlert = check.reason) }
                null
            }
            is UrlValidationResult.RestrictedPlatform -> check.host
        }
    }

    fun onPageStarted(url: String) {
        localState.update {
            it.copy(
                currentUrl = url,
                addressInput = url,
                isLoading = true,
                detectedMediaUrls = emptyList()
            )
        }
    }

    fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: url
        localState.update {
            it.copy(
                currentUrl = url,
                addressInput = url,
                pageTitle = safeTitle,
                isLoading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
        if (!localState.value.isPrivateMode && url.startsWith("https://")) {
            viewModelScope.launch {
                historyDao.insert(BrowserHistoryEntity(url = url, title = safeTitle))
            }
        }
    }

    fun onProgressChanged(progress: Int) {
        localState.update { it.copy(progress = progress, isLoading = progress < 100) }
    }

    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        localState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    fun onMediaUrlDetected(candidateUrl: String) {
        if (!UrlSecurityValidator.looksLikeDownloadableMediaUrl(candidateUrl)) return
        localState.update { current ->
            if (candidateUrl in current.detectedMediaUrls || current.detectedMediaUrls.size >= 15) {
                current
            } else {
                current.copy(detectedMediaUrls = current.detectedMediaUrls + candidateUrl)
            }
        }
    }

    fun togglePrivateMode() {
        localState.update {
            it.copy(isPrivateMode = !it.isPrivateMode)
        }
    }

    fun setShowHistorySheet(show: Boolean) {
        localState.update { it.copy(showHistorySheet = show) }
    }

    fun setShowDetectedSheet(show: Boolean) {
        localState.update { it.copy(showDetectedSheet = show) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyDao.clearAll() }
    }

    fun dismissSecurityAlert() {
        localState.update { it.copy(securityAlert = null) }
    }

    fun analyzeUrl(url: String) {
        localState.update {
            it.copy(
                showDetectedSheet = false,
                isAnalyzing = true,
                showAnalysisSheet = true,
                analysisResult = null
            )
        }
        viewModelScope.launch {
            val result = mediaAnalyzer.analyze(url)
            localState.update { it.copy(isAnalyzing = false, analysisResult = result) }
        }
    }

    fun dismissAnalysisSheet() {
        localState.update { it.copy(showAnalysisSheet = false) }
    }

    fun startDownload(media: AnalyzedMediaResult.Success, format: MediaFormatOption) {
        viewModelScope.launch {
            coordinator.enqueueDownload(
                sourceUrl = media.originalUrl,
                title = media.title,
                thumbnailUrl = media.thumbnailUrl,
                format = format
            )
            localState.update { it.copy(showAnalysisSheet = false) }
        }
    }

    class Factory(
        private val historyDao: BrowserHistoryDao,
        private val mediaAnalyzer: MediaAnalyzer,
        private val coordinator: DownloadCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BrowserViewModel(historyDao, mediaAnalyzer, coordinator) as T
        }
    }
}
