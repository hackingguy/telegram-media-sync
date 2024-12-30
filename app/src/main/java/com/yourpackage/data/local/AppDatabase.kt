package com.yourpackage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yourpackage.data.local.dao.SyncedFileDao
import com.yourpackage.data.local.entity.SyncedFile

@Database(entities = [SyncedFile::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncedFileDao(): SyncedFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "telegram_drive_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
} 