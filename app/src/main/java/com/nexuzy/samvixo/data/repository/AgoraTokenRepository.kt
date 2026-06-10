package com.nexuzy.samvixo.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class AgoraTokenRepository {

    private val functions = FirebaseFunctions.getInstance()

    suspend fun generateToken(channelName: String, uid: Int = 0): String {
        val data = hashMapOf(
            "channelName" to channelName,
            "uid" to uid
        )
        val result = functions
            .getHttpsCallable("generateAgoraToken")
            .call(data)
            .await()
        
        @Suppress("UNCHECKED_CAST")
        val resultMap = result.getData() as Map<String, Any>
        return resultMap["token"] as String
    }
}
