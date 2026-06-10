package com.nexuzy.samvixo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nexuzy.samvixo.MainActivity
import com.nexuzy.samvixo.util.PresenceManager

/**
 * SamvixoMessagingService — handles all FCM push messages.
 *
 * Message types:
 *   - "new_message"  → show chat notification, trigger MessageSyncService sync
 *   - "call"         → show incoming call notification, launch CallActivity
 *   - "admin_otp"    → show HIGH PRIORITY admin 2FA OTP notification
 *   - default        → show general notification
 *
 * Token refresh: writes new FCM token to Firestore users/{uid}/deviceToken
 */
class SamvixoMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_CHAT   = "samvixo_chat"
        const val CHANNEL_CALL   = "samvixo_call"
        const val CHANNEL_ADMIN  = "samvixo_admin_2fa"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Persist new token to Firestore so Cloud Functions can send push to this device
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("deviceToken", token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type      = message.data["type"]     ?: ""
        val title     = message.notification?.title ?: message.data["title"] ?: "Samvixo"
        val body      = message.notification?.body  ?: message.data["body"]  ?: ""
        val chatId    = message.data["chatId"]   ?: ""
        val senderUid = message.data["senderUid"] ?: ""
        val callId    = message.data["callId"]   ?: ""
        val callType  = message.data["callType"] ?: "voice"
        val peerName  = message.data["peerName"] ?: "Unknown"

        when (type) {
            "new_message" -> {
                // Trigger background sync for this chat so Room gets updated even if app is closed
                if (chatId.isNotEmpty()) {
                    MessageSyncService.getInstance(applicationContext).startSync(chatId)
                }
                showNotification(
                    channelId    = CHANNEL_CHAT,
                    channelName  = "Messages",
                    importance   = NotificationManager.IMPORTANCE_HIGH,
                    notifId      = chatId.hashCode(),
                    title        = title,
                    body         = body,
                    intentExtras = mapOf("chatId" to chatId, "senderUid" to senderUid)
                )
            }

            "call" -> {
                // Launch CallActivity directly for incoming call
                val callIntent = Intent(this, com.nexuzy.samvixo.ui.CallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("channel_name", chatId)
                    putExtra("call_type",    callType)
                    putExtra("peer_name",    peerName)
                    putExtra("peer_uid",     senderUid)
                    putExtra("call_id",      callId)
                    putExtra("is_incoming",  true)
                }
                startActivity(callIntent)
            }

            "admin_otp" -> {
                val otp = message.data["otp"] ?: ""
                val intent = PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("show_otp", otp)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                createChannel(CHANNEL_ADMIN, "Admin 2FA", NotificationManager.IMPORTANCE_HIGH)
                val notif = NotificationCompat.Builder(this, CHANNEL_ADMIN)
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentTitle("\uD83D\uDD10 Samvixo Admin 2FA")
                    .setContentText("Your code: $otp  (expires in 5 min)")
                    .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("Your admin verification code is:\n\n$otp\n\nExpires in 5 minutes. Do not share."))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .setContentIntent(intent)
                    .build()
                getSystemService(NotificationManager::class.java).notify(9001, notif)
            }

            else -> {
                showNotification(
                    channelId   = CHANNEL_CHAT,
                    channelName = "Notifications",
                    importance  = NotificationManager.IMPORTANCE_DEFAULT,
                    notifId     = System.currentTimeMillis().toInt(),
                    title       = title,
                    body        = body
                )
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showNotification(
        channelId: String,
        channelName: String,
        importance: Int,
        notifId: Int,
        title: String,
        body: String,
        intentExtras: Map<String, String> = emptyMap()
    ) {
        createChannel(channelId, channelName, importance)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            intentExtras.forEach { (k, v) -> putExtra(k, v) }
        }
        val pi = PendingIntent.getActivity(
            this, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(if (importance >= NotificationManager.IMPORTANCE_HIGH)
                NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        getSystemService(NotificationManager::class.java).notify(notifId, notif)
    }

    private fun createChannel(id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(id, name, importance))
        }
    }
}
