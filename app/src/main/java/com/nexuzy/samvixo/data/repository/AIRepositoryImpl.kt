package com.nexuzy.samvixo.data.repository

import com.nexuzy.samvixo.data.remote.*
import com.nexuzy.samvixo.domain.repository.AIRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

class AIRepositoryImpl @Inject constructor(
    private val ollamaApi: OllamaApi
) : AIRepository {

    private val sarvamApiKey = "MOCK_KEY" 

    private val sarvamApi: SarvamApi by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.sarvam.ai/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SarvamApi::class.java)
    }

    override suspend fun getAIReply(prompt: String): String {
        return try {
            val response = ollamaApi.generate(
                OllamaRequest(
                    model = "devil-ai",
                    prompt = prompt,
                    stream = false
                )
            )
            response.response
        } catch (e: Exception) {
            "Error connecting to Ollama: ${e.message}"
        }
    }

    override suspend fun translateText(text: String, targetLanguage: String): String {
        return try {
            val response = sarvamApi.translate(
                apiKey = sarvamApiKey,
                request = TranslationRequest(
                    input = text,
                    source_language = "en-IN",
                    target_language = targetLanguage
                )
            )
            response.translated_text
        } catch (e: Exception) {
            "Translation Error: ${e.message}"
        }
    }
}
