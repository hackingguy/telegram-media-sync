package com.yourpackage.data.repository

import android.util.Log
import com.yourpackage.data.remote.TelegramBotService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MediaSyncRepository {
    private val telegramBotService = TelegramBotService()

    companion object {
        private const val TAG = "MediaSyncRepository"
    }

    fun isSyncEnabled(): Flow<Boolean> = flow {
        emit(false) // For now, always return false since we're not persisting the state
    }

    suspend fun setupBot(token: String, chatId: String) {
        Log.d(TAG, "Setting up bot with chat ID: $chatId")
        telegramBotService.initialize(token)
    }

    suspend fun updateSyncEnabled(enabled: Boolean) {
        Log.d(TAG, "Updating sync enabled: $enabled")
        // No-op for now since we're not persisting the state
    }
} 