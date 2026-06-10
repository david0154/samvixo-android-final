package com.nexuzy.samvixo.ui

/**
 * ContactUsScreen.kt
 *
 * User fills in: Subject, Email, Message.
 * On submit -> writes to Firestore contact_requests collection.
 * Admin sees the request in the Admin Panel -> Contact Requests page.
 * When admin replies, the reply goes to inbox_messages.
 * User can see admin reply in InboxScreen.
 *
 * Zero mock data. Zero hardcoded email addresses.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactUsScreen(navController: NavController) {
    val auth    = FirebaseAuth.getInstance()
    val uid     = auth.currentUser?.uid ?: ""
    val purple  = Color(0xFF7C3AED)
    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)

    var subject   by remember { mutableStateOf("") }
    var email     by remember { mutableStateOf("") }
    var message   by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitted  by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun submitRequest() {
        if (subject.isBlank()) { errorMsg = "Please enter a subject."; return }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMsg = "Please enter a valid email address."; return
        }
        if (message.isBlank()) { errorMsg = "Please enter your message."; return }
        errorMsg = ""
        submitting = true
        scope.launch {
            try {
                Firebase.firestore.collection("contact_requests").add(
                    hashMapOf(
                        "uid"       to uid,
                        "email"     to email.trim(),
                        "subject"   to subject.trim(),
                        "message"   to message.trim(),
                        "replied"   to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                submitted  = true
            } catch (e: Exception) {
                errorMsg = "Failed to send: ${e.message}"
            } finally {
                submitting = false
            }
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("Contact Us", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (submitted) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("\u2705", fontSize = 52.sp)
                    Text(
                        "Request Sent!",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                    Text(
                        "We have received your message. The Samvixo Team will review it and reply in your Inbox.",
                        color     = Color(0xFF94A3B8),
                        fontSize  = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { navController.navigate("inbox") },
                        colors  = ButtonDefaults.buttonColors(containerColor = purple),
                        shape   = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Inbox, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Go to Inbox", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        shape   = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back", color = Color(0xFF94A3B8))
                    }
                }
            } else {
                Text(
                    "Send a message to Samvixo Team",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
                Text(
                    "Fill in the form below. We will review your message and reply directly to your inbox.",
                    fontSize = 13.sp,
                    color    = Color(0xFF94A3B8)
                )

                OutlinedTextField(
                    value          = subject,
                    onValueChange  = { subject = it },
                    label          = { Text("Subject") },
                    modifier       = Modifier.fillMaxWidth(),
                    shape          = RoundedCornerShape(12.dp),
                    singleLine     = true,
                    colors         = contactFieldColors(purple)
                )

                OutlinedTextField(
                    value          = email,
                    onValueChange  = { email = it },
                    label          = { Text("Your Email Address") },
                    modifier       = Modifier.fillMaxWidth(),
                    shape          = RoundedCornerShape(12.dp),
                    singleLine     = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors         = contactFieldColors(purple)
                )

                OutlinedTextField(
                    value         = message,
                    onValueChange = { message = it },
                    label         = { Text("Message") },
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    shape         = RoundedCornerShape(12.dp),
                    maxLines      = 10,
                    colors        = contactFieldColors(purple)
                )

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = Color(0xFFEF4444), fontSize = 13.sp)
                }

                Button(
                    onClick  = { submitRequest() },
                    enabled  = !submitting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = purple),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color    = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Send, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Submit Request", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun contactFieldColors(purple: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = purple,
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor    = purple,
    unfocusedLabelColor  = Color(0xFF94A3B8),
    focusedTextColor     = Color.White,
    unfocusedTextColor   = Color.White,
    cursorColor          = purple
)
