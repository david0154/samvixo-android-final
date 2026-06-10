package com.nexuzy.samvixo.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelCreationScreen(onCreated: (channelId: String) -> Unit, onBack: () -> Unit) {
    val uid    = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db     = Firebase.firestore
    val scope  = rememberCoroutineScope()
    val purple = Color(0xFF7C3AED)
    val bg     = Color(0xFF0F0F13)
    val surface= Color(0xFF1A1A24)

    var channelName  by remember { mutableStateOf("") }
    var description  by remember { mutableStateOf("") }
    var isPrivate    by remember { mutableStateOf(false) }
    var photoUri     by remember { mutableStateOf<Uri?>(null) }
    var loading      by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf("") }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { photoUri = it }
    }

    fun create() {
        if (channelName.isBlank()) { error = "Enter channel name"; return }
        loading = true; error = ""
        scope.launch {
            try {
                var photoUrl = ""
                if (photoUri != null) {
                    val ref = Firebase.storage.reference.child("channel_photos/${System.currentTimeMillis()}.jpg")
                    ref.putFile(photoUri!!).await()
                    photoUrl = ref.downloadUrl.await().toString()
                }
                // FIX: write both "name" and "channelName", write correct ownerUid
                // and subscriberCount so ChannelsScreen orderBy query works
                val doc = db.collection("channels").add(hashMapOf(
                    "name"            to channelName.trim(),
                    "channelName"     to channelName.trim(),
                    "description"     to description.trim(),
                    "ownerUid"        to uid,
                    "ownerId"         to uid,
                    "members"         to listOf(uid),
                    "subscribers"     to listOf(uid),
                    "isPrivate"       to isPrivate,
                    "isPublic"        to !isPrivate,
                    "photoUrl"        to photoUrl,
                    "createdAt"       to Timestamp.now(),
                    "type"            to "channel",
                    "category"        to "",
                    "subscriberCount" to 1L,
                    "postsCount"      to 0L,
                    "active"          to true
                )).await()
                db.collection("chats").document(doc.id).set(hashMapOf(
                    "type"            to "channel",
                    "channelName"     to channelName.trim(),
                    "name"            to channelName.trim(),
                    "members"         to listOf(uid),
                    "photoUrl"        to photoUrl,
                    "lastMessage"     to "",
                    "lastMessageTime" to 0L,
                    "ownerUid"        to uid
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
                title = { Text("New Channel", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(90.dp).clip(CircleShape)
                    .background(Color(0xFF2D2D3D), CircleShape)
                    .border(2.dp, purple, CircleShape)
                    .align(Alignment.CenterHorizontally)
                    .clickable { photoLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(photoUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(32.dp))
                }
            }
            Text("Tap to set channel photo", fontSize = 12.sp, color = Color(0xFF94A3B8),
                modifier = Modifier.align(Alignment.CenterHorizontally))

            OutlinedTextField(
                value = channelName, onValueChange = { channelName = it },
                label = { Text("Channel Name") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, colors = channelCreationFieldColors(purple)
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(),
                maxLines = 3, colors = channelCreationFieldColors(purple)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Private Channel", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = isPrivate, onCheckedChange = { isPrivate = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = purple, checkedTrackColor = purple.copy(alpha = 0.4f)))
            }
            if (error.isNotEmpty()) Text(error, color = Color(0xFFEF4444), fontSize = 13.sp)
            Button(
                onClick = { create() }, enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("\uD83D\uDCE1 Create Channel", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun channelCreationFieldColors(purple: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = purple,
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor    = purple,
    cursorColor          = purple,
    focusedTextColor     = Color.White,
    unfocusedTextColor   = Color.White
)
