package com.example.analyzer

import java.util.Locale

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytesPerSec >= gb -> String.format(Locale.US, "%.2f GB/s", bytesPerSec / gb)
            bytesPerSec >= mb -> String.format(Locale.US, "%.2f MB/s", bytesPerSec / mb)
            else -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / kb)
        }
    }

    fun formatEta(seconds: Long): String {
        if (seconds <= 0L) return "--"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hrs > 0 -> String.format(Locale.US, "%dh %02dm", hrs, mins)
            mins > 0 -> String.format(Locale.US, "%dm %02ds", mins, secs)
            else -> "${secs}s"
        }
    }

    fun formatDuration(durationMs: Long?): String {
        if (durationMs == null || durationMs <= 0L) return "Unknown"
        val totalSecs = durationMs / 1000L
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun qualityBadgeForHeight(height: Int): Pair<String, String> {
        return when {
            height >= 4320 -> "4320p" to "8K UHD"
            height >= 2160 -> "2160p" to "4K UHD"
            height >= 1440 -> "1440p" to "2K QHD"
            height >= 1080 -> "1080p" to "Full HD"
            height >= 720 -> "720p" to "HD"
            height >= 480 -> "480p" to "SD"
            height >= 360 -> "360p" to "Standard"
            height >= 240 -> "240p" to "Low"
            height >= 144 -> "144p" to "Data Saver"
            else -> "${height}p" to "Custom"
        }
    }
}
