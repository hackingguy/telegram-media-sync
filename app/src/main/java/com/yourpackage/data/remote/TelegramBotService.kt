package com.yourpackage.data.remote

import android.util.Log
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.request.CreateForumTopic
import com.pengrad.telegrambot.request.SendDocument
import com.pengrad.telegrambot.request.SendPhoto
import com.pengrad.telegrambot.request.SendVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Build
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.GetUpdates
import com.pengrad.telegrambot.request.GetMe

class TelegramBotService {
    private var bot: TelegramBot? = null

    companion object {
        private const val TAG = "TelegramBotService"
        const val IMAGES_TOPIC_NAME = "Images"
        const val VIDEOS_TOPIC_NAME = "Videos"
    }

    fun initialize(token: String) {
        Log.d(TAG, "Initializing bot with token: ${token.take(10)}...")
        bot = TelegramBot(token)
    }

    suspend fun topicExists(chatId: String, topicName: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Checking if topic '$topicName' exists in chat: $chatId")
        try {
            createTopic(chatId, topicName)
            false
        } catch (e: Exception) {
            e.message?.contains("topic already exists") == true
        }
    }

    suspend fun getTopicId(chatId: String, topicName: String): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting topic ID for '$topicName' in chat: $chatId")
        try {
            createTopic(chatId, topicName)
        } catch (e: Exception) {
            if (e.message?.contains("topic already exists") == true) {
                val idMatch = "topic with id (\\d+)".toRegex().find(e.message ?: "")
                idMatch?.groupValues?.get(1)?.toIntOrNull() 
                    ?: throw IllegalStateException("Could not extract topic ID from error message")
            } else {
                throw e
            }
        }
    }

    private suspend fun createTopic(chatId: String, topicName: String): Int = withContext(Dispatchers.IO) {
        val request = CreateForumTopic(chatId.toLong(), topicName)
        val response = bot?.execute(request)
        if (response?.isOk == true) {
            val messageThreadId = response.forumTopic()?.messageThreadId() ?: 0
            Log.d(TAG, "Created topic '$topicName' with ID: $messageThreadId")
            messageThreadId
        } else {
            throw IllegalStateException("Failed to create topic: ${response?.description()}")
        }
    }

    suspend fun sendMedia(chatId: String, messageThreadId: Int, file: File) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sending media: ${file.name} to chat: $chatId, thread: $messageThreadId")
        val mimeType = getMimeType(file)
        
        val request = when {
            mimeType.startsWith("image/") -> {
                SendPhoto(chatId.toLong(), file)
                    .messageThreadId(messageThreadId)
            }
            mimeType.startsWith("video/") -> {
                SendVideo(chatId.toLong(), file)
                    .messageThreadId(messageThreadId)
            }
            else -> {
                SendDocument(chatId.toLong(), file)
                    .messageThreadId(messageThreadId)
            }
        }

        val response = bot?.execute(request)
        if (response?.isOk == true) {
            Log.d(TAG, "Successfully sent media: ${file.name}")
        } else {
            Log.e(TAG, "Failed to send media: ${file.name}")
            throw IllegalStateException("Failed to send media: ${file.name}")
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }

    suspend fun createImagesTopic(chatId: String): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "Creating images topic in chat: $chatId")
        try {
            return@withContext getTopicId(chatId, IMAGES_TOPIC_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create images topic", e)
            throw e
        }
    }

    suspend fun createVideosTopic(chatId: String): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "Creating videos topic in chat: $chatId")
        try {
            return@withContext getTopicId(chatId, VIDEOS_TOPIC_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create videos topic", e)
            throw e
        }
    }

    suspend fun sendTestMessage(chatId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sending test message to chat: $chatId")
        try {
            val message = """
                🔔 Test Connection Successful!
                
                ✅ Bot is properly configured
                ✅ Chat access verified
                ✅ Permissions confirmed
                
                Your media backup can now be started.
                
                📱 Device: ${Build.MANUFACTURER} ${Build.MODEL}
                🕒 Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
            """.trimIndent()

            val response = bot?.execute(SendMessage(chatId, message))
            if (response?.isOk == true) {
                Log.d(TAG, "Test message sent successfully")
            } else {
                Log.e(TAG, "Failed to send test message: ${response?.description()}")
                throw IllegalStateException("Failed to send test message: ${response?.description()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending test message", e)
            throw e
        }
    }

    data class ChatInfo(
        val id: String,
        val title: String,
        val type: String // "group", "supergroup", etc.
    )

    suspend fun getChats(): List<ChatInfo> = withContext(Dispatchers.IO) {
        val bot = bot ?: throw IllegalStateException("Bot not initialized")
        
        try {
            val updates = bot.execute(GetUpdates()
                .limit(100))  // Get last 100 updates
                ?: throw IllegalStateException("Failed to get updates from Telegram")

            if (!updates.isOk) {
                throw IllegalStateException("Telegram error: ${updates.description()}")
            }

            val chatList = updates.updates()
                .mapNotNull { update ->
                    // Try different types of updates that might contain chat info
                    val chat = update.message()?.chat()
                        ?: update.channelPost()?.chat()
                        ?: update.editedMessage()?.chat()
                        ?: update.editedChannelPost()?.chat()

                    chat?.let {
                        // Convert Chat.Type to String for comparison
                        val chatType = it.type().toString()
                        if (chatType in listOf("group", "supergroup", "channel")) {
                            ChatInfo(
                                id = it.id().toString(),
                                title = it.title() ?: "Unnamed Group",
                                type = chatType
                            )
                        } else null
                    }
                }
                .distinctBy { it.id } // Remove duplicates

            Log.d(TAG, "Found ${chatList.size} chats")
            return@withContext chatList

        } catch (e: Exception) {
            Log.e(TAG, "Error getting chats", e)
            throw IllegalStateException("Failed to get chats: ${e.message ?: "Unknown error"}", e)
        }
    }

    data class BotInfo(
        val id: Long,
        val username: String,
        val firstName: String
    )

    suspend fun getBotInfo(): BotInfo? = withContext(Dispatchers.IO) {
        try {
            val bot = bot ?: throw IllegalStateException("Bot not initialized")
            val response = bot.execute(GetMe())
            if (response.isOk) {
                val user = response.user()
                return@withContext BotInfo(
                    id = user.id(),
                    username = user.username() ?: "",
                    firstName = user.firstName() ?: ""
                )
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bot info", e)
            return@withContext null
        }
    }
}