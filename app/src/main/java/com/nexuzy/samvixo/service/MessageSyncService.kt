package com.nexuzy.samvixo.service

import android.content.Context
import com.nexuzy.samvixo.data.local.AppDatabase
import com.nexuzy.samvixo.data.local.entity.MessageEntity
import com.nexuzy.samvixo.data.repository.FirestoreRepository
import com.nexuzy.samvixo.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * MessageSyncService — bridges Firestore (relay) → Room (permanent local store).
 *
 * FLOW per chat:
 *   1. Subscribe to Firestore messages via [FirestoreRepository.getMessagesForSync]
 *   2. For each incoming message:
 *      a. Decrypt content (already done in getMessagesForSync)
 *      b. Write DECRYPTED copy to Room (MessageEntity)
 *      c. If message is from someone else AND status = SENT → mark DELIVERED
 *         (this does NOT trigger onMessageRead — only READ status triggers deletion)
 *   3. Firestore stream naturally ends when messages are deleted from Firestore;
 *      Room copy is unaffected and continues showing in UI
 *
 * USAGE — call [startSync] from your ChatViewModel or MainActivity once per chat:
 *   MessageSyncService.getInstance(context).startSync(chatId)
 */
class MessageSyncService private constructor(private val context: Context) {

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = FirestoreRepository.getInstance()
    private val db         = AppDatabase.getInstance(context)
    private val myUid      get() = repository.getCurrentUserId() ?: ""

    companion object {
        @Volatile
        private var INSTANCE: MessageSyncService? = null
        fun getInstance(context: Context): MessageSyncService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MessageSyncService(context.applicationContext).also { INSTANCE = it }
            }
    }

    /**
     * Start syncing messages for [chatId] from Firestore → Room.
     * Safe to call multiple times for same chatId — each call creates a new collector.
     * For production, track active jobs and cancel duplicates if needed.
     */
    fun startSync(chatId: String) {
        repository.getMessagesForSync(chatId)
            .onEach { messages ->
                val messageDao = db.messageDao()
                messages.forEach { msg ->
                    // Write to Room (decrypted content, Room is the permanent store)
                    val entity = MessageEntity(
                        messageId          = msg.messageId,
                        chatId             = chatId,
                        senderId           = msg.senderId,
                        content            = msg.content,   // already decrypted
                        timestamp          = msg.timestamp,
                        type               = msg.type.name,
                        status             = msg.status.name,
                        isEncrypted        = false,          // stored as plaintext in Room
                        mediaUrl           = msg.mediaUrl ?: "",
                        replyToMessageId   = msg.replyToMessageId ?: ""
                    )
                    messageDao.insertMessage(entity)

                    // Mark as DELIVERED if we're the recipient and it's still SENT
                    if (msg.senderId != myUid && msg.status == MessageStatus.SENT) {
                        scope.launch {
                            repository.markMessageDelivered(chatId, msg.messageId, msg.status.name)
                            messageDao.updateStatus(msg.messageId, MessageStatus.DELIVERED.name)
                        }
                    }
                }

                // Prune local cache to last 500 messages per chat
                messageDao.pruneOldMessages(chatId)
            }
            .catch { e ->
                // Log but don't crash — Room still has all previous messages
                android.util.Log.w("MessageSyncService", "Firestore sync error for $chatId: ${e.message}")
            }
            .launchIn(scope)
    }

    /**
     * Mark all messages in [chatId] as READ in both Room and Firestore.
     * Firestore update triggers [onMessageRead] Cloud Function → deletes from Firestore.
     * Room copy is kept permanently.
     */
    fun markChatRead(chatId: String) {
        scope.launch {
            db.messageDao().markAllReadInChat(chatId, myUid)
            repository.markChatAsRead(chatId)
        }
    }
}
