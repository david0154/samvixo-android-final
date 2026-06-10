package com.nexuzy.samvixo.utils

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await

object AutoCleanupManager {

    private val db      = Firebase.firestore
    private val storage = Firebase.storage

    suspend fun deleteExpiredStatuses(uid: String) {
        try {
            val now = System.currentTimeMillis()
            val expired = db.collection("statuses")
                .whereEqualTo("uid", uid)
                .whereLessThan("expiryTs", now)
                .get().await()
            for (doc in expired.documents) {
                val mediaUrl = doc.getString("mediaUrl")
                if (!mediaUrl.isNullOrEmpty()) {
                    try { storage.getReferenceFromUrl(mediaUrl).delete().await() } catch (_: Exception) {}
                }
                doc.reference.delete().await()
            }
        } catch (_: Exception) {}
    }

    suspend fun deleteAllExpiredStatuses() {
        try {
            val now = System.currentTimeMillis()
            val expired = db.collection("statuses")
                .whereLessThan("expiryTs", now)
                .get().await()
            for (doc in expired.documents) {
                val mediaUrl = doc.getString("mediaUrl")
                if (!mediaUrl.isNullOrEmpty()) {
                    try { storage.getReferenceFromUrl(mediaUrl).delete().await() } catch (_: Exception) {}
                }
                doc.reference.delete().await()
            }
        } catch (_: Exception) {}
    }

    suspend fun deleteDeliveredMessage(chatId: String, messageId: String, mediaUrl: String?) {
        try {
            if (!mediaUrl.isNullOrEmpty()) {
                try { storage.getReferenceFromUrl(mediaUrl).delete().await() } catch (_: Exception) {}
            }
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .delete().await()
        } catch (_: Exception) {}
    }

    suspend fun deleteDeliveredMessagesInChat(chatId: String) {
        try {
            val delivered = db.collection("chats").document(chatId)
                .collection("messages")
                .whereIn("status", listOf("delivered", "read"))
                .get().await()
            for (doc in delivered.documents) {
                val mediaUrl = doc.getString("mediaUrl")
                if (!mediaUrl.isNullOrEmpty()) {
                    try { storage.getReferenceFromUrl(mediaUrl).delete().await() } catch (_: Exception) {}
                }
                doc.reference.delete().await()
            }
        } catch (_: Exception) {}
    }
}
