package com.nexuzy.samvixo.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
// AccountScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(navController: NavController) {
    val auth             = FirebaseAuth.getInstance()
    val db               = FirebaseFirestore.getInstance()
    val scope            = rememberCoroutineScope()
    val me               = auth.currentUser
    var twoFa            by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(me?.uid) {
        me?.uid?.let { uid ->
            try {
                val snap = db.collection("users").document(uid).get().await()
                twoFa = snap.getBoolean("twoFaEnabled") ?: false
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent   = { Text("Phone Number") },
                supportingContent = { Text(me?.phoneNumber ?: "Not available") },
                leadingContent    = { Icon(Icons.Default.Phone, null) },
                trailingContent   = {
                    TextButton(onClick = { }) { Text("Change") }
                }
            )
            HorizontalDivider(thickness = 0.5.dp)
            ListItem(
                headlineContent   = { Text("Two-Factor Authentication") },
                supportingContent = { Text("Extra security via authenticator app") },
                leadingContent    = { Icon(Icons.Default.Security, null) },
                trailingContent   = {
                    Switch(
                        checked         = twoFa,
                        onCheckedChange = { v ->
                            twoFa = v
                            scope.launch {
                                me?.uid?.let { uid ->
                                    db.collection("users").document(uid)
                                        .update("twoFaEnabled", v).await()
                                }
                            }
                        }
                    )
                }
            )
            HorizontalDivider(thickness = 0.5.dp)
            ListItem(
                headlineContent   = { Text("Security Notifications") },
                supportingContent = { Text("Alert when account is accessed from new device") },
                leadingContent    = { Icon(Icons.Default.NotificationsActive, null) }
            )
            HorizontalDivider(thickness = 0.5.dp)
            Spacer(Modifier.height(16.dp))
            ListItem(
                headlineContent   = { Text("Delete Account", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Permanently delete your Samvixo account and all data") },
                leadingContent    = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                modifier          = Modifier.clickable { showDeleteDialog = true }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Delete Account?") },
            text    = { Text("This will permanently delete your account, messages, and profile. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            try {
                                me?.uid?.let { uid -> db.collection("users").document(uid).delete().await() }
                                me?.delete()?.await()
                                auth.signOut()
                            } catch (_: Exception) {}
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PrivacyScreen  (Last Seen + Read Receipts + Blocked Contacts)
// ─────────────────────────────────────────────────────────────────────────────
data class BlockedUser(val uid: String, val name: String, val phone: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(navController: NavController) {
    val db    = FirebaseFirestore.getInstance()
    val me    = FirebaseAuth.getInstance().currentUser
    val scope = rememberCoroutineScope()

    var lastSeenOption    by remember { mutableStateOf("Everyone") }
    var readReceipts      by remember { mutableStateOf(true) }
    var profilePhotoVisib by remember { mutableStateOf("Everyone") }
    var onlineStatus      by remember { mutableStateOf(true) }
    var disappearTimer    by remember { mutableStateOf("Off") }

    // Blocked contacts state
    var blockedUsers  by remember { mutableStateOf<List<BlockedUser>>(emptyList()) }
    var isLoadingBlock by remember { mutableStateOf(true) }
    var showUnblockDialog by remember { mutableStateOf<BlockedUser?>(null) }

    val visibilityOptions = listOf("Everyone", "My Contacts", "Nobody")
    val disappearOptions  = listOf("Off", "24 hours", "7 days", "90 days")

    fun save(field: String, value: Any) = scope.launch {
        me?.uid?.let { db.collection("users").document(it).update(field, value) }
    }

    // Load privacy prefs + blocked list from Firestore
    LaunchedEffect(me?.uid) {
        me?.uid?.let { uid ->
            try {
                val doc = db.collection("users").document(uid).get().await()
                lastSeenOption    = doc.getString("privacy.lastSeen")     ?: "Everyone"
                profilePhotoVisib = doc.getString("privacy.profilePhoto") ?: "Everyone"
                readReceipts      = doc.getBoolean("privacy.readReceipts") ?: true
                onlineStatus      = doc.getBoolean("privacy.showOnline")   ?: true
                disappearTimer    = doc.getString("privacy.disappearTimer") ?: "Off"

                // Load blocked UIDs
                @Suppress("UNCHECKED_CAST")
                val blockedUids = (doc.get("blockedUsers") as? List<String>) ?: emptyList()
                val loaded = mutableListOf<BlockedUser>()
                for (bUid in blockedUids) {
                    try {
                        val bDoc = db.collection("users").document(bUid).get().await()
                        loaded.add(
                            BlockedUser(
                                uid   = bUid,
                                name  = bDoc.getString("displayName") ?: "Unknown",
                                phone = bDoc.getString("phoneNumber") ?: ""
                            )
                        )
                    } catch (_: Exception) {
                        loaded.add(BlockedUser(bUid, "Unknown", ""))
                    }
                }
                blockedUsers  = loaded
            } catch (_: Exception) {}
            isLoadingBlock = false
        }
    }

    fun unblockUser(user: BlockedUser) = scope.launch {
        me?.uid?.let { uid ->
            try {
                db.collection("users").document(uid)
                    .update("blockedUsers", FieldValue.arrayRemove(user.uid)).await()
                blockedUsers = blockedUsers.filter { it.uid != user.uid }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Who can see personal info ──
            item {
                SectionHeader("WHO CAN SEE MY PERSONAL INFO")
                PrivacyOptionRow("Last Seen & Online", lastSeenOption, visibilityOptions) {
                    lastSeenOption = it; save("privacy.lastSeen", it)
                }
                PrivacyOptionRow("Profile Photo", profilePhotoVisib, visibilityOptions) {
                    profilePhotoVisib = it; save("privacy.profilePhoto", it)
                }
                HorizontalDivider(thickness = 0.5.dp)
            }

            // ── Messages ──
            item {
                SectionHeader("MESSAGES")
                ListItem(
                    headlineContent   = { Text("Read Receipts") },
                    supportingContent = { Text("Show blue ticks when you've read messages") },
                    leadingContent    = { Icon(Icons.Default.DoneAll, null) },
                    trailingContent   = {
                        Switch(checked = readReceipts, onCheckedChange = { readReceipts = it; save("privacy.readReceipts", it) })
                    }
                )
                ListItem(
                    headlineContent   = { Text("Online Status") },
                    supportingContent = { Text("Show when you're active") },
                    leadingContent    = { Icon(Icons.Default.Circle, null) },
                    trailingContent   = {
                        Switch(checked = onlineStatus, onCheckedChange = { onlineStatus = it; save("privacy.showOnline", it) })
                    }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }

            // ── Disappearing messages ──
            item {
                SectionHeader("DISAPPEARING MESSAGES")
                PrivacyOptionRow("Default Timer", disappearTimer, disappearOptions) {
                    disappearTimer = it; save("privacy.disappearTimer", it)
                }
                HorizontalDivider(thickness = 0.5.dp)
            }

            // ── Blocked contacts ───────────────────────────────────
            item {
                SectionHeader("BLOCKED CONTACTS")
            }

            if (isLoadingBlock) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                }
            } else if (blockedUsers.isEmpty()) {
                item {
                    ListItem(
                        headlineContent   = { Text("No blocked contacts", color = Color(0xFF94A3B8)) },
                        supportingContent = { Text("Block someone from their chat profile") },
                        leadingContent    = { Icon(Icons.Default.Block, null, tint = Color(0xFF64748B)) }
                    )
                }
            } else {
                items(blockedUsers) { user ->
                    ListItem(
                        headlineContent   = { Text(user.name) },
                        supportingContent = { Text(user.phone.ifBlank { user.uid }) },
                        leadingContent    = {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Block, null,
                                    modifier = Modifier.padding(8.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        trailingContent   = {
                            TextButton(
                                onClick = { showUnblockDialog = user },
                                colors  = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("Unblock") }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Unblock confirmation dialog
    showUnblockDialog?.let { user ->
        AlertDialog(
            onDismissRequest = { showUnblockDialog = null },
            title   = { Text("Unblock ${user.name}?") },
            text    = { Text("${user.name} will be able to send you messages and see your profile again.") },
            confirmButton = {
                TextButton(onClick = {
                    unblockUser(user)
                    showUnblockDialog = null
                }) { Text("Unblock") }
            },
            dismissButton = {
                TextButton(onClick = { showUnblockDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text       = text,
        modifier   = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        fontSize   = 11.sp,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
fun PrivacyOptionRow(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent   = { Text(label) },
        supportingContent = { Text(selected) },
        modifier          = Modifier.clickable { expanded = true }
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { opt ->
            DropdownMenuItem(
                text    = { Text(opt) },
                onClick = { onSelect(opt); expanded = false },
                leadingIcon = { if (opt == selected) Icon(Icons.Default.Check, null) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AppLockerScreen  (PIN / pattern lock for app)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockerScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("samvixo_prefs", Context.MODE_PRIVATE) }
    var lockerEnabled  by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var biometricEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_biometric", true)) }
    var savedPin       by remember { mutableStateOf(prefs.getString("app_lock_pin", "") ?: "") }
    var pinInput       by remember { mutableStateOf("") }
    var confirmPin     by remember { mutableStateOf("") }
    var pinStep        by remember { mutableStateOf(0) } // 0=idle,1=enter,2=confirm
    var pinError       by remember { mutableStateOf("") }
    val purple = Color(0xFF7C3AED)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Lock") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Enable toggle
            ListItem(
                headlineContent   = { Text("Enable App Lock") },
                supportingContent = { Text("Require PIN or fingerprint to open Samvixo") },
                leadingContent    = { Icon(Icons.Default.Lock, null) },
                trailingContent   = {
                    Switch(
                        checked         = lockerEnabled,
                        onCheckedChange = { v ->
                            if (v && savedPin.isBlank()) {
                                pinStep = 1  // must set PIN first
                            } else {
                                lockerEnabled = v
                                prefs.edit().putBoolean("app_lock_enabled", v).apply()
                            }
                        }
                    )
                }
            )
            HorizontalDivider(thickness = 0.5.dp)

            if (lockerEnabled || savedPin.isNotBlank()) {
                // ── Biometric toggle
                ListItem(
                    headlineContent   = { Text("Use Fingerprint / Biometric") },
                    supportingContent = { Text("Allow unlocking with fingerprint") },
                    leadingContent    = { Icon(Icons.Default.Fingerprint, null) },
                    trailingContent   = {
                        Switch(
                            checked         = biometricEnabled,
                            onCheckedChange = { v ->
                                biometricEnabled = v
                                prefs.edit().putBoolean("app_lock_biometric", v).apply()
                            }
                        )
                    }
                )
                HorizontalDivider(thickness = 0.5.dp)

                // ── Change PIN
                ListItem(
                    headlineContent   = { Text("Change PIN") },
                    supportingContent = { Text(if (savedPin.isBlank()) "Set a 4-digit PIN" else "PIN is set ✓") },
                    leadingContent    = { Icon(Icons.Default.Pin, null) },
                    modifier          = Modifier.clickable { pinStep = 1; pinInput = ""; confirmPin = ""; pinError = "" }
                )
            }

            // ── PIN entry flow
            if (pinStep > 0) {
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    if (pinStep == 1) "Enter new 4-digit PIN" else "Confirm PIN",
                    modifier   = Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = if (pinStep == 1) pinInput else confirmPin,
                    onValueChange = { v ->
                        if (v.length <= 4 && v.all { it.isDigit() }) {
                            if (pinStep == 1) pinInput = v else confirmPin = v
                        }
                    },
                    label         = { Text(if (pinStep == 1) "New PIN" else "Confirm PIN") },
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine    = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    )
                )
                if (pinError.isNotBlank()) {
                    Text(pinError, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { pinStep = 0; pinInput = ""; confirmPin = ""; pinError = "" }) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (pinStep == 1) {
                                if (pinInput.length < 4) { pinError = "PIN must be 4 digits"; return@Button }
                                pinStep = 2
                                pinError = ""
                            } else {
                                if (confirmPin != pinInput) { pinError = "PINs don't match"; return@Button }
                                savedPin = pinInput
                                prefs.edit().putString("app_lock_pin", pinInput).apply()
                                lockerEnabled = true
                                prefs.edit().putBoolean("app_lock_enabled", true).apply()
                                pinStep = 0; pinInput = ""; confirmPin = ""; pinError = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = purple)
                    ) { Text(if (pinStep == 1) "Next" else "Save PIN") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AvatarScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Avatar") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Face, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("Avatar Builder", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Create your personal avatar or upload a profile photo.", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { }) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Upload Profile Photo")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { }) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("Create Cartoon Avatar")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NotifSettingsScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifSettingsScreen(navController: NavController) {
    val context      = LocalContext.current
    val prefs        = remember { context.getSharedPreferences("samvixo_prefs", Context.MODE_PRIVATE) }
    var msgVibrate   by remember { mutableStateOf(prefs.getBoolean("notif_msg_vibrate", true)) }
    var grpVibrate   by remember { mutableStateOf(prefs.getBoolean("notif_grp_vibrate", true)) }
    var callRingtone by remember { mutableStateOf(prefs.getBoolean("notif_call_ring",  true)) }
    var showPreview  by remember { mutableStateOf(prefs.getBoolean("notif_preview",    true)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                SectionHeader("MESSAGES")
                ListItem(
                    headlineContent = { Text("Message Vibration") },
                    trailingContent = {
                        Switch(checked = msgVibrate, onCheckedChange = {
                            msgVibrate = it; prefs.edit().putBoolean("notif_msg_vibrate", it).apply()
                        })
                    }
                )
                ListItem(
                    headlineContent   = { Text("Message Notification Tone") },
                    supportingContent = { Text("Default") },
                    modifier          = Modifier.clickable {
                        context.startActivity(Intent(AndroidSettings.ACTION_SOUND_SETTINGS))
                    }
                )
                ListItem(
                    headlineContent   = { Text("Show Message Preview") },
                    supportingContent = { Text("Show content in notifications") },
                    trailingContent   = {
                        Switch(checked = showPreview, onCheckedChange = {
                            showPreview = it; prefs.edit().putBoolean("notif_preview", it).apply()
                        })
                    }
                )
                HorizontalDivider(thickness = 0.5.dp)
                SectionHeader("GROUPS")
                ListItem(
                    headlineContent = { Text("Group Vibration") },
                    trailingContent = {
                        Switch(checked = grpVibrate, onCheckedChange = {
                            grpVibrate = it; prefs.edit().putBoolean("notif_grp_vibrate", it).apply()
                        })
                    }
                )
                HorizontalDivider(thickness = 0.5.dp)
                SectionHeader("CALLS")
                ListItem(
                    headlineContent = { Text("Call Ringtone") },
                    trailingContent = {
                        Switch(checked = callRingtone, onCheckedChange = {
                            callRingtone = it; prefs.edit().putBoolean("notif_call_ring", it).apply()
                        })
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick  = {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                ) { Text("Open System Notification Settings") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StorageDataScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AutoDownloadItem(
    label: String, sub: String, checked: Boolean,
    prefs: android.content.SharedPreferences, key: String, set: (Boolean) -> Unit
) {
    ListItem(
        headlineContent   = { Text(label) },
        supportingContent = { Text(sub) },
        trailingContent   = {
            Switch(checked = checked, onCheckedChange = {
                set(it); prefs.edit().putBoolean(key, it).apply()
            })
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDataScreen(navController: NavController) {
    val context    = LocalContext.current
    val prefs      = remember { context.getSharedPreferences("samvixo_prefs", Context.MODE_PRIVATE) }
    var autoImages by remember { mutableStateOf(prefs.getBoolean("auto_dl_images", true)) }
    var autoVideos by remember { mutableStateOf(prefs.getBoolean("auto_dl_videos", false)) }
    var autoAudio  by remember { mutableStateOf(prefs.getBoolean("auto_dl_audio",  true)) }
    var autoDocs   by remember { mutableStateOf(prefs.getBoolean("auto_dl_docs",   true)) }

    val cacheSize = remember {
        try {
            val bytes = context.cacheDir.walkTopDown().sumOf { it.length() }
            when {
                bytes < 1024    -> "${bytes} B"
                bytes < 1048576 -> "${bytes / 1024} KB"
                else            -> "${bytes / 1048576} MB"
            }
        } catch (_: Exception) { "Unknown" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage and Data") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                SectionHeader("STORAGE")
                ListItem(
                    headlineContent   = { Text("Cache Size") },
                    supportingContent = { Text(cacheSize) },
                    leadingContent    = { Icon(Icons.Default.Storage, null) },
                    trailingContent   = {
                        TextButton(onClick = { context.cacheDir.deleteRecursively() }) { Text("Clear") }
                    }
                )
                HorizontalDivider(thickness = 0.5.dp)
                SectionHeader("AUTO-DOWNLOAD")
                AutoDownloadItem("Images",    "Auto-download images over Wi-Fi",   autoImages, prefs, "auto_dl_images") { autoImages = it }
                AutoDownloadItem("Videos",    "Auto-download videos over Wi-Fi",   autoVideos, prefs, "auto_dl_videos") { autoVideos = it }
                AutoDownloadItem("Audio",     "Auto-download voice notes",          autoAudio,  prefs, "auto_dl_audio")  { autoAudio  = it }
                AutoDownloadItem("Documents", "Auto-download documents",            autoDocs,   prefs, "auto_dl_docs")   { autoDocs   = it }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AppLanguageScreen
// ─────────────────────────────────────────────────────────────────────────────
val LANGUAGES = listOf(
    "🇮🇳 Hindi", "🇮🇳 Bengali", "🇮🇳 Telugu", "🇮🇳 Tamil",
    "🇮🇳 Marathi", "🇮🇳 Gujarati", "🇮🇳 Kannada", "🇮🇳 Malayalam",
    "🇮🇳 Punjabi", "🇮🇳 Odia", "🇮🇳 Assamese", "🇮🇳 Urdu",
    "🇬🇧 English"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLanguageScreen(navController: NavController) {
    val context  = LocalContext.current
    val prefs    = remember { context.getSharedPreferences("samvixo_prefs", Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf(prefs.getString("app_language", "🇬🇧 English") ?: "🇬🇧 English") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Language") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(LANGUAGES.size) { i ->
                val lang = LANGUAGES[i]
                ListItem(
                    headlineContent = { Text(lang) },
                    trailingContent = {
                        if (lang == selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.selectable(
                        selected = lang == selected,
                        onClick  = { selected = lang; prefs.edit().putString("app_language", lang).apply() }
                    )
                )
                if (i < LANGUAGES.lastIndex) HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EmergencyMeshScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyMeshScreen(navController: NavController) {
    val context     = LocalContext.current
    val prefs       = remember { context.getSharedPreferences("samvixo_prefs", Context.MODE_PRIVATE) }
    var meshEnabled by remember { mutableStateOf(prefs.getBoolean("mesh_enabled", false)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Mesh Mode") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Icon(Icons.Default.WifiOff, null,
                modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Emergency Mesh Mode", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "When enabled, Samvixo creates a peer-to-peer network using Bluetooth Low Energy " +
                "and Wi-Fi Direct so you can send messages even without internet or cellular signal.",
                fontSize = 14.sp, color = Color.Gray, lineHeight = 22.sp
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable Mesh Mode", fontWeight = FontWeight.SemiBold)
                    Text("Requires Bluetooth + Location permission", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked         = meshEnabled,
                    onCheckedChange = {
                        meshEnabled = it
                        prefs.edit().putBoolean("mesh_enabled", it).apply()
                    }
                )
            }
            if (meshEnabled) {
                Spacer(Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Mesh Active", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Scanning for nearby Samvixo devices…", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// InviteFriendScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteFriendScreen(navController: NavController) {
    val context   = LocalContext.current
    val shareText = "Hey! I'm using Samvixo — a next-gen messaging app with AI, " +
        "end-to-end encryption, and offline messaging. Download it: " +
        "https://play.google.com/store/apps/details?id=com.nexuzy.samvixo"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite a Friend") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Share, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("Share Samvixo", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Invite friends and family to join Samvixo!",
                fontSize = 14.sp, color = Color.Gray
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick  = {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }, "Share Samvixo via"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Share App Link", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
