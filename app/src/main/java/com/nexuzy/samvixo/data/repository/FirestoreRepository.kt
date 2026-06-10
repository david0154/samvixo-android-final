package com.nexuzy.samvixo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.nexuzy.samvixo.domain.model.*
import com.nexuzy.samvixo.util.EncryptionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * FirestoreRepository — Firestore is a RELAY layer, not a storage layer.
 *
 * MESSAGE LIFECYCLE (WhatsApp model):
 *   1. Sender calls [sendMessage] → encrypted message written to Firestore
 *   2. [onNewMessage] Cloud Function → sends FCM push to recipient
 *   3. Recipient’s [MessageSyncService] picks up message → writes DECRYPTED copy to Room
 *   4. Recipient calls [markChatAsRead] → status set to READ in Firestore
 *   5. [onMessageRead] Cloud Function trigger → DELETES message from Firestore
 *   6. Message lives on in Room DB on both devices permanently
 *
 * CALL LIFECYCLE:
 *   - Signalling doc written to [call_signals/{callId}]
 *   - [onCallEnded] Cloud Function deletes it on ended/rejected
 *   - [cleanupOldCallSignals] scheduled hourly as safety net
 *   - Call history saved to Room (CallLogEntity) — never in Firestore long-term
 */
class FirestoreRepository private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: FirestoreRepository? = null
        fun getInstance(): FirestoreRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirestoreRepository().also { INSTANCE = it }
            }
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth      = FirebaseAuth.getInstance()

    fun getCurrentUserId() = auth.currentUser?.uid

    // ────────────────────────────────────────────────────────────────────────
    // Chats
    // ────────────────────────────────────────────────────────────────────────

    fun getChats(): Flow<List<Chat>> {
        val uid = getCurrentUserId() ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return firestore.collection("chats")
            .whereArrayContains("participants", uid)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(Chat::class.java) }
    }

    /**
     * Get an existing personal (1-to-1) chat between [myUid] and [otherUid],
     * or create one if it doesn’t exist.
     *
     * Strategy: canonical chatId = sorted UIDs joined by "_".
     * This guarantees both sides always resolve to the same document without
     * a query — no race condition, no duplicate chats.
     *
     * If the Firestore document doesn’t exist yet it is created atomically
     * via [com.google.firebase.firestore.DocumentReference.set] with merge=false
     * (first write wins; concurrent creates are safe because both would write
     * identical data to the same document ID).
     *
     * @return the chatId (also usable as the Agora channel name)
     */
    suspend fun getOrCreatePersonalChat(myUid: String, otherUid: String): String {
        // Deterministic, collision-free chatId for any two UIDs
        val chatId = listOf(myUid, otherUid).sorted().joinToString("_")

        val docRef = firestore.collection("chats").document(chatId)
        val snap   = docRef.get().await()

        if (!snap.exists()) {
            val myName    = auth.currentUser?.displayName ?: "User"
            docRef.set(
                mapOf(
                    "chatId"               to chatId,
                    "participants"         to listOf(myUid, otherUid),
                    "type"                 to ChatType.PERSONAL.name,
                    "lastMessage"          to "",
                    "lastMessageTimestamp" to System.currentTimeMillis(),
                    "createdAt"            to System.currentTimeMillis(),
                    "memberNames"          to mapOf(myUid to myName)
                )
            ).await()
        }

        return chatId
    }

    /**
     * Delete a chat for the current user.
     *
     * For personal chats: removes current user from the participants array.
     * When participants becomes empty, [cleanupEmptyChats] Cloud Function
     * deletes the document and all sub-collections.
     *
     * For group chats: same — user leaves the group.
     */
    suspend fun deleteChat(chatId: String) {
        val uid = getCurrentUserId() ?: return
        firestore.collection("chats").document(chatId)
            .update(
                "participants",
                com.google.firebase.firestore.FieldValue.arrayRemove(uid)
            ).await()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Messages — Firestore relay only
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Returns raw Firestore message stream used ONLY by [MessageSyncService]
     * to populate Room. UI should read from Room via MessageDao, not this.
     */
    fun getMessagesForSync(chatId: String): Flow<List<Message>> {
        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Message::class.java).map { msg ->
                    if (msg.isEncrypted && msg.content.isNotBlank()) {
                        try { msg.copy(content = EncryptionManager.decrypt(msg.content)) }
                        catch (_: Exception) { msg }
                    } else msg
                }
            }
    }

    /**
     * Send a message:
     *   1. Encrypt content with EncryptionManager (Keystore AES-256-GCM)
     *   2. Write to Firestore relay
     *   3. Update chat’s lastMessage preview in same batch
     */
    suspend fun sendMessage(chatId: String, message: Message) {
        val encryptedContent = if (message.content.isNotBlank()) {
            try { EncryptionManager.encrypt(message.content) }
            catch (_: Exception) { message.content }
        } else message.content

        val encryptedMessage = message.copy(
            content     = encryptedContent,
            isEncrypted = encryptedContent != message.content
        )

        val msgRef = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(message.messageId)

        firestore.runBatch { batch ->
            batch.set(msgRef, encryptedMessage)
            batch.update(
                firestore.collection("chats").document(chatId),
                "lastMessage",          message.content.ifBlank { message.type.name },
                "lastMessageTimestamp", message.timestamp
            )
        }.await()
    }

    /**
     * Mark all messages in a chat as READ.
     * Triggers [onMessageRead] Cloud Function → deletes them from Firestore.
     */
    suspend fun markChatAsRead(chatId: String) {
        val uid  = getCurrentUserId() ?: return
        val snap = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .whereNotEqualTo("senderId", uid)
            .whereEqualTo("status", MessageStatus.DELIVERED.name)
            .limit(50)
            .get().await()

        if (snap.isEmpty) return
        val batch = firestore.batch()
        snap.documents.forEach { doc ->
            batch.update(doc.reference, "status", MessageStatus.READ.name)
        }
        batch.commit().await()
    }

    /** Mark a single message DELIVERED (SENT → DELIVERED only). */
    suspend fun markMessageDelivered(chatId: String, messageId: String, currentStatus: String) {
        if (currentStatus != MessageStatus.SENT.name) return
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .update("status", MessageStatus.DELIVERED.name)
            .await()
    }

    /** Manual delete — “Delete for everyone”. */
    suspend fun deleteMessage(chatId: String, messageId: String) {
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .delete().await()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Call Signalling
    // ────────────────────────────────────────────────────────────────────────

    suspend fun initiateCallSignal(
        callId: String,
        calleeUid: String,
        isVideo: Boolean,
        channelName: String
    ) {
        val uid = getCurrentUserId() ?: return
        firestore.collection("call_signals").document(callId).set(
            mapOf(
                "callId"      to callId,
                "callerUid"   to uid,
                "calleeUid"   to calleeUid,
                "isVideo"     to isVideo,
                "channelName" to channelName,
                "status"      to "ringing",
                "startedAt"   to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun updateCallSignalStatus(callId: String, status: String) {
        firestore.collection("call_signals").document(callId)
            .update("status", status).await()
    }

    fun listenToCallSignal(callId: String): Flow<Map<String, Any>?> {
        return firestore.collection("call_signals").document(callId)
            .snapshots()
            .map { if (it.exists()) it.data else null }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Typing indicators
    // ────────────────────────────────────────────────────────────────────────

    suspend fun setTyping(chatId: String, isTyping: Boolean) {
        val uid = getCurrentUserId() ?: return
        val ref = firestore.collection("typing").document(chatId)
        if (isTyping) {
            ref.set(mapOf(uid to System.currentTimeMillis()),
                com.google.firebase.firestore.SetOptions.merge()).await()
        } else {
            ref.update(uid, com.google.firebase.firestore.FieldValue.delete()).await()
        }
    }

    fun getTypingUsers(chatId: String): Flow<List<String>> {
        val uid            = getCurrentUserId() ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        val staleThreshold = 5_000L
        return firestore.collection("typing").document(chatId)
            .snapshots()
            .map { doc ->
                if (!doc.exists()) return@map emptyList()
                val now = System.currentTimeMillis()
                doc.data?.entries
                    ?.filter { (k, v) -> k != uid && (v as? Long ?: 0L) > (now - staleThreshold) }
                    ?.map { it.key } ?: emptyList()
            }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Chat creation (legacy — prefer getOrCreatePersonalChat for 1-to-1)
    // ────────────────────────────────────────────────────────────────────────

    suspend fun createChat(otherUid: String, otherName: String): String {
        val uid    = getCurrentUserId() ?: return ""
        val myName = auth.currentUser?.displayName ?: "User"
        val chatId = UUID.randomUUID().toString()
        firestore.collection("chats").document(chatId).set(
            mapOf(
                "chatId"               to chatId,
                "participants"         to listOf(uid, otherUid),
                "type"                 to "direct",
                "lastMessage"          to "",
                "lastMessageTimestamp" to System.currentTimeMillis(),
                "createdAt"            to System.currentTimeMillis(),
                "memberNames"          to mapOf(uid to myName, otherUid to otherName)
            )
        ).await()
        return chatId
    }

    suspend fun createGroup(name: String, participants: List<String>) {
        val uid    = getCurrentUserId() ?: return
        val chatId = UUID.randomUUID().toString()
        val chat   = Chat(
            chatId       = chatId,
            participants = participants + uid,
            type         = ChatType.GROUP,
            metadata     = ChatMetadata(name = name, creatorId = uid, admins = listOf(uid))
        )
        firestore.collection("chats").document(chatId).set(chat).await()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Channels
    // ────────────────────────────────────────────────────────────────────────

    fun getChannels(): Flow<List<Channel>> =
        firestore.collection("channels").snapshots()
            .map { it.toObjects(Channel::class.java) }

    suspend fun subscribeToChannel(channelId: String) {
        val uid = getCurrentUserId() ?: return
        firestore.collection("channels").document(channelId)
            .collection("subscribers").document(uid)
            .set(mapOf("timestamp" to System.currentTimeMillis())).await()
    }

    // ────────────────────────────────────────────────────────────────────────
    // User Profiles & Privacy
    // ────────────────────────────────────────────────────────────────────────

    suspend fun saveUserProfile(user: User) {
        firestore.collection("users").document(user.uid).set(user).await()
    }

    fun getUserProfile(uid: String): Flow<User?> =
        firestore.collection("users").document(uid)
            .snapshots().map { it.toObject(User::class.java) }

    suspend fun updatePrivacySettings(settings: Map<String, Any>) {
        val uid = getCurrentUserId() ?: return
        firestore.collection("users").document(uid)
            .update("privacySettings", settings).await()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Blocking
    // ────────────────────────────────────────────────────────────────────────

    suspend fun blockUser(targetUid: String) {
        val uid = getCurrentUserId() ?: return
        firestore.collection("users").document(uid)
            .collection("blocked_users").document(targetUid)
            .set(mapOf("timestamp" to System.currentTimeMillis())).await()
    }

    suspend fun unblockUser(targetUid: String) {
        val uid = getCurrentUserId() ?: return
        firestore.collection("users").document(uid)
            .collection("blocked_users").document(targetUid)
            .delete().await()
    }

    fun isUserBlocked(targetUid: String): Flow<Boolean> {
        val uid = getCurrentUserId() ?: return kotlinx.coroutines.flow.flowOf(false)
        return firestore.collection("users").document(uid)
            .collection("blocked_users").document(targetUid)
            .snapshots().map { it.exists() }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Trust & Safety
    // ────────────────────────────────────────────────────────────────────────

    suspend fun reportUser(targetUid: String, reason: String) {
        val reporterUid = getCurrentUserId() ?: return
        firestore.collection("reports").add(
            mapOf(
                "reporterUid" to reporterUid,
                "targetUid"   to targetUid,
                "reason"      to reason,
                "timestamp"   to System.currentTimeMillis()
            )
        ).await()
        firestore.runTransaction { transaction ->
            val ref   = firestore.collection("users").document(targetUid)
            val snap  = transaction.get(ref)
            val score = snap.getLong("trustScore") ?: 100
            transaction.update(ref, "trustScore", (score - 5).coerceAtLeast(0))
        }.await()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Status
    // ────────────────────────────────────────────────────────────────────────

    fun getStatuses(): Flow<List<Status>> {
        val now = System.currentTimeMillis()
        return firestore.collection("status")
            .whereGreaterThan("expiryTimestamp", now)
            .snapshots().map { it.toObjects(Status::class.java) }
    }

    suspend fun postStatus(status: Status) {
        firestore.collection("status").document(status.statusId).set(status).await()
    }
}
