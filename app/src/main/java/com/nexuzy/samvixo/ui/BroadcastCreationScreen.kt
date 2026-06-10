package com.nexuzy.samvixo.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ContactUser(val uid: String, val name: String, val photo: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastCreationScreen(onCreated: (broadcastId: String) -> Unit, onBack: () -> Unit) {
    val uid    = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db     = Firebase.firestore
    val scope  = rememberCoroutineScope()
    val purple = Color(0xFF7C3AED)
    val bg     = Color(0xFF0F0F13)
    val surface= Color(0xFF1A1A24)

    var broadcastName by remember { mutableStateOf("") }
    var contacts      by remember { mutableStateOf<List<ContactUser>>(emptyList()) }
    var selected      by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading       by remember { mutableStateOf(false) }
    var error         by remember { mutableStateOf("") }
    var searchQ       by remember { mutableStateOf("") }

    // Load all users (in real app filter by contacts/following)
    LaunchedEffect(Unit) {
        val snap = db.collection("users").limit(100).get().await()
        contacts = snap.documents
            .filter { it.id != uid }
            .map { ContactUser(it.id, it.getString("displayName") ?: "?", it.getString("photoUrl") ?: "") }
    }

    val filtered = if (searchQ.isBlank()) contacts
                   else contacts.filter { it.name.contains(searchQ, ignoreCase = true) }

    fun create() {
        if (broadcastName.isBlank()) { error = "Enter broadcast name"; return }
        if (selected.isEmpty()) { error = "Select at least one recipient"; return }
        loading = true; error = ""
        scope.launch {
            try {
                val members = selected.toMutableList().also { it.add(uid) }
                val doc = db.collection("chats").add(hashMapOf(
                    "type"          to "broadcast",
                    "broadcastName" to broadcastName.trim(),
                    "ownerUid"      to uid,
                    "members"       to members,
                    "lastMessage"   to "",
                    "lastMessageTime" to 0L,
                    "createdAt"     to com.google.firebase.Timestamp.now()
                )).await()
                onCreated(doc.id)
            } catch (e: Exception) { error = e.message ?: "Error" }
            loading = false
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("New Broadcast", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = { create() }, enabled = !loading) {
                        Text("Create", color = if (loading) Color(0xFF64748B) else Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Name input
            OutlinedTextField(
                value = broadcastName, onValueChange = { broadcastName = it },
                label = { Text("Broadcast Name") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
                colors = settingsFieldColors(purple)
            )
            // Search
            OutlinedTextField(
                value = searchQ, onValueChange = { searchQ = it },
                placeholder = { Text("Search contacts…", color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF64748B)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                colors = settingsFieldColors(purple)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${selected.size} selected",
                fontSize = 12.sp, color = Color(0xFF94A3B8),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (error.isNotEmpty()) Text(error, color = Color(0xFFEF4444), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn {
                items(filtered, key = { it.uid }) { user ->
                    val isChecked = user.uid in selected
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = if (isChecked) selected - user.uid else selected + user.uid
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(purple, Color(0xFF06B6D4)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(user.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(user.name, color = Color.White, modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF59E0B))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun settingsFieldColors(purple: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = purple,
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor    = purple,
    cursorColor          = purple,
    focusedTextColor     = Color.White,
    unfocusedTextColor   = Color.White
)
