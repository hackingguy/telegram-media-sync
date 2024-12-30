package com.yourpackage.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.yourpackage.R
import com.yourpackage.ui.MainActivity
import com.yourpackage.util.NotificationConstants
import android.provider.MediaStore
import android.util.Log
import com.yourpackage.data.local.AppDatabase
import com.yourpackage.data.local.entity.SyncedFile
import com.yourpackage.data.remote.TelegramBotService
import java.io.File
import android.content.pm.ServiceInfo
import androidx.work.workDataOf
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.yourpackage.receiver.NotificationActionReceiver
import android.content.IntentFilter

class MediaSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val telegramBotService = TelegramBotService()
    private val syncedFileDao = AppDatabase.getDatabase(context).syncedFileDao()

    companion object {
        private const val TAG = "MediaSyncWorker"
    }

    private suspend fun updateProgress(
        imageCount: Int, 
        videoCount: Int, 
        status: String,
        isPaused: Boolean = false,
        pauseReason: String? = null
    ) {
        setProgress(workDataOf(
            "image_count" to imageCount,
            "video_count" to videoCount,
            "status" to status,
            "is_paused" to isPaused,
            "pause_reason" to (pauseReason ?: "")
        ))
        setForeground(createForegroundInfo(
            "$status (Images: $imageCount, Videos: $videoCount)",
            isPaused,
            pauseReason
        ))
    }

    override suspend fun doWork(): Result {
        try {
            // Check if work is paused
            val isPaused = inputData.getBoolean("is_paused", false)
            if (isPaused) {
                val imageCount = inputData.getInt("image_count", 0)
                val videoCount = inputData.getInt("video_count", 0)
                setProgress(workDataOf(
                    "is_paused" to true,
                    "pause_reason" to "Sync manually paused",
                    "primary_constraint" to "Sync manually paused",
                    "image_count" to imageCount,
                    "video_count" to videoCount
                ))
                return Result.retry()
            }

            // Check if this is a forced sync
            val isForced = inputData.getBoolean("force_sync", false)
            
            // Skip constraint checking if forced
            if (!isForced && !checkConstraints()) {
                return Result.retry()
            }

            var imageCount = 0
            var videoCount = 0

            setForeground(createForegroundInfo("Starting media sync..."))
            updateProgress(0, 0, "Starting sync")
            
            val token = inputData.getString("bot_token")
            val chatId = inputData.getString("chat_id")
            
            if (token == null || chatId == null) {
                Log.e(TAG, "Missing required parameters: token=$token, chatId=$chatId")
                return Result.failure()
            }

            Log.d(TAG, "Initializing bot with chat ID: $chatId")
            telegramBotService.initialize(token)

            // Create topics if they don't exist
            Log.d(TAG, "Creating topics")
            val imageTopicId = telegramBotService.getTopicId(chatId, TelegramBotService.IMAGES_TOPIC_NAME)
            val videoTopicId = telegramBotService.getTopicId(chatId, TelegramBotService.VIDEOS_TOPIC_NAME)
            Log.d(TAG, "Created topics - Images: $imageTopicId, Videos: $videoTopicId")

            // Query for new media files
            val contentResolver = applicationContext.contentResolver
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.MIME_TYPE
            )

            // Query for images
            Log.d(TAG, "Querying for images")
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                    val file = File(path)
                    Log.d(TAG, "Found image: $path, exists: ${file.exists()}")
                    
                    // Check if file is already synced
                    if (syncedFileDao.getSyncedFile(path) != null) {
                        Log.d(TAG, "File already synced: $path")
                        continue
                    }

                    if (file.exists()) {
                        try {
                            telegramBotService.sendMedia(chatId, imageTopicId, file)
                            syncedFileDao.insertSyncedFile(
                                SyncedFile(
                                    filePath = path,
                                    syncTimestamp = System.currentTimeMillis(),
                                    messageId = 0, // We don't track message IDs for now
                                    type = "image"
                                )
                            )
                            imageCount++
                            updateProgress(imageCount, videoCount, "Syncing media")
                            Log.d(TAG, "Successfully sent image: $path")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send image: $path", e)
                        }
                    }
                }
            }
            Log.d(TAG, "Processed $imageCount images")

            // Query for videos
            Log.d(TAG, "Querying for videos")
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                    val file = File(path)
                    Log.d(TAG, "Found video: $path, exists: ${file.exists()}")
                    
                    // Check if file is already synced
                    if (syncedFileDao.getSyncedFile(path) != null) {
                        Log.d(TAG, "File already synced: $path")
                        continue
                    }

                    if (file.exists()) {
                        try {
                            telegramBotService.sendMedia(chatId, videoTopicId, file)
                            syncedFileDao.insertSyncedFile(
                                SyncedFile(
                                    filePath = path,
                                    syncTimestamp = System.currentTimeMillis(),
                                    messageId = 0, // We don't track message IDs for now
                                    type = "video"
                                )
                            )
                            videoCount++
                            updateProgress(imageCount, videoCount, "Syncing media")
                            Log.d(TAG, "Successfully sent video: $path")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send video: $path", e)
                        }
                    }
                }
            }
            Log.d(TAG, "Processed $videoCount videos")

            updateProgress(imageCount, videoCount, "Sync completed")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync", e)
            return Result.failure()
        }
    }

    private fun createForegroundInfo(progress: String, isPaused: Boolean = false, pauseReason: String? = null): ForegroundInfo {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Create resume action
        val resumeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_RESUME_SYNC
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            resumeIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(if (isPaused) "Media Sync Paused" else "Media Sync")
            .setContentText(if (isPaused) pauseReason else progress)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (isPaused) {
                    "$pauseReason\nTap Resume to force sync anyway"
                } else {
                    progress
                }
            ))
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .apply {
                if (isPaused) {
                    addAction(
                        android.R.drawable.ic_media_play,
                        "Resume",
                        resumePendingIntent
                    )
                }
            }
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NotificationConstants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationConstants.NOTIFICATION_ID, notification)
        }
    }

    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    private fun isCharging(): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.isCharging
    }

    private fun isBatteryLow(): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
        val threshold = prefs.getInt("battery_threshold", 20)
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) <= threshold
    }

    private suspend fun checkConstraints(): Boolean {
        val prefs = applicationContext.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
        val requireBatteryNotLow = prefs.getBoolean("require_battery_not_low", true)
        val requireWifi = prefs.getBoolean("require_wifi", true)

        // Check battery level
        if (requireBatteryNotLow) {
            val batteryStatus = applicationContext.registerReceiver(null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = level * 100 / scale.toFloat()

            if (batteryPct < 20) {
                setProgress(workDataOf(
                    "is_paused" to true,
                    "pause_reason" to "Battery below 20%",
                    "primary_constraint" to "Battery below 20%"
                ))
                return false
            }
        }

        // Check network connectivity
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        if (capabilities == null) {
            setProgress(workDataOf(
                "is_paused" to true,
                "pause_reason" to "No network connection",
                "primary_constraint" to "No network connection"
            ))
            return false
        }

        // Check WiFi requirement
        if (requireWifi && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            setProgress(workDataOf(
                "is_paused" to true,
                "pause_reason" to "Waiting for WiFi connection",
                "primary_constraint" to "Waiting for WiFi connection"
            ))
            return false
        }

        // All constraints met
        setProgress(workDataOf(
            "is_paused" to false,
            "pause_reason" to null,
            "primary_constraint" to null
        ))
        return true
    }
} 