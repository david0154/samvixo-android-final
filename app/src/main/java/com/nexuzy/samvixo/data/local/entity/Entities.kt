package com.nexuzy.samvixo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexuzy.samvixo.domain.model.MessageStatus

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val phoneNumber: String = "",
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    val profileImageUrl: String = "",
    val lastSeen: Long = 0L,
    val isOnline: Boolean = false,
    val trustScore: Int = 100
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val chatId: String,
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupImageUrl: String? = null
)

/**
 * MessageEntity — local Room cache for all messages.
 *
 * DESIGN (WhatsApp relay model):
 *   - Messages arrive from Firestore via [MessageSyncService]
 *   - Written to Room immediately (persistent on device even after Firestore delete)
 *   - [deleteAfter] is informational only — actual Firestore deletion is done
 *     server-side by the [onMessageRead] Cloud Function trigger
 *   - [isDeletedFromServer] marks when Firestore copy is confirmed gone
 *
 * This means:
 *   - Messages ALWAYS show in UI (from Room), even after Firestore deletes them
 *   - Firestore is only a relay/transport layer — not a storage layer
 *   - On new device install: messages come from Google Drive backup (BackupManager)
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["status"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val chatId: String = "",
    val senderId: String = "",
    val content: String = "",           // Always stored DECRYPTED in Room
    val timestamp: Long = 0L,
    val type: String = "TEXT",
    val status: String = MessageStatus.SENT.name,
    val isEncrypted: Boolean = false,   // true = content was encrypted in Firestore
    val deleteAfter: Long = 0L,         // Unix ms when Firestore copy should be deleted
    val isDeletedFromServer: Boolean = false,  // true = no longer in Firestore
    val mediaUrl: String = "",
    val replyToMessageId: String = ""
)

/**
 * CallLogEntity — local call history.
 * Call data is NEVER persisted long-term in Firestore.
 * Firestore stores call signalling only (ringing/answer/reject/ended),
 * and [onCallEnded] Cloud Function deletes the signalling document immediately.
 */
@Entity(
    tableName = "call_logs",
    indices = [Index(value = ["chatId"]), Index(value = ["timestamp"])]
)
data class CallLogEntity(
    @PrimaryKey val callId: String,
    val chatId: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val peerAvatarUrl: String = "",
    val isIncoming: Boolean = false,
    val isVideo: Boolean = false,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val durationSeconds: Int = 0,
    val status: String = "COMPLETED",   // COMPLETED | MISSED | REJECTED | NO_ANSWER
    val timestamp: Long = System.currentTimeMillis()
)
