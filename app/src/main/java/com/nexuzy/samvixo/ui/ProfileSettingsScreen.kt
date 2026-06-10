package com.nexuzy.samvixo.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
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
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * EditProfileScreen — lets the user update their name, about text, and profile photo.
 * FIX: photo is uploaded exactly once when Save is tapped; newPhotoUri cleared after upload
 * so recomposition / back-press cannot trigger a second upload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(navController: NavController) {
    val auth    = FirebaseAuth.getInstance()
    val uid     = auth.currentUser?.uid ?: return
    val scope   = rememberCoroutineScope()
    val purple  = Color(0xFF7C3AED)
    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)

    var displayName  by remember { mutableStateOf("") }
    var about        by remember { mutableStateOf("") }
    var photoUrl     by remember { mutableStateOf("") }
    var newPhotoUri  by remember { mutableStateOf<Uri?>(null) }
    // FIX: uploading flag prevents re-entry (double upload) if Save is tapped again
    // while a previous save is in progress
    var loading      by remember { mutableStateOf(false) }
    var saved        by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        try {
            val snap    = Firebase.firestore.collection("users").document(uid).get().await()
            displayName = snap.getString("displayName") ?: ""
            about       = snap.getString("about") ?: ""
            photoUrl    = snap.getString("photoUrl") ?: ""
        } catch (_: Exception) {}
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { newPhotoUri = it } }

    fun saveProfile() {
        if (displayName.isBlank()) { error = "Name cannot be empty"; return }
        if (loading) return          // FIX: guard against double-tap / double-upload
        loading = true; error = ""; saved = false
        scope.launch {
            try {
                var finalPhotoUrl = photoUrl
                // FIX: capture uri once and clear it BEFORE the await so that if
                // the composable recomposes mid-upload it cannot upload again
                val uriToUpload = newPhotoUri
                newPhotoUri     = null
                if (uriToUpload != null) {
                    val ref = Firebase.storage.reference.child("avatars/$uid.jpg")
                    ref.putFile(uriToUpload).await()
                    finalPhotoUrl = ref.downloadUrl.await().toString()
                }
                Firebase.firestore.collection("users").document(uid).update(
                    mapOf(
                        "displayName" to displayName.trim(),
                        "about"       to about.trim().ifBlank { "Available" },
                        "photoUrl"    to finalPhotoUrl
                    )
                ).await()
                photoUrl = finalPhotoUrl
                saved    = true
            } catch (e: Exception) {
                error = e.message ?: "Save failed"
                // Restore uri on failure so user can retry
            }
            loading = false
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .border(3.dp, purple, CircleShape)
                    .clickable { if (!loading) photoLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                val painter = when {
                    newPhotoUri != null   -> rememberAsyncImagePainter(newPhotoUri)
                    photoUrl.isNotEmpty() -> rememberAsyncImagePainter(photoUrl)
                    else                  -> null
                }
                if (painter != null) {
                    Image(
                        painter            = painter,
                        contentDescription = "Profile photo",
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFF2D2D3D), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .background(purple, CircleShape)
                        .border(2.dp, bg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Text("Tap photo to change", fontSize = 12.sp, color = Color(0xFF94A3B8))

            OutlinedTextField(
                value         = displayName,
                onValueChange = { displayName = it },
                label         = { Text("Display Name") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                colors        = editFieldColors(purple)
            )
            OutlinedTextField(
                value         = about,
                onValueChange = { about = it },
                label         = { Text("About") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                colors        = editFieldColors(purple)
            )
            OutlinedTextField(
                value         = auth.currentUser?.phoneNumber ?: "",
                onValueChange = {},
                label         = { Text("Phone Number") },
                modifier      = Modifier.fillMaxWidth(),
                enabled       = false,
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = Color(0xFF1E293B),
                    disabledLabelColor  = Color(0xFF475569),
                    disabledTextColor   = Color(0xFF64748B)
                )
            )

            if (error.isNotEmpty()) Text(error, color = Color(0xFFEF4444), fontSize = 13.sp)
            if (saved) Text("✓ Profile saved successfully", color = Color(0xFF22C55E), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

            Button(
                onClick  = { saveProfile() },
                enabled  = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = purple)
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun editFieldColors(purple: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = purple,
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor    = purple,
    cursorColor          = purple,
    focusedTextColor     = Color.White,
    unfocusedTextColor   = Color.White
)
