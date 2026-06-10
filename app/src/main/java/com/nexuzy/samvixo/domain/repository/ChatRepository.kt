package com.nexuzy.samvixo.domain.repository

import com.nexuzy.samvixo.domain.model.Chat
import com.nexuzy.samvixo.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getChats(userId: String): Flow<List<Chat>>
    fun getMessages(chatId: String): Flow<List<Message>>
    suspend fun sendMessage(message: Message)
    suspend fun deleteMessage(messageId: String, chatId: String, deleteForEveryone: Boolean)
    suspend fun editMessage(messageId: String, chatId: String, newContent: String)
    suspend fun markMessageRead(messageId: String, chatId: String)
    suspend fun createChat(chat: Chat): String
    suspend fun deleteChat(chatId: String)
}
