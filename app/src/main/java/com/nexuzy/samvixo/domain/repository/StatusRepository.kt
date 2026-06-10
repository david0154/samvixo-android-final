package com.nexuzy.samvixo.domain.repository

import com.nexuzy.samvixo.domain.model.Status
import kotlinx.coroutines.flow.Flow

interface StatusRepository {
    fun getStatuses(): Flow<List<Status>>
    fun getOfficialStatuses(): Flow<List<Status>>
    suspend fun postStatus(status: Status)
    suspend fun deleteStatus(statusId: String)
    suspend fun markStatusViewed(statusId: String, viewerUid: String)
}
