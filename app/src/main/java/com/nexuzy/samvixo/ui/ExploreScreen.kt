package com.nexuzy.samvixo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

data class ExploreUser(
    val uid: String        = "",
    val displayName: String = "",
    val about: String      = "",
    val photoUrl: String   = "",
    val phoneNumber: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen() {
    val myUid  = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val purple = Color(0xFF7C3AED)
    val bg     = Color(0xFF0F0F13)

    var users     by remember { mutableStateOf<List<ExploreUser>>(emptyList()) }
    var loading   by remember { mutableStateOf(true) }
    var searchQ   by remember { mutableStateOf("") }
    var errorMsg  by remember { mutableStateOf("") }

    // Load real users from Firestore (excluding self)
    LaunchedEffect(Unit) {
        loading = true
        try {
            val snap = Firebase.firestore.collection("users")
                .whereEqualTo("banned",     false)
                .whereEqualTo("restricted", false)
                .limit(50)
                .get().await()
            users = snap.documents
                .filter { it.id != myUid }
                .mapNotNull { doc ->
                    val name = doc.getString("displayName") ?: return@mapNotNull null
                    ExploreUser(
                        uid         = doc.id,
                        displayName = name,
                        about       = doc.getString("about") ?: "",
                        photoUrl    = doc.getString("photoUrl") ?: "",
                        phoneNumber = doc.getString("phoneNumber") ?: ""
                    )
                }
        } catch (e: Exception) {
            errorMsg = "Could not load users: ${e.message}"
        }
        loading = false
    }

    val filtered = if (searchQ.isBlank()) users
    else users.filter {
        it.displayName.contains(searchQ, ignoreCase = true) ||
        it.phoneNumber.contains(searchQ)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Explore", fontWeight = FontWeight.Bold) })
        },
        containerColor = bg
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value         = searchQ,
                onValueChange = { searchQ = it },
                placeholder   = { Text("Search by name or phone") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape         = RoundedCornerShape(24.dp),
                singleLine    = true
            )

            when {
                loading  -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = purple)
                }
                errorMsg.isNotEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(errorMsg, color = Color(0xFFEF4444))
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No users found", color = Color(0xFF64748B))
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.uid }) { u ->
                        Card(
                            shape  = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))
                        ) {
                            Row(
                                modifier          = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (u.photoUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model    = u.photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier         = Modifier.size(48.dp).background(purple, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            u.displayName.firstOrNull()?.uppercase() ?: "?",
                                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(u.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    if (u.about.isNotEmpty())
                                        Text(u.about, color = Color(0xFF94A3B8), fontSize = 13.sp)
                                }
                                Icon(Icons.Default.Message, null, tint = purple)
                            }
                        }
                    }
                }
            }
        }
    }
}
