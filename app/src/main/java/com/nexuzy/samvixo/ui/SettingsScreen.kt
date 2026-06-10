package com.nexuzy.samvixo.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nexuzy.samvixo.ui.navigation.Screen
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val auth    = FirebaseAuth.getInstance()
    val user    = auth.currentUser

    var displayName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var photoUrl    by remember { mutableStateOf("") }
    var about       by remember { mutableStateOf("") }

    LaunchedEffect(user?.uid) {
        user ?: return@LaunchedEffect
        try {
            val doc = Firebase.firestore.collection("users").document(user.uid).get().await()
            displayName = doc.getString("displayName") ?: ""
            phoneNumber = doc.getString("phoneNumber") ?: user.phoneNumber ?: ""
            photoUrl    = doc.getString("photoUrl") ?: ""
            about       = doc.getString("about") ?: ""
        } catch (_: Exception) {}
    }

    val purple  = Color(0xFF7C3AED)
    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)
    val divider = Color(0xFF2A2A3A)

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        },
        containerColor = bg
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // ─────────────────── Profile card ───────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surface)
                        .clickable { navController.navigate(Screen.EditProfile.route) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (photoUrl.isNotEmpty()) {
                        AsyncImage(
                            model              = photoUrl,
                            contentDescription = "Avatar",
                            modifier           = Modifier.size(64.dp).clip(CircleShape),
                            contentScale       = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier         = Modifier.size(64.dp).background(purple, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                displayName.firstOrNull()?.uppercase() ?: "S",
                                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(displayName.ifBlank { "Samvixo User" },
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(phoneNumber.ifBlank { "No number" },
                            color = Color(0xFF94A3B8), fontSize = 13.sp)
                        if (about.isNotEmpty())
                            Text(about, color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF64748B))
                }
                Spacer(Modifier.height(8.dp))
            }

            // ─────────────────── ACCOUNT ────────────────────────────────────
            item { SettSectionLabel("Account") }
            item {
                SettCard(surface) {
                    SettNavRow(Icons.Default.ManageAccounts, "Account Info", purple) {
                        navController.navigate(Screen.Account.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.Person, "Edit Profile", purple) {
                        navController.navigate(Screen.EditProfile.route)
                    }
                    HorizontalDivider(color = divider)
                    // FIX: navigate to real LinkedDevicesScreen (was showing Coming Soon dialog)
                    SettNavRow(Icons.Default.Devices, "Linked Devices & Web Login", purple) {
                        navController.navigate(Screen.LinkedDevices.route)
                    }
                }
            }

            // ─────────────────── NOTIFICATIONS ──────────────────────────────
            item { SettSectionLabel("Notifications") }
            item {
                SettCard(surface) {
                    SettNavRow(Icons.Default.Notifications, "Notification Settings", purple) {
                        navController.navigate(Screen.NotifSettings.route)
                    }
                }
            }

            // ─────────────────── PRIVACY & SECURITY ─────────────────────────
            item { SettSectionLabel("Privacy & Security") }
            item {
                SettCard(surface) {
                    // FIX: navigate to full AppLockerScreen (was broken inline toggle)
                    SettNavRow(Icons.Default.Lock, "App Lock", purple) {
                        navController.navigate(Screen.AppLocker.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.PrivacyTip, "Privacy Settings", purple) {
                        navController.navigate(Screen.ProfileSettings.route)
                    }
                }
            }

            // ─────────────────── CHATS ──────────────────────────────────────
            item { SettSectionLabel("Chats") }
            item {
                SettCard(surface) {
                    SettNavRow(Icons.Default.Storage, "Storage & Data", purple) {
                        navController.navigate(Screen.StorageData.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.Wallpaper, "Chat Wallpaper", purple) {
                        navController.navigate(Screen.WallpaperPicker.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.Backup, "Google Drive Backup", purple) {
                        navController.navigate(Screen.Backup.route)
                    }
                }
            }

            // ─────────────────── AI ─────────────────────────────────────────
            item { SettSectionLabel("AI") }
            item {
                SettCard(surface) {
                    SettNavRow(Icons.Default.AutoAwesome, "Devil AI Chat", purple) {
                        navController.navigate(Screen.AI.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.Note, "AI Notes", purple) {
                        navController.navigate(Screen.AINotes.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.Psychology, "AI Knowledge Vault", purple) {
                        navController.navigate(Screen.AIVault.route)
                    }
                }
            }

            // ─────────────────── GENERAL ────────────────────────────────────
            item { SettSectionLabel("General") }
            item {
                SettCard(surface) {
                    SettNavRow(Icons.Default.Language, "App Language", purple) {
                        navController.navigate(Screen.AppLanguage.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.WbSunny, "Weather", purple) {
                        navController.navigate(Screen.Weather.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.WifiOff, "Emergency Mesh Mode", purple) {
                        navController.navigate(Screen.EmergencyMesh.route)
                    }
                }
            }

            // ─────────────────── HELP ───────────────────────────────────────
            item { SettSectionLabel("Help") }
            item {
                SettCard(surface) {
                    SettNavRow(Icons.Default.Mail, "Contact Us", purple) {
                        navController.navigate(Screen.ContactUs.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.Share, "Invite a Friend", purple) {
                        navController.navigate(Screen.InviteFriend.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.Info, "About Samvixo", purple) {
                        navController.navigate(Screen.AboutUs.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.Description, "Terms & Conditions", purple) {
                        navController.navigate(Screen.TermsConditions.route)
                    }
                    HorizontalDivider(color = divider)
                    SettNavRow(Icons.Default.Shield, "Privacy Policy", purple) {
                        navController.navigate(Screen.PrivacyPolicy.route)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Reusable composable helpers ───────────────────────────────────────────────

@Composable
fun SettSectionLabel(text: String) {
    Text(
        text          = text.uppercase(),
        color         = Color(0xFF7C3AED),
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier      = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun SettCard(surface: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(0.dp),
        colors   = CardDefaults.cardColors(containerColor = surface),
        content  = { Column(content = content) }
    )
}

@Composable
fun SettRow(icon: ImageVector, label: String, value: String, tint: Color) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color(0xFF94A3B8), fontSize = 12.sp)
            Text(value, color = Color.White,        fontSize = 14.sp)
        }
    }
}

@Composable
fun SettNavRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF64748B))
    }
}
