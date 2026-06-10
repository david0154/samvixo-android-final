package com.nexuzy.samvixo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    navController: NavController,
    vm: BackupViewModel = viewModel(
        factory = BackupViewModel.Factory(LocalContext.current)
    )
) {
    val state   by vm.state.collectAsState()
    val context  = LocalContext.current
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var hexKey   by remember { mutableStateOf("") }
    var tab      by remember { mutableIntStateOf(0) } // 0=Backup, 1=Restore

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vm.onSignInResult(result)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Backup") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            // ── Drive status card ────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = if (state.isConnected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = if (state.isConnected) Icons.Default.Cloud else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint               = if (state.isConnected) MaterialTheme.colorScheme.primary
                                             else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.isConnected) "✅ Google Drive Connected" else "Google Drive Not Connected",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.lastBackupTime != "Never") {
                            Text(
                                "Last backup: ${state.lastBackupTime}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (state.keyFingerprint.isNotBlank()) {
                            Text(
                                "Key ID: ${state.keyFingerprint}",
                                style      = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color      = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (!state.isConnected) {
                        TextButton(onClick = {
                            signInLauncher.launch(vm.getSignInIntent())
                        }) { Text("Connect") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Your chats are encrypted with AES-256-GCM using your password before being stored in " +
                "YOUR Google Drive. Samvixo never stores or sees your messages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // ── Tab: Backup / Restore ────────────────────────────────────────
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }) {
                    Text("☁️ Backup", modifier = Modifier.padding(vertical = 12.dp))
                }
                Tab(selected = tab == 1, onClick = { tab = 1 }) {
                    Text("🔄 Restore", modifier = Modifier.padding(vertical = 12.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            if (tab == 0) {
            // ─── BACKUP TAB ───────────────────────────────────────────────────

                OutlinedTextField(
                    value                = password,
                    onValueChange        = { password = it },
                    label                = { Text("Backup Password (min 8 characters)") },
                    visualTransformation = if (showPassword) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier   = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled    = !state.isLoading
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    "💡 Remember this password — you need it to restore on a new device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick  = { vm.backupNow(password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !state.isLoading && state.isConnected && password.isNotBlank()
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Back Up Now (Encrypted)")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick  = { vm.exportBackupKey(password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !state.isLoading && password.isNotBlank()
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export 64-Digit Backup Key")
                }

                if (state.showKeyExport && state.exportedKey.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔑 Your Backup Key", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Backup Key", state.exportedKey))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            SelectionContainer {
                                Text(
                                    text       = state.exportedKey.chunked(8).joinToString(" "),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 13.sp,
                                    modifier   = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "⚠️ Save this key somewhere safe. It will NOT be shown again.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(6.dp))
                            TextButton(
                                onClick  = { vm.hideKeyExport() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("I've saved my key — Hide it") }
                        }
                    }
                }

            } else {
            // ─── RESTORE TAB ─────────────────────────────────────────────────

                Text(
                    "Restore your chats to this device from Google Drive.",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(16.dp))

                Text("Option A — Restore with password", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value                = password,
                    onValueChange        = { password = it },
                    label                = { Text("Backup Password") },
                    visualTransformation = if (showPassword) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier   = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled    = !state.isLoading
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = { vm.restoreWithPassword(password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !state.isLoading && password.isNotBlank() && state.isConnected
                ) {
                    Text("Restore with Password")
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text("Option B — Restore with 64-digit backup key", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "If you exported your backup key, paste it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value         = hexKey,
                    onValueChange = { hexKey = it },
                    label         = { Text("64-Digit Backup Key") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    enabled       = !state.isLoading,
                    placeholder   = { Text("Paste your hex key here…", fontFamily = FontFamily.Monospace) },
                    textStyle     = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = { vm.restoreWithHexKey(hexKey) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !state.isLoading && hexKey.trim().length == 64 && state.isConnected
                ) {
                    Text("Restore with Backup Key")
                }
            }

            // ── Disconnect ────────────────────────────────────────────────────
            if (state.isConnected) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick  = { vm.disconnectDrive() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !state.isLoading
                ) {
                    Text("Disconnect Google Drive", color = MaterialTheme.colorScheme.error)
                }
            }

            // ── Status message ─────────────────────────────────────────────────
            state.message?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = when {
                            msg.startsWith("✅") -> MaterialTheme.colorScheme.primaryContainer
                            msg.startsWith("❌") -> MaterialTheme.colorScheme.errorContainer
                            else               -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(
                        text     = msg,
                        modifier = Modifier.padding(14.dp),
                        style    = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
