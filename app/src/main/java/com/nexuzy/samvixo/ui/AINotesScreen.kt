package com.nexuzy.samvixo.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import java.text.SimpleDateFormat
import java.util.*

data class AINote(
    val id: String,
    val title: String,
    val content: String,
    val timestamp: Long
)

// ── On-device storage using SharedPreferences JSON (no cloud, device-only) ──
private const val PREFS_NOTES = "ai_notes_prefs"
private const val KEY_NOTES   = "notes_json"

private fun loadNotes(ctx: Context): List<AINote> {
    val json = ctx.getSharedPreferences(PREFS_NOTES, Context.MODE_PRIVATE)
        .getString(KEY_NOTES, "[]") ?: "[]"
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AINote(
                id        = o.getString("id"),
                title     = o.getString("title"),
                content   = o.getString("content"),
                timestamp = o.getLong("timestamp")
            )
        }.sortedByDescending { it.timestamp }
    } catch (_: Exception) { emptyList() }
}

private fun saveNotes(ctx: Context, notes: List<AINote>) {
    val arr = JSONArray()
    notes.forEach { n ->
        arr.put(JSONObject().apply {
            put("id",        n.id)
            put("title",     n.title)
            put("content",   n.content)
            put("timestamp", n.timestamp)
        })
    }
    ctx.getSharedPreferences(PREFS_NOTES, Context.MODE_PRIVATE)
        .edit().putString(KEY_NOTES, arr.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AINotesScreen(navController: NavController) {
    val ctx     = LocalContext.current
    val scope   = rememberCoroutineScope()
    val purple  = Color(0xFF7C3AED)
    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)

    var notes        by remember { mutableStateOf<List<AINote>>(emptyList()) }
    var showEditor   by remember { mutableStateOf(false) }
    var editNote     by remember { mutableStateOf<AINote?>(null) }
    var titleInput   by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        notes = withContext(Dispatchers.IO) { loadNotes(ctx) }
    }

    fun openEditor(note: AINote? = null) {
        editNote     = note
        titleInput   = note?.title   ?: ""
        contentInput = note?.content ?: ""
        showEditor   = true
    }

    fun saveNote() {
        if (titleInput.isBlank() && contentInput.isBlank()) return
        scope.launch {
            val updated = notes.toMutableList()
            val existing = editNote
            if (existing != null) {
                val idx = updated.indexOfFirst { it.id == existing.id }
                if (idx >= 0) updated[idx] = existing.copy(title = titleInput, content = contentInput, timestamp = System.currentTimeMillis())
            } else {
                updated.add(0, AINote(
                    id        = UUID.randomUUID().toString(),
                    title     = titleInput.ifBlank { "Untitled" },
                    content   = contentInput,
                    timestamp = System.currentTimeMillis()
                ))
            }
            withContext(Dispatchers.IO) { saveNotes(ctx, updated) }
            notes      = updated.sortedByDescending { it.timestamp }
            showEditor = false
        }
    }

    fun deleteNote(id: String) {
        scope.launch {
            val updated = notes.filter { it.id != id }
            withContext(Dispatchers.IO) { saveNotes(ctx, updated) }
            notes = updated
        }
    }

    // Note editor dialog
    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editNote == null) "New AI Note" else "Edit Note", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value         = titleInput,
                        onValueChange = { titleInput = it },
                        label         = { Text("Title") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = contentInput,
                        onValueChange = { contentInput = it },
                        label         = { Text("Content") },
                        modifier      = Modifier.fillMaxWidth().height(180.dp),
                        maxLines      = 10
                    )
                }
            },
            confirmButton = {
                Button(onClick = { saveNote() }, colors = ButtonDefaults.buttonColors(containerColor = purple)) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("Cancel") }
            },
            containerColor = surface
        )
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title  = { Text("AI Notes", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { openEditor() }, containerColor = purple) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📝", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No notes yet", color = Color(0xFF94A3B8), fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap + to create your first AI note", color = Color(0xFF64748B), fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("🔒 Stored on device only", color = Color(0xFF64748B), fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { openEditor(note) },
                        shape    = RoundedCornerShape(12.dp),
                        colors   = CardDefaults.cardColors(containerColor = surface)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(note.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(fmt.format(Date(note.timestamp)), color = Color(0xFF64748B), fontSize = 11.sp)
                                }
                                IconButton(onClick = { deleteNote(note.id) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                                }
                            }
                            if (note.content.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    note.content,
                                    color    = Color(0xFF94A3B8),
                                    fontSize = 13.sp,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
