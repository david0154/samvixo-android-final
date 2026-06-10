package com.nexuzy.samvixo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nexuzy.samvixo.domain.model.User
import com.nexuzy.samvixo.domain.repository.UserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepositoryImpl : UserRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection("users").document(uid).get().await().toObject(User::class.java)
    }

    override suspend fun getUserById(uid: String): User? {
        return db.collection("users").document(uid).get().await().toObject(User::class.java)
    }

    override fun searchUsers(query: String): Flow<List<User>> = callbackFlow {
        val listener = db.collection("users")
            .whereGreaterThanOrEqualTo("username", query)
            .whereLessThanOrEqualTo("username", query + "\uf8ff")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val users = snapshot?.documents?.mapNotNull { it.toObject(User::class.java) } ?: emptyList()
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateProfile(uid: String, name: String, bio: String, profileImageUrl: String) {
        db.collection("users").document(uid).update(
            mapOf("name" to name, "bio" to bio, "profileImageUrl" to profileImageUrl)
        ).await()
    }

    override suspend fun updateDeviceToken(uid: String, token: String) {
        db.collection("users").document(uid).update("deviceToken", token).await()
    }

    override suspend fun updateOnlineStatus(uid: String, isOnline: Boolean) {
        db.collection("users").document(uid).update(
            mapOf("isOnline" to isOnline, "lastSeen" to System.currentTimeMillis())
        ).await()
    }

    override suspend fun reportUser(reportedUid: String, reportedByUid: String, reason: String) {
        val reportData = mapOf(
            "reportedUid" to reportedUid,
            "reportedByUid" to reportedByUid,
            "reason" to reason,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("reports").add(reportData).await()
        db.collection("users").document(reportedUid)
            .update("reportCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
    }
}
