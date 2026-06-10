package com.nexuzy.samvixo.domain.repository

interface AIRepository {
    suspend fun getAIReply(prompt: String): String
    suspend fun translateText(text: String, targetLanguage: String): String
    // Keeping these if they are intended for future use, but making them optional or ensuring implementation
    suspend fun generateReply(prompt: String, model: String = "devil-ai"): String = ""
    suspend fun summarizeChat(messages: List<String>): String = ""
    suspend fun generateStatusCaption(context: String): String = ""
}
