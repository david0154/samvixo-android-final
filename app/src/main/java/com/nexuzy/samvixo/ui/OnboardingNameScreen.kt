package com.nexuzy.samvixo.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─── OnboardingNameScreen ─────────────────────────────────────────────────────────
// Shown after OTP verification for NEW users only
// Flow: OTP verify → OnboardingNameScreen → MainScreen
// User sets: Name (required), About (default "Available"), Profile photo (optional)
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingNameScreen(navController: NavController) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val auth      = FirebaseAuth.getInstance()
    val db        = FirebaseFirestore.getInstance()
    val storage   = FirebaseStorage.getInstance()
    val me        = auth.currentUser

    var name      by remember { mutableStateOf("") }
    var about     by remember { mutableStateOf("Available") }
    var photoUri  by remember { mutableStateOf<Uri?>(null) }
    var loading   by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> photoUri = uri }

    val gradient = Brush.verticalGradient(listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.background
    ))

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text("Welcome to Samvixo!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text("Set up your profile to get started.", fontSize = 14.sp, color = Color.Gray)
            Spacer(Modifier.height(32.dp))

            // Profile photo picker
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { photoPicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(Icons.Default.Person, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Camera overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null,
                        tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Tap to add photo", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(24.dp))

            // Name field
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Your Name *") },
                placeholder   = { Text("Enter your full name") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                singleLine    = true,
                isError       = error.isNotEmpty() && name.isBlank()
            )
            Spacer(Modifier.height(12.dp))

            // About field
            OutlinedTextField(
                value         = about,
                onValueChange = { about = it },
                label         = { Text("About") },
                placeholder   = { Text("e.g. Available, Hey there!") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                singleLine    = true
            )
            Spacer(Modifier.height(8.dp))
            Text("Your phone number is your ID. Name & about are public.",
                fontSize = 12.sp, color = Color.Gray)

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (name.isBlank()) { error = "Name is required."; return@Button }
                    error = ""; loading = true
                    scope.launch {
                        try {
                            var photoUrl = ""
                            // Upload photo if selected
                            photoUri?.let { uri ->
                                val ref = storage.reference.child("profile_photos/${me?.uid}.jpg")
                                ref.putFile(uri).await()
                                photoUrl = ref.downloadUrl.await().toString()
                            }

                            // Update FirebaseAuth display name
                            me?.updateProfile(
                                UserProfileChangeRequest.Builder()
                                    .setDisplayName(name.trim())
                                    .apply { if (photoUrl.isNotEmpty()) setPhotoUri(Uri.parse(photoUrl)) }
                                    .build()
                            )?.await()

                            // Write Firestore user doc
                            db.collection("users").document(me?.uid ?: "").set(
                                mapOf(
                                    "uid"         to (me?.uid ?: ""),
                                    "name"        to name.trim(),
                                    "bio"         to about.trim(),
                                    "phoneNumber" to (me?.phoneNumber ?: ""),
                                    "photoUrl"    to photoUrl,
                                    "trustScore"  to 100,
                                    "createdAt"   to FieldValue.serverTimestamp(),
                                    "restricted"  to false,
                                    "banned"      to false
                                )
                            ).await()

                            navController.navigate("main") {
                                popUpTo("onboarding_name") { inclusive = true }
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Failed to save profile."
                        }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = !loading,
                shape    = RoundedCornerShape(12.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Continue →", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
