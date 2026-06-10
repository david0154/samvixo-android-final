package com.nexuzy.samvixo.domain.repository

import com.nexuzy.samvixo.domain.model.User

interface AuthRepository {
    fun getCurrentUser(): User?
    suspend fun signInWithPhone(phoneNumber: String)
    suspend fun signOut()
}
