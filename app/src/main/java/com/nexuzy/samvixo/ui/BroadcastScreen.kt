package com.nexuzy.samvixo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserContact(val uid: String, val name: String, val phone: String, val photoUrl: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastScreen(navController: NavController) {
    val auth     = FirebaseAuth.getInstance()
    val db       = FirebaseFirestore.getInstance()
    val scope    = rememberCoroutineScope()
    val me       = auth.currentUser

    var contacts      by remember { mutableStateOf<List<UserContact>>(emptyList()) }
    var selected      by remember { mutableStateOf<Set<String>>(emptySet()) }
    var listName      by remember { mutableStateOf("") }
    var loading       by remember { mutableStateOf(false) }
    var error         by remember { mutableStateOf("") }

    // Load contacts (users the current user has chatted with)
    LaunchedEffect(me?.uid) {
        me?.uid?.let { uid ->
            try {
                val snaps = db.collection("users").whereNotEqualTo("uid", uid).limit(100).get().await()
                contacts = snaps.documents.mapNotNull { doc ->
                    val duid = doc.getString("uid") ?: return@mapNotNull null
                    UserContact(
                        uid = duid,
                        name = doc.getString("name") ?: "Unknown",
                        phone = doc.getString("phoneNumber") ?: ""
                    )
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Broadcast") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = {
                            if (listName.isBlank()) { error = "Enter a list name"; return@TextButton }
                            loading = true
                            scope.launch {
                                try {
                                    db.collection("broadcasts").add(mapOf(
                                        "name"       to listName.trim(),
                                        "senderId"   to (me?.uid ?: ""),
                                        "recipients" to selected.toList(),
                                        "createdAt"  to FieldValue.serverTimestamp()
                                    )).await()
                                    navController.popBackStack()
                                } catch (e: Exception) { error = e.message ?: "Error" }
                                loading = false
                            }
                        }) { Text("Create (${selected.size})") }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = listName, onValueChange = { listName = it },
                label = { Text("List Name") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            Text("Select recipients: ${selected.size} selected",
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                fontSize = 12.sp, color = Color.Gray)
            LazyColumn {
                items(contacts) { contact ->
                    val isSelected = contact.uid in selected
                    ListItem(
                        headlineContent = { Text(contact.name) },
                        supportingContent = { Text(contact.phone) },
                        leadingContent = {
                            Surface(Modifier.size(44.dp).clip(CircleShape), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.padding(10.dp))
                            }
                        },
                        trailingContent = {
                            Checkbox(checked = isSelected, onCheckedChange = {
                                selected = if (it) selected + contact.uid else selected - contact.uid
                            })
                        },
                        modifier = Modifier.clickable {
                            selected = if (isSelected) selected - contact.uid else selected + contact.uid
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}
