package com.nexuzy.samvixo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nexuzy.samvixo.domain.model.Chat
import com.nexuzy.samvixo.domain.model.ChatType
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ConversationManager {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun createDirectChat(otherUserId: String): String {
        val currentUserId = auth.currentUser?.uid ?: throw Exception("Not authenticated")
        
        // Check if chat already exists
        val existing = firestore.collection("chats")
            .whereEqualTo("type", ChatType.PERSONAL.name)
            .whereArrayContains("participants", currentUserId)
            .get()
            .await()
        
        val existingChat = existing.documents.find { 
            val participants = it.get("participants") as? List<*>
            participants?.contains(otherUserId) == true
        }

        if (existingChat != null) return existingChat.id

        // Create new chat
        val chatId = UUID.randomUUID().toString()
        val chat = Chat(
            chatId = chatId,
            participants = listOf(currentUserId, otherUserId),
            type = ChatType.PERSONAL,
            lastMessage = "Start of conversation",
            lastMessageTimestamp = System.currentTimeMillis()
        )
        
        firestore.collection("chats").document(chatId).set(chat).await()
        return chatId
    }
}
