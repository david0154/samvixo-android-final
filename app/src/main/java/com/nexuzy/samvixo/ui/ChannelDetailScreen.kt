package com.nexuzy.samvixo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ChannelMessage(
    val id: String = "",
    val text: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val timestamp: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(channelId: String, navController: NavHostController) {
    val db = Firebase.firestore
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    var channelName by remember { mutableStateOf("Channel") }
    var messages by remember { mutableStateOf(listOf<ChannelMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }

    // Load channel info
    LaunchedEffect(channelId) {
        try {
            val doc = db.collection("channels").document(channelId).get().await()
            channelName = doc.getString("name") ?: "Channel"
            val adminUid = doc.getString("ownerUid") ?: ""
            isAdmin = adminUid == auth.currentUser?.uid
        } catch (_: Exception) {}
    }

    // Listen to messages
    DisposableEffect(channelId) {
        val listener = db.collection("channels").document(channelId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    messages = snapshot.documents.mapNotNull { doc ->
                        ChannelMessage(
                            id = doc.id,
                            text = doc.getString("text") ?: "",
                            senderUid = doc.getString("senderUid") ?: "",
                            senderName = doc.getString("senderName") ?: "Unknown",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }
                }
            }
        onDispose { listener.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(channelName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (isAdmin) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Broadcast a message…") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotEmpty()) {
                                    scope.launch {
                                        try {
                                            val user = auth.currentUser
                                            db.collection("channels").document(channelId)
                                                .collection("messages")
                                                .add(
                                                    mapOf(
                                                        "text" to text,
                                                        "senderUid" to (user?.uid ?: ""),
                                                        "senderName" to (user?.displayName ?: "Admin"),
                                                        "timestamp" to System.currentTimeMillis()
                                                    )
                                                ).await()
                                            inputText = ""
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            reverseLayout = false
        ) {
            items(messages, key = { it.id }) { msg ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = msg.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = msg.text,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
