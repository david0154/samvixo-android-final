package com.nexuzy.samvixo.util

import android.content.Context
import android.content.Intent
import android.util.Base64
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(private val context: Context) {

    companion object {
        private const val PBKDF2_ITERATIONS = 310_000
        private const val KEY_LENGTH_BITS   = 256
        private const val SALT_LENGTH       = 32
        private const val IV_LENGTH         = 12
        private const val GCM_TAG_LENGTH    = 128
        private const val BACKUP_VERSION    = 1
        private const val DRIVE_FILE_NAME   = "samvixo_backup.enc"
        private const val DRIVE_MIME_TYPE   = "application/octet-stream"
        private const val PREFS_NAME        = "samvixo_backup"
        private const val KEY_LAST_BACKUP   = "last_backup_ts"
        private const val KEY_SALT          = "backup_salt"
    }

    private val prefs     = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth      = FirebaseAuth.getInstance()

    // ── Google Sign-In ───────────────────────────────────────────────────

    fun buildSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    suspend fun handleSignInResult(data: Intent?): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val task    = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(Exception::class.java)
            account != null
        } catch (e: Exception) { false }
    }

    fun isDriveConnected(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return account.grantedScopes.any { it.scopeUri.contains("drive") }
    }

    fun disconnectDrive() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        GoogleSignIn.getClient(context, gso).signOut()
    }

    // ── Drive service ──────────────────────────────────────────────────
    // FIX: replaced deprecated AndroidHttp.newCompatibleTransport() with NetHttpTransport()
    private fun getDriveService(): Drive {
        val account    = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("Google Drive not connected. Please connect first.")
        val credential = GoogleAccountCredential
            .usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),                  // ✔ replaces removed AndroidHttp
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Samvixo").build()
    }

    // ── Upload to Drive ───────────────────────────────────────────────

    suspend fun uploadToDrive(encryptedBlob: String): String = withContext(Dispatchers.IO) {
        val drive  = getDriveService()
        val bytes  = Base64.decode(encryptedBlob, Base64.NO_WRAP)
        val stream = ByteArrayInputStream(bytes)

        val existing = drive.files().list()
            .setQ("name='$DRIVE_FILE_NAME' and trashed=false")
            .setSpaces("drive")
            .execute()
        existing.files?.forEach { drive.files().delete(it.id).execute() }

        val meta      = com.google.api.services.drive.model.File()
        meta.name     = DRIVE_FILE_NAME
        meta.mimeType = DRIVE_MIME_TYPE
        val content   = com.google.api.client.http.InputStreamContent(DRIVE_MIME_TYPE, stream)
        val file      = drive.files().create(meta, content).setFields("id, name").execute()
        return@withContext file.name ?: DRIVE_FILE_NAME
    }

    // ── Download from Drive ────────────────────────────────────────────

    suspend fun downloadFromDrive(): String = withContext(Dispatchers.IO) {
        val drive = getDriveService()
        val list  = drive.files().list()
            .setQ("name='$DRIVE_FILE_NAME' and trashed=false")
            .setSpaces("drive")
            .execute()
        val file  = list.files?.firstOrNull()
            ?: throw IllegalStateException("No backup found in Google Drive. Please backup first.")
        val out   = ByteArrayOutputStream()
        drive.files().get(file.id).executeMediaAndDownloadTo(out)
        return@withContext Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // ── Backup time helpers ───────────────────────────────────────────

    fun saveLastBackupTime() {
        prefs.edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply()
    }

    fun getLastBackupTime(): String {
        val ts = prefs.getLong(KEY_LAST_BACKUP, 0L)
        return if (ts == 0L) "Never"
        else SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ts))
    }

    // ── Create encrypted backup ─────────────────────────────────────────

    suspend fun createEncryptedBackup(password: String): BackupResult {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

        val chatsSnap = firestore.collection("chats")
            .whereArrayContains("participants", uid)
            .get().await()

        val chatsArray = JSONArray()
        for (chatDoc in chatsSnap.documents) {
            val chatData = chatDoc.data ?: continue
            val chatObj  = JSONObject(chatData)

            val msgsSnap = firestore
                .collection("chats").document(chatDoc.id)
                .collection("messages")
                .get().await()

            val messagesArray = JSONArray()
            for (msgDoc in msgsSnap.documents) {
                val msgData     = msgDoc.data ?: continue
                val rawContent  = msgData["content"] as? String ?: ""
                val isEncrypted = msgData["isEncrypted"] as? Boolean ?: false
                val decrypted   = if (isEncrypted && rawContent.isNotBlank()) {
                    try { EncryptionManager.decrypt(rawContent) } catch (_: Exception) { rawContent }
                } else rawContent
                val msgObj = JSONObject(msgData)
                msgObj.put("content", decrypted)
                msgObj.put("isEncrypted", false)
                messagesArray.put(msgObj)
            }
            chatObj.put("messages", messagesArray)
            chatsArray.put(chatObj)
        }

        val backupJson = JSONObject().apply {
            put("version",   BACKUP_VERSION)
            put("uid",       uid)
            put("timestamp", System.currentTimeMillis())
            put("chats",     chatsArray)
        }

        val plaintext                       = backupJson.toString().toByteArray(Charsets.UTF_8)
        val (encryptedBlob, keyFingerprint) = encryptWithPassword(plaintext, password)

        firestore.collection("users").document(uid)
            .collection("backup_meta").document("latest")
            .set(mapOf(
                "timestamp"      to System.currentTimeMillis(),
                "keyFingerprint" to keyFingerprint,
                "messageCount"   to chatsSnap.documents.size,
                "version"        to BACKUP_VERSION
            )).await()

        return BackupResult(
            encryptedBlob  = encryptedBlob,
            keyFingerprint = keyFingerprint,
            messageCount   = chatsSnap.documents.size
        )
    }

    // ── Decrypt & restore (password) ───────────────────────────────────

    suspend fun decryptAndRestore(encryptedBlob: String, password: String): RestoreResult {
        val uid       = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val plaintext = decryptWithPassword(encryptedBlob, password)
        return restoreFromPlaintext(plaintext, uid)
    }

    // ── Decrypt & restore (hex key) ────────────────────────────────────

    suspend fun decryptAndRestoreWithKey(encryptedBlob: String, hexKey: String): RestoreResult {
        val uid      = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val keyBytes = hexKey.trim().hexToBytes()
        if (keyBytes.size != 32) throw IllegalArgumentException("Key must be 64 hex characters (32 bytes)")
        val combined   = Base64.decode(encryptedBlob, Base64.NO_WRAP)
        val iv         = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)
        val secretKey  = SecretKeySpec(keyBytes, "AES")
        val cipher     = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val plaintext  = cipher.doFinal(ciphertext)
        return restoreFromPlaintext(plaintext, uid)
    }

    // ── Export backup key ───────────────────────────────────────────────

    suspend fun exportBackupKey(password: String): String {
        val saltHex = prefs.getString(KEY_SALT, null)
            ?: run {
                val s   = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
                val hex = s.toHexString()
                prefs.edit().putString(KEY_SALT, hex).apply()
                hex
            }
        val salt = saltHex.hexToBytes()
        val key  = deriveKey(password.toCharArray(), salt)
        return key.encoded.toHexString()
    }

    // ── Encrypt ────────────────────────────────────────────────────────

    private fun encryptWithPassword(data: ByteArray, password: String): Pair<String, String> {
        val rng  = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { rng.nextBytes(it) }
        val iv   = ByteArray(IV_LENGTH).also { rng.nextBytes(it) }
        prefs.edit().putString(KEY_SALT, salt.toHexString()).apply()
        val secretKey  = deriveKey(password.toCharArray(), salt)
        val cipher     = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext  = cipher.doFinal(data)
        val combined    = salt + iv + ciphertext
        val fingerprint = secretKey.encoded.copyOfRange(0, 8).toHexString()
        return Base64.encodeToString(combined, Base64.NO_WRAP) to fingerprint
    }

    // ── Decrypt ────────────────────────────────────────────────────────

    private fun decryptWithPassword(encryptedBase64: String, password: String): ByteArray {
        val combined   = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val salt       = combined.copyOfRange(0, SALT_LENGTH)
        val iv         = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)
        val secretKey  = deriveKey(password.toCharArray(), salt)
        val cipher     = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    // ── Restore from plaintext ───────────────────────────────────────────

    private suspend fun restoreFromPlaintext(plaintext: ByteArray, uid: String): RestoreResult {
        val backupJson = JSONObject(String(plaintext, Charsets.UTF_8))
        val backupUid  = backupJson.optString("uid")
        if (backupUid.isNotBlank() && backupUid != uid)
            throw SecurityException("Backup belongs to a different account")

        var restoredChats    = 0
        var restoredMessages = 0
        val batch      = firestore.batch()
        var batchCount = 0

        val chatsArray = backupJson.optJSONArray("chats") ?: JSONArray()
        for (i in 0 until chatsArray.length()) {
            val chatObj = chatsArray.getJSONObject(i)
            val chatId  = chatObj.optString("chatId")
            if (chatId.isBlank()) continue
            batch.set(firestore.collection("chats").document(chatId), jsonObjectToMap(chatObj))
            batchCount++
            restoredChats++

            val msgsArray = chatObj.optJSONArray("messages") ?: JSONArray()
            for (j in 0 until msgsArray.length()) {
                val msgObj    = msgsArray.getJSONObject(j)
                val messageId = msgObj.optString("messageId")
                if (messageId.isBlank()) continue
                val content = msgObj.optString("content")
                val reEnc   = if (content.isNotBlank()) {
                    try { EncryptionManager.encrypt(content) } catch (_: Exception) { content }
                } else content
                msgObj.put("content", reEnc)
                msgObj.put("isEncrypted", reEnc != content)
                batch.set(
                    firestore.collection("chats").document(chatId)
                        .collection("messages").document(messageId),
                    jsonObjectToMap(msgObj)
                )
                batchCount++
                restoredMessages++
                if (batchCount >= 490) { batch.commit().await(); batchCount = 0 }
            }
        }
        if (batchCount > 0) batch.commit().await()
        return RestoreResult(restoredChats, restoredMessages)
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec    = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val encoded = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(encoded, "AES")
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map  = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.opt(key)
        }
        return map
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray {
        val s = replace(" ", "")
        return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}

data class BackupResult(
    val encryptedBlob  : String,
    val keyFingerprint : String,
    val messageCount   : Int
)

data class RestoreResult(
    val restoredChats    : Int,
    val restoredMessages : Int
)
