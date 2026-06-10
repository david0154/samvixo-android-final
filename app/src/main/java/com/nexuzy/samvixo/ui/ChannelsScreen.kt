package com.nexuzy.samvixo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nexuzy.samvixo.ui.navigation.Screen
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest

data class ChannelData(
    val id: String,
    val name: String,
    val description: String,
    val category: String = "",
    val ownerUid: String = "",
    val subscriberCount: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(navController: NavController) {
    val db         = Firebase.firestore
    val myUid      = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val purple     = Color(0xFF7C3AED)
    val bg         = Color(0xFF0F0F13)
    val surface    = Color(0xFF1A1A24)

    val categories = listOf("All", "News", "Sports", "Business", "Education", "Technology", "Government", "AI Hub")
    var selectedCat by remember { mutableStateOf("All") }
    var allChannels  by remember { mutableStateOf<List<ChannelData>>(emptyList()) }
    var subscribedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading   by remember { mutableStateOf(true) }

    // Load all channels from Firestore
    LaunchedEffect(Unit) {
        callbackFlow<List<ChannelData>> {
            val listener = db.collection("channels")
                .orderBy("subscriberCount", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snap, _ ->
                    if (snap == null) { trySend(emptyList()); return@addSnapshotListener }
                    trySend(snap.documents.mapNotNull { doc ->
                        ChannelData(
                            id              = doc.id,
                            name            = doc.getString("name") ?: return@mapNotNull null,
                            description     = doc.getString("description") ?: "",
                            category        = doc.getString("category") ?: "",
                            ownerUid        = doc.getString("ownerUid") ?: "",
                            subscriberCount = doc.getLong("subscriberCount") ?: 0L
                        )
                    })
                }
            awaitClose { listener.remove() }
        }.collectLatest {
            allChannels = it
            isLoading   = false
        }
    }

    // Load subscriptions
    LaunchedEffect(myUid) {
        if (myUid.isEmpty()) return@LaunchedEffect
        callbackFlow<Set<String>> {
            val listener = db.collection("users").document(myUid)
                .collection("subscribedChannels")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.map { it.id }?.toSet() ?: emptySet())
                }
            awaitClose { listener.remove() }
        }.collectLatest { subscribedIds = it }
    }

    val displayed = if (selectedCat == "All") allChannels
    else allChannels.filter { it.category.equals(selectedCat, ignoreCase = true) }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("Channels", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.CreateChannel.route) }) {
                        Icon(Icons.Default.Add, null, tint = purple)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Category chips
            LazyRow(
                modifier              = Modifier.fillMaxWidth().background(surface),
                contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCat == cat,
                        onClick  = { selectedCat = cat },
                        label    = { Text(cat, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = purple,
                            selectedLabelColor     = Color.White
                        )
                    )
                }
            }

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = purple)
                    }
                }
                displayed.isEmpty() -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📢", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (selectedCat == "All") "No channels yet"
                                else "No $selectedCat channels yet",
                                color = Color(0xFF94A3B8), fontSize = 16.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Be the first to create one!",
                                color = Color(0xFF64748B), fontSize = 13.sp
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { navController.navigate(Screen.CreateChannel.route) },
                                colors  = ButtonDefaults.buttonColors(containerColor = purple)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Create Channel")
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(displayed, key = { it.id }) { ch ->
                            ChannelRow(
                                channel       = ch,
                                isSubscribed  = ch.id in subscribedIds,
                                myUid         = myUid,
                                purple        = purple,
                                surface       = surface,
                                onSubscribe   = { id, sub ->
                                    val ref = db.collection("users").document(myUid)
                                        .collection("subscribedChannels").document(id)
                                    if (sub) ref.set(mapOf("ts" to System.currentTimeMillis()))
                                    else     ref.delete()
                                },
                                onClick = { navController.navigate("channel_detail/${ch.id}") }
                            )
                            HorizontalDivider(
                                modifier  = Modifier.padding(start = 72.dp),
                                thickness = 0.5.dp,
                                color     = Color(0xFF2A2A3A)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelData,
    isSubscribed: Boolean,
    myUid: String,
    purple: Color,
    surface: Color,
    onSubscribe: (String, Boolean) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F13))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(50.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(purple, Color(0xFF06B6D4)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                channel.name.firstOrNull()?.uppercase() ?: "C",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                channel.description.ifBlank { "No description" },
                color = Color(0xFF94A3B8), fontSize = 12.sp, maxLines = 1
            )
            if (channel.subscriberCount > 0) {
                Text(
                    "${channel.subscriberCount} subscribers",
                    color = Color(0xFF64748B), fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (channel.ownerUid == myUid) {
            // Owner badge
            Surface(shape = RoundedCornerShape(12.dp), color = purple.copy(alpha = 0.2f)) {
                Text("Owner", color = purple, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        } else {
            Button(
                onClick         = { onSubscribe(channel.id, !isSubscribed) },
                shape           = RoundedCornerShape(20.dp),
                contentPadding  = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier        = Modifier.height(32.dp),
                colors          = if (isSubscribed)
                    ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A3A), contentColor = Color.White)
                else
                    ButtonDefaults.buttonColors(containerColor = purple)
            ) {
                Text(if (isSubscribed) "Following" else "Follow", fontSize = 12.sp)
            }
        }
    }
}
