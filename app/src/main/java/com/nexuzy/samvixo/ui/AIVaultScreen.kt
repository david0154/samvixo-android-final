package com.nexuzy.samvixo.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class VaultEntry(
    val id: String,
    val question: String,
    val answer: String,
    val tag: String,
    val timestamp: Long
)

private const val PREFS_VAULT = "ai_vault_prefs"
private const val KEY_VAULT   = "vault_json"

/** internal so AIViewModel can call it from the same module */
internal fun loadVault(ctx: Context): List<VaultEntry> {
    val json = ctx.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE)
        .getString(KEY_VAULT, "[]") ?: "[]"
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VaultEntry(
                id        = o.getString("id"),
                question  = o.getString("question"),
                answer    = o.getString("answer"),
                tag       = o.optString("tag", "General"),
                timestamp = o.getLong("timestamp")
            )
        }.sortedByDescending { it.timestamp }
    } catch (_: Exception) { emptyList() }
}

fun saveVaultEntry(ctx: Context, question: String, answer: String, tag: String = "General") {
    val existing = loadVault(ctx).toMutableList()
    existing.add(0, VaultEntry(
        id        = UUID.randomUUID().toString(),
        question  = question,
        answer    = answer,
        tag       = tag,
        timestamp = System.currentTimeMillis()
    ))
    saveVaultList(ctx, existing)
}

internal fun saveVaultList(ctx: Context, entries: List<VaultEntry>) {
    val arr = JSONArray()
    entries.forEach { e ->
        arr.put(JSONObject().apply {
            put("id",        e.id)
            put("question",  e.question)
            put("answer",    e.answer)
            put("tag",       e.tag)
            put("timestamp", e.timestamp)
        })
    }
    ctx.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE)
        .edit().putString(KEY_VAULT, arr.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIVaultScreen(navController: NavController) {
    val ctx     = LocalContext.current
    val scope   = rememberCoroutineScope()
    val purple  = Color(0xFF7C3AED)
    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)

    var entries     by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var showDialog  by remember { mutableStateOf(false) }
    var qInput      by remember { mutableStateOf("") }
    var aInput      by remember { mutableStateOf("") }
    var tagInput    by remember { mutableStateOf("General") }
    var searchQuery by remember { mutableStateOf("") }

    val tags = listOf("General", "Science", "History", "Tech", "Health", "Finance", "Other")

    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { loadVault(ctx) }
    }

    val displayed: List<VaultEntry> = if (searchQuery.isBlank()) entries
    else entries.filter {
        it.question.contains(searchQuery, ignoreCase = true) ||
        it.answer.contains(searchQuery, ignoreCase = true) ||
        it.tag.contains(searchQuery, ignoreCase = true)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add to Knowledge Vault", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value         = qInput,
                        onValueChange = { qInput = it },
                        label         = { Text("Question / Topic") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = aInput,
                        onValueChange = { aInput = it },
                        label         = { Text("Answer / Knowledge") },
                        modifier      = Modifier.fillMaxWidth().height(140.dp),
                        maxLines      = 8
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Tag", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.take(4).forEach { t ->
                            FilterChip(
                                selected = tagInput == t,
                                onClick  = { tagInput = t },
                                label    = { Text(t, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (qInput.isNotBlank() || aInput.isNotBlank()) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    saveVaultEntry(ctx, qInput, aInput, tagInput)
                                }
                                entries    = withContext(Dispatchers.IO) { loadVault(ctx) }
                                showDialog = false
                                qInput     = ""
                                aInput     = ""
                                tagInput   = "General"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = purple)
                ) { Text("Save to Vault") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
            containerColor = surface
        )
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title  = { Text("AI Knowledge Vault", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }, containerColor = purple) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder   = { Text("Search vault...") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                modifier      = Modifier.fillMaxWidth().padding(12.dp),
                shape         = RoundedCornerShape(24.dp),
                singleLine    = true
            )

            if (displayed.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧠", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isBlank()) "Vault is empty" else "No results",
                            color = Color(0xFF94A3B8), fontSize = 16.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Save AI answers here as your personal knowledge base",
                            color = Color(0xFF64748B), fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("🔒 Stored on device only — never sent to cloud",
                            color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayed, key = { it.id }) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = CardDefaults.cardColors(containerColor = surface)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = purple.copy(alpha = 0.2f)
                                    ) {
                                        Text(entry.tag, color = purple, fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val updated = entries.filter { it.id != entry.id }
                                                withContext(Dispatchers.IO) { saveVaultList(ctx, updated) }
                                                entries = updated
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, null,
                                            tint     = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(entry.question, color = Color.White,
                                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(entry.answer, color = Color(0xFF94A3B8),
                                    fontSize = 13.sp, maxLines = 4)
                            }
                        }
                    }
                }
            }
        }
    }
}
