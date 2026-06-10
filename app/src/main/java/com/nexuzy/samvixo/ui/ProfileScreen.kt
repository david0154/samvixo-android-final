package com.nexuzy.samvixo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val auth       = FirebaseAuth.getInstance()
    val user       = auth.currentUser
    val loginPhone = user?.phoneNumber ?: ""

    val purple  = Color(0xFF7C3AED)
    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)

    var displayName by remember { mutableStateOf("") }
    var bio         by remember { mutableStateOf("") }
    var photoUrl    by remember { mutableStateOf("") }

    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        try {
            val doc = Firebase.firestore.collection("users").document(uid).get().await()
            displayName = doc.getString("name") ?: doc.getString("displayName") ?: ""
            bio         = doc.getString("bio") ?: doc.getString("about") ?: ""
            photoUrl    = doc.getString("photoUrl") ?: ""
        } catch (_: Exception) {}
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
                actions = {
                    // ✏️ Edit icon → goes to EditProfileScreen (edit_profile route)
                    IconButton(onClick = { navController.navigate("edit_profile") }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Avatar ──────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(purple, Color(0xFF06B6D4))))
                        .border(3.dp, purple, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = displayName.firstOrNull()?.uppercase() ?: "?",
                        fontSize   = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text       = displayName.ifBlank { "Set your name" },
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
                Spacer(Modifier.height(4.dp))
                if (bio.isNotBlank()) {
                    Text(bio, fontSize = 14.sp, color = Color(0xFF94A3B8))
                }
                Spacer(Modifier.height(24.dp))
            }

            // ── Phone number ────────────────────────────────────────────────
            item {
                ProfileInfoCard(
                    icon    = Icons.Default.Phone,
                    label   = "Phone Number",
                    value   = loginPhone.ifBlank { "Not available" },
                    surface = surface,
                    purple  = purple
                )
                Spacer(Modifier.height(10.dp))
            }

            // ── Edit Profile button ─────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = { navController.navigate("edit_profile") },  // ← fixed route
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = purple),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Profile", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    label  : String,
    value  : String,
    surface: Color,
    purple : Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = purple, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
            Text(value, fontSize = 15.sp, color = Color.White,       fontWeight = FontWeight.SemiBold)
        }
    }
}