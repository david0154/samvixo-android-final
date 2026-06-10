package com.nexuzy.samvixo.domain.repository

import com.nexuzy.samvixo.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getCurrentUser(): User?
    suspend fun getUserById(uid: String): User?
    fun searchUsers(query: String): Flow<List<User>>
    suspend fun updateProfile(uid: String, name: String, bio: String, profileImageUrl: String)
    suspend fun updateDeviceToken(uid: String, token: String)
    suspend fun updateOnlineStatus(uid: String, isOnline: Boolean)
    suspend fun reportUser(reportedUid: String, reportedByUid: String, reason: String)
}
