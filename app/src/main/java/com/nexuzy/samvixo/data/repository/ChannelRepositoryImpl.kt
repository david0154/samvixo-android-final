package com.nexuzy.samvixo.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.nexuzy.samvixo.domain.model.Channel
import com.nexuzy.samvixo.domain.repository.ChannelRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChannelRepositoryImpl : ChannelRepository {

    private val db = FirebaseFirestore.getInstance()

    override fun getAllChannels(): Flow<List<Channel>> = callbackFlow {
        val listener = db.collection("channels")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { it.toObject(Channel::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override fun getChannelsByCategory(category: String): Flow<List<Channel>> = callbackFlow {
        val listener = db.collection("channels")
            .whereEqualTo("category", category)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { it.toObject(Channel::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun subscribeToChannel(channelId: String, userId: String) {
        db.collection("channels").document(channelId)
            .update("subscribers", com.google.firebase.firestore.FieldValue.arrayUnion(userId)).await()
        db.collection("users").document(userId)
            .update("subscribedChannels", com.google.firebase.firestore.FieldValue.arrayUnion(channelId)).await()
    }

    override suspend fun unsubscribeFromChannel(channelId: String, userId: String) {
        db.collection("channels").document(channelId)
            .update("subscribers", com.google.firebase.firestore.FieldValue.arrayRemove(userId)).await()
        db.collection("users").document(userId)
            .update("subscribedChannels", com.google.firebase.firestore.FieldValue.arrayRemove(channelId)).await()
    }

    override suspend fun createChannel(channel: Channel) {
        db.collection("channels").document(channel.channelId).set(channel).await()
    }

    override suspend fun getSubscribedChannels(userId: String): List<Channel> {
        val userDoc = db.collection("users").document(userId).get().await()
        @Suppress("UNCHECKED_CAST")
        val ids = userDoc.get("subscribedChannels") as? List<String> ?: return emptyList()
        return ids.mapNotNull {
            db.collection("channels").document(it).get().await().toObject(Channel::class.java)
        }
    }
}
