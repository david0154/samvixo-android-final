package com.nexuzy.samvixo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InboxNotification(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val read: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(navController: NavController) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val db  = Firebase.firestore

    var notifications by remember { mutableStateOf<List<InboxNotification>>(emptyList()) }
    var loading       by remember { mutableStateOf(true) }

    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)
    val purple  = Color(0xFF7C3AED)

    LaunchedEffect(uid) {
        if (uid.isEmpty()) { loading = false; return@LaunchedEffect }
        db.collection("users").document(uid)
            .collection("notifications")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                notifications = snap?.documents?.mapNotNull { doc ->
                    InboxNotification(
                        id        = doc.id,
                        title     = doc.getString("title") ?: "Notification",
                        body      = doc.getString("body") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        read      = doc.getBoolean("read") ?: false
                    )
                } ?: emptyList()
                loading = false
            }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("Inbox", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = purple)
            }
        } else if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF64748B)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No notifications yet", color = Color(0xFF64748B), fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { notif ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (notif.read) surface else Color(0xFF1E1B33)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            if (!notif.read && uid.isNotEmpty()) {
                                db.collection("users").document(uid)
                                    .collection("notifications").document(notif.id)
                                    .update("read", true)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                if (notif.read) Icons.Default.NotificationsNone else Icons.Default.Notifications,
                                contentDescription = null,
                                tint   = if (notif.read) Color(0xFF64748B) else purple,
                                modifier = Modifier.size(22.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    notif.title,
                                    fontWeight = if (notif.read) FontWeight.Normal else FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                if (notif.body.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(notif.body, fontSize = 12.sp, color = Color(0xFF94A3B8), lineHeight = 18.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(notif.timestamp)),
                                    fontSize = 10.sp, color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
