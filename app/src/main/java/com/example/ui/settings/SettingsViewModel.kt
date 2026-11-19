package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BrowserHistoryDao
import com.example.data.local.DownloadDao
import com.example.data.preferences.AppSettings
import com.example.data.preferences.DefaultQualityPreference
import com.example.data.preferences.DownloadStorageLocation
import com.example.data.preferences.SettingsRepository
import com.example.ui.theme.VortexThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val downloadDao: DownloadDao,
    private val browserHistoryDao: BrowserHistoryDao
) : ViewModel() {

    val settingsState: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    fun setThemeMode(mode: VortexThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDefaultQuality(q: DefaultQualityPreference) = viewModelScope.launch { settingsRepository.setDefaultQuality(q) }
    fun setMaxSimultaneous(count: Int) = viewModelScope.launch { settingsRepository.setMaxSimultaneousDownloads(count) }
    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settingsRepository.setWifiOnly(enabled) }
    fun setAllowMobileData(allowed: Boolean) = viewModelScope.launch { settingsRepository.setAllowMobileData(allowed) }
    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    fun setAutoResume(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoResume(enabled) }
    fun setClipboardAutoDetect(enabled: Boolean) = viewModelScope.launch { settingsRepository.setClipboardAutoDetect(enabled) }
    fun setStorageLocation(loc: DownloadStorageLocation) = viewModelScope.launch { settingsRepository.setStorageLocation(loc) }
    fun clearDownloadHistory() = viewModelScope.launch { downloadDao.clearHistory() }
    fun clearBrowserHistory() = viewModelScope.launch { browserHistoryDao.clearAll() }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val downloadDao: DownloadDao,
        private val browserHistoryDao: BrowserHistoryDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, downloadDao, browserHistoryDao) as T
        }
    }
}
