package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadEntity::class, BrowserHistoryEntity::class, ChatMessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VortexDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun browserHistoryDao(): BrowserHistoryDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: VortexDatabase? = null

        fun getInstance(context: Context): VortexDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VortexDatabase::class.java,
                    "vortex_media.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
