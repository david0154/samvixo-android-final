package com.nexuzy.samvixo.data.local.dao

import androidx.room.*
import com.nexuzy.samvixo.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ── Reactive (Flow) — for UI layers ───────────────────────────────────────

    /** Live-updating list of all chats, sorted newest-first. Used by ChatViewModel. */
    @Query("SELECT * FROM chats ORDER BY lastMessageTimestamp DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    // ── Suspend (one-shot) — for sync services ────────────────────────────────

    /** One-shot snapshot — used by ChatSyncService to diff local vs Firestore chats. */
    @Query("SELECT * FROM chats ORDER BY lastMessageTimestamp DESC")
    suspend fun getAllChatsSnapshot(): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Insert or replace a single chat (upsert). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    /** Batch upsert — used by ChatSyncService. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    /** Update only the lastMessage preview and timestamp (called after sendMessage). */
    @Query("UPDATE chats SET lastMessage = :lastMessage, lastMessageTimestamp = :timestamp WHERE chatId = :chatId")
    suspend fun updateLastMessage(chatId: String, lastMessage: String, timestamp: Long)

    // ── Delete ────────────────────────────────────────────────────────────────

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChatById(chatId: String)
}
