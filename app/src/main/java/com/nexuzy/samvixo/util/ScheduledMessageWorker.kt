package com.nexuzy.samvixo.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexuzy.samvixo.data.repository.ChatRepositoryImpl
import com.nexuzy.samvixo.domain.model.Message
import com.nexuzy.samvixo.domain.model.MessageType

class ScheduledMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.failure()
        val content = inputData.getString(KEY_CONTENT) ?: return Result.failure()
        val senderId = inputData.getString(KEY_SENDER_ID) ?: return Result.failure()

        val message = Message(
            chatId = chatId,
            senderId = senderId,
            content = content,
            type = MessageType.TEXT
        )

        return try {
            ChatRepositoryImpl().sendMessage(message)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_CONTENT = "content"
        const val KEY_SENDER_ID = "sender_id"
    }
}
