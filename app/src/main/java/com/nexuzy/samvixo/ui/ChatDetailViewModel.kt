package com.nexuzy.samvixo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexuzy.samvixo.data.local.AppDatabase
import com.nexuzy.samvixo.data.local.entity.MessageEntity
import com.nexuzy.samvixo.data.remote.GiphyApi
import com.nexuzy.samvixo.data.repository.FirestoreRepository
import com.nexuzy.samvixo.domain.model.Message
import com.nexuzy.samvixo.domain.model.MessageStatus
import com.nexuzy.samvixo.domain.model.MessageType
import com.nexuzy.samvixo.service.MessageSyncService
import com.nexuzy.samvixo.util.Config
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

class ChatDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository   = FirestoreRepository.getInstance()
    private val db           = AppDatabase.getInstance(application)
    private val syncService  = MessageSyncService.getInstance(application)

    private val giphyApi: GiphyApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.giphy.com/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GiphyApi::class.java)
    }

    private val _chatId = MutableStateFlow("")
    val chatId: StateFlow<String> = _chatId

    private val _peerUid = MutableStateFlow<String?>(null)
    val peerUid: StateFlow<String?> = _peerUid

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = _chatId
        .flatMapLatest { id ->
            if (id.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
            else db.messageDao().getMessagesForChat(id).map { entities ->
                entities.map { it.toMessage() }
            }
        }
        .stateIn(
            scope         = viewModelScope,
            started       = SharingStarted.WhileSubscribed(5_000),
            initialValue  = emptyList()
        )

    fun setChatId(id: String) {
        if (id == _chatId.value) return
        _chatId.value = id
        
        // Extract peerUid from chatId (format: uid1_uid2)
        val currentUid = repository.getCurrentUserId()
        if (currentUid != null && id.contains("_")) {
            val parts = id.split("_")
            _peerUid.value = parts.firstOrNull { it != currentUid }
        }

        syncService.startSync(id)
        syncService.markChatRead(id)
    }

    fun sendMessage(
        content: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        replyToMessageId: String? = null
    ) {
        val uid    = repository.getCurrentUserId() ?: return
        val chatId = _chatId.value
        if (chatId.isEmpty() || (content.isBlank() && mediaUrl == null)) return

        val message = Message(
            messageId        = UUID.randomUUID().toString(),
            chatId           = chatId,
            senderId         = uid,
            content          = content,
            type             = type,
            mediaUrl         = mediaUrl,
            replyToMessageId = replyToMessageId,
            timestamp        = System.currentTimeMillis(),
            status           = MessageStatus.SENDING
        )

        viewModelScope.launch {
            db.messageDao().insertMessage(message.toEntity(chatId))
            try {
                repository.sendMessage(chatId, message)
                db.messageDao().updateStatus(message.messageId, MessageStatus.SENT.name)
            } catch (e: Exception) {
                db.messageDao().updateStatus(message.messageId, "FAILED")
            }
        }
    }

    fun sendVoiceNote(filePath: String) {
        // Upload logic would go here, for now just a placeholder mapping to sendMessage
        sendMessage(content = "[Voice Note]", type = MessageType.VOICE_NOTE, mediaUrl = filePath)
    }

    fun deleteMessage(messageId: String) {
        val chatId = _chatId.value
        viewModelScope.launch {
            db.messageDao().deleteById(messageId)
            repository.deleteMessage(chatId, messageId)
        }
    }

    private val _gifResults = MutableStateFlow<List<String>>(emptyList())
    val gifResults: StateFlow<List<String>> = _gifResults

    fun searchGifs(query: String) {
        viewModelScope.launch {
            try {
                val response = giphyApi.searchGifs(Config.GIPHY_API_KEY, query)
                _gifResults.value = response.data.map { it.images.fixed_height.url }
            } catch (_: Exception) { }
        }
    }

    fun setTyping(isTyping: Boolean) {
        val chatId = _chatId.value.ifEmpty { return }
        viewModelScope.launch {
            repository.setTyping(chatId, isTyping)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val typingUsers: StateFlow<List<String>> = _chatId
        .flatMapLatest { id ->
            if (id.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.getTypingUsers(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun MessageEntity.toMessage(): Message = Message(
        messageId        = messageId,
        chatId           = chatId,
        senderId         = senderId,
        content          = content,
        timestamp        = timestamp,
        type             = runCatching { MessageType.valueOf(type) }.getOrDefault(MessageType.TEXT),
        status           = runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.SENT),
        isEncrypted      = false,
        mediaUrl         = mediaUrl.ifEmpty { null },
        replyToMessageId = replyToMessageId.ifEmpty { null }
    )

    private fun Message.toEntity(chatId: String): MessageEntity = MessageEntity(
        messageId        = messageId,
        chatId           = chatId,
        senderId         = senderId,
        content          = content,
        timestamp        = timestamp,
        type             = type.name,
        status           = status.name,
        isEncrypted      = false,
        mediaUrl         = mediaUrl ?: "",
        replyToMessageId = replyToMessageId ?: ""
    )
}
