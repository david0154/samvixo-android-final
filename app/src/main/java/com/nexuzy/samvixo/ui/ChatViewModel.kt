package com.nexuzy.samvixo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexuzy.samvixo.data.local.AppDatabase
import com.nexuzy.samvixo.data.local.entity.ChatEntity
import com.nexuzy.samvixo.data.repository.FirestoreRepository
import com.nexuzy.samvixo.domain.model.Chat
import com.nexuzy.samvixo.domain.model.ChatType
import com.nexuzy.samvixo.service.ChatSyncService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ChatViewModel — production-ready, Room-first.
 *
 * Data flow:
 *   Firestore → ChatSyncService → Room (chats table) → [chats] StateFlow → UI
 *
 * Unread counts come purely from Room MessageDao — no Firestore reads needed.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository  = FirestoreRepository.getInstance()
    private val db          = AppDatabase.getInstance(application)
    private val syncService = ChatSyncService.getInstance(application)

    // ── Chat list ─────────────────────────────────────────────────────────────

    val chats: StateFlow<List<Chat>> = db.chatDao()
        .getAllChats()
        .map { entities -> entities.map { it.toChat() } }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── Unread counts — Map<chatId, Int> ─────────────────────────────────────
    // Built by joining Room chats + Room messages — zero Firestore reads.
    // Uses flatMapLatest so the map re-emits whenever chats list changes.

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadCounts: StateFlow<Map<String, Int>> = db.chatDao()
        .getAllChats()
        .flatMapLatest { chats ->
            val myUid = repository.getCurrentUserId()
            if (myUid == null || chats.isEmpty()) {
                flowOf(emptyMap())
            } else {
                // Combine unread Flow per chat into a single Map flow
                // Each inner flow emits whenever a message status changes
                val chatIds = chats.map { it.chatId }
                // Aggregate all unread count flows into one map
                // We use a simple approach: observe per-chat unread flows and merge
                kotlinx.coroutines.flow.combine(
                    chatIds.map { chatId ->
                        db.messageDao().getUnreadCountFlow(chatId, myUid)
                            .map { count -> chatId to count }
                    }
                ) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    // ── Init: start Firestore → Room chat sync ────────────────────────────────

    init {
        syncService.startSync()
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            db.chatDao().deleteChatById(chatId)
            repository.deleteChat(chatId)
        }
    }

    fun createOrOpenChat(otherUid: String, onResult: (chatId: String) -> Unit) {
        viewModelScope.launch {
            val myUid = repository.getCurrentUserId() ?: return@launch
            val chatId = repository.getOrCreatePersonalChat(myUid, otherUid)
            onResult(chatId)
        }
    }

    // ── Mapper: ChatEntity → Chat domain model ────────────────────────────────

    private fun ChatEntity.toChat(): Chat = Chat(
        chatId               = chatId,
        participants         = emptyList(),
        lastMessage          = lastMessage,
        lastMessageTimestamp = lastMessageTimestamp,
        type                 = if (isGroup) ChatType.GROUP else ChatType.PERSONAL
    )
}
