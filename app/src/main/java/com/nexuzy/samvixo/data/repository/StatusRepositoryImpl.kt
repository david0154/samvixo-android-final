package com.nexuzy.samvixo.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nexuzy.samvixo.domain.model.Status
import com.nexuzy.samvixo.domain.repository.StatusRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class StatusRepositoryImpl : StatusRepository {

    private val db = FirebaseFirestore.getInstance()
    private val now get() = System.currentTimeMillis()

    override fun getStatuses(): Flow<List<Status>> = callbackFlow {
        val listener = db.collection("status")
            .whereGreaterThan("expiryTimestamp", now)
            .whereEqualTo("isOfficial", false)
            .orderBy("expiryTimestamp", Query.Direction.DESCENDING)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { it.toObject(Status::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override fun getOfficialStatuses(): Flow<List<Status>> = callbackFlow {
        val listener = db.collection("official_status")
            .whereGreaterThan("expiryTimestamp", now)
            .orderBy("expiryTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { it.toObject(Status::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun postStatus(status: Status) {
        db.collection("status").document(status.statusId).set(status).await()
    }

    override suspend fun deleteStatus(statusId: String) {
        db.collection("status").document(statusId).delete().await()
    }

    override suspend fun markStatusViewed(statusId: String, viewerUid: String) {
        db.collection("status").document(statusId)
            .update("views", com.google.firebase.firestore.FieldValue.arrayUnion(viewerUid)).await()
    }
}
