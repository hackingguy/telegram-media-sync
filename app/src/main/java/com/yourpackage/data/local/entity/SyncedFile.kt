package com.yourpackage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "synced_files")
data class SyncedFile(
    @PrimaryKey val filePath: String,
    val syncTimestamp: Long,
    val messageId: Int,
    val type: String // "image" or "video"
) 