package com.nexuzy.samvixo.domain.model

import java.util.UUID

data class User(
    val uid: String = "",
    val phoneNumber: String = "",
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    val profileImageUrl: String = "",
    val lastSeen: Long = 0,
    val isOnline: Boolean = false,
    val trustScore: Int = 100,
    val deviceToken: String = ""
)

data class Chat(
    val chatId: String = "",          // Empty default — always set explicitly; UUID.randomUUID() default caused Firestore deserialization bugs
    val participants: List<String> = emptyList(),   // Unified field: both Android & Web now use 'participants'
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val type: ChatType = ChatType.PERSONAL,
    val metadata: ChatMetadata = ChatMetadata(),
    val settings: ChatSettings = ChatSettings()
)

enum class ChatType {
    PERSONAL, GROUP, BROADCAST, CHANNEL, SECRET
}

data class ChatMetadata(
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val admins: List<String> = emptyList(),
    val creatorId: String = ""
)

data class ChatSettings(
    val disappearingMessagesTimer: Long = 0, // 0 = off; value in ms
    val isMuted: Boolean = false,
    val screenshotAlerts: Boolean = true
)

data class Message(
    val messageId: String = UUID.randomUUID().toString(),
    val chatId: String = "",
    val senderId: String = "",
    val content: String = "",
    val mediaUrl: String? = null,
    val mediaMetadata: MediaMetadata? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val isEncrypted: Boolean = true,
    val isViewOnce: Boolean = false,
    val replyToMessageId: String? = null,
    val reactions: Map<String, List<String>> = emptyMap() // emoji -> list of userIds
)

enum class MessageType {
    TEXT, IMAGE, VIDEO, AUDIO, VOICE_NOTE, DOCUMENT, LOCATION, CONTACT, POLL, STICKER, GIF
}

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ
}

data class MediaMetadata(
    val fileName: String = "",
    val fileSize: Long = 0,
    val mimeType: String = "",
    val duration: Long = 0,
    val width: Int = 0,
    val height: Int = 0
)

data class Poll(
    val question: String = "",
    val options: List<PollOption> = emptyList(),
    val allowMultipleAnswers: Boolean = false
)

data class PollOption(
    val optionId: String = UUID.randomUUID().toString(),
    val text: String = "",
    val votes: List<String> = emptyList()
)

data class Status(
    val statusId: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val userName: String = "",
    val profileUrl: String = "",
    val mediaUrl: String? = null,
    val videoUrl: String? = null,
    val text: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val expiryTimestamp: Long = timestamp + (24 * 60 * 60 * 1000),
    val views: List<String> = emptyList(),
    val isOfficial: Boolean = false
)

data class Channel(
    val channelId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val subscriberCount: Int = 0,
    val category: String = "General",
    val isAdminVerified: Boolean = false
)
