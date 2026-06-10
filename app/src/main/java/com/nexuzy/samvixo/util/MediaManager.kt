package com.nexuzy.samvixo.util

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MediaManager {
    private val storage = FirebaseStorage.getInstance()

    suspend fun uploadMedia(chatId: String, uri: Uri): String {
        val fileName = UUID.randomUUID().toString()
        val storageRef = storage.reference.child("media/$chatId/$fileName")
        
        val uploadTask = storageRef.putFile(uri).await()
        return storageRef.downloadUrl.await().toString()
    }
}
