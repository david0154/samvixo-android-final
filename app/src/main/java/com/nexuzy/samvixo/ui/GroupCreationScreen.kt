package com.nexuzy.samvixo.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class FirestoreUser(
    val uid        : String = "",
    val displayName: String = "",
    val photoUrl   : String = "",
    val phone      : String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreationScreen(navController: NavController) {
    val auth    = FirebaseAuth.getInstance()
    val db      = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val scope   = rememberCoroutineScope()
    val me      = auth.currentUser
    val purple  = Color(0xFF7C3AED)
    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)

    var groupName    by remember { mutableStateOf("") }
    var groupPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var loading      by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf("") }
    var users        by remember { mutableStateOf<List<FirestoreUser>>(emptyList()) }
    val selected     = remember { mutableStateListOf<String>() } // list of uids

    // FIX: load real users from Firestore instead of hardcoded mock list
    LaunchedEffect(Unit) {
        try {
            val snap = db.collection("users").limit(100).get().await()
            users = snap.documents.mapNotNull { doc ->
                val uid = doc.getString("uid") ?: doc.id
                if (uid == me?.uid) return@mapNotNull null  // exclude self
                FirestoreUser(
                    uid         = uid,
                    displayName = doc.getString("displayName") ?: uid.take(8),
                    photoUrl    = doc.getString("photoUrl") ?: "",
                    phone       = doc.getString("phone") ?: ""
                )
            }
        } catch (e: Exception) {
            error = "Could not load users: ${e.message}"
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { groupPhotoUri = it }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("New Group", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (groupName.isBlank()) { error = "Group name required"; return@IconButton }
                            if (selected.isEmpty()) { error = "Select at least 1 member"; return@IconButton }
                            error = ""; loading = true
                            scope.launch {
                                try {
                                    // FIX: single upload path — only uploads once, never twice
                                    var photoUrl = ""
                                    groupPhotoUri?.let { uri ->
                                        val ref = storage.reference.child("group_photos/${System.currentTimeMillis()}.jpg")
                                        ref.putFile(uri).await()
                                        photoUrl = ref.downloadUrl.await().toString()
                                    }
                                    val members = selected.toMutableList().also { it.add(0, me?.uid ?: "") }
                                    val groupDoc = db.collection("groups").document()
                                    groupDoc.set(mapOf(
                                        "id"          to groupDoc.id,
                                        "name"        to groupName.trim(),
                                        "photoUrl"    to photoUrl,
                                        "ownerId"     to (me?.uid ?: ""),
                                        "members"     to members,
                                        "createdAt"   to FieldValue.serverTimestamp(),
                                        "lastMessage" to "",
                                        "lastMsgTs"   to FieldValue.serverTimestamp()
                                    )).await()
                                    navController.popBackStack()
                                } catch (e: Exception) { error = e.message ?: "Failed" }
                                loading = false
                            }
                        },
                        enabled = groupName.isNotBlank() && selected.isNotEmpty() && !loading
                    ) {
                        if (loading)
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = purple)
                        else
                            Icon(Icons.Default.Check, "Create", tint = purple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Group photo + name row
            Row(
                modifier          = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(surface)
                        .border(2.dp, purple, CircleShape)
                        .clickable { photoPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (groupPhotoUri != null)
                        AsyncImage(groupPhotoUri, null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    else
                        Icon(Icons.Default.Group, null, tint = purple, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value         = groupName,
                    onValueChange = { groupName = it },
                    placeholder   = { Text("Group Name") },
                    modifier      = Modifier.weight(1f),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = purple,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = purple
                    )
                )
            }

            if (error.isNotEmpty()) {
                Text(error, color = Color(0xFFEF4444), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }

            Text(
                "Select Participants (${selected.size} selected)",
                modifier  = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style     = MaterialTheme.typography.titleSmall,
                color     = Color(0xFF94A3B8)
            )

            if (users.isEmpty() && error.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = purple)
                }
            }

            LazyColumn {
                items(users, key = { it.uid }) { user ->
                    val isChecked = user.uid in selected
                    ListItem(
                        modifier = Modifier
                            .clickable {
                                if (isChecked) selected.remove(user.uid)
                                else selected.add(user.uid)
                            }
                            .background(if (isChecked) purple.copy(alpha = 0.08f) else Color.Transparent),
                        headlineContent = {
                            Text(user.displayName, color = Color.White, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            if (user.phone.isNotEmpty()) Text(user.phone, color = Color(0xFF94A3B8), fontSize = 12.sp)
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(surface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (user.photoUrl.isNotEmpty())
                                    AsyncImage(user.photoUrl, null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                else
                                    Text(user.displayName.firstOrNull()?.uppercase() ?: "?", color = purple, fontWeight = FontWeight.Bold)
                            }
                        },
                        trailingContent = {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(user.uid)
                                    else selected.remove(user.uid)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = purple)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = bg)
                    )
                    HorizontalDivider(color = Color(0xFF1E293B))
                }
            }
        }
    }
}
