package com.example.download

import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.analyzer.MediaFormatOption
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.data.preferences.SettingsRepository
import com.example.security.SafeFileManager
import kotlinx.coroutines.flow.first
import java.io.File

class DownloadCoordinator(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val safeFileManager: SafeFileManager,
    private val settingsRepository: SettingsRepository
) {
    private val workManager: WorkManager by lazy {
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(
                context.applicationContext,
                Configuration.Builder().build()
            )
        }
        WorkManager.getInstance(context.applicationContext)
    }

    suspend fun enqueueDownload(
        sourceUrl: String,
        title: String,
        thumbnailUrl: String?,
        format: MediaFormatOption
    ): Long {
        val sanitizedName = safeFileManager.sanitizeFileName(
            "$title.${format.containerExt}",
            fallbackExt = format.containerExt.ifBlank { "mp4" }
        )
        val targetFile = safeFileManager.resolveSafeTargetFile(format.category, sanitizedName)

        val entity = DownloadEntity(
            url = sourceUrl,
            finalUrl = format.url,
            title = title,
            fileName = targetFile.name,
            localFilePath = targetFile.absolutePath,
            mimeType = format.mimeType,
            category = format.category.name,
            resolutionLabel = format.resolutionLabel,
            thumbnailUrl = thumbnailUrl,
            totalBytes = format.estimatedSizeBytes,
            downloadedBytes = 0L,
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            status = DownloadStatus.QUEUED.name,
            supportsRanges = format.supportsRanges
        )

        val id = downloadDao.insert(entity)
        scheduleWorker(id)
        return id
    }

    suspend fun pauseDownload(downloadId: Long) {
        downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED.name, null)
        workManager.cancelUniqueWork(workName(downloadId))
    }

    suspend fun resumeDownload(downloadId: Long) {
        val entity = downloadDao.getById(downloadId) ?: return
        if (entity.statusEnum == DownloadStatus.COMPLETED) return
        downloadDao.updateStatus(downloadId, DownloadStatus.QUEUED.name, null)
        scheduleWorker(downloadId)
    }

    suspend fun retryDownload(downloadId: Long) {
        val entity = downloadDao.getById(downloadId) ?: return
        downloadDao.updateStatus(downloadId, DownloadStatus.QUEUED.name, null)
        scheduleWorker(entity.id)
    }

    suspend fun cancelDownload(downloadId: Long) {
        val entity = downloadDao.getById(downloadId)
        downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED.name, null)
        workManager.cancelUniqueWork(workName(downloadId))
        if (entity != null) {
            safeFileManager.deleteSafeFile(File("${entity.localFilePath}.part"))
            safeFileManager.deleteSafeFile(File(entity.localFilePath))
        }
    }

    suspend fun deleteDownloadRecord(entity: DownloadEntity) {
        workManager.cancelUniqueWork(workName(entity.id))
        safeFileManager.deleteSafeFile(File("${entity.localFilePath}.part"))
        safeFileManager.deleteSafeFile(File(entity.localFilePath))
        downloadDao.deleteById(entity.id)
    }

    suspend fun renameDownload(entity: DownloadEntity, newTitle: String): Boolean {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isEmpty()) return false
        val existingFile = File(entity.localFilePath)
        val renamedFile = if (existingFile.exists()) {
            safeFileManager.renameSafeFile(existingFile, cleanTitle) ?: return false
        } else {
            existingFile
        }
        downloadDao.updateFileDetails(
            id = entity.id,
            newFileName = renamedFile.name,
            newTitle = cleanTitle,
            newPath = renamedFile.absolutePath
        )
        return true
    }

    suspend fun resumeNetworkPausedDownloads() {
        val settings = settingsRepository.settingsFlow.first()
        if (!settings.autoResume) return
        val paused = downloadDao.getNetworkPausedDownloads()
        for (item in paused) {
            resumeDownload(item.id)
        }
    }

    private suspend fun scheduleWorker(downloadId: Long) {
        val settings = settingsRepository.settingsFlow.first()
        val networkType = if (settings.wifiOnly || !settings.allowMobileData) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(Data.Builder().putLong(DownloadWorker.KEY_DOWNLOAD_ID, downloadId).build())
            .addTag(TAG_VORTEX_DOWNLOAD)
            .build()

        workManager.enqueueUniqueWork(
            workName(downloadId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun workName(id: Long): String = "vortex_download_$id"

    companion object {
        const val TAG_VORTEX_DOWNLOAD = "vortex_download_tag"
    }
}
