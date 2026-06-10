package com.nexuzy.samvixo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexuzy.samvixo.data.remote.OllamaApi
import com.nexuzy.samvixo.data.remote.OllamaRequest
import com.nexuzy.samvixo.util.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * SmartFolderViewModel — Tier 1 AI (Ollama / Devil AI) powered chat classification (§7 PID)
 *
 * Classifies chat previews into folders:
 *   Personal · Work · Family · Important · Unread · Groups · Channels
 *
 * Falls back gracefully if Devil AI endpoint is unreachable.
 * No ads shown (§7.5 — Smart Folders: Ad Shown? No).
 */
data class ChatFolder(
    val id: String,
    val label: String,
    val emoji: String,
    val chatIds: List<String> = emptyList()
)

class SmartFolderViewModel : ViewModel() {

    private val _folders = MutableStateFlow<List<ChatFolder>>(
        listOf(
            ChatFolder("all",        "All",       "💬"),
            ChatFolder("personal",   "Personal",  "👤"),
            ChatFolder("work",       "Work",       "💼"),
            ChatFolder("family",     "Family",     "👨‍👩‍👧"),
            ChatFolder("important",  "Important",  "⭐"),
            ChatFolder("groups",     "Groups",     "👥")
        )
    )
    val folders: StateFlow<List<ChatFolder>> = _folders

    private val _isClassifying = MutableStateFlow(false)
    val isClassifying: StateFlow<Boolean> = _isClassifying

    private val ollamaApi: OllamaApi by lazy {
        Retrofit.Builder()
            .baseUrl(Config.DEVIL_AI_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OllamaApi::class.java)
    }

    /**
     * Classifies a list of (chatId, lastMessage) pairs into smart folders using Ollama.
     * Falls back to rule-based classification if AI fails.
     */
    fun classifyChats(chats: List<Pair<String, String>>) {
        if (chats.isEmpty()) return
        _isClassifying.value = true
        viewModelScope.launch {
            try {
                val preview = chats.take(20).joinToString("\n") { (id, msg) ->
                    "$id: $msg"
                }
                val prompt = """
                    Classify each chat by its last message into ONE of these folders:
                    personal, work, family, important, groups.
                    Return ONLY JSON like: {"chat_id": "folder_name"}
                    Chats:
                    $preview
                """.trimIndent()

                val response = ollamaApi.generate(
                    OllamaRequest(
                        model  = Config.DEVIL_AI_MODEL,
                        prompt = prompt,
                        stream = false
                    )
                )

                // Parse JSON response and assign chats to folders
                val json = response.response
                val updated = _folders.value.map { folder ->
                    val matchingIds = chats
                        .filter { (id, _) -> json.contains("\"$id\":\"${folder.id}\"") }
                        .map { it.first }
                    folder.copy(chatIds = matchingIds)
                }
                _folders.value = updated
            } catch (_: Exception) {
                // Fallback: rule-based classification
                ruleBasedClassify(chats)
            } finally {
                _isClassifying.value = false
            }
        }
    }

    private fun ruleBasedClassify(chats: List<Pair<String, String>>) {
        val workKeywords   = listOf("meeting", "project", "deadline", "report", "office")
        val familyKeywords = listOf("mom", "dad", "bhai", "didi", "beta", "family")

        val updated = _folders.value.map { folder ->
            val ids = when (folder.id) {
                "work"   -> chats.filter { (_, msg) ->
                    workKeywords.any { kw -> msg.contains(kw, ignoreCase = true) }
                }.map { it.first }
                "family" -> chats.filter { (_, msg) ->
                    familyKeywords.any { kw -> msg.contains(kw, ignoreCase = true) }
                }.map { it.first }
                else     -> folder.chatIds
            }
            folder.copy(chatIds = ids)
        }
        _folders.value = updated
    }
}
