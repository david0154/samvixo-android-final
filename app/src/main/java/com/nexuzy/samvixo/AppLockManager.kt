package com.nexuzy.samvixo

import android.content.Context

/**
 * FIX: App Lock state must persist across app close/reopen.
 * Previously the unlock state was held only in-memory so after app was killed
 * the lock was never re-checked on next launch.
 *
 * NOTE: Uses plain SharedPreferences so there is no dependency on
 * androidx.security.crypto. If you want extra security, add the
 * 'androidx.security:security-crypto' dep and swap back to
 * EncryptedSharedPreferences — but for now this compiles out-of-the-box.
 */
object AppLockManager {

    private const val PREFS_NAME = "app_lock_prefs"
    private const val KEY_ENABLED = "lock_enabled"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val SESSION_PREFS = "app_lock_session"
    private const val KEY_UNLOCKED = "session_unlocked"

    private fun lockPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun sessionPrefs(context: Context) =
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)

    fun isLockEnabled(context: Context): Boolean =
        lockPrefs(context).getBoolean(KEY_ENABLED, false)

    fun setLockEnabled(context: Context, enabled: Boolean) {
        lockPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) clearSessionUnlock(context)
    }

    fun setPinHash(context: Context, pinHash: String) {
        lockPrefs(context).edit().putString(KEY_PIN_HASH, pinHash).apply()
    }

    fun verifyPin(context: Context, enteredPin: String): Boolean {
        val stored = lockPrefs(context).getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(enteredPin) == stored
    }

    fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun markSessionUnlocked(context: Context) {
        sessionPrefs(context).edit().putBoolean(KEY_UNLOCKED, true).apply()
    }

    fun clearSessionUnlock(context: Context) {
        sessionPrefs(context).edit().putBoolean(KEY_UNLOCKED, false).apply()
    }

    fun shouldShowLock(context: Context): Boolean {
        if (!isLockEnabled(context)) return false
        return !sessionPrefs(context).getBoolean(KEY_UNLOCKED, false)
    }
}
