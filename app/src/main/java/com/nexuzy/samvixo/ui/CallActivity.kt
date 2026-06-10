package com.nexuzy.samvixo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.functions.FirebaseFunctions
import com.nexuzy.samvixo.data.local.AppDatabase
import com.nexuzy.samvixo.data.local.entity.CallLogEntity
import com.nexuzy.samvixo.data.repository.FirestoreRepository
import com.nexuzy.samvixo.ui.theme.SamvixoTheme
import io.agora.rtc2.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallActivity : ComponentActivity() {

    private var rtcEngine: RtcEngine? = null
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) fetchTokenAndJoin()
        else {
            Toast.makeText(this, "Permissions required for calls", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val callStatus    = mutableStateOf("Connecting…")
    private val isMuted       = mutableStateOf(false)
    private val isCameraOff   = mutableStateOf(false)
    private val callConnected = mutableStateOf(false)
    private val callDuration  = mutableStateOf(0L)

    private var channelName = ""
    private var callType    = "voice"
    private var peerName    = "Peer"
    private var peerUid     = ""
    private var callId      = ""
    private var isIncoming  = false
    private var callStartTime = 0L
    private var agoraAppId  = ""
    private var agoraToken  = ""
    private var timerJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelName = intent.getStringExtra("channel_name") ?: "room"
        callType    = intent.getStringExtra("call_type")    ?: "voice"
        peerName    = intent.getStringExtra("peer_name")    ?: "User"
        peerUid     = intent.getStringExtra("peer_uid")     ?: ""
        callId      = intent.getStringExtra("call_id")      ?: UUID.randomUUID().toString()
        isIncoming  = intent.getBooleanExtra("is_incoming", false)

        setContent {
            SamvixoTheme {
                CallScreen(
                    peerName       = peerName,
                    callType       = callType,
                    callStatus     = callStatus.value,
                    isMuted        = isMuted.value,
                    isCameraOff    = isCameraOff.value,
                    callConnected  = callConnected.value,
                    callDuration   = callDuration.value,
                    onMuteToggle   = { toggleMute() },
                    onCameraToggle = { toggleCamera() },
                    onEndCall      = { endCall() }
                )
            }
        }

        requestPermissionsForCall()
    }

    private fun requestPermissionsForCall() {
        val needed  = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (callType == "video") needed.add(Manifest.permission.CAMERA)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) fetchTokenAndJoin() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun fetchTokenAndJoin() {
        callStatus.value = "Getting token…"
        activityScope.launch {
            try {
                val result = FirebaseFunctions.getInstance()
                    .getHttpsCallable("generateAgoraToken")
                    .call(mapOf("channelName" to channelName, "role" to "publisher"))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val data   = result.getData() as Map<String, Any>
                agoraToken = data["token"]  as? String ?: ""
                agoraAppId = data["appId"]  as? String ?: ""

                if (agoraAppId.isBlank() || agoraToken.isBlank()) {
                    callStatus.value = "Call config not set up"
                    return@launch
                }

                if (!isIncoming && callId.isNotEmpty()) {
                    try {
                        FirestoreRepository.getInstance().initiateCallSignal(
                            callId      = callId,
                            calleeUid   = peerUid,
                            isVideo     = callType == "video",
                            channelName = channelName
                        )
                    } catch (_: Exception) { }
                }

                initAgoraAndJoin()
            } catch (e: Exception) {
                callStatus.value = "Failed: ${e.message}"
            }
        }
    }

    private fun initAgoraAndJoin() {
        val config = RtcEngineConfig().apply {
            mContext      = applicationContext
            mAppId        = agoraAppId
            mEventHandler = object : IRtcEngineEventHandler() {
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    runOnUiThread {
                        callStartTime       = System.currentTimeMillis()
                        callStatus.value    = "Connected"
                        callConnected.value = true
                        startCallTimer()
                        updateSignal("answered")
                    }
                }
                override fun onUserOffline(uid: Int, reason: Int) {
                    runOnUiThread { callStatus.value = "Call ended"; endCall() }
                }
                override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                    runOnUiThread { callStatus.value = "Ringing…" }
                }
                override fun onError(err: Int) {
                    runOnUiThread { callStatus.value = "Error $err" }
                }
            }
        }

        try {
            rtcEngine = RtcEngine.create(config)
            if (callType == "video") { rtcEngine?.enableVideo(); rtcEngine?.startPreview() }
            else rtcEngine?.disableVideo()
            rtcEngine?.setEnableSpeakerphone(true)
            rtcEngine?.joinChannel(agoraToken, channelName, 0, ChannelMediaOptions().apply {
                publishMicrophoneTrack = true
                publishCameraTrack     = callType == "video"
                autoSubscribeAudio     = true
                autoSubscribeVideo     = callType == "video"
                clientRoleType         = Constants.CLIENT_ROLE_BROADCASTER
            })
        } catch (e: Exception) {
            callStatus.value = "Init failed: ${e.message}"
        }
    }

    private fun startCallTimer() {
        timerJob = activityScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                callDuration.value++
            }
        }
    }

    private fun toggleMute() {
        isMuted.value = !isMuted.value
        rtcEngine?.muteLocalAudioStream(isMuted.value)
    }

    private fun toggleCamera() {
        if (callType != "video") return
        isCameraOff.value = !isCameraOff.value
        rtcEngine?.muteLocalVideoStream(isCameraOff.value)
    }

    private fun updateSignal(status: String) {
        if (callId.isEmpty()) return
        activityScope.launch {
            try { FirestoreRepository.getInstance().updateCallSignalStatus(callId, status) }
            catch (_: Exception) {}
        }
    }

    private fun endCall() {
        timerJob?.cancel()
        val endTime      = System.currentTimeMillis()
        val durationSecs = if (callStartTime > 0) ((endTime - callStartTime) / 1000).toInt() else 0
        val wasMissed    = !callConnected.value && isIncoming
        val callStatusStr   = when {
            wasMissed          -> "MISSED"
            !callConnected.value -> "NO_ANSWER"
            else               -> "COMPLETED"
        }

        updateSignal("ended")

        activityScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getInstance(applicationContext).callLogDao().insertCallLog(
                    CallLogEntity(
                        callId          = callId,
                        chatId          = channelName,
                        peerId          = peerUid,
                        peerName        = peerName,
                        isIncoming      = isIncoming,
                        isVideo         = callType == "video",
                        startTime       = callStartTime,
                        endTime         = endTime,
                        durationSeconds = durationSecs,
                        status          = callStatusStr,
                        timestamp       = endTime
                    )
                )
            } catch (_: Exception) {}
        }

        rtcEngine?.leaveChannel()
        RtcEngine.destroy()
        rtcEngine = null
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        try { rtcEngine?.leaveChannel(); RtcEngine.destroy() } catch (_: Exception) {}
        rtcEngine = null
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60; val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@Composable
private fun CallScreen(
    peerName: String, callType: String, callStatus: String,
    isMuted: Boolean, isCameraOff: Boolean, callConnected: Boolean,
    callDuration: Long, onMuteToggle: () -> Unit, onCameraToggle: () -> Unit, onEndCall: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(vertical = 64.dp, horizontal = 24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(96.dp), shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(peerName.take(1).uppercase(), fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(peerName, fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (callConnected) formatDuration(callDuration) else callStatus,
                    fontSize = 15.sp,
                    color = if (callConnected) Color(0xFF34C759) else Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Text(if (callType == "video") "📹 Video Call" else "🎙 Voice Call", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isMuted) "Unmute" else "Mute",
                        color = if (isMuted) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.15f),
                        iconTint = Color.White, onClick = onMuteToggle
                    )
                    CallControlButton(
                        icon = Icons.Default.CallEnd, label = "End",
                        color = Color.Red, iconTint = Color.White, size = 72.dp, onClick = onEndCall
                    )
                    if (callType == "video") {
                        CallControlButton(
                            icon = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            label = if (isCameraOff) "Cam Off" else "Cam On",
                            color = if (isCameraOff) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.15f),
                            iconTint = Color.White, onClick = onCameraToggle
                        )
                    } else {
                        CallControlButton(
                            icon = Icons.Default.VolumeUp, label = "Speaker",
                            color = Color.White.copy(alpha = 0.15f), iconTint = Color.White, onClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, color: Color, iconTint: Color,
    size: androidx.compose.ui.unit.Dp = 56.dp, onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(size), shape = CircleShape, color = color, onClick = onClick) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
    }
}
