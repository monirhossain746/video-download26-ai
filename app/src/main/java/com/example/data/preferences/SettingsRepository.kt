package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.security.VortexKeystoreManager
import com.example.ui.theme.VortexThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vortexDataStore: DataStore<Preferences> by preferencesDataStore(name = "vortex_settings")

enum class DefaultQualityPreference(val label: String) {
    AUTO_BEST("Auto (Highest Available)"),
    Q_1080P("1080p Full HD"),
    Q_720P("720p HD"),
    Q_480P("480p SD"),
    Q_360P("360p Standard")
}

enum class DownloadStorageLocation(val label: String, val subtitle: String) {
    APP_VAULT("Vortex Private Vault", "Scoped app storage (faster resume & encryption)"),
    PUBLIC_DOWNLOADS("Public Downloads / Vortex", "Also exports completed files to system Downloads")
}

data class AppSettings(
    val themeMode: VortexThemeMode = VortexThemeMode.DARK,
    val defaultQuality: DefaultQualityPreference = DefaultQualityPreference.AUTO_BEST,
    val maxSimultaneousDownloads: Int = 3,
    val wifiOnly: Boolean = false,
    val allowMobileData: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val autoResume: Boolean = true,
    val clipboardAutoDetect: Boolean = true,
    val storageLocation: DownloadStorageLocation = DownloadStorageLocation.APP_VAULT
)

class SettingsRepository(
    private val context: Context,
    private val keystoreManager: VortexKeystoreManager
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val ALLOW_MOBILE_DATA = booleanPreferencesKey("allow_mobile_data")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val CLIPBOARD_DETECT = booleanPreferencesKey("clipboard_detect")
        val STORAGE_LOCATION = stringPreferencesKey("storage_location")
        val ENCRYPTED_VAULT_TOKEN = stringPreferencesKey("encrypted_vault_token")
    }

    val settingsFlow: Flow<AppSettings> = context.vortexDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { VortexThemeMode.valueOf(it) }.getOrNull()
            } ?: VortexThemeMode.DARK,
            defaultQuality = prefs[Keys.DEFAULT_QUALITY]?.let {
                runCatching { DefaultQualityPreference.valueOf(it) }.getOrNull()
            } ?: DefaultQualityPreference.AUTO_BEST,
            maxSimultaneousDownloads = (prefs[Keys.MAX_CONCURRENT] ?: 3).coerceIn(1, 5),
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            allowMobileData = prefs[Keys.ALLOW_MOBILE_DATA] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            autoResume = prefs[Keys.AUTO_RESUME] ?: true,
            clipboardAutoDetect = prefs[Keys.CLIPBOARD_DETECT] ?: true,
            storageLocation = prefs[Keys.STORAGE_LOCATION]?.let {
                runCatching { DownloadStorageLocation.valueOf(it) }.getOrNull()
            } ?: DownloadStorageLocation.APP_VAULT
        )
    }

    suspend fun setThemeMode(mode: VortexThemeMode) {
        context.vortexDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultQuality(quality: DefaultQualityPreference) {
        context.vortexDataStore.edit { it[Keys.DEFAULT_QUALITY] = quality.name }
    }

    suspend fun setMaxSimultaneousDownloads(count: Int) {
        context.vortexDataStore.edit { it[Keys.MAX_CONCURRENT] = count.coerceIn(1, 5) }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.vortexDataStore.edit {
            it[Keys.WIFI_ONLY] = enabled
            if (enabled) it[Keys.ALLOW_MOBILE_DATA] = false
        }
    }

    suspend fun setAllowMobileData(allowed: Boolean) {
        context.vortexDataStore.edit {
            it[Keys.ALLOW_MOBILE_DATA] = allowed
            if (allowed) it[Keys.WIFI_ONLY] = false
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.vortexDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setAutoResume(enabled: Boolean) {
        context.vortexDataStore.edit { it[Keys.AUTO_RESUME] = enabled }
    }

    suspend fun setClipboardAutoDetect(enabled: Boolean) {
        context.vortexDataStore.edit { it[Keys.CLIPBOARD_DETECT] = enabled }
    }

    suspend fun setStorageLocation(location: DownloadStorageLocation) {
        context.vortexDataStore.edit { it[Keys.STORAGE_LOCATION] = location.name }
    }

    suspend fun storeEncryptedPreferenceNote(note: String) {
        val encrypted = keystoreManager.encrypt(note)
        context.vortexDataStore.edit { it[Keys.ENCRYPTED_VAULT_TOKEN] = encrypted }
    }
}
