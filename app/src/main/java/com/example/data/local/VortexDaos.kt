package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt DESC")
    fun observeActiveAndPausedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY updatedAt DESC")
    fun observeCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 10")
    fun observeRecentlyPlayed(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING')")
    suspend fun countRunningOrQueued(): Int

    @Query("SELECT * FROM downloads WHERE status = 'PAUSED' AND errorMessage = 'PAUSED_NETWORK'")
    suspend fun getNetworkPausedDownloads(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Query(
        """
        UPDATE downloads SET 
            downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes,
            speedBytesPerSec = :speedBytesPerSec,
            etaSeconds = :etaSeconds,
            status = :status,
            supportsRanges = :supportsRanges,
            errorMessage = :errorMessage,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long,
        status: String,
        supportsRanges: Boolean,
        errorMessage: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET status = :status, speedBytesPerSec = 0, etaSeconds = 0, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(
        id: Long,
        status: String,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET fileName = :newFileName, title = :newTitle, localFilePath = :newPath, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFileDetails(
        id: Long,
        newFileName: String,
        newTitle: String,
        newPath: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET lastPlayedAt = :playedAt, lastPlaybackPositionMs = :positionMs WHERE id = :id")
    suspend fun updatePlaybackProgress(id: Long, playedAt: Long, positionMs: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearHistory()
}

@Dao
interface BrowserHistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<BrowserHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BrowserHistoryEntity)

    @Query("DELETE FROM browser_history")
    suspend fun clearAll()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM ai_chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages ORDER BY createdAt ASC, id ASC")
    suspend fun getAllMessages(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM ai_chat_messages")
    suspend fun clearAll()
}

