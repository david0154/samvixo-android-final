package com.nexuzy.samvixo.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

// ── Non-streaming request/response ────────────────────────────────────────────
data class OllamaRequest(
    val model   : String,
    val prompt  : String,
    val stream  : Boolean = false,
    val options : OllamaOptions? = null
)

data class OllamaOptions(
    val temperature : Float = 0.7f,
    val top_k       : Int   = 40,
    val top_p       : Float = 0.9f,
    val num_predict : Int   = 512    // max tokens to generate
)

data class OllamaResponse(
    val model    : String  = "",
    val response : String  = "",
    val done     : Boolean = false,
    val context  : List<Int>? = null  // conversation context for multi-turn
)

// ── Streaming request/response ─────────────────────────────────────────────────
/**
 * For streaming, use stream = true in [OllamaRequest].
 * Parse each newline-delimited JSON object from the response body into [OllamaStreamChunk].
 */
data class OllamaStreamChunk(
    val response : String  = "",
    val done     : Boolean = false
)

// ── Multi-turn chat (Ollama /api/chat endpoint) ────────────────────────────────
data class OlamaChatRequest(
    val model    : String,
    val messages : List<OllamaMessage>,
    val stream   : Boolean = false,
    val options  : OllamaOptions? = null
)

data class OllamaMessage(
    val role    : String,   // "system" | "user" | "assistant"
    val content : String
)

data class OlamaChatResponse(
    val model   : String  = "",
    val message : OllamaMessage = OllamaMessage("", ""),
    val done    : Boolean = false
)

// ── Ollama model list ─────────────────────────────────────────────────────────
data class OllamaModelListResponse(
    val models : List<OllamaModelInfo> = emptyList()
)

data class OllamaModelInfo(
    val name       : String = "",
    val size       : Long   = 0L,
    val modified_at: String = ""
)

// ── Retrofit interface ────────────────────────────────────────────────────────
interface OllamaApi {

    /**
     * Single-turn text generation (stream = false).
     * Returns the complete response in one call.
     */
    @POST("api/generate")
    suspend fun generate(@Body request: OllamaRequest): OllamaResponse

    /**
     * Streaming text generation (stream = true).
     * Returns raw [ResponseBody] — caller must read line by line
     * and parse each line as [OllamaStreamChunk] JSON.
     *
     * Example:
     * ```
     * val body = ollamaApi.generateStream(req).body() ?: return
     * body.source().use { source ->
     *     while (!source.exhausted()) {
     *         val line = source.readUtf8Line() ?: break
     *         val chunk = Gson().fromJson(line, OllamaStreamChunk::class.java)
     *         emit(chunk.response)
     *         if (chunk.done) break
     *     }
     * }
     * ```
     */
    @Streaming
    @POST("api/generate")
    suspend fun generateStream(@Body request: OllamaRequest): Response<ResponseBody>

    /**
     * Multi-turn chat — maintains conversation history via messages list.
     * Use [OlamaChatRequest] with role = "system"/"user"/"assistant".
     */
    @POST("api/chat")
    suspend fun chat(@Body request: OlamaChatRequest): OlamaChatResponse

    /**
     * Streaming multi-turn chat.
     */
    @Streaming
    @POST("api/chat")
    suspend fun chatStream(@Body request: OlamaChatRequest): Response<ResponseBody>

    /**
     * List all locally available models on the Ollama server.
     * Useful for letting the user pick a model in AI settings.
     */
    @retrofit2.http.GET("api/tags")
    suspend fun listModels(): OllamaModelListResponse
}
