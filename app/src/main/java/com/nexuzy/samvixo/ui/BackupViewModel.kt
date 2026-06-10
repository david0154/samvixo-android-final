package com.nexuzy.samvixo.ui

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nexuzy.samvixo.util.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BackupState(
    val isConnected    : Boolean = false,
    val lastBackupTime : String  = "Never",
    val keyFingerprint : String  = "",
    val exportedKey    : String  = "",
    val showKeyExport  : Boolean = false,
    val isLoading      : Boolean = false,
    val message        : String? = null,
    val signInIntent   : Intent? = null   // emitted once to trigger launcher
)

class BackupViewModel(private val context: Context) : ViewModel() {

    private val backupManager = BackupManager(context)

    private val _state = MutableStateFlow(
        BackupState(
            isConnected    = backupManager.isDriveConnected(),
            lastBackupTime = backupManager.getLastBackupTime()
        )
    )
    val state: StateFlow<BackupState> = _state

    // ── Drive connect ────────────────────────────────────────────────────────
    fun getSignInIntent(): Intent = backupManager.buildSignInIntent()

    fun onSignInResult(result: ActivityResult) {
        viewModelScope.launch {
            val ok = backupManager.handleSignInResult(result.data)
            if (ok) {
                _state.value = _state.value.copy(
                    isConnected = true,
                    message     = "✅ Google Drive connected"
                )
            } else {
                _state.value = _state.value.copy(
                    message = "❌ Google Sign-In failed. Make sure you allow Drive access."
                )
            }
        }
    }

    // ── Backup ───────────────────────────────────────────────────────────────
    fun backupNow(password: String) {
        if (password.length < 8) {
            _state.value = _state.value.copy(message = "⚠️ Password must be at least 8 characters")
            return
        }
        _state.value = _state.value.copy(isLoading = true, message = "🔐 Encrypting chats…")
        viewModelScope.launch {
            try {
                val result = backupManager.createEncryptedBackup(password)
                _state.value = _state.value.copy(message = "☁️ Uploading to Google Drive…")
                val fileName = backupManager.uploadToDrive(result.encryptedBlob)
                backupManager.saveLastBackupTime()
                _state.value = _state.value.copy(
                    isLoading      = false,
                    lastBackupTime = backupManager.getLastBackupTime(),
                    keyFingerprint = result.keyFingerprint,
                    message        = "✅ ${result.messageCount} chats backed up\n📄 $fileName\n🔑 Key ID: ${result.keyFingerprint}"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message   = "❌ Backup failed: ${e.message}"
                )
            }
        }
    }

    // ── Export key ──────────────────────────────────────────────────────────
    fun exportBackupKey(password: String) {
        if (password.length < 8) {
            _state.value = _state.value.copy(message = "⚠️ Enter your backup password first")
            return
        }
        viewModelScope.launch {
            try {
                val key = backupManager.exportBackupKey(password)
                _state.value = _state.value.copy(
                    exportedKey   = key,
                    showKeyExport = true,
                    message       = "🔑 Save this 64-digit key safely. It can restore chats on any device."
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = "❌ ${e.message}")
            }
        }
    }

    // ── Restore with password ────────────────────────────────────────────────
    fun restoreWithPassword(password: String) {
        if (password.isBlank()) {
            _state.value = _state.value.copy(message = "⚠️ Enter your backup password")
            return
        }
        _state.value = _state.value.copy(isLoading = true, message = "⬇️ Downloading backup from Drive…")
        viewModelScope.launch {
            try {
                val encryptedBlob = backupManager.downloadFromDrive()
                _state.value = _state.value.copy(message = "🔓 Decrypting…")
                val result = backupManager.decryptAndRestore(encryptedBlob, password)
                _state.value = _state.value.copy(
                    isLoading = false,
                    message   = "✅ Restored ${result.restoredChats} chats, ${result.restoredMessages} messages. Please restart the app."
                )
            } catch (e: SecurityException) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message   = "❌ Wrong password or corrupted backup"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message   = "❌ Restore failed: ${e.message}"
                )
            }
        }
    }

    // ── Restore with hex key ─────────────────────────────────────────────────
    fun restoreWithHexKey(hexKey: String) {
        val cleanKey = hexKey.trim().replace(" ", "")
        if (cleanKey.length != 64) {
            _state.value = _state.value.copy(message = "⚠️ Backup key must be exactly 64 hex characters")
            return
        }
        _state.value = _state.value.copy(isLoading = true, message = "⬇️ Downloading backup from Drive…")
        viewModelScope.launch {
            try {
                val encryptedBlob = backupManager.downloadFromDrive()
                _state.value = _state.value.copy(message = "🔓 Decrypting with key…")
                val result = backupManager.decryptAndRestoreWithKey(encryptedBlob, cleanKey)
                _state.value = _state.value.copy(
                    isLoading = false,
                    message   = "✅ Restored ${result.restoredChats} chats, ${result.restoredMessages} messages."
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message   = "❌ Key restore failed: ${e.message}"
                )
            }
        }
    }

    // ── Disconnect ───────────────────────────────────────────────────────────
    fun disconnectDrive() {
        backupManager.disconnectDrive()
        _state.value = BackupState(message = "✅ Google Drive disconnected")
    }

    fun hideKeyExport() {
        _state.value = _state.value.copy(exportedKey = "", showKeyExport = false)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BackupViewModel(context) as T
        }
    }
}
