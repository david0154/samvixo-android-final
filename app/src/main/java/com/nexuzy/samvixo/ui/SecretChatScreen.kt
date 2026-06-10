package com.nexuzy.samvixo.ui

import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SecretMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val isOwn: Boolean = false,
    val disappearing: Boolean = true
)

data class SecretChatState(
    val messages: List<SecretMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val screenshotAlertMessage: String? = null,
    val timerHours: Int = 24
)

class SecretChatViewModel : ViewModel() {
    private val _state = MutableStateFlow(SecretChatState())
    val state: StateFlow<SecretChatState> = _state

    private val firestore = Firebase.firestore
    private val functions = Firebase.functions
    private val auth = Firebase.auth

    fun setInput(t: String) { _state.value = _state.value.copy(inputText = t) }
    fun setTimer(hours: Int) { _state.value = _state.value.copy(timerHours = hours) }

    fun onScreenshotDetected(chatId: String, taker: String) {
        viewModelScope.launch {
            try {
                functions.getHttpsCallable("notifyScreenshot")
                    .call(hashMapOf("chatId" to chatId, "takerName" to taker))
                    .await()
                _state.value = _state.value.copy(
                    screenshotAlertMessage = "\uD83D\uDCF8 Screenshot alert sent to chat partner"
                )
            } catch (_: Exception) {}
        }
    }

    fun sendSecretMessage(chatId: String) {
        val text = _state.value.inputText.trim().ifEmpty { return }
        val uid = auth.currentUser?.uid ?: return
        _state.value = _state.value.copy(isSending = true, inputText = "")
        viewModelScope.launch {
            try {
                val disappearMs = _state.value.timerHours.toLong() * 3600_000L
                firestore
                    .collection("chats").document(chatId)
                    .collection("messages")
                    .add(
                        hashMapOf(
                            "text" to text,
                            "senderId" to uid,
                            "sentAt" to System.currentTimeMillis(),
                            "disappearing" to true,
                            "disappearAt" to (System.currentTimeMillis() + disappearMs),
                            "status" to "SENT",
                            "secretChat" to true
                        )
                    )
                    .await()
                _state.value = _state.value.copy(isSending = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSending = false)
            }
        }
    }

    fun clearAlert() { _state.value = _state.value.copy(screenshotAlertMessage = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretChatScreen(
    navController: NavController,
    chatId: String,
    peerName: String,
    vm: SecretChatViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val view = LocalView.current

    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    var showTimerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(peerName)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, Modifier.size(12.dp))
                            Text(
                                " Secret Chat · ${state.timerHours}h disappear",
                                style = MaterialTheme.typography.labelSmall
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
                    IconButton(onClick = { showTimerDialog = true }) {
                        Icon(Icons.Default.Timer, "Set disappearing timer")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            state.screenshotAlertMessage?.let { alert ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(alert, modifier = Modifier.padding(12.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                reverseLayout = true
            ) {
                items(state.messages) { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = if (msg.isOwn)
                            Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (msg.isOwn)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                msg.text,
                                modifier = Modifier.padding(10.dp, 6.dp),
                                color = if (msg.isOwn)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = { vm.setInput(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Secret message...") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { vm.sendSecretMessage(chatId) },
                    enabled = !state.isSending && state.inputText.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Disappearing Messages") },
            text = {
                Column {
                    listOf(24 to "24 Hours", 168 to "7 Days", 2160 to "90 Days").forEach { (h, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setTimer(h)
                                    showTimerDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.timerHours == h,
                                onClick = {
                                    vm.setTimer(h)
                                    showTimerDialog = false
                                }
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTimerDialog = false }) { Text("Cancel") } }
        )
    }
}
