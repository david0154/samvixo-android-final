package com.nexuzy.samvixo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nexuzy.samvixo.utils.AutoCleanupManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class StatusItem(
    val statusId: String = "",
    val uid: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = "",
    val text: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "text",
    val timestamp: Long = 0L,
    val expiryTs: Long = 0L,
    val viewers: List<String> = emptyList()
)

data class StatusViewer(
    val uid: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val viewedAt: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    onCreateStatus: () -> Unit,
    onViewStatus: (String) -> Unit
) {
    val me = Firebase.auth.currentUser
    val db = Firebase.firestore
    val scope = rememberCoroutineScope()

    var myStatuses by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    var contactStatuses by remember { mutableStateOf<List<Pair<String, List<StatusItem>>>>(emptyList()) }
    var myPhotoUrl by remember { mutableStateOf("") }
    var selectedStatusForViewers by remember { mutableStateOf<StatusItem?>(null) }
    var viewers by remember { mutableStateOf<List<StatusViewer>>(emptyList()) }
    var showViewersSheet by remember { mutableStateOf(false) }
    var showMyStatusPreview by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf(0) }

    // Auto-cleanup expired statuses + load data
    LaunchedEffect(me?.uid) {
        if (me == null) return@LaunchedEffect
        AutoCleanupManager.deleteExpiredStatuses(me.uid)

        // Load my profile for photo
        db.collection("users").document(me.uid).get().addOnSuccessListener {
            myPhotoUrl = it.getString("photoUrl") ?: ""
        }

        // Load MY statuses
        db.collection("statuses")
            .whereEqualTo("uid", me.uid)
            .whereGreaterThan("expiryTs", System.currentTimeMillis())
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    myStatuses = snap.documents
                        .mapNotNull { it.toObject(StatusItem::class.java) }
                        .sortedBy { it.timestamp }
                }
            }

        // Load CONTACTS' statuses
        db.collection("statuses")
            .whereNotEqualTo("uid", me.uid)
            .whereGreaterThan("expiryTs", System.currentTimeMillis())
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val grouped = snap.documents
                        .mapNotNull { it.toObject(StatusItem::class.java) }
                        .filter { it.uid != me.uid }
                        .groupBy { it.uid }
                        .map { (uid, items) -> uid to items.sortedBy { it.timestamp } }
                    contactStatuses = grouped
                }
            }
    }

    // Load viewers for a selected status
    suspend fun loadViewers(status: StatusItem) {
        selectedStatusForViewers = status
        val viewerList = mutableListOf<StatusViewer>()
        status.viewers.forEach { viewerUid ->
            try {
                val userDoc = db.collection("users").document(viewerUid).get().await()
                val viewedAt = db.collection("statuses").document(status.statusId)
                    .collection("views").document(viewerUid).get().await()
                    .getLong("viewedAt") ?: 0L
                viewerList.add(
                    StatusViewer(
                        uid = viewerUid,
                        name = userDoc.getString("displayName") ?: "Unknown",
                        photoUrl = userDoc.getString("photoUrl") ?: "",
                        viewedAt = viewedAt
                    )
                )
            } catch (_: Exception) {}
        }
        viewers = viewerList.sortedByDescending { it.viewedAt }
        showViewersSheet = true
    }

    // Mark a contact's status as viewed
    fun markViewed(status: StatusItem) {
        if (me == null || status.uid == me.uid) return
        scope.launch {
            try {
                val viewRef = db.collection("statuses").document(status.statusId)
                    .collection("views").document(me.uid)
                viewRef.set(mapOf("viewedAt" to System.currentTimeMillis()))
                db.collection("statuses").document(status.statusId)
                    .update("viewers", com.google.firebase.firestore.FieldValue.arrayUnion(me.uid))
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateStatus) {
                Icon(Icons.Default.Edit, contentDescription = "Add status")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // MY STATUS section with self-preview
            item {
                Text(
                    "My Status",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (myStatuses.isNotEmpty()) {
                                previewIndex = 0
                                showMyStatusPreview = true
                            } else {
                                onCreateStatus()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with status ring
                    Box(modifier = Modifier.size(56.dp)) {
                        if (myStatuses.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                        AsyncImage(
                            model = if (myPhotoUrl.isNotEmpty()) myPhotoUrl else me?.photoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        if (myStatuses.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "My Status",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (myStatuses.isEmpty()) "Tap to add status"
                            else "${myStatuses.size} update${if (myStatuses.size > 1) "s" else ""}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Viewers count - tap to see who viewed
                    if (myStatuses.isNotEmpty()) {
                        val totalViewers = myStatuses.sumOf { it.viewers.size }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                scope.launch { loadViewers(myStatuses.last()) }
                            }
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = "Viewers",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "$totalViewers",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // CONTACTS' statuses
            if (contactStatuses.isNotEmpty()) {
                item {
                    Text(
                        "Recent Updates",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(contactStatuses) { (uid, statuses) ->
                    val latest = statuses.last()
                    val isViewed = me != null && latest.viewers.contains(me.uid)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                markViewed(latest)
                                onViewStatus(uid)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(56.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(
                                        3.dp,
                                        if (isViewed) Color.Gray else MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                            )
                            AsyncImage(
                                model = latest.authorPhotoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(52.dp)
                                    .align(Alignment.Center)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(latest.authorName, fontWeight = FontWeight.SemiBold)
                            Text(
                                formatStatusTime(latest.timestamp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // My Status Full Preview
    if (showMyStatusPreview && myStatuses.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showMyStatusPreview = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("My Status")
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${previewIndex + 1}/${myStatuses.size}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                val current = myStatuses[previewIndex]
                Column {
                    // Status progress bar
                    LinearProgressIndicator(
                        progress = { (previewIndex + 1f) / myStatuses.size },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    if (current.mediaUrl.isNotEmpty()) {
                        AsyncImage(
                            model = current.mediaUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (current.text.isNotEmpty()) {
                        Text(current.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                    }

                    // Viewers row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { loadViewers(current) } },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${current.viewers.size} viewer${if (current.viewers.size != 1) "s" else ""} · Tap to see",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        formatStatusTime(current.timestamp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                if (previewIndex < myStatuses.size - 1) {
                    TextButton(onClick = { previewIndex++ }) { Text("Next →") }
                } else {
                    TextButton(onClick = { showMyStatusPreview = false }) { Text("Done") }
                }
            },
            dismissButton = {
                if (previewIndex > 0) {
                    TextButton(onClick = { previewIndex-- }) { Text("← Prev") }
                }
            }
        )
    }

    // Viewers Bottom Sheet
    if (showViewersSheet) {
        ModalBottomSheet(onDismissRequest = { showViewersSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Viewed by ${viewers.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                if (viewers.isEmpty()) {
                    Text(
                        "No one has viewed this status yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    viewers.forEach { viewer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = viewer.photoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(viewer.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    formatStatusTime(viewer.viewedAt),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

private fun formatStatusTime(ts: Long): String {
    if (ts == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ts))
    }
}
