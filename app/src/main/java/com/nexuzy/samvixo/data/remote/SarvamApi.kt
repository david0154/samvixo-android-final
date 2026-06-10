package com.nexuzy.samvixo.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class TranslationRequest(
    val input: String,
    val source_language: String,
    val target_language: String,
    val speaker_gender: String = "Male"
)

data class TranslationResponse(
    val translated_text: String
)

data class SummaryRequest(
    val text: String,
    val prompt: String = "Summarize the following chat conversation succinctly."
)

data class SummaryResponse(
    val summary: String
)

interface SarvamApi {
    @POST("v1/translate")
    suspend fun translate(
        @Header("api-key") apiKey: String,
        @Body request: TranslationRequest
    ): TranslationResponse

    @POST("v1/summarize")
    suspend fun summarize(
        @Header("api-key") apiKey: String,
        @Body request: SummaryRequest
    ): SummaryResponse
}
