package com.example.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import com.example.analyzer.FormatUtils
import java.util.UUID

class DownloadNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannels()
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activeChannel = NotificationChannel(
                CHANNEL_ACTIVE_DOWNLOADS,
                context.getString(R.string.notification_channel_downloads_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_downloads_desc)
                setShowBadge(false)
            }

            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED_DOWNLOADS,
                context.getString(R.string.notification_channel_completed_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_completed_desc)
            }

            notificationManager.createNotificationChannel(activeChannel)
            notificationManager.createNotificationChannel(completedChannel)
        }
    }

    fun buildProgressNotification(
        workerId: UUID,
        title: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(workerId)

        val percent = if (totalBytes > 0L) ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
        val contentText = buildString {
            if (totalBytes > 0L) {
                append("$percent% • ${FormatUtils.formatBytes(downloadedBytes)} / ${FormatUtils.formatBytes(totalBytes)}")
            } else {
                append(FormatUtils.formatBytes(downloadedBytes))
            }
            if (speedBytesPerSec > 0L) {
                append(" • ${FormatUtils.formatSpeed(speedBytesPerSec)}")
            }
            if (etaSeconds > 0L) {
                append(" • ${FormatUtils.formatEta(etaSeconds)} left")
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_ACTIVE_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, totalBytes <= 0L)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.action_cancel), cancelIntent)
            .build()
    }

    fun showCompletedNotification(downloadId: Long, title: String, totalBytes: Long) {
        val openAppIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETED_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText("$title (${FormatUtils.formatBytes(totalBytes)})")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()

        runCatching {
            notificationManager.notify((20_000 + (downloadId % 10_000)).toInt(), notification)
        }
    }

    companion object {
        const val CHANNEL_ACTIVE_DOWNLOADS = "vortex_active_downloads"
        const val CHANNEL_COMPLETED_DOWNLOADS = "vortex_completed_downloads"
    }
}
