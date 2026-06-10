package com.nexuzy.samvixo.data.local.dao

import androidx.room.*
import com.nexuzy.samvixo.data.local.entity.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getCallLogsForChat(chatId: String): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE peerId = :peerId ORDER BY timestamp DESC LIMIT 50")
    fun getCallsWithPeer(peerId: String): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)

    @Query("DELETE FROM call_logs WHERE callId = :callId")
    suspend fun deleteCallLog(callId: String)

    @Query("DELETE FROM call_logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldLogs(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM call_logs WHERE status = 'MISSED' AND isIncoming = 1")
    fun getMissedCallCount(): Flow<Int>
}
