package com.nexuzy.samvixo.ui

/**
 * UserProfileViewScreen.kt
 *
 * Shows another user's public profile (name, photo, about/bio, phone if shared).
 * Navigated to via: navController.navigate("user_profile/$uid")
 * Also used to view own profile in full-screen photo preview mode.
 *
 * Photo preview: tapping the avatar opens a full-screen photo dialog.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileViewScreen(navController: NavController, uid: String) {
    val purple  = Color(0xFF7C3AED)
    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)
    val me      = FirebaseAuth.getInstance().currentUser
    val isOwn   = (me?.uid == uid)

    var name       by remember { mutableStateOf("") }
    var bio        by remember { mutableStateOf("") }
    var photoUrl   by remember { mutableStateOf("") }
    var phone      by remember { mutableStateOf("") }
    var loading    by remember { mutableStateOf(true) }
    var showPhoto  by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        loading = true
        try {
            val doc = Firebase.firestore.collection("users").document(uid).get().await()
            name     = doc.getString("name") ?: doc.getString("displayName") ?: ""
            bio      = doc.getString("bio") ?: doc.getString("about") ?: ""
            photoUrl = doc.getString("photoUrl") ?: ""
            // Only show phone if it's own profile
            phone    = if (isOwn) (me?.phoneNumber ?: "") else ""
        } catch (_: Exception) {
            if (isOwn) {
                name  = me?.displayName ?: ""
                phone = me?.phoneNumber ?: ""
            }
        } finally {
            loading = false
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text(if (isOwn) "My Profile" else name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    if (isOwn) {
                        IconButton(onClick = { navController.navigate("profile_settings") }) {
                            Icon(Icons.Default.Edit, null, tint = Color.White)
                        }
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
            return@Scaffold
        }

        Column(
            modifier            = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Tappable Avatar (opens full-screen preview) ──────────────────
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(purple, Color(0xFF06B6D4))))
                    .border(3.dp, purple, CircleShape)
                    .clickable { if (photoUrl.isNotEmpty()) showPhoto = true },
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl.isNotEmpty()) {
                    AsyncImage(
                        model              = photoUrl,
                        contentDescription = "Profile Photo",
                        modifier           = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale       = ContentScale.Crop
                    )
                } else {
                    Text(
                        name.firstOrNull()?.uppercase() ?: "?",
                        fontSize   = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }
            }

            // Name
            Text(name.ifBlank { "Unknown User" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)

            // Bio
            if (bio.isNotBlank()) {
                Text(bio, fontSize = 14.sp, color = Color(0xFF94A3B8))
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(Modifier.height(4.dp))

            // Phone (only for own profile)
            if (isOwn && phone.isNotBlank()) {
                ProfileInfoRow(
                    icon  = Icons.Default.Phone,
                    label = "Phone",
                    value = phone,
                    tint  = purple
                )
            }

            // Info rows
            ProfileInfoRow(
                icon  = Icons.Default.Info,
                label = "About",
                value = bio.ifBlank { "Hey there! I'm using Samvixo." },
                tint  = purple
            )

            // Message button (not for own profile)
            if (!isOwn) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = {
                        navController.navigate("chat_detail/${uid}/$name")
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = purple),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Message, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Send Message", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // ── Full-screen photo preview dialog ─────────────────────────────────
    if (showPhoto && photoUrl.isNotEmpty()) {
        Dialog(
            onDismissRequest = { showPhoto = false },
            properties       = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { showPhoto = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model              = photoUrl,
                    contentDescription = "Profile Photo Full",
                    modifier           = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape),
                    contentScale       = ContentScale.Crop
                )
                IconButton(
                    onClick  = { showPhoto = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon : androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint : Color
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
            Text(value, fontSize = 15.sp, color = Color.White)
        }
    }
}
