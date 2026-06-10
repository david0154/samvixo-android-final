package com.nexuzy.samvixo.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nexuzy.samvixo.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest

data class ChatPreview(
    val chatId: String,
    val otherName: String,
    val otherPhoto: String,
    val lastMessage: String,
    val timestamp: Long,
    val unread: Int,
    val type: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenChat: (chatId: String, type: String) -> Unit,
    onNewGroup: () -> Unit,
    onNewBroadcast: () -> Unit,
    onNewChannel: () -> Unit,
    onSearchUser: () -> Unit
) {
    val uid    = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db     = Firebase.firestore
    val purple = Color(0xFF7C3AED)
    val cyan   = Color(0xFF06B6D4)
    val bg     = Color(0xFF0F0F13)
    val surface= Color(0xFF1A1A24)

    var chats    by remember { mutableStateOf<List<ChatPreview>>(emptyList()) }
    var fabOpen  by remember { mutableStateOf(false) }
    var query    by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        callbackFlow {
            val listener = db.collection("chats")
                .whereArrayContains("members", uid)
                .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, _ ->
                    if (snap == null) return@addSnapshotListener
                    val list = snap.documents.mapNotNull { doc ->
                        val members  = (doc.get("members") as? List<*>) ?: return@mapNotNull null
                        val other    = members.firstOrNull { it != uid }?.toString() ?: ""
                        val type     = doc.getString("type") ?: "direct"
                        ChatPreview(
                            chatId      = doc.id,
                            otherName   = doc.getString("groupName")
                                ?: doc.getString("channelName")
                                ?: doc.getString("broadcastName")
                                ?: other.take(8),
                            otherPhoto  = doc.getString("photoUrl") ?: "",
                            lastMessage = doc.getString("lastMessage") ?: "",
                            timestamp   = doc.getLong("lastMessageTime") ?: 0L,
                            unread      = (doc.getLong("unread_$uid") ?: 0L).toInt(),
                            type        = type
                        )
                    }
                    trySend(list)
                }
            awaitClose { listener.remove() }
        }.collectLatest { chats = it }
    }

    val filtered = if (query.isBlank()) chats
                   else chats.filter { it.otherName.contains(query, ignoreCase = true) }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter            = painterResource(id = R.drawable.samvixo_logo),
                            contentDescription = "Samvixo",
                            modifier           = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text       = "Samvixo",
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style      = LocalTextStyle.current.copy(
                                brush = Brush.linearGradient(
                                    colors = listOf(purple, cyan)
                                )
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedVisibility(
                    visible = fabOpen,
                    enter = fadeIn() + slideInVertically { it },
                    exit  = fadeOut() + slideOutVertically { it }
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FabOption(icon = Icons.Default.Person,   label = "New Chat",      color = Color(0xFF06B6D4), onClick = { fabOpen = false; onSearchUser() })
                        FabOption(icon = Icons.Default.Group,    label = "New Group",     color = Color(0xFF8B5CF6), onClick = { fabOpen = false; onNewGroup() })
                        FabOption(icon = Icons.Default.Campaign, label = "New Broadcast", color = Color(0xFFF59E0B), onClick = { fabOpen = false; onNewBroadcast() })
                        FabOption(icon = Icons.Default.Tv,       label = "New Channel",   color = Color(0xFF22C55E), onClick = { fabOpen = false; onNewChannel() })
                    }
                }
                FloatingActionButton(
                    onClick  = { fabOpen = !fabOpen },
                    containerColor = purple,
                    shape = CircleShape
                ) {
                    Icon(
                        if (fabOpen) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search chats…", color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF64748B)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedBorderColor   = purple,
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    focusedContainerColor = surface,
                    unfocusedContainerColor = surface
                )
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💬", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No chats yet", color = Color(0xFF94A3B8))
                        Text("Tap + to start a chat, group or channel", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.chatId }) { chat ->
                        ChatListItem(chat = chat, onClick = { onOpenChat(chat.chatId, chat.type) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FabOption(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            shape  = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))
        ) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 13.sp, color = Color.White)
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = color,
            shape = CircleShape
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
    }
}

@Composable
private fun ChatListItem(chat: ChatPreview, onClick: () -> Unit) {
    val purple  = Color(0xFF7C3AED)

    val typeIcon = when (chat.type) {
        "group"     -> "👥"
        "broadcast" -> "📢"
        "channel"   -> "📡"
        else        -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(purple, Color(0xFF06B6D4)))),
            contentAlignment = Alignment.Center
        ) {
            if (chat.otherPhoto.isNotEmpty()) {
                AsyncImage(
                    model = chat.otherPhoto,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (typeIcon.isNotEmpty()) {
                Text(typeIcon, fontSize = 22.sp)
            } else {
                Text(chat.otherName.firstOrNull()?.uppercase() ?: "?", color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.otherName, color = Color.White, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(chat.lastMessage.ifBlank { "Tap to open" }, color = Color(0xFF94A3B8),
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (chat.unread > 0) {
            Box(
                modifier = Modifier.size(20.dp).background(purple, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(chat.unread.toString(), color = Color.White, fontSize = 11.sp)
            }
        }
    }
}
