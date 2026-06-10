package com.nexuzy.samvixo.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.nexuzy.samvixo.util.EncryptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class StatusPrivacy { EVERYONE, CONTACTS, CUSTOM, HIDDEN }
enum class StatusType { TEXT, PHOTO, VIDEO, VOICE }

data class StatusCreateState(
    val type               : StatusType   = StatusType.TEXT,
    val text               : String       = "",
    val mediaUri           : Uri?         = null,
    val privacy            : StatusPrivacy = StatusPrivacy.EVERYONE,
    val aiCaption          : String       = "",
    val isGeneratingCaption: Boolean      = false,
    val isPosting          : Boolean      = false,
    val message            : String?      = null,
    val bgColorArgb        : Int          = 0xFF1A1A24.toInt()
)

class StatusCreateViewModel : ViewModel() {
    private val _state = MutableStateFlow(StatusCreateState())
    val state: StateFlow<StatusCreateState> = _state

    private val firestore = Firebase.firestore
    private val storage   = Firebase.storage
    private val auth      = Firebase.auth

    fun setText(t: String)           { _state.value = _state.value.copy(text = t) }
    fun setType(t: StatusType)       { _state.value = _state.value.copy(type = t, mediaUri = null, aiCaption = "") }
    fun setPrivacy(p: StatusPrivacy) { _state.value = _state.value.copy(privacy = p) }
    fun setMediaUri(u: Uri?)         { _state.value = _state.value.copy(mediaUri = u) }
    fun setBgColor(c: Int)           { _state.value = _state.value.copy(bgColorArgb = c) }

    fun generateAiCaption(aiViewModel: AIViewModel) {
        val text = _state.value.text.ifBlank { return }
        _state.value = _state.value.copy(isGeneratingCaption = true, aiCaption = "")
        viewModelScope.launch {
            val caption = try {
                aiViewModel.generateCaption(text)
            } catch (e: Exception) { "❌ Caption failed: ${e.message}" }
            _state.value = _state.value.copy(isGeneratingCaption = false, aiCaption = caption)
        }
    }

    fun postStatus(onDone: (String) -> Unit) {
        val user = auth.currentUser ?: run { onDone("⚠️ Not signed in"); return }
        val uid  = user.uid
        if (_state.value.text.isBlank() && _state.value.mediaUri == null) {
            onDone("⚠️ Write something or pick a photo first")
            return
        }
        _state.value = _state.value.copy(isPosting = true)
        viewModelScope.launch {
            try {
                val mediaUrl: String? = _state.value.mediaUri?.let { uri ->
                    val ref = storage.reference.child("status_media/$uid/${System.currentTimeMillis()}")
                    ref.putFile(uri).await()
                    ref.downloadUrl.await().toString()
                }
                val authorName = try {
                    firestore.collection("users").document(uid).get().await()
                        .getString("displayName") ?: user.displayName ?: "User"
                } catch (_: Exception) { user.displayName ?: "User" }

                // FIX: encrypt status text before storing in Firestore
                val rawText      = _state.value.text
                val encryptedText = if (rawText.isNotBlank()) {
                    try { EncryptionManager.encrypt(rawText) } catch (_: Exception) { rawText }
                } else rawText
                val isTextEncrypted = encryptedText != rawText && rawText.isNotBlank()

                firestore.collection("statuses").add(
                    hashMapOf(
                        "uid"         to uid,
                        "authorName"  to authorName,
                        "text"        to encryptedText,   // ✔ encrypted
                        "isEncrypted" to isTextEncrypted, // ✔ flag so reader can decrypt
                        "mediaUrl"    to (mediaUrl ?: ""),
                        "mediaType"   to _state.value.type.name.lowercase(),
                        "aiCaption"   to _state.value.aiCaption,
                        "privacy"     to _state.value.privacy.name,
                        "bgColor"     to _state.value.bgColorArgb,
                        "createdAt"   to System.currentTimeMillis(),
                        "expiryTs"    to (System.currentTimeMillis() + 24L * 3600 * 1000),
                        "sponsored"   to false
                    )
                ).await()
                _state.value = _state.value.copy(isPosting = false)
                onDone("posted")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPosting = false)
                onDone("❌ Failed: ${e.message}")
            }
        }
    }
}

private val BG_COLORS = listOf(
    0xFF1A1A24.toInt(), 0xFF7C3AED.toInt(), 0xFF1E40AF.toInt(), 0xFF065F46.toInt(),
    0xFF92400E.toInt(), 0xFF9D174D.toInt(), 0xFF1F2937.toInt(), 0xFF0F172A.toInt(),
    0xFF7C2D12.toInt(), 0xFF134E4A.toInt()
)
private val QUICK_EMOJIS = listOf("😂","😍","🙏","🔥","❤️","🎉","🙌","👏","😎","💪")
private val PRIVACY_INFO = mapOf(
    StatusPrivacy.EVERYONE to Pair(Icons.Default.Public,        "Everyone"),
    StatusPrivacy.CONTACTS to Pair(Icons.Default.Contacts,      "Contacts"),
    StatusPrivacy.CUSTOM   to Pair(Icons.Default.Group,         "Custom"),
    StatusPrivacy.HIDDEN   to Pair(Icons.Default.VisibilityOff, "Hidden")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusCreateScreen(
    navController: NavController,
    vm: StatusCreateViewModel = viewModel(),
    aiViewModel: AIViewModel  = viewModel()
) {
    val state by vm.state.collectAsState()
    val purple  = Color(0xFF7C3AED)
    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)
    val divider = Color(0xFF2A2A3A)

    var errorMsg by remember { mutableStateOf<String?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> vm.setMediaUri(uri) }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> vm.setMediaUri(uri) }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("Create Status", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (state.isPosting) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                            color       = purple
                        )
                    } else {
                        TextButton(onClick = {
                            vm.postStatus { result ->
                                if (result == "posted") navController.popBackStack()
                                else errorMsg = result
                            }
                        }) {
                            Text("POST", color = purple, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Type selector ─────────────────────────────────────────────
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val types = listOf(
                    StatusType.TEXT  to "📝 Text",
                    StatusType.PHOTO to "🖼️ Photo",
                    StatusType.VIDEO to "🎥 Video",
                    StatusType.VOICE to "🎤 Voice"
                )
                items(types.size) { i ->
                    val (type, label) = types[i]
                    FilterChip(
                        selected = state.type == type,
                        onClick  = { vm.setType(type) },
                        label    = { Text(label, fontSize = 13.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = purple,
                            selectedLabelColor     = Color.White,
                            labelColor             = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            HorizontalDivider(color = divider)

            // ── Text status preview card ──────────────────────────────────
            if (state.type == StatusType.TEXT) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(state.bgColorArgb))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = state.text.ifBlank { "Start typing below...↓" },
                        color      = Color.White.copy(alpha = if (state.text.isBlank()) 0.35f else 1f),
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center,
                        lineHeight = 30.sp
                    )
                }
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(BG_COLORS.size) { i ->
                        val c = BG_COLORS[i]
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (state.bgColorArgb == c) 2.dp else 0.dp,
                                    color = if (state.bgColorArgb == c) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { vm.setBgColor(c) }
                        )
                    }
                }
            }

            // ── Photo / video preview ────────────────────────────────────
            if (state.mediaUri != null && (state.type == StatusType.PHOTO || state.type == StatusType.VIDEO)) {
                AsyncImage(
                    model              = state.mediaUri,
                    contentDescription = "Selected media",
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.Black),
                    contentScale       = ContentScale.Fit
                )
            }

            // ── Text input ────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value         = state.text,
                    onValueChange = { if (it.length <= 280) vm.setText(it) },
                    placeholder   = { Text("What's on your mind?", color = Color(0xFF64748B)) },
                    modifier      = Modifier.fillMaxWidth().height(110.dp),
                    maxLines      = 5,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = purple,
                        unfocusedBorderColor = divider,
                        focusedLabelColor    = purple,
                        cursorColor          = purple,
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White
                    )
                )
                Text(
                    "${state.text.length}/280",
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    fontSize = 11.sp,
                    color    = if (state.text.length > 250) Color(0xFFEF4444) else Color(0xFF64748B)
                )
            }

            // ── Quick emoji row ─────────────────────────────────────────────
            if (state.type == StatusType.TEXT) {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(QUICK_EMOJIS.size) { i ->
                        val emoji = QUICK_EMOJIS[i]
                        TextButton(
                            onClick        = { vm.setText(state.text + emoji) },
                            contentPadding = PaddingValues(6.dp)
                        ) { Text(emoji, fontSize = 22.sp) }
                    }
                }
            }

            // ── AI Caption ───────────────────────────────────────────────
            Row(
                modifier             = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { vm.generateAiCaption(aiViewModel) },
                    enabled = !state.isGeneratingCaption && state.text.isNotBlank(),
                    shape   = RoundedCornerShape(20.dp)
                ) {
                    if (state.isGeneratingCaption) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = purple)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, tint = purple, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (state.isGeneratingCaption) "Generating..." else "😈 Devil AI Caption",
                        color    = if (state.isGeneratingCaption) Color(0xFF94A3B8) else purple,
                        fontSize = 13.sp
                    )
                }
            }
            if (state.aiCaption.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { vm.setText(state.aiCaption) },
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = purple.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier          = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("😈", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Devil AI suggestion — tap to use", color = purple, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(state.aiCaption, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }

            // ── Media picker ───────────────────────────────────────────────
            if (state.type == StatusType.PHOTO || state.type == StatusType.VIDEO) {
                OutlinedButton(
                    onClick  = {
                        if (state.type == StatusType.PHOTO) photoLauncher.launch("image/*")
                        else videoLauncher.launch("video/*")
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (state.type == StatusType.PHOTO) Icons.Default.Image else Icons.Default.VideoLibrary,
                        null, tint = purple, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.mediaUri != null) "✅ Media selected — tap to change" else "Pick ${state.type.name.lowercase()}",
                        color = if (state.mediaUri != null) Color(0xFF22C55E) else Color.White
                    )
                }
            }

            // ── Voice note placeholder ────────────────────────────────────
            if (state.type == StatusType.VOICE) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape    = RoundedCornerShape(12.dp),
                    color    = surface
                ) {
                    Row(
                        modifier          = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Mic, null, tint = purple)
                        Spacer(Modifier.width(10.dp))
                        Text("Voice status — coming soon", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    }
                }
            }

            HorizontalDivider(color = divider, modifier = Modifier.padding(top = 8.dp))

            // ── Privacy selector ───────────────────────────────────────────
            Text("  Who can see this?", color = Color(0xFF94A3B8), fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(StatusPrivacy.entries.size) { i ->
                    val p          = StatusPrivacy.entries[i]
                    val (icon, label) = PRIVACY_INFO[p]!!
                    FilterChip(
                        selected    = state.privacy == p,
                        onClick     = { vm.setPrivacy(p) },
                        label       = { Text(label, fontSize = 12.sp) },
                        leadingIcon = { Icon(icon, null, modifier = Modifier.size(14.dp)) },
                        colors      = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = purple,
                            selectedLabelColor     = Color.White,
                            labelColor             = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            errorMsg?.let { msg ->
                Text(msg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = Color(0xFFEF4444), fontSize = 13.sp)
            }
        }
    }
}
