package com.nexuzy.samvixo.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class PresenceManager {
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun updatePresence() {
        val uid = auth.currentUser?.uid ?: return
        val userStatusDatabaseRef = database.getReference("/status/$uid")

        val isOfflineForDatabase = mapOf(
            "state" to "offline",
            "last_changed" to ServerValue.TIMESTAMP
        )

        val isOnlineForDatabase = mapOf(
            "state" to "online",
            "last_changed" to ServerValue.TIMESTAMP
        )

        database.getReference(".info/connected").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    userStatusDatabaseRef.onDisconnect().setValue(isOfflineForDatabase)
                    userStatusDatabaseRef.setValue(isOnlineForDatabase)
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }
}
