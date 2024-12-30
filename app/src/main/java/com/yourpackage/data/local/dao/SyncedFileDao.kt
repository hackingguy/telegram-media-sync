package com.yourpackage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yourpackage.data.local.entity.SyncedFile
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncedFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedFile(file: SyncedFile)

    @Query("SELECT * FROM synced_files WHERE filePath = :filePath")
    suspend fun getSyncedFile(filePath: String): SyncedFile?

    @Query("SELECT * FROM synced_files ORDER BY syncTimestamp DESC")
    fun getAllSyncedFiles(): Flow<List<SyncedFile>>
} 