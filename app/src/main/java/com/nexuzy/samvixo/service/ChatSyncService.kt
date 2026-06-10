package com.nexuzy.samvixo.service

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nexuzy.samvixo.data.local.AppDatabase
import com.nexuzy.samvixo.data.local.entity.ChatEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ChatSyncService — bridges Firestore chats collection → Room chats table.
 *
 * Mirrors [MessageSyncService] but for the chat list.
 *
 * FLOW:
 *   1. Subscribe to Firestore: chats where 'participants' contains currentUserId
 *   2. For each Firestore snapshot:
 *      a. Upsert all chats into Room (ChatEntity)
 *      b. Delete locally removed chats (chats the user left)
 *   3. Room ChatDao then drives the UI via ChatViewModel.chats StateFlow
 *
 * USAGE:
 *   ChatSyncService.getInstance(context).startSync()
 *   Call once from [ChatViewModel] init block — idempotent.
 */
class ChatSyncService private constructor(private val context: Context) {

    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db     = AppDatabase.getInstance(context)
    private val auth   = FirebaseAuth.getInstance()
    private val fs     = FirebaseFirestore.getInstance()

    companion object {
        @Volatile
        private var INSTANCE: ChatSyncService? = null

        fun getInstance(context: Context): ChatSyncService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatSyncService(context.applicationContext).also { INSTANCE = it }
            }
    }

    private var syncStarted = false

    /**
     * Start syncing the current user's chat list from Firestore → Room.
     * Idempotent — calling multiple times does nothing after first call.
     */
    fun startSync() {
        if (syncStarted) return
        syncStarted = true

        val uid = auth.currentUser?.uid ?: run {
            Log.w("ChatSyncService", "startSync called before user is authenticated")
            return
        }

        chatsFlow(uid)
            .onEach { firestoreChats ->
                val dao = db.chatDao()

                // Upsert all chats from Firestore into Room
                dao.insertChats(firestoreChats)

                // Remove chats from Room that no longer exist in Firestore
                // (e.g. user was removed from group, or chat was deleted)
                val firestoreIds = firestoreChats.map { it.chatId }.toSet()
                val localChats   = dao.getAllChatsSnapshot()
                val removed      = localChats.filter { it.chatId !in firestoreIds }
                removed.forEach { dao.deleteChat(it) }
            }
            .catch { e ->
                Log.w("ChatSyncService", "Firestore chat sync error: ${e.message}")
                // Room still has all previous chats — UI unaffected
            }
            .launchIn(scope)
    }

    /**
     * Firestore → Flow<List<ChatEntity>>.
     * Listens to chats where 'participants' array contains current user's UID.
     * Emits on every Firestore change (new message, chat added/removed).
     */
    private fun chatsFlow(uid: String) = callbackFlow<List<ChatEntity>> {
        val listener = fs.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    runCatching {
                        val chatId               = doc.id
                        val lastMessage          = doc.getString("lastMessage")        ?: ""
                        val lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L
                        val type                 = doc.getString("type")               ?: "PERSONAL"
                        val isGroup              = type == "GROUP" || type == "BROADCAST" || type == "CHANNEL"
                        val name                 = (doc.get("metadata") as? Map<*, *>)?.get("name") as? String
                        val imageUrl             = (doc.get("metadata") as? Map<*, *>)?.get("imageUrl") as? String

                        ChatEntity(
                            chatId               = chatId,
                            lastMessage          = lastMessage,
                            lastMessageTimestamp = lastMessageTimestamp,
                            isGroup              = isGroup,
                            groupName            = if (isGroup) name else null,
                            groupImageUrl        = if (isGroup) imageUrl else null
                        )
                    }.getOrNull()
                } ?: emptyList()
                trySend(chats)
            }
        awaitClose { listener.remove() }
    }
}
