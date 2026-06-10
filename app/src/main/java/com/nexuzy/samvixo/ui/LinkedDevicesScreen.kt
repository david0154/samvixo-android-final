package com.nexuzy.samvixo.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

// CRITICAL FIX: Use correct RTDB asia-southeast1 region URL
// Logcat: "Database lives in a different region. Please change your database URL to
//          https://samvixo-default-rtdb.asia-southeast1.firebasedatabase.app"
private val rtdb by lazy {
    Firebase.database("https://samvixo-default-rtdb.asia-southeast1.firebasedatabase.app")
}

data class LinkedDevice(
    val deviceId: String = "",
    val deviceName: String = "",
    val platform: String = "Android",
    val linkedAt: Long = 0L,
    val lastSeen: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesScreen(onBack: () -> Unit) {
    val me = Firebase.auth.currentUser
    val db = Firebase.firestore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var linkedDevices by remember { mutableStateOf<List<LinkedDevice>>(emptyList()) }
    var showQrDialog by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrSessionToken by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isWaitingForScan by remember { mutableStateOf(false) }

    // Load existing linked devices
    LaunchedEffect(me?.uid) {
        if (me == null) return@LaunchedEffect
        db.collection("users").document(me.uid)
            .collection("linkedDevices")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    linkedDevices = snap.documents.mapNotNull { doc ->
                        doc.toObject(LinkedDevice::class.java)
                    }
                }
            }
    }

    // Generate QR for web login
    fun generateQrForWebLogin() {
        if (me == null) { statusMessage = "Please sign in first"; return }
        scope.launch {
            try {
                val token = UUID.randomUUID().toString().replace("-", "")
                qrSessionToken = token

                // Write pending session to correct RTDB region
                val sessionRef = rtdb.reference.child("webSessions").child(token)
                sessionRef.setValue(
                    mapOf(
                        "status" to "pending",
                        "createdAt" to System.currentTimeMillis(),
                        "expiresAt" to (System.currentTimeMillis() + 5 * 60 * 1000)
                    )
                ).await()

                // Generate QR bitmap
                val webUrl = "https://samvixo.web.app/auth?session=$token"
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(webUrl, BarcodeFormat.QR_CODE, 512, 512)
                val w = bitMatrix.width
                val h = bitMatrix.height
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                for (x in 0 until w) {
                    for (y in 0 until h) {
                        bmp.setPixel(
                            x, y,
                            if (bitMatrix.get(x, y)) android.graphics.Color.BLACK
                            else android.graphics.Color.WHITE
                        )
                    }
                }
                qrBitmap = bmp
                showQrDialog = true
                isWaitingForScan = true

                // Listen for web client scan confirmation
                sessionRef.child("status").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        when (snap.getValue(String::class.java)) {
                            "scanned" -> statusMessage = "Web client scanned — confirm on web page"
                            "confirmed" -> {
                                statusMessage = "✅ Web login confirmed!"
                                isWaitingForScan = false
                                scope.launch {
                                    val deviceId = UUID.randomUUID().toString()
                                    db.collection("users").document(me.uid)
                                        .collection("linkedDevices").document(deviceId)
                                        .set(
                                            LinkedDevice(
                                                deviceId = deviceId,
                                                deviceName = "Web Browser",
                                                platform = "Web",
                                                linkedAt = System.currentTimeMillis(),
                                                lastSeen = System.currentTimeMillis()
                                            )
                                        ).await()
                                }
                                sessionRef.removeEventListener(this)
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

                // Auto-expire after 5 minutes
                delay(5 * 60 * 1000L)
                if (isWaitingForScan) {
                    sessionRef.child("status").setValue("expired").await()
                    isWaitingForScan = false
                    statusMessage = "QR expired. Generate a new one."
                }
            } catch (e: WriterException) {
                statusMessage = "Failed to generate QR: ${e.message}"
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linked Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (statusMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            statusMessage.startsWith("✅") -> Color(0xFF1B5E20)
                            statusMessage.startsWith("❌") -> Color(0xFFB71C1C)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(12.dp),
                        color = Color.White
                    )
                }
            }

            // Show QR for web to scan
            Button(
                onClick = { generateQrForWebLogin() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Show QR Code for Web Login")
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Linked Devices (${linkedDevices.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            if (linkedDevices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DevicesOther,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No linked devices yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Open samvixo.web.app, then tap \"Show QR\" above",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(linkedDevices) { device ->
                        LinkedDeviceCard(
                            device = device,
                            onRemove = {
                                scope.launch {
                                    if (me != null) {
                                        db.collection("users").document(me.uid)
                                            .collection("linkedDevices")
                                            .document(device.deviceId)
                                            .delete().await()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // QR Code dialog
    if (showQrDialog && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false; isWaitingForScan = false },
            title = { Text("Scan on web browser") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(250.dp).padding(8.dp)
                    )
                    if (isWaitingForScan) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Waiting for web client...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false; isWaitingForScan = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun LinkedDeviceCard(device: LinkedDevice, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (device.platform == "Web") Icons.Default.Language else Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.deviceName, fontWeight = FontWeight.SemiBold)
                Text(
                    device.platform,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.LinkOff,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
