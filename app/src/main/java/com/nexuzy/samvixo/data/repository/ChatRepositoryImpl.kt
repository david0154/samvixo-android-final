package com.nexuzy.samvixo.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nexuzy.samvixo.domain.model.Chat
import com.nexuzy.samvixo.domain.model.Message
import com.nexuzy.samvixo.domain.model.MessageStatus
import com.nexuzy.samvixo.domain.repository.ChatRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepositoryImpl : ChatRepository {

    private val db = FirebaseFirestore.getInstance()

    override fun getChats(userId: String): Flow<List<Chat>> = callbackFlow {
        val listener = db.collection("chats")
            .whereArrayContains("participants", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val chats = snapshot?.documents?.mapNotNull { it.toObject(Chat::class.java) } ?: emptyList()
                trySend(chats)
            }
        awaitClose { listener.remove() }
    }

    override fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val messages = snapshot?.documents?.mapNotNull { it.toObject(Message::class.java) } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun sendMessage(message: Message) {
        val chatRef = db.collection("chats").document(message.chatId)
        chatRef.collection("messages").document(message.messageId).set(message).await()
        chatRef.update(
            mapOf(
                "lastMessage" to message.content,
                "lastMessageTimestamp" to message.timestamp
            )
        ).await()
    }

    override suspend fun deleteMessage(messageId: String, chatId: String, deleteForEveryone: Boolean) {
        if (deleteForEveryone) {
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId).delete().await()
        } else {
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("deletedFor", com.google.firebase.firestore.FieldValue.arrayUnion(
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                )).await()
        }
    }

    override suspend fun editMessage(messageId: String, chatId: String, newContent: String) {
        db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .update(mapOf("content" to newContent, "isEdited" to true)).await()
    }

    override suspend fun markMessageRead(messageId: String, chatId: String) {
        db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .update("status", MessageStatus.READ.name).await()
    }

    override suspend fun createChat(chat: Chat): String {
        val ref = db.collection("chats").document(chat.chatId)
        ref.set(chat).await()
        return chat.chatId
    }

    override suspend fun deleteChat(chatId: String) {
        db.collection("chats").document(chatId).delete().await()
    }
}
