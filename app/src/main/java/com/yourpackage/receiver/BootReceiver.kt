package com.yourpackage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yourpackage.service.MediaSyncWorker
import java.util.concurrent.TimeUnit
import androidx.work.Data

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
            val autoRestart = prefs.getBoolean("auto_restart_on_boot", true)
            
            if (autoRestart) {
                val imageTopicId = prefs.getInt("images_topic_id", -1)
                val videoTopicId = prefs.getInt("videos_topic_id", -1)
                val token = prefs.getString("bot_token", "") ?: ""
                val chatId = prefs.getString("chat_id", "") ?: ""

                val inputData = Data.Builder()
                    .putString("bot_token", token)
                    .putString("chat_id", chatId)
                    .apply {
                        if (imageTopicId != -1) putInt("images_topic_id", imageTopicId)
                        if (videoTopicId != -1) putInt("videos_topic_id", videoTopicId)
                    }
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<MediaSyncWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        "media_sync_work",
                        ExistingPeriodicWorkPolicy.KEEP,
                        workRequest
                    )
            }
        }
    }
} 