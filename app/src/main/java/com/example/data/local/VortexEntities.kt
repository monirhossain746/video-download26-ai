package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val url: String,
    val finalUrl: String,
    val title: String,
    val fileName: String,
    val localFilePath: String,
    val mimeType: String,
    val category: String,
    val resolutionLabel: String,
    val thumbnailUrl: String? = null,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val status: String = DownloadStatus.QUEUED.name,
    val supportsRanges: Boolean = false,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L,
    val lastPlaybackPositionMs: Long = 0L
) {
    val statusEnum: DownloadStatus
        get() = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED)

    val categoryEnum: DownloadCategory
        get() = runCatching { DownloadCategory.valueOf(category) }.getOrDefault(DownloadCategory.OTHER)

    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

@Entity(tableName = "browser_history")
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val role: String, // "user" or "model"
    val text: String,
    val modelName: String,
    val createdAt: Long = System.currentTimeMillis()
)
