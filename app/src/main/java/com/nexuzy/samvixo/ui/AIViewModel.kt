package com.nexuzy.samvixo.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nexuzy.samvixo.data.remote.*
import com.nexuzy.samvixo.util.Config
import com.nexuzy.samvixo.util.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class AIMessage(val content: String, val role: String)

data class AIUiState(
    val messages: List<AIMessage> = emptyList(),
    val isLoading: Boolean = false,
    val activeEngine: String = ""
)

class AIViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()

    // Kept for compatibility — no longer user-editable in UI
    private val _ollamaUrl = MutableStateFlow(Config.DEVIL_AI_BASE_URL)
    val ollamaUrl: StateFlow<String> = _ollamaUrl.asStateFlow()

    // Generous timeout: 120s read for slow model inference
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val sarvamRetrofit: SarvamApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.sarvam.ai/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SarvamApi::class.java)
    }

    // ── On-device chat history ─────────────────────────────────────────────
    private var appContext: Context? = null

    fun initContext(ctx: Context) {
        appContext = ctx.applicationContext
        loadLocalHistory()
    }

    private fun loadLocalHistory() {
        val ctx = appContext ?: return
        val json = ctx.getSharedPreferences("ai_chat_history", Context.MODE_PRIVATE)
            .getString("history_json", "[]") ?: "[]"
        try {
            val arr = JSONArray(json)
            val loaded = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AIMessage(content = o.getString("content"), role = o.getString("role"))
            }
            _uiState.value = _uiState.value.copy(messages = loaded)
        } catch (_: Exception) {}
    }

    private fun saveLocalHistory(messages: List<AIMessage>) {
        val ctx = appContext ?: return
        val arr = JSONArray()
        messages.takeLast(200).forEach { m ->
            arr.put(JSONObject().apply {
                put("content", m.content)
                put("role",    m.role)
            })
        }
        ctx.getSharedPreferences("ai_chat_history", Context.MODE_PRIVATE)
            .edit().putString("history_json", arr.toString()).apply()
    }

    fun clearMessages() {
        appContext?.getSharedPreferences("ai_chat_history", Context.MODE_PRIVATE)
            ?.edit()?.remove("history_json")?.apply()
        _uiState.value = AIUiState()
    }

    // ── RSS context ────────────────────────────────────────────────────────
    private suspend fun fetchRssContext(): String {
        return try {
            val results = RssSources.feeds.map { feed ->
                viewModelScope.async(Dispatchers.IO) {
                    val headlines = RssParser.fetch(feed.url, maxPerFeed = 3)
                    if (headlines.isEmpty()) null
                    else "${feed.flag} ${feed.region} NEWS:\n" +
                        headlines.mapIndexed { i, t -> "  ${i + 1}. $t" }.joinToString("\n")
                }
            }.mapNotNull { it.await() }
            if (results.isEmpty()) "(live news unavailable)"
            else results.joinToString("\n\n")
        } catch (_: Exception) { "(live news unavailable)" }
    }

    // ── App/user context ───────────────────────────────────────────────────
    private suspend fun fetchAppContext(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: return "(not logged in)"
        return try {
            val doc   = Firebase.firestore.collection("users").document(user.uid).get().await()
            val name  = doc.getString("displayName") ?: "Unknown"
            val phone = doc.getString("phoneNumber") ?: user.phoneNumber ?: ""
            val about = doc.getString("about") ?: ""
            "APP USER: name=$name, phone=$phone, about=$about"
        } catch (_: Exception) { "(app context unavailable)" }
    }

    // ── Vault context ──────────────────────────────────────────────────────
    private fun fetchVaultContext(): String {
        val ctx = appContext ?: return ""
        val entries: List<VaultEntry> = loadVault(ctx)
        if (entries.isEmpty()) return ""
        val lines = entries.take(20).joinToString(separator = "\n") { e: VaultEntry ->
            "- ${e.question}: ${e.answer.take(200)}"
        }
        return "\n\nUSER'S KNOWLEDGE VAULT (personal facts they saved):\n$lines"
    }

    // ── System prompt ──────────────────────────────────────────────────────
    private fun buildSystemPrompt(
        mode: String, news: String, appCtx: String, vaultCtx: String
    ): String {
        val now = SimpleDateFormat("EEEE, dd MMM yyyy, hh:mm a z", Locale.ENGLISH)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date())
        val base = """
            You are Devil AI — the smart, bold AI assistant inside Samvixo, a secure Indian messaging app.
            Current date & time (IST): $now
            $appCtx
            LIVE NEWS (via RSS):
            $news
            $vaultCtx
        """.trimIndent()
        return when (mode) {
            "Companion" -> "$base\nYou are a warm, empathetic companion. Be friendly and supportive."
            else        -> "$base\nBe bold, witty, direct, and always helpful as Devil AI."
        }
    }

    // ── ENGINE 1: Devil AI self-hosted (/api/generate) ─────────────────────
    //
    // Endpoint: https://aiapi.devilpvt.in/api/generate
    // Format  : Ollama /api/generate  (prompt string, stream=false)
    //
    // We build the full conversation as a single prompt string:
    //   <system>\n\nUser: ...\nAssistant: ...\nUser: [new]
    //
    private suspend fun callDevilAI(systemPrompt: String, userText: String): String? {
        val baseUrl = Config.DEVIL_AI_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                // Build conversation string from recent history
                val history = _uiState.value.messages.takeLast(10)
                val promptBuilder = StringBuilder()
                promptBuilder.append("<system>\n").append(systemPrompt).append("\n</system>\n\n")
                history.forEach { m ->
                    when (m.role) {
                        "user"      -> promptBuilder.append("User: ").append(m.content).append("\n")
                        "assistant" -> promptBuilder.append("Assistant: ").append(m.content).append("\n")
                    }
                }
                promptBuilder.append("User: ").append(userText).append("\nAssistant:")

                val requestBody = JSONObject().apply {
                    put("model",  Config.DEVIL_AI_MODEL)
                    put("prompt", promptBuilder.toString())
                    put("stream", false)
                    put("options", JSONObject().apply {
                        put("temperature", 0.7)
                        put("num_predict", 512)
                    })
                }.toString()

                val request = Request.Builder()
                    .url("$baseUrl/api/generate")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val responseStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(responseStr)

                // Ollama /api/generate returns {"response": "...", "done": true}
                val text = json.optString("response", "").trim()
                if (text.isBlank()) null else text
            } catch (_: Exception) { null }  // null → triggers Sarvam fallback
        }
    }

    // ── ENGINE 2: Sarvam AI (cloud fallback) ──────────────────────────────
    private suspend fun callSarvamChat(systemPrompt: String, userText: String): String {
        val key = Config.SARVAM_API_KEY
        if (key.isBlank()) {
            return "⚠️ Devil AI is temporarily unavailable.\n" +
                   "Cloud fallback also not configured (no SARVAM_API_KEY).\n" +
                   "Please try again in a moment."
        }
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("model", "sarvam-m")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system"); put("content", systemPrompt)
                        })
                        _uiState.value.messages.takeLast(10).forEach { m ->
                            put(JSONObject().apply {
                                put("role", m.role); put("content", m.content)
                            })
                        }
                        put(JSONObject().apply {
                            put("role", "user"); put("content", userText)
                        })
                    })
                }.toString()
                val req = Request.Builder()
                    .url("https://api.sarvam.ai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val res     = httpClient.newCall(req).execute()
                val resBody = res.body?.string() ?: return@withContext "No response from cloud AI"
                if (!res.isSuccessful) return@withContext "Cloud AI error ${res.code}"
                JSONObject(resBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } catch (e: Exception) {
                "❌ AI error: ${e.message ?: "Connection failed"}"
            }
        }
    }

    // ── Caption generation (used by StatusCreateScreen) ────────────────────
    suspend fun generateCaption(text: String): String {
        val prompt = "Write a short, creative social media caption for: $text"
        return callDevilAI("You are a creative writing assistant.", prompt)
            ?: callSarvamChat("You are a creative writing assistant.", prompt)
    }

    // ── Dual-engine dispatch: Devil AI preferred, Sarvam fallback ──────────
    // Both engines always return the label "😈 Devil AI" — brand stays consistent.
    private suspend fun callAI(systemPrompt: String, userText: String): Pair<String, String> {
        val devilResult = callDevilAI(systemPrompt, userText)
        if (devilResult != null) return Pair(devilResult, "😈 Devil AI")
        return Pair(callSarvamChat(systemPrompt, userText), "😈 Devil AI")
    }

    // ── Public: send message ───────────────────────────────────────────────
    fun sendMessage(text: String, mode: String = "Devil AI") {
        if (text.isBlank()) return

        val withUser = _uiState.value.messages + AIMessage(text, "user")
        _uiState.value = _uiState.value.copy(messages = withUser, isLoading = true)

        viewModelScope.launch {
            try {
                val responseText: String
                val engine: String

                if (text.startsWith("translate:", ignoreCase = true)) {
                    val parts = text.split(" to ", ignoreCase = true)
                    responseText = if (parts.size >= 2) {
                        val content = parts[0].removePrefix("translate:").trim()
                        val lang    = parts[1].trim()
                        try {
                            sarvamRetrofit.translate(
                                apiKey  = Config.SARVAM_API_KEY,
                                request = TranslationRequest(content, "en-IN", lang)
                            ).translated_text
                        } catch (e: Exception) { "Translation failed: ${e.message}" }
                    } else "Format: translate: [text] to [language-code]"
                    engine = "🌐 Sarvam Translate"
                } else {
                    val news     = fetchRssContext()
                    val appCtx   = fetchAppContext()
                    val vaultCtx = fetchVaultContext()
                    val sys      = buildSystemPrompt(mode, news, appCtx, vaultCtx)
                    val (resp, eng) = callAI(sys, text)
                    responseText = resp
                    engine = eng
                }

                val final = withUser + AIMessage(responseText, "assistant")
                _uiState.value = _uiState.value.copy(
                    messages     = final,
                    isLoading    = false,
                    activeEngine = engine
                )
                saveLocalHistory(final)
            } catch (e: Exception) {
                val errList = withUser +
                    AIMessage("❌ Error: ${e.message ?: "Unknown"}", "assistant")
                _uiState.value = _uiState.value.copy(
                    messages  = errList,
                    isLoading = false
                )
                saveLocalHistory(errList)
            }
        }
    }
}
