package com.nexuzy.samvixo.util

/**
 * DEPRECATED — SecurityUtils has been removed.
 *
 * Use EncryptionManager instead:
 *   - EncryptionManager.encrypt(text)   — AES-256-GCM, Base64.NO_WRAP output (safe for Firestore)
 *   - EncryptionManager.decrypt(encoded) — decrypts EncryptionManager-encrypted strings
 *
 * Reason for removal:
 *   - SecurityUtils used a different key alias ("SamvixoMasterKey" vs "samvixo_message_key")
 *   - SecurityUtils used Base64.DEFAULT which includes newlines — bad for Firestore string fields
 *   - Having two AES-GCM objects with different keys caused cross-encrypt/decrypt failures
 */
@Deprecated(
    message = "Use EncryptionManager instead. SecurityUtils key alias conflicts with EncryptionManager.",
    replaceWith = ReplaceWith("EncryptionManager", "com.nexuzy.samvixo.util.EncryptionManager")
)
object SecurityUtils {
    @Deprecated("Use EncryptionManager.encrypt()", ReplaceWith("EncryptionManager.encrypt(text)"))
    fun encrypt(text: String): String = EncryptionManager.encrypt(text)

    @Deprecated("Use EncryptionManager.decrypt()", ReplaceWith("EncryptionManager.decrypt(encryptedBase64)"))
    fun decrypt(encryptedBase64: String): String = EncryptionManager.decrypt(encryptedBase64)
}
