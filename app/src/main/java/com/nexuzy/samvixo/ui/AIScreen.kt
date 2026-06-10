package com.nexuzy.samvixo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIScreen(aiViewModel: AIViewModel = viewModel()) {
    val uiState   by aiViewModel.uiState.collectAsState()
    val listState  = rememberLazyListState()
    var inputText  by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("Devil AI") }
    val aiModes    = listOf("Devil AI", "Companion")

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty())
            listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    val modeIcon    = if (selectedMode == "Companion") Icons.Default.Favorite else Icons.Default.AutoAwesome
    val engineColor = Color(0xFF7C3AED)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(modeIcon, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("😈 Devil AI", fontWeight = FontWeight.Bold)
                        }
                        // Active engine badge — shown only after first reply
                        if (uiState.activeEngine.isNotEmpty()) {
                            Text(
                                uiState.activeEngine,
                                fontSize = 10.sp,
                                color    = engineColor
                            )
                        }
                    }
                }
                // No settings gear — Ollama URL is internal, not user-configurable
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Mode chips
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(aiModes) { mode ->
                    FilterChip(
                        selected    = selectedMode == mode,
                        onClick     = { selectedMode = mode; aiViewModel.clearMessages() },
                        label       = { Text(mode) },
                        leadingIcon = if (selectedMode == mode) ({
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        }) else null
                    )
                }
            }

            // Messages list
            LazyColumn(
                state               = listState,
                modifier            = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding      = PaddingValues(vertical = 8.dp)
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        Box(
                            modifier         = Modifier.fillParentMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (selectedMode == "Companion") "💕" else "😈",
                                    fontSize = 56.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (selectedMode == "Companion") "I'm your Companion"
                                    else "I'm Devil AI",
                                    color      = Color(0xFF94A3B8),
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Ask me anything!",
                                    color    = Color(0xFF64748B),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                items(uiState.messages) { msg ->
                    val isUser = msg.role == "user"
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        if (!isUser) {
                            Surface(
                                modifier = Modifier.size(32.dp).align(Alignment.Bottom),
                                shape    = CircleShape,
                                color    = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    modeIcon, null,
                                    modifier = Modifier.padding(6.dp),
                                    tint     = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(
                                topStart    = 16.dp, topEnd    = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd   = if (isUser) 4.dp  else 16.dp
                            ),
                            color    = if (isUser) MaterialTheme.colorScheme.primary
                            else      MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                msg.content,
                                modifier = Modifier.padding(10.dp, 8.dp),
                                color    = if (isUser) Color.White
                                else      MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                if (uiState.isLoading) {
                    item {
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(38.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text("•", color = Color(0xFF7C3AED), fontSize = 18.sp)
                                    Spacer(Modifier.width(3.dp))
                                    Text("•", color = Color(0xFF7C3AED).copy(alpha = 0.6f), fontSize = 18.sp)
                                    Spacer(Modifier.width(3.dp))
                                    Text("•", color = Color(0xFF7C3AED).copy(alpha = 0.3f), fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Input bar
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { inputText = it },
                    placeholder   = { Text("Ask Devil AI anything...") },
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(24.dp),
                    maxLines      = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            aiViewModel.sendMessage(inputText.trim(), selectedMode)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !uiState.isLoading
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }
}
