package com.nexuzy.samvixo.domain.repository

import com.nexuzy.samvixo.domain.model.Channel
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    fun getAllChannels(): Flow<List<Channel>>
    fun getChannelsByCategory(category: String): Flow<List<Channel>>
    suspend fun subscribeToChannel(channelId: String, userId: String)
    suspend fun unsubscribeFromChannel(channelId: String, userId: String)
    suspend fun createChannel(channel: Channel)
    suspend fun getSubscribedChannels(userId: String): List<Channel>
}
