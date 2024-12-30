package com.yourpackage.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yourpackage.data.remote.TelegramBotService
import com.yourpackage.databinding.ActivityMainBinding
import com.yourpackage.service.MediaSyncWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import android.Manifest
import android.view.View
import androidx.work.BackoffPolicy
import androidx.work.WorkRequest
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import com.yourpackage.R
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.work.await
import android.app.AlertDialog
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yourpackage.ui.adapter.ChatListAdapter
import com.yourpackage.data.remote.TelegramBotService.ChatInfo
import androidx.work.workDataOf

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val telegramBotService = TelegramBotService()
    private var isSyncing = false // Track sync state

    companion object {
        private const val TAG = "MainActivity"
        private const val WORK_NAME = "media_sync_work"
        private const val PERMISSION_REQUEST_CODE = 123
        private const val NOTIFICATION_PERMISSION_CODE = 124
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // Permission granted, proceed with your operation
                Log.d(TAG, "All files access permission granted")
            } else {
                Log.d(TAG, "All files access permission denied")
                // Show explanation to the user
                showPermissionExplanationDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request permissions when activity starts
        requestStoragePermissions()
        requestNotificationPermission()

        // Observe existing work
        observeExistingWork()

        binding.startSyncButton.setOnClickListener {
            if (isSyncing) {
                stopSync()
            } else {
                lifecycleScope.launch {
                    startSync()
                }
            }
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.forceResumeButton.setOnClickListener {
            lifecycleScope.launch {
                forceResumeSync()
            }
        }

        binding.testConnectionButton.setOnClickListener {
            lifecycleScope.launch {
                testTelegramConnection()
            }
        }

        binding.chatIdInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clearStoredTopicIds() // Clear stored topic IDs when chat ID changes
            }
        })

        binding.helpButton.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        binding.getChatIdButton.setOnClickListener {
            showChatListDialog()
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 and above - requests full file access
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse("package:$packageName")
                    requestPermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    requestPermissionLauncher.launch(intent)
                }
            }
        } else {
            // Below Android 11 - requests legacy storage permissions
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            
            if (!hasPermissions(permissions)) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && 
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "Storage permissions granted")
                } else {
                    Log.d(TAG, "Storage permissions denied")
                    showPermissionExplanationDialog()
                }
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Storage Permission Required")
            .setMessage("This app needs storage access to sync your media files. Please grant the permission in Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    private suspend fun startSync() {
        Log.d(TAG, "Starting sync process")
        setLoadingState(true)
        
        try {
            val token = binding.botTokenInput.text.toString()
            val chatId = binding.chatIdInput.text.toString()

            Log.d(TAG, "Initializing TelegramBotService")
            telegramBotService.initialize(token)

            // Get stored topic IDs from preferences
            val prefs = getSharedPreferences("sync_settings", MODE_PRIVATE)
            var imageTopicId = prefs.getInt("images_topic_id", -1)
            var videoTopicId = prefs.getInt("videos_topic_id", -1)

            // Only create/fetch topics if we don't have them stored
            if (imageTopicId == -1 || videoTopicId == -1) {
                try {
                    if (imageTopicId == -1) {
                        imageTopicId = telegramBotService.getTopicId(chatId, TelegramBotService.IMAGES_TOPIC_NAME)
                        prefs.edit().putInt("images_topic_id", imageTopicId).apply()
                    }

                    if (videoTopicId == -1) {
                        videoTopicId = telegramBotService.getTopicId(chatId, TelegramBotService.VIDEOS_TOPIC_NAME)
                        prefs.edit().putInt("videos_topic_id", videoTopicId).apply()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create/get topics", e)
                    showSnackbar("Failed to setup topics: ${e.message}")
                    isSyncing = false
                    return
                }
            }

            // Store topic IDs in input data
            val inputData = Data.Builder()
                .putString("bot_token", token)
                .putString("chat_id", chatId)
                .putInt("images_topic_id", imageTopicId)
                .putInt("videos_topic_id", videoTopicId)
                .build()

            // Get sync settings
            val requireCharging = prefs.getBoolean("require_charging", true)
            val requireBatteryNotLow = prefs.getBoolean("require_battery_not_low", true)
            val requireWifi = prefs.getBoolean("require_wifi", true)

            // Create work constraints based on settings
            Log.d(TAG, "Created work constraints")
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (requireWifi) NetworkType.UNMETERED
                    else NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(requireBatteryNotLow)
                .build()

            // Create periodic work request
            val workRequest = PeriodicWorkRequestBuilder<MediaSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,  // Starts at 10 seconds
                    TimeUnit.MILLISECONDS
                )
                .addTag("media_sync")
                .build()
            Log.d(TAG, "Created work request with ID: ${workRequest.id}")

            // Cancel existing work and enqueue new work
            Log.d(TAG, "Enqueueing work request")
            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE, // Replace existing work with new constraints
                    workRequest
                )
                .also { 
                    Log.d(TAG, "Work request enqueued successfully") 
                    binding.startSyncButton.text = "Stop Sync" // Change button text to "Stop Sync"
                }

            // Observe work status
            WorkManager.getInstance(this)
                .getWorkInfoByIdLiveData(workRequest.id)
                .observe(this) { workInfo ->
                    if (workInfo != null) { // Check if workInfo is not null
                        Log.d(TAG, "Work status update - ID: ${workRequest.id}, State: ${workInfo.state}")
                        when (workInfo.state) {
                            WorkInfo.State.ENQUEUED -> {
                                Log.d(TAG, "Work is enqueued")
                            }
                            WorkInfo.State.RUNNING -> {
                                Log.d(TAG, "Work is running")
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                Log.d(TAG, "Work completed successfully")
                            }
                            WorkInfo.State.FAILED -> {
                                Log.e(TAG, "Work failed")
                            }
                            else -> {
                                Log.d(TAG, "Work in state: ${workInfo.state}")
                            }
                        }
                    } else {
                        Log.d(TAG, "WorkInfo is null for ID: ${workRequest.id}")
                    }
                }

            isSyncing = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sync", e)
            showSnackbar("Failed to start sync: ${e.message}")
        } finally {
            setLoadingState(false)
        }
    }

    private fun stopSync() {
        setLoadingState(true)
        lifecycleScope.launch {
            try {
                // Instead of canceling work, we'll set a pause flag
                val workManager = WorkManager.getInstance(this@MainActivity)
                val workData = workDataOf(
                    "is_paused" to true,
                    "pause_reason" to "Sync manually paused",
                    "primary_constraint" to "Sync manually paused"
                )

                // Get current work and update its progress
                val workInfo = workManager.getWorkInfosForUniqueWork(WORK_NAME)
                    .await()
                    .firstOrNull()

                if (workInfo != null) {
                    // Keep the current progress data
                    val currentProgress = workInfo.progress
                    val imageCount = currentProgress.getInt("image_count", 0)
                    val videoCount = currentProgress.getInt("video_count", 0)

                    // Combine current progress with pause state
                    val newProgress = workDataOf(
                        "is_paused" to true,
                        "pause_reason" to "Sync manually paused",
                        "primary_constraint" to "Sync manually paused",
                        "image_count" to imageCount,
                        "video_count" to videoCount
                    )

                    // Update the work progress
                    workManager.getWorkInfoByIdLiveData(workInfo.id)
                        .observe(this@MainActivity) { info ->
                            if (info != null && info.state.isFinished) {
                                // Re-enqueue the work with the same parameters but paused
                                val inputData = info.outputData
                                val workRequest = PeriodicWorkRequestBuilder<MediaSyncWorker>(
                                    repeatInterval = 15,
                                    repeatIntervalTimeUnit = TimeUnit.MINUTES
                                )
                                    .setInputData(inputData)
                                    .setInitialDelay(1, TimeUnit.HOURS) // Add delay before next attempt
                                    .build()

                                workManager.enqueueUniquePeriodicWork(
                                    WORK_NAME,
                                    ExistingPeriodicWorkPolicy.REPLACE,
                                    workRequest
                                )
                            }
                        }
                }

                isSyncing = false
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping sync", e)
                showSnackbar("Failed to stop sync: ${e.message}")
            } finally {
                setLoadingState(false)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    private fun observeExistingWork() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(WORK_NAME)
            .observe(this) { workInfoList ->
                workInfoList?.firstOrNull()?.let { workInfo ->
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            binding.startSyncButton.text = "Stop Sync"
                            val isPaused = workInfo.progress.getBoolean("is_paused", false)
                            val pauseReason = workInfo.progress.getString("pause_reason")
                            binding.forceResumeButton.isEnabled = isPaused && !pauseReason.isNullOrEmpty()
                            isSyncing = true
                            updateSyncStatus(workInfo)
                        }
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                            binding.startSyncButton.text = "Stop Sync"
                            val isPaused = workInfo.progress.getBoolean("is_paused", false)
                            val pauseReason = workInfo.progress.getString("pause_reason")
                            
                            // Enable force resume for any constraint-based pause
                            binding.forceResumeButton.isEnabled = (isPaused && !pauseReason.isNullOrEmpty()) || 
                                                                workInfo.state == WorkInfo.State.BLOCKED
                            isSyncing = true
                            
                            if (isPaused && !pauseReason.isNullOrEmpty()) {
                                binding.syncStatusText.text = pauseReason
                            } else if (workInfo.state == WorkInfo.State.BLOCKED) {
                                // Get the primary constraint reason from the worker
                                val primaryReason = workInfo.progress.getString("primary_constraint") 
                                    ?: "Waiting for conditions"
                                binding.syncStatusText.text = primaryReason
                            } else {
                                binding.syncStatusText.text = "Sync enqueued"
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            binding.startSyncButton.text = "Start Sync"
                            binding.forceResumeButton.isEnabled = false
                            isSyncing = false
                            updateSyncStatus(workInfo)
                        }
                        WorkInfo.State.FAILED -> {
                            binding.startSyncButton.text = "Start Sync"
                            binding.forceResumeButton.isEnabled = false
                            isSyncing = false
                            updateSyncStatus(workInfo)
                        }
                        else -> {
                            binding.startSyncButton.text = "Start Sync"
                            binding.forceResumeButton.isEnabled = false
                            isSyncing = false
                            binding.syncStatusText.text = "Not syncing"
                            binding.syncCountText.text = "Images: 0, Videos: 0"
                        }
                    }
                }
            }
    }

    private fun updateSyncStatus(workInfo: WorkInfo) {
        val imageCount = workInfo.progress.getInt("image_count", 0)
        val videoCount = workInfo.progress.getInt("video_count", 0)
        val status = workInfo.progress.getString("status") ?: "Unknown"
        val isPaused = workInfo.progress.getBoolean("is_paused", false)
        val pauseReason = workInfo.progress.getString("pause_reason")
        val primaryConstraint = workInfo.progress.getString("primary_constraint")

        // Show the most specific reason available
        when {
            isPaused && !pauseReason.isNullOrEmpty() -> {
                binding.syncStatusText.text = pauseReason
                binding.forceResumeButton.isEnabled = true
            }
            !primaryConstraint.isNullOrEmpty() -> {
                binding.syncStatusText.text = primaryConstraint
                binding.forceResumeButton.isEnabled = true
            }
            else -> {
                binding.syncStatusText.text = status
                binding.forceResumeButton.isEnabled = false
            }
        }
        binding.syncCountText.text = "Images: $imageCount, Videos: $videoCount"
    }

    private fun forceResumeSync() {
        setLoadingState(true)
        lifecycleScope.launch {
            try {
                val workInfo = WorkManager.getInstance(this@MainActivity)
                    .getWorkInfosForUniqueWork(WORK_NAME)
                    .await()
                    .firstOrNull() ?: return@launch

                // Cancel existing work
                WorkManager.getInstance(this@MainActivity)
                    .cancelUniqueWork(WORK_NAME)
                    .await()

                // Start new work without constraints
                val token = binding.botTokenInput.text.toString()
                val chatId = binding.chatIdInput.text.toString()

                Log.d(TAG, "Force resuming sync")
                telegramBotService.initialize(token)

                val prefs = getSharedPreferences("sync_settings", MODE_PRIVATE)
                var imageTopicId = prefs.getInt("images_topic_id", -1)
                var videoTopicId = prefs.getInt("videos_topic_id", -1)

                if (imageTopicId == -1 || videoTopicId == -1) {
                    if (imageTopicId == -1) {
                        imageTopicId = telegramBotService.getTopicId(chatId, TelegramBotService.IMAGES_TOPIC_NAME)
                        prefs.edit().putInt("images_topic_id", imageTopicId).apply()
                    }

                    if (videoTopicId == -1) {
                        videoTopicId = telegramBotService.getTopicId(chatId, TelegramBotService.VIDEOS_TOPIC_NAME)
                        prefs.edit().putInt("videos_topic_id", videoTopicId).apply()
                    }
                }

                val inputData = Data.Builder()
                    .putString("bot_token", token)
                    .putString("chat_id", chatId)
                    .putInt("images_topic_id", imageTopicId)
                    .putInt("videos_topic_id", videoTopicId)
                    .putBoolean("force_sync", true)
                    .build()

                // Create work request without constraints
                val workRequest = PeriodicWorkRequestBuilder<MediaSyncWorker>(
                    repeatInterval = 15,
                    repeatIntervalTimeUnit = TimeUnit.MINUTES
                )
                    .setInputData(inputData)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
                    .build()

                // Enqueue new work
                WorkManager.getInstance(this@MainActivity)
                    .enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest
                    )

            } catch (e: Exception) {
                Log.e(TAG, "Error during force resume", e)
                showSnackbar("Failed to resume sync: ${e.message}")
            } finally {
                setLoadingState(false)
            }
        }
    }

    private suspend fun testTelegramConnection() {
        try {
            val token = binding.botTokenInput.text.toString()
            val chatId = binding.chatIdInput.text.toString()

            if (token.isBlank() || chatId.isBlank()) {
                showSnackbar("Please enter both Bot Token and Chat ID")
                return
            }

            binding.testConnectionButton.isEnabled = false
            binding.testConnectionButton.text = "Testing..."

            withContext(Dispatchers.IO) {
                telegramBotService.initialize(token)
                telegramBotService.sendTestMessage(chatId)
            }

            showSnackbar("Test message sent successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Test connection failed", e)
            showSnackbar("Test failed: ${e.message}")
        } finally {
            binding.testConnectionButton.isEnabled = true
            binding.testConnectionButton.text = "Test Connection"
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun clearStoredTopicIds() {
        getSharedPreferences("sync_settings", MODE_PRIVATE)
            .edit()
            .remove("images_topic_id")
            .remove("videos_topic_id")
            .apply()
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.startSyncButton.isEnabled = !isLoading
        binding.settingsButton.isEnabled = !isLoading
        binding.testConnectionButton.isEnabled = !isLoading
        
        if (isLoading) {
            binding.startSyncButton.text = if (isSyncing) "Stopping..." else "Starting..."
            // Show circular progress indicator
            binding.startSyncButton.icon = CircularProgressDrawable(this).apply {
                setStyle(CircularProgressDrawable.DEFAULT)
                setColorSchemeColors(getColor(R.color.white))
                start()
            }
        } else {
            binding.startSyncButton.text = if (isSyncing) "Stop Sync" else "Start Sync"
            binding.startSyncButton.setIconResource(R.drawable.ic_sync)
        }
    }

    private fun showChatListDialog() {
        val dialog = AlertDialog.Builder(this)
            .setView(R.layout.dialog_chat_list)
            .create()

        dialog.show()

        val chatList = dialog.findViewById<RecyclerView>(R.id.chatList)!!
        val progressBar = dialog.findViewById<ProgressBar>(R.id.progressBar)!!
        val noChatsText = dialog.findViewById<TextView>(R.id.noChatsText)!!

        chatList.layoutManager = LinearLayoutManager(this)
        val adapter = ChatListAdapter { chat ->
            binding.chatIdInput.setText(chat.id)
            dialog.dismiss()
        }
        chatList.adapter = adapter

        lifecycleScope.launch {
            try {
                val token = binding.botTokenInput.text.toString()
                if (token.isBlank()) {
                    showSnackbar("Please enter Bot Token first")
                    dialog.dismiss()
                    return@launch
                }

                telegramBotService.initialize(token)
                
                // Get bot username from token
                val botUsername = token.split(":")[0]
                
                try {
                    // Try to get bot info instead of sending a message
                    val botInfo = telegramBotService.getBotInfo()
                    if (botInfo == null) {
                        showSnackbar("Invalid bot token. Please check and try again.")
                        dialog.dismiss()
                        return@launch
                    }
                    Log.d(TAG, "Bot info retrieved successfully: ${botInfo.username}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to validate bot token", e)
                    showSnackbar("Invalid bot token. Please check and try again.")
                    dialog.dismiss()
                    return@launch
                }

                val chats = try {
                    telegramBotService.getChats()
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting chats", e)
                    emptyList()
                }
                
                progressBar.visibility = View.GONE
                
                if (chats.isEmpty()) {
                    noChatsText.text = """
                        No groups found. To get started:
                        
                        1. Add the bot to your group
                        2. Make the bot an admin
                        3. Send at least one message in the group
                        4. Try again
                        
                        Note: The bot can only see messages after it's added to the group.
                    """.trimIndent()
                    noChatsText.visibility = View.VISIBLE
                    chatList.visibility = View.GONE
                } else {
                    adapter.updateChats(chats)
                    noChatsText.visibility = View.GONE
                    chatList.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in chat list dialog", e)
                progressBar.visibility = View.GONE
                noChatsText.text = """
                    Error: ${e.message}
                    
                    Please check:
                    1. Bot token is correct
                    2. Bot has admin rights
                    3. Internet connection is available
                """.trimIndent()
                noChatsText.visibility = View.VISIBLE
                chatList.visibility = View.GONE
            }
        }
    }
} 