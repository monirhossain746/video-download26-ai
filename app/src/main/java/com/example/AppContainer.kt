package com.example

import android.content.Context
import com.example.ai.VortexAiRepository
import com.example.analyzer.MediaAnalyzer
import com.example.data.local.VortexDatabase
import com.example.data.preferences.SettingsRepository
import com.example.download.DownloadCoordinator
import com.example.download.DownloadNotificationHelper
import com.example.download.NetworkRecoveryMonitor
import com.example.security.NetworkModule
import com.example.security.SafeFileManager
import com.example.security.VortexKeystoreManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.atomic.AtomicInteger

class AppContainer(context: Context) {
    val keystoreManager = VortexKeystoreManager()
    val safeFileManager = SafeFileManager(context)
    val okHttpClient: OkHttpClient = NetworkModule.createSecureOkHttpClient()
    val retrofit: Retrofit = NetworkModule.createRetrofit(okHttpClient)
    val database: VortexDatabase = VortexDatabase.getInstance(context)
    val settingsRepository = SettingsRepository(context, keystoreManager)
    val notificationHelper = DownloadNotificationHelper(context)
    val mediaAnalyzer = MediaAnalyzer(okHttpClient)
    val aiRepository by lazy { VortexAiRepository() }
    val downloadCoordinator = DownloadCoordinator(
        context = context,
        downloadDao = database.downloadDao(),
        safeFileManager = safeFileManager,
        settingsRepository = settingsRepository
    )
    val networkRecoveryMonitor = NetworkRecoveryMonitor(context, downloadCoordinator)

    private val activeDownloadsCount = AtomicInteger(0)
    private val slotMutex = Mutex()

    suspend fun tryAcquireDownloadSlot(maxAllowed: Int): Boolean = slotMutex.withLock {
        if (activeDownloadsCount.get() < maxAllowed) {
            activeDownloadsCount.incrementAndGet()
            true
        } else {
            false
        }
    }

    fun releaseDownloadSlot() {
        activeDownloadsCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }
}
