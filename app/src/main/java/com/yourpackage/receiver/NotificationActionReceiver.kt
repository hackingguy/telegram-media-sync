package com.yourpackage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import com.yourpackage.service.MediaSyncWorker
import java.util.concurrent.TimeUnit

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RESUME_SYNC = "com.yourpackage.action.RESUME_SYNC"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RESUME_SYNC) {
            val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
            
            // Get stored data
            val token = prefs.getString("bot_token", "") ?: ""
            val chatId = prefs.getString("chat_id", "") ?: ""
            val imageTopicId = prefs.getInt("images_topic_id", -1)
            val videoTopicId = prefs.getInt("videos_topic_id", -1)

            // Create work request with force sync flag
            val inputData = androidx.work.Data.Builder()
                .putString("bot_token", token)
                .putString("chat_id", chatId)
                .putInt("images_topic_id", imageTopicId)
                .putInt("videos_topic_id", videoTopicId)
                .putBoolean("force_sync", true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<MediaSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setInputData(inputData)
                .build()

            // Replace existing work with forced sync
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "media_sync_work",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }
} 