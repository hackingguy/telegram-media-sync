package com.yourpackage.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object NotificationChannelManager {
    const val SYNC_CHANNEL_ID = "media_sync_channel"
    const val SYNC_CHANNEL_NAME = "Media Sync Service"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create sync service channel
            val syncChannel = NotificationChannel(
                SYNC_CHANNEL_ID,
                SYNC_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows media sync progress"
                enableVibration(false)
                setShowBadge(false)
            }

            NotificationManagerCompat.from(context)
                .createNotificationChannel(syncChannel)
        }
    }
} 