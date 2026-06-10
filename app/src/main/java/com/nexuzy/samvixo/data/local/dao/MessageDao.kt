package com.nexuzy.samvixo.data.local.dao

import androidx.room.*
import com.nexuzy.samvixo.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // ── Read (Flow — reactive) ─────────────────────────────────────────────────

    /** Live-updating message list for a chat, oldest-first. Used by ChatDetailViewModel. */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND status != 'READ' AND senderId != :myUid")
    fun getUnreadCountFlow(chatId: String, myUid: String): Flow<Int>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    // ── Read (suspend — one-shot) ──────────────────────────────────────────────

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    /** One-shot unread count — used by ChatViewModel.unreadCounts map builder. */
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND status != 'READ' AND senderId != :myUid")
    suspend fun getUnreadCount(chatId: String, myUid: String): Int

    /** Latest message in a chat — used to refresh chat list preview after send. */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(chatId: String): MessageEntity?

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Insert or replace. Content MUST be decrypted before calling. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Query("UPDATE messages SET status = 'READ' WHERE chatId = :chatId AND senderId != :myUid AND status != 'READ'")
    suspend fun markAllReadInChat(chatId: String, myUid: String)

    @Query("UPDATE messages SET isDeletedFromServer = 1 WHERE messageId = :messageId")
    suspend fun markDeletedFromServer(messageId: String)

    // ── Delete ────────────────────────────────────────────────────────────────

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteById(messageId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesForChat(chatId: String)

    /**
     * Prune old messages — keep only last 500 per chat to save device storage.
     * Called by MessageSyncService after every Firestore batch write.
     */
    @Query("""
        DELETE FROM messages WHERE messageId IN (
            SELECT messageId FROM messages
            WHERE chatId = :chatId
            ORDER BY timestamp DESC
            LIMIT -1 OFFSET 500
        )
    """)
    suspend fun pruneOldMessages(chatId: String)
}
