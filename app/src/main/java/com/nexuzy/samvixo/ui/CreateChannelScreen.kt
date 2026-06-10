package com.nexuzy.samvixo.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChannelScreen(navController: NavController) {
    val auth    = FirebaseAuth.getInstance()
    val db      = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val scope   = rememberCoroutineScope()
    val me      = auth.currentUser

    var channelName  by remember { mutableStateOf("") }
    var description  by remember { mutableStateOf("") }
    var category     by remember { mutableStateOf("") }
    var isPublic     by remember { mutableStateOf(true) }
    var photoUri     by remember { mutableStateOf<Uri?>(null) }
    var loading      by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf("") }

    if (me == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("\u26a0\ufe0f You must be signed in to create a channel.",
                color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
        }
        return
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { photoUri = it }
    }

    val categories = listOf("", "News", "Sports", "Business", "Education", "Technology", "Government", "AI Hub")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Channel") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar picker
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { photoPicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null)
                    AsyncImage(photoUri, null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                else
                    Icon(Icons.Default.Campaign, null, modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text("Channel Photo (optional)", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = channelName, onValueChange = { channelName = it },
                label = { Text("Channel Name *") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), minLines = 2, maxLines = 4
            )
            Spacer(Modifier.height(12.dp))

            // Category dropdown
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = category.ifEmpty { "Select Category (optional)" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.ifEmpty { "None" }) },
                            onClick = { category = cat; expanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isPublic) "Public Channel" else "Private Channel", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isPublic) "Anyone can find and follow" else "Invite-only, link required",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
                Switch(checked = isPublic, onCheckedChange = { isPublic = it })
            }

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (channelName.isBlank()) { error = "Channel name is required."; return@Button }
                    error = ""; loading = true
                    scope.launch {
                        try {
                            var photoUrl = ""
                            val currentUri = photoUri
                            if (currentUri != null) {
                                val ref = storage.reference
                                    .child("channel_photos/${me.uid}/${System.currentTimeMillis()}.jpg")
                                ref.putFile(currentUri).await()
                                photoUrl = ref.downloadUrl.await().toString()
                            }
                            // FIX: write both "name" (read by ChannelsScreen) and "channelName"
                            // FIX: write "ownerUid" (not "ownerId") — matches ChannelsScreen query
                            // FIX: write "subscriberCount" = 1 so Firestore orderBy index is satisfied
                            // FIX: write "category" so category filter in ChannelsScreen works
                            val channelDoc = db.collection("channels").document()
                            channelDoc.set(mapOf(
                                "id"              to channelDoc.id,
                                "name"            to channelName.trim(),
                                "channelName"     to channelName.trim(),
                                "description"     to description.trim(),
                                "photoUrl"        to photoUrl,
                                "isPublic"        to isPublic,
                                "isPrivate"       to !isPublic,
                                "ownerUid"        to me.uid,
                                "ownerId"         to me.uid,
                                "ownerName"       to (me.displayName ?: ""),
                                "category"        to category,
                                "createdAt"       to Timestamp.now(),
                                "subscribers"     to listOf(me.uid),
                                "members"         to listOf(me.uid),
                                "subscriberCount" to 1L,
                                "postsCount"      to 0L,
                                "type"            to "channel",
                                "active"          to true
                            )).await()
                            // Also write a chat doc so the channel shows in chat list
                            db.collection("chats").document(channelDoc.id).set(mapOf(
                                "type"            to "channel",
                                "channelName"     to channelName.trim(),
                                "name"            to channelName.trim(),
                                "members"         to listOf(me.uid),
                                "photoUrl"        to photoUrl,
                                "lastMessage"     to "",
                                "lastMessageTime" to 0L,
                                "ownerUid"        to me.uid
                            )).await()
                            navController.popBackStack()
                        } catch (e: Exception) {
                            error = e.message ?: "Failed to create channel."
                        }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = !loading,
                shape    = RoundedCornerShape(12.dp)
            ) {
                if (loading)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else
                    Text("Create Channel", fontWeight = FontWeight.Bold)
            }
        }
    }
}
