package com.nexuzy.samvixo.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.nexuzy.samvixo.data.repository.FirestoreRepository
import com.nexuzy.samvixo.domain.model.Message
import com.nexuzy.samvixo.domain.model.MessageStatus
import com.nexuzy.samvixo.domain.model.MessageType
import com.nexuzy.samvixo.util.VoiceRecorder
import com.nexuzy.samvixo.util.ScreenshotDetector
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatId: String?,
    peerName: String = "",
    navController: NavHostController,
    viewModel: ChatDetailViewModel = viewModel()
) {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages      by viewModel.messages.collectAsState()
    val gifResults    by viewModel.gifResults.collectAsState()
    val typingUsers   by viewModel.typingUsers.collectAsState()
    val currentUserId = remember { FirestoreRepository.getInstance().getCurrentUserId() }
    val repository    = remember { FirestoreRepository.getInstance() }
    val peerUid       by viewModel.peerUid.collectAsState()
    val listState     = rememberLazyListState()

    var showGifPicker  by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showMenu       by remember { mutableStateOf(false) }
    var isRecording    by remember { mutableStateOf(false) }
    val recorder  = remember { VoiceRecorder(context) }
    val timeFmt   = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    val screenshotDetector = remember {
        ScreenshotDetector(context) {
            scope.launch { viewModel.sendMessage("📸 Screenshot detected", type = MessageType.TEXT) }
        }
    }

    LaunchedEffect(chatId) {
        chatId?.let {
            viewModel.setChatId(it)
            scope.launch { repository.markChatAsRead(it) }
        }
        screenshotDetector.start()
    }

    // Auto-scroll to last message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    DisposableEffect(Unit) { onDispose { screenshotDetector.stop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            peerUid?.let { navController.navigate("user_profile/$it") }
                        }
                    ) {
                        Surface(modifier = Modifier.size(36.dp).clip(CircleShape), color = MaterialTheme.colorScheme.surfaceVariant) {}
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(peerName.ifBlank { "Chat" }, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (typingUsers.isNotEmpty()) "typing..." else "Online",
                                fontSize = 12.sp,
                                color    = if (typingUsers.isNotEmpty()) MaterialTheme.colorScheme.primary else Color(0xFF34C759)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, CallActivity::class.java).apply {
                            putExtra("channel_name", chatId); putExtra("call_type", "voice")
                            putExtra("peer_uid", peerUid);    putExtra("peer_name", peerName)
                        })
                    }) { Icon(Icons.Default.Call, null, tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, CallActivity::class.java).apply {
                            putExtra("channel_name", chatId); putExtra("call_type", "video")
                            putExtra("peer_uid", peerUid);    putExtra("peer_name", peerName)
                        })
                    }) { Icon(Icons.Default.VideoCall, null, tint = MaterialTheme.colorScheme.primary) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text        = { Text("View Profile") },
                                onClick     = { showMenu = false; peerUid?.let { navController.navigate("user_profile/$it") } },
                                leadingIcon = { Icon(Icons.Default.Person, null) }
                            )
                            DropdownMenuItem(
                                text        = { Text("Block User") },
                                onClick     = { showMenu = false; peerUid?.let { uid -> scope.launch { repository.blockUser(uid) } } },
                                leadingIcon = { Icon(Icons.Default.Block, null, tint = Color.Red) }
                            )
                            DropdownMenuItem(
                                text        = { Text("Report Chat") },
                                onClick     = { showMenu = false; peerUid?.let { uid -> scope.launch { repository.reportUser(uid, "Spam/Fraud detected") } } },
                                leadingIcon = { Icon(Icons.Default.Report, null, tint = Color.Red) }
                            )
                            DropdownMenuItem(
                                text        = { Text("Clear Chat") },
                                onClick     = { showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Delete, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        // KEY FIX: imePadding() ensures message input slides above keyboard
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Messages list
            LazyColumn(
                state          = listState,
                modifier       = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { message ->
                    val isMe = message.senderId == currentUserId
                    MessageBubbleModern(
                        message       = message,
                        isMe          = isMe,
                        timeFormatter = timeFmt,
                        onDelivered   = { msgId, status ->
                            scope.launch { chatId?.let { repository.markMessageDelivered(it, msgId, status) } }
                        }
                    )
                }
            }

            // GIF picker
            if (showGifPicker) {
                GifPicker(
                    gifs          = gifResults,
                    onSearch      = { viewModel.searchGifs(it) },
                    onGifSelected = { url -> viewModel.sendMessage(url); showGifPicker = false }
                )
            }

            // Attach menu
            if (showAttachMenu) {
                AttachMenu { showAttachMenu = false }
            }

            // Input bar
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 8.dp) {
                Column {
                    if (typingUsers.isNotEmpty()) {
                        Text(
                            "$peerName is typing...",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                    Row(
                        modifier          = Modifier.padding(8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                            Icon(Icons.Default.Add, null)
                        }
                        IconButton(onClick = { showGifPicker = !showGifPicker }) {
                            Icon(Icons.Default.Gif, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        TextField(
                            value         = inputText,
                            onValueChange = { text ->
                                inputText = text
                                scope.launch { chatId?.let { repository.setTyping(it, text.isNotBlank()) } }
                            },
                            modifier    = Modifier.weight(1f),
                            placeholder = { Text(if (isRecording) "Recording..." else "Message") },
                            shape       = RoundedCornerShape(24.dp),
                            colors      = TextFieldDefaults.colors(
                                focusedIndicatorColor   = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            maxLines  = 4,
                            readOnly  = isRecording
                        )
                        Spacer(Modifier.width(4.dp))
                        if (inputText.isBlank()) {
                            IconButton(onClick = {
                                if (!isRecording) {
                                    recorder.startRecording("vn_${System.currentTimeMillis()}.mp4")
                                    isRecording = true
                                } else {
                                    val file = recorder.stopRecording()
                                    isRecording = false
                                    file?.let { scope.launch { viewModel.sendVoiceNote(it.absolutePath) } }
                                }
                            }) {
                                Icon(
                                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    null,
                                    tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            FilledIconButton(
                                onClick  = {
                                    scope.launch { chatId?.let { repository.setTyping(it, false) } }
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                },
                                modifier = Modifier.size(48.dp),
                                colors   = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachMenu(onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AttachItem(Icons.Default.Description, "Document", Color(0xFF5E5EDD))
                AttachItem(Icons.Default.CameraAlt,   "Camera",   Color(0xFFFF2D55))
                AttachItem(Icons.Default.PhotoLibrary, "Gallery",  Color(0xFFA256F1))
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AttachItem(Icons.Default.Headset,    "Audio",    Color(0xFFFF9500))
                AttachItem(Icons.Default.LocationOn, "Location", Color(0xFF4CD964))
                AttachItem(Icons.Default.Person,     "Contact",  Color(0xFF007AFF))
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AttachItem(Icons.Default.Poll, "Poll", Color(0xFFFFCC00))
                Box(Modifier.weight(1f)); Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun AttachItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.width(80.dp).clickable { }
    ) {
        Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = color) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.padding(12.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
fun GifPicker(gifs: List<String>, onSearch: (String) -> Unit, onGifSelected: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.height(300.dp).fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        TextField(
            value         = query,
            onValueChange = { query = it; onSearch(it) },
            modifier      = Modifier.fillMaxWidth().padding(8.dp),
            placeholder   = { Text("Search GIPHY") }
        )
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f)) {
            items(gifs) { url ->
                AsyncImage(
                    model              = url,
                    contentDescription = null,
                    modifier           = Modifier.padding(2.dp).height(100.dp).clickable { onGifSelected(url) },
                    contentScale       = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun MessageBubbleModern(
    message: Message,
    isMe: Boolean,
    timeFormatter: SimpleDateFormat,
    onDelivered: (String, String) -> Unit = { _, _ -> }
) {
    val alignment      = if (isMe) Alignment.End else Alignment.Start
    val shape          = if (isMe) RoundedCornerShape(18.dp, 18.dp, 2.dp, 18.dp)
                         else      RoundedCornerShape(18.dp, 18.dp, 18.dp, 2.dp)
    val containerColor = if (isMe) MaterialTheme.colorScheme.primary
                         else      MaterialTheme.colorScheme.surfaceVariant
    val contentColor   = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface

    LaunchedEffect(message.messageId) {
        if (!isMe && message.status == MessageStatus.SENT) {
            onDelivered(message.messageId, message.status.name)
        }
    }

    Column(
        modifier             = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment  = alignment
    ) {
        Surface(color = containerColor, shape = shape) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                when (message.type) {
                    MessageType.VOICE_NOTE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = contentColor)
                            Spacer(Modifier.width(8.dp))
                            LinearProgressIndicator(progress = { 0.5f }, modifier = Modifier.width(100.dp), color = contentColor)
                            Spacer(Modifier.width(8.dp))
                            Text("0:12", fontSize = 12.sp, color = contentColor)
                        }
                    }
                    else -> {
                        message.mediaUrl?.let {
                            AsyncImage(
                                model              = it,
                                contentDescription = null,
                                modifier           = Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 300.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(message.content, color = contentColor, fontSize = 16.sp)
                    }
                }
                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(timeFormatter.format(Date(message.timestamp)), fontSize = 10.sp, color = contentColor.copy(alpha = 0.7f))
                    if (isMe) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text     = when (message.status) {
                                MessageStatus.READ, MessageStatus.DELIVERED -> "\u2713\u2713"
                                MessageStatus.SENT  -> "\u2713"
                                else                -> "\u23F3"
                            },
                            fontSize = 10.sp,
                            color    = if (message.status == MessageStatus.READ) Color(0xFF34C759)
                                       else contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
