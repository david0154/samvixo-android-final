package com.nexuzy.samvixo.ui

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.auth.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nexuzy.samvixo.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

private enum class AuthStep { PHONE, OTP, SETUP_NAME, SETUP_ABOUT, SETUP_PHOTO, WEB_QR }

private data class Country(val flag: String, val name: String, val code: String)

private val COUNTRIES = listOf(
    Country("🇮🇳", "India",          "+91"),
    Country("🇺🇸", "USA",            "+1"),
    Country("🇬🇧", "United Kingdom", "+44"),
    Country("🇸🇬", "Singapore",      "+65"),
    Country("🇦🇺", "Australia",      "+61"),
    Country("🇫🇷", "France",         "+33"),
    Country("🇷🇺", "Russia",         "+7"),
    Country("🇪🇸", "Spain",          "+34"),
    Country("🇦🇪", "UAE",            "+971"),
    Country("🇯🇵", "Japan",          "+81"),
    Country("🇰🇷", "South Korea",    "+82"),
    Country("🇦🇲", "Armenia",        "+374"),
    Country("🇧🇷", "Brazil",         "+55")
)

// ─────────────────────────────────────────────────────────────────────────────
// QR code generator helper
// ─────────────────────────────────────────────────────────────────────────────
fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
    val hints  = mapOf(EncodeHintType.MARGIN to 1)
    val writer = QRCodeWriter()
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bmp
}

// ─────────────────────────────────────────────────────────────────────────────
// Web-QR Composable (self-contained panel shown inside the auth card)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun WebQrPanel(
    purple: Color,
    surface: Color,
    onApproved: (String) -> Unit,
    onCancel: () -> Unit
) {
    val scope   = rememberCoroutineScope()
    val db      = Firebase.firestore

    val sessionToken = remember { UUID.randomUUID().toString() }
    val qrContent    = remember { "samvixo://web-login?token=$sessionToken" }
    val qrBitmap     = remember { generateQrBitmap(qrContent) }

    var status     by remember { mutableStateOf("pending") }
    var countdown  by remember { mutableStateOf(300) }
    var sessionUid by remember { mutableStateOf("") }

    LaunchedEffect(sessionToken) {
        val expireAt = Timestamp(Date(System.currentTimeMillis() + 5 * 60 * 1000))
        db.collection("web_sessions").document(sessionToken).set(
            hashMapOf(
                "status"    to "pending",
                "expireAt"  to expireAt,
                "createdAt" to Timestamp.now()
            )
        ).await()
    }

    LaunchedEffect(sessionToken) {
        while (isActive && countdown > 0 && status !in listOf("approved", "expired")) {
            delay(1000)
            countdown--
            if (countdown <= 0) {
                status = "expired"
                db.collection("web_sessions").document(sessionToken)
                    .update("status", "expired")
            }
        }
    }

    LaunchedEffect(sessionToken) {
        while (isActive && status !in listOf("approved", "expired")) {
            delay(2000)
            try {
                val snap = db.collection("web_sessions").document(sessionToken).get().await()
                val s    = snap.getString("status") ?: "pending"
                status = s
                if (s == "approved") {
                    sessionUid = snap.getString("uid") ?: ""
                    onApproved(sessionUid)
                }
            } catch (_: Exception) {}
        }
    }

    val minutes = countdown / 60
    val seconds = countdown % 60
    val timeStr = "%d:%02d".format(minutes, seconds)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.QrCode, null, tint = purple, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("Link a Device / Web", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Text(
            "Open Samvixo Web on your browser and scan this QR code to log in instantly.",
            fontSize  = 13.sp,
            color     = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        when (status) {
            "pending", "scanned" -> {
                Box(
                    modifier         = Modifier
                        .size(220.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap             = qrBitmap.asImageBitmap(),
                        contentDescription = "Web Login QR",
                        modifier           = Modifier.fillMaxSize()
                    )
                }

                if (status == "scanned") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color       = Color(0xFF22C55E)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("✓ QR Scanned — waiting for confirmation…",
                            fontSize = 13.sp, color = Color(0xFF22C55E))
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Timer, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Expires in $timeStr", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QrStep("1", "Open ", "web.samvixo.com", " in your browser", purple)
                        QrStep("2", "Click ", "\u201cLink with QR\u201d", "", purple)
                        QrStep("3", "Scan this QR with your ", "browser's camera", "", purple)
                        QrStep("4", "Confirm on your ", "phone", " \u2014 done!", purple)
                    }
                }
            }

            "expired" -> {
                Icon(Icons.Default.HourglassEmpty, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(56.dp))
                Text("QR Expired", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                Text("The QR code has expired. Generate a new one.", fontSize = 13.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
                Button(
                    onClick  = { onCancel() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) { Text("↺ Generate New QR", fontWeight = FontWeight.Bold, color = Color.Black) }
            }

            "approved" -> {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(56.dp))
                Text("Logged In!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                Text("Web session approved. Opening Samvixo…", fontSize = 13.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
                CircularProgressIndicator(color = Color(0xFF22C55E))
            }
        }

        TextButton(onClick = {
            scope.launch {
                try { db.collection("web_sessions").document(sessionToken).delete().await() } catch (_: Exception) {}
            }
            onCancel()
        }) {
            Text("← Back to Phone Login", color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun QrStep(num: String, before: String, highlight: String, after: String, purple: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape    = CircleShape,
            color    = purple.copy(alpha = 0.2f),
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(num, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = purple)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildAnnotatedString {
                append(before)
                withStyle(SpanStyle(color = purple, fontWeight = FontWeight.SemiBold)) {
                    append(highlight)
                }
                append(after)
            },
            fontSize = 13.sp,
            color    = Color(0xFFCBD5E1)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AuthScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuthScreen(onAuthComplete: () -> Unit) {
    val context  = LocalContext.current
    val activity = context as Activity
    val scope    = rememberCoroutineScope()
    val auth     = FirebaseAuth.getInstance()

    var step            by remember { mutableStateOf(AuthStep.PHONE) }
    var localPhone      by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(COUNTRIES[0]) }
    var countryMenuOpen by remember { mutableStateOf(false) }
    var otpCode         by remember { mutableStateOf("") }
    var displayName     by remember { mutableStateOf("") }
    var about           by remember { mutableStateOf("Available") }
    var photoUri        by remember { mutableStateOf<Uri?>(null) }
    var verificationId  by remember { mutableStateOf("") }
    var error           by remember { mutableStateOf("") }
    var loading         by remember { mutableStateOf(false) }
    var resendToken     by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { photoUri = it } }

    val purple  = Color(0xFF7C3AED)
    val bg      = Color(0xFF0F0F13)
    val surface = Color(0xFF1A1A24)

    val fullPhone by remember { derivedStateOf { "${selectedCountry.code}${localPhone.trimStart('0')}" } }

    fun buildCallbacks() = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(cred: PhoneAuthCredential) {
            scope.launch {
                loading = true; error = ""
                try {
                    auth.signInWithCredential(cred).await()
                    val uid  = auth.currentUser!!.uid
                    val snap = Firebase.firestore.collection("users").document(uid).get().await()
                    if (snap.exists() && snap.getString("displayName") != null) onAuthComplete()
                    else step = AuthStep.SETUP_NAME
                } catch (e: Exception) { error = e.message ?: "Error" }
                loading = false
            }
        }
        override fun onVerificationFailed(e: FirebaseException) {
            error = e.message ?: "Verification failed"; loading = false
        }
        override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = vid; resendToken = token
            step = AuthStep.OTP; loading = false
        }
    }

    fun sendOtp() {
        if (localPhone.isBlank()) { error = "Enter phone number"; return }
        loading = true; error = ""
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(fullPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(buildCallbacks())
                .build()
        )
    }

    fun verifyOtp() {
        if (otpCode.length < 6) { error = "Enter 6-digit OTP"; return }
        loading = true; error = ""
        scope.launch {
            try {
                auth.signInWithCredential(
                    PhoneAuthProvider.getCredential(verificationId, otpCode)
                ).await()
                val uid  = auth.currentUser!!.uid
                val snap = Firebase.firestore.collection("users").document(uid).get().await()
                if (snap.exists() && snap.getString("displayName") != null) onAuthComplete()
                else step = AuthStep.SETUP_NAME
            } catch (e: Exception) { error = e.message ?: "Wrong OTP" }
            loading = false
        }
    }

    fun finishSetup() {
        if (displayName.isBlank()) { error = "Enter your name"; return }
        loading = true; error = ""
        scope.launch {
            try {
                val uid      = auth.currentUser!!.uid
                val phone    = auth.currentUser!!.phoneNumber ?: fullPhone
                var photoUrl = ""
                if (photoUri != null) {
                    val ref = Firebase.storage.reference.child("avatars/$uid.jpg")
                    ref.putFile(photoUri!!).await()
                    photoUrl = ref.downloadUrl.await().toString()
                }
                Firebase.firestore.collection("users").document(uid).set(
                    hashMapOf(
                        "uid"         to uid,
                        "displayName" to displayName.trim(),
                        "about"       to about.trim().ifBlank { "Available" },
                        "photoUrl"    to photoUrl,
                        "phoneNumber" to phone,
                        "createdAt"   to Timestamp.now(),
                        "trustScore"  to 100,
                        "restricted"  to false,
                        "banned"      to false
                    )
                ).await()
                onAuthComplete()
            } catch (e: Exception) { error = e.message ?: "Setup failed"; loading = false }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Image(
                painter            = painterResource(id = R.drawable.samvixo_logo),
                contentDescription = "Samvixo",
                modifier           = Modifier.size(90.dp),
                contentScale       = ContentScale.Fit
            )
            Spacer(Modifier.height(8.dp))
            Text("Samvixo", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Secure AI Messaging", fontSize = 12.sp, color = Color(0xFF94A3B8), letterSpacing = 1.5.sp)
            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(20.dp),
                colors   = CardDefaults.cardColors(containerColor = surface)
            ) {
                Column(
                    modifier            = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (step) {

                        AuthStep.PHONE -> {
                            Text("Welcome to Samvixo", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Enter your phone number to continue", fontSize = 13.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)

                            Box {
                                OutlinedButton(
                                    onClick  = { countryMenuOpen = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape    = RoundedCornerShape(12.dp),
                                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text(
                                        "${selectedCountry.flag}  ${selectedCountry.name}  ${selectedCountry.code}",
                                        modifier = Modifier.weight(1f), color = Color.White, fontSize = 15.sp
                                    )
                                    Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF94A3B8))
                                }
                                DropdownMenu(
                                    expanded         = countryMenuOpen,
                                    onDismissRequest = { countryMenuOpen = false },
                                    modifier         = Modifier.background(surface)
                                ) {
                                    COUNTRIES.forEach { country ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment     = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text(country.flag, fontSize = 18.sp)
                                                    Text(country.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                                    Text(country.code, color = Color(0xFF94A3B8), fontSize = 13.sp)
                                                }
                                            },
                                            onClick     = { selectedCountry = country; countryMenuOpen = false },
                                            leadingIcon = if (selectedCountry == country) ({ Icon(Icons.Default.Check, null, tint = purple) }) else null
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value           = localPhone,
                                onValueChange   = { v -> if (v.all { it.isDigit() }) localPhone = v },
                                label           = { Text("Phone number") },
                                placeholder     = { Text("98765 43210", color = Color(0xFF475569)) },
                                prefix          = { Text("${selectedCountry.code}  ", color = purple, fontWeight = FontWeight.Bold) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier        = Modifier.fillMaxWidth(),
                                singleLine      = true,
                                colors          = authFieldColors(purple)
                            )
                            Button(
                                onClick  = { sendOtp() },
                                enabled  = !loading,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = purple)
                            ) {
                                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                else Text("Send OTP", fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier          = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
                                Text("  OR  ", color = Color(0xFF475569), fontSize = 12.sp)
                                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
                            }

                            OutlinedButton(
                                onClick  = { step = AuthStep.WEB_QR },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(12.dp),
                                border   = androidx.compose.foundation.BorderStroke(1.dp, purple.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Default.QrCode, null, tint = purple, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Link a Device / Web Login", color = purple, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        AuthStep.WEB_QR -> {
                            WebQrPanel(
                                purple     = purple,
                                surface    = surface,
                                onApproved = { approvedUid ->
                                    scope.launch {
                                        loading = true
                                        try {
                                            val snap = Firebase.firestore
                                                .collection("users")
                                                .document(approvedUid)
                                                .get().await()
                                            if (snap.exists() && snap.getString("displayName") != null)
                                                onAuthComplete()
                                            else
                                                step = AuthStep.SETUP_NAME
                                        } catch (e: Exception) {
                                            error = e.message ?: "Login error"
                                        }
                                        loading = false
                                    }
                                },
                                onCancel = { step = AuthStep.PHONE; error = "" }
                            )
                        }

                        AuthStep.OTP -> {
                            Text("Verify OTP", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("6-digit code sent to $fullPhone", fontSize = 13.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
                            OutlinedTextField(
                                value           = otpCode,
                                onValueChange   = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                                label           = { Text("Enter OTP") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier        = Modifier.fillMaxWidth(),
                                singleLine      = true,
                                colors          = authFieldColors(purple)
                            )
                            Button(
                                onClick  = { verifyOtp() },
                                enabled  = !loading,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = purple)
                            ) {
                                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                else Text("Verify", fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { step = AuthStep.PHONE; otpCode = "" }) {
                                Text("← Change number", color = Color(0xFF94A3B8))
                            }
                        }

                        AuthStep.SETUP_NAME -> {
                            Icon(Icons.Default.Person, null, tint = purple, modifier = Modifier.size(40.dp))
                            Text("What's your name?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            OutlinedTextField(
                                value         = displayName,
                                onValueChange = { displayName = it },
                                label         = { Text("Your Name") },
                                modifier      = Modifier.fillMaxWidth(),
                                singleLine    = true,
                                colors        = authFieldColors(purple)
                            )
                            Button(
                                onClick  = { if (displayName.isNotBlank()) { error = ""; step = AuthStep.SETUP_ABOUT } else error = "Enter name" },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = purple)
                            ) { Text("Next →", fontWeight = FontWeight.Bold) }
                        }

                        AuthStep.SETUP_ABOUT -> {
                            Icon(Icons.Default.CheckCircle, null, tint = purple, modifier = Modifier.size(40.dp))
                            Text("About (optional)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("You can keep the default or change it", fontSize = 13.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
                            OutlinedTextField(
                                value         = about,
                                onValueChange = { about = it },
                                label         = { Text("About") },
                                modifier      = Modifier.fillMaxWidth(),
                                singleLine    = true,
                                colors        = authFieldColors(purple)
                            )
                            Button(
                                onClick  = { error = ""; step = AuthStep.SETUP_PHOTO },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = purple)
                            ) { Text("Next →", fontWeight = FontWeight.Bold) }
                        }

                        AuthStep.SETUP_PHOTO -> {
                            Text("Profile Picture", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Add a photo now or skip — change later in Profile", fontSize = 13.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, purple, CircleShape)
                                    .clickable { photoLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (photoUri != null) {
                                    Image(
                                        painter            = rememberAsyncImagePainter(photoUri),
                                        contentDescription = "Profile photo",
                                        modifier           = Modifier.fillMaxSize(),
                                        contentScale       = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        Modifier.fillMaxSize().background(Color(0xFF2D2D3D), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(30.dp)
                                        .background(purple, CircleShape)
                                        .border(2.dp, surface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                            Text("Tap circle to choose photo", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier              = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick  = { photoUri = null; finishSetup() },
                                    enabled  = !loading,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape    = RoundedCornerShape(12.dp)
                                ) { Text("Skip", color = Color(0xFF94A3B8)) }
                                Button(
                                    onClick  = { finishSetup() },
                                    enabled  = !loading,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape    = RoundedCornerShape(12.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = purple)
                                ) {
                                    if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    else Text("Done ✓", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (error.isNotEmpty()) {
                        Text(error, color = Color(0xFFEF4444), fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun authFieldColors(purple: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = purple,
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor    = purple,
    cursorColor          = purple,
    focusedTextColor     = Color.White,
    unfocusedTextColor   = Color.White
)
