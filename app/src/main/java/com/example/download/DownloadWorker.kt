package com.example.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.VortexApplication
import com.example.data.local.DownloadStatus
import com.example.data.preferences.DownloadStorageLocation
import com.example.security.UrlSecurityValidator
import com.example.security.UrlValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return@withContext Result.failure()

        val container = (applicationContext as VortexApplication).container
        val dao = container.database.downloadDao()
        val fileManager = container.safeFileManager
        val settings = container.settingsRepository.settingsFlow.first()
        val notificationHelper = container.notificationHelper

        val entity = dao.getById(downloadId) ?: return@withContext Result.failure()

        if (UrlSecurityValidator.validate(entity.finalUrl, checkPlatformPolicy = true) !is UrlValidationResult.Valid) {
            dao.updateStatus(downloadId, DownloadStatus.FAILED.name, "Blocked unsafe download URL.")
            return@withContext Result.failure()
        }

        val targetFile = File(entity.localFilePath)
        val partFile = File("${entity.localFilePath}.part")
        targetFile.parentFile?.mkdirs()

        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        if (entity.totalBytes > 0L && !fileManager.hasEnoughSpace(entity.totalBytes - existingBytes)) {
            dao.updateStatus(downloadId, DownloadStatus.FAILED.name, "Insufficient device storage space.")
            return@withContext Result.failure()
        }

        val notificationId = (10_000 + (downloadId % 9_000)).toInt()
        setForegroundSafe(
            notificationId,
            notificationHelper.buildProgressNotification(id, entity.title, existingBytes, entity.totalBytes, 0L, 0L)
        )

        var acquiredSlot = false
        while (!isStopped && !acquiredSlot) {
            acquiredSlot = container.tryAcquireDownloadSlot(settings.maxSimultaneousDownloads)
            if (!acquiredSlot) {
                kotlinx.coroutines.delay(500L)
            }
        }
        if (isStopped) {
            if (acquiredSlot) container.releaseDownloadSlot()
            handleStop(downloadId, partFile)
            return@withContext Result.success()
        }

        dao.updateProgress(
            id = downloadId,
            downloadedBytes = existingBytes,
            totalBytes = entity.totalBytes,
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            status = DownloadStatus.DOWNLOADING.name,
            supportsRanges = entity.supportsRanges,
            errorMessage = null
        )

        return@withContext try {
            val useParallelChunks = entity.supportsRanges &&
                entity.totalBytes >= PARALLEL_THRESHOLD_BYTES &&
                existingBytes == 0L

            if (useParallelChunks) {
                downloadParallelRanges(
                    downloadId = downloadId,
                    url = entity.finalUrl,
                    title = entity.title,
                    totalBytes = entity.totalBytes,
                    partFile = partFile,
                    notificationId = notificationId,
                    notificationsEnabled = settings.notificationsEnabled
                )
            } else {
                downloadResumableSingleStream(
                    downloadId = downloadId,
                    url = entity.finalUrl,
                    title = entity.title,
                    knownTotalBytes = entity.totalBytes,
                    supportsRanges = entity.supportsRanges,
                    partFile = partFile,
                    notificationId = notificationId,
                    notificationsEnabled = settings.notificationsEnabled
                )
            }

            if (isStopped) {
                handleStop(downloadId, partFile)
                return@withContext Result.success()
            }

            if (partFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                partFile.renameTo(targetFile)
            }

            val finalSize = targetFile.length()
            dao.updateProgress(
                id = downloadId,
                downloadedBytes = finalSize,
                totalBytes = finalSize,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
                status = DownloadStatus.COMPLETED.name,
                supportsRanges = entity.supportsRanges,
                errorMessage = null
            )

            if (settings.storageLocation == DownloadStorageLocation.PUBLIC_DOWNLOADS) {
                fileManager.exportToPublicDownloads(targetFile, entity.mimeType)
            }

            if (settings.notificationsEnabled) {
                notificationHelper.showCompletedNotification(downloadId, entity.title, finalSize)
            }
            Result.success()
        } catch (ce: CancellationException) {
            handleStop(downloadId, partFile)
            Result.success()
        } catch (ex: Exception) {
            val currentBytes = if (partFile.exists()) partFile.length() else existingBytes
            val isNetworkFailure = ex is java.io.IOException
            if (isNetworkFailure && settings.autoResume && entity.supportsRanges) {
                dao.updateProgress(
                    id = downloadId,
                    downloadedBytes = currentBytes,
                    totalBytes = entity.totalBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    status = DownloadStatus.PAUSED.name,
                    supportsRanges = entity.supportsRanges,
                    errorMessage = "PAUSED_NETWORK"
                )
            } else {
                dao.updateProgress(
                    id = downloadId,
                    downloadedBytes = currentBytes,
                    totalBytes = entity.totalBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    status = DownloadStatus.FAILED.name,
                    supportsRanges = entity.supportsRanges,
                    errorMessage = ex.message ?: "Download error occurred."
                )
            }
            Result.failure()
        } finally {
            container.releaseDownloadSlot()
        }
    }

    private suspend fun handleStop(downloadId: Long, partFile: File) {
        val container = (applicationContext as VortexApplication).container
        val dao = container.database.downloadDao()
        val latest = dao.getById(downloadId) ?: return
        if (latest.statusEnum == DownloadStatus.CANCELLED) {
            if (partFile.exists()) partFile.delete()
            return
        }
        val currentBytes = if (partFile.exists()) partFile.length() else latest.downloadedBytes
        dao.updateProgress(
            id = downloadId,
            downloadedBytes = currentBytes,
            totalBytes = latest.totalBytes,
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            status = DownloadStatus.PAUSED.name,
            supportsRanges = latest.supportsRanges,
            errorMessage = latest.errorMessage
        )
    }

    private suspend fun downloadParallelRanges(
        downloadId: Long,
        url: String,
        title: String,
        totalBytes: Long,
        partFile: File,
        notificationId: Int,
        notificationsEnabled: Boolean
    ) = coroutineScope {
        val container = (applicationContext as VortexApplication).container
        val client = container.okHttpClient
        val dao = container.database.downloadDao()
        val notificationHelper = container.notificationHelper

        // Pre-allocate target part file safely
        RandomAccessFile(partFile, "rw").use { raf -> raf.setLength(totalBytes) }

        val chunkCount = 4
        val chunkSize = totalBytes / chunkCount
        val totalDownloaded = AtomicLong(0L)
        val lastReportTime = AtomicLong(System.currentTimeMillis())
        val lastReportBytes = AtomicLong(0L)

        val jobs = (0 until chunkCount).map { idx ->
            val start = idx * chunkSize
            val end = if (idx == chunkCount - 1) totalBytes - 1 else ((idx + 1) * chunkSize) - 1
            async(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$start-$end")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 206) {
                        throw java.io.IOException("Server returned HTTP ${resp.code} for range chunk $idx")
                    }
                    val body = resp.body ?: throw java.io.IOException("Empty response body")
                    val buffer = ByteArray(16 * 1024)
                    RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(start)
                        body.byteStream().use { input ->
                            while (!isStopped) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                raf.write(buffer, 0, read)
                                val current = totalDownloaded.addAndGet(read.toLong())
                                maybePublishProgress(
                                    downloadId = downloadId,
                                    title = title,
                                    currentBytes = current,
                                    totalBytes = totalBytes,
                                    supportsRanges = true,
                                    lastReportTime = lastReportTime,
                                    lastReportBytes = lastReportBytes,
                                    notificationId = notificationId,
                                    notificationsEnabled = notificationsEnabled,
                                    dao = dao,
                                    notificationHelper = notificationHelper
                                )
                            }
                        }
                    }
                }
            }
        }
        jobs.awaitAll()
    }

    private suspend fun downloadResumableSingleStream(
        downloadId: Long,
        url: String,
        title: String,
        knownTotalBytes: Long,
        supportsRanges: Boolean,
        partFile: File,
        notificationId: Int,
        notificationsEnabled: Boolean
    ) {
        val container = (applicationContext as VortexApplication).container
        val client = container.okHttpClient
        val dao = container.database.downloadDao()
        val notificationHelper = container.notificationHelper

        var existingBytes = if (partFile.exists()) partFile.length() else 0L
        val reqBuilder = Request.Builder().url(url)
        if (existingBytes > 0L && supportsRanges) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        } else if (existingBytes > 0L && !supportsRanges) {
            existingBytes = 0L
            partFile.delete()
        }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                throw java.io.IOException("Server returned HTTP ${resp.code}")
            }
            val isPartial = resp.code == 206
            val append = isPartial && existingBytes > 0L
            if (!append && partFile.exists()) {
                existingBytes = 0L
            }

            val body = resp.body ?: throw java.io.IOException("Empty response body")
            val contentLength = body.contentLength()
            val resolvedTotal = when {
                isPartial && contentLength > 0L -> existingBytes + contentLength
                contentLength > 0L -> contentLength
                else -> knownTotalBytes
            }.coerceAtLeast(0L)

            val serverAcceptsRanges = isPartial || resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true

            val buffer = ByteArray(16 * 1024)
            var totalRead = existingBytes
            val lastReportTime = AtomicLong(System.currentTimeMillis())
            val lastReportBytes = AtomicLong(existingBytes)

            FileOutputStream(partFile, append).use { out ->
                body.byteStream().use { input ->
                    while (!isStopped) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        totalRead += read
                        maybePublishProgress(
                            downloadId = downloadId,
                            title = title,
                            currentBytes = totalRead,
                            totalBytes = resolvedTotal,
                            supportsRanges = serverAcceptsRanges,
                            lastReportTime = lastReportTime,
                            lastReportBytes = lastReportBytes,
                            notificationId = notificationId,
                            notificationsEnabled = notificationsEnabled,
                            dao = dao,
                            notificationHelper = notificationHelper
                        )
                    }
                }
            }
        }
    }

    private suspend fun maybePublishProgress(
        downloadId: Long,
        title: String,
        currentBytes: Long,
        totalBytes: Long,
        supportsRanges: Boolean,
        lastReportTime: AtomicLong,
        lastReportBytes: AtomicLong,
        notificationId: Int,
        notificationsEnabled: Boolean,
        dao: com.example.data.local.DownloadDao,
        notificationHelper: DownloadNotificationHelper
    ) {
        val now = System.currentTimeMillis()
        val prevTime = lastReportTime.get()
        val elapsedMs = now - prevTime
        if (elapsedMs < 450L) return
        if (!lastReportTime.compareAndSet(prevTime, now)) return

        val prevBytes = lastReportBytes.getAndSet(currentBytes)
        val deltaBytes = (currentBytes - prevBytes).coerceAtLeast(0L)
        val speedBps = if (elapsedMs > 0L) (deltaBytes * 1000L) / elapsedMs else 0L
        val remainingBytes = (totalBytes - currentBytes).coerceAtLeast(0L)
        val etaSecs = if (speedBps > 0L && totalBytes > 0L) remainingBytes / speedBps else 0L

        dao.updateProgress(
            id = downloadId,
            downloadedBytes = currentBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = speedBps,
            etaSeconds = etaSecs,
            status = DownloadStatus.DOWNLOADING.name,
            supportsRanges = supportsRanges,
            errorMessage = null,
            updatedAt = now
        )

        if (notificationsEnabled) {
            setForegroundSafe(
                notificationId,
                notificationHelper.buildProgressNotification(
                    workerId = id,
                    title = title,
                    downloadedBytes = currentBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSec = speedBps,
                    etaSeconds = etaSecs
                )
            )
        }
    }

    private suspend fun setForegroundSafe(notificationId: Int, notification: android.app.Notification) {
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(notificationId, notification)
            }
            setForeground(info)
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val PARALLEL_THRESHOLD_BYTES = 2L * 1024L * 1024L
    }
}
