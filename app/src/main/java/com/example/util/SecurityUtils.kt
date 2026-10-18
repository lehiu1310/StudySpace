package com.example.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.mindrot.jbcrypt.BCrypt
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurityUtils {
    private const val BACKUP_PREFIX = "KS1:"
    private const val KEY_ALIAS = "studyspace_backup_aes_key"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        return try {
            when {
                isBcryptHash(storedHash) -> BCrypt.checkpw(password, storedHash)
                isLegacySha256(storedHash) -> storedHash.equals(legacySha256(password), ignoreCase = true)
                else -> storedHash == password
            }
        } catch (e: Exception) {
            false
        }
    }

    fun needsPasswordRehash(storedHash: String): Boolean = !isBcryptHash(storedHash)

    fun encryptAes(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateBackupKey())
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val payload = cipher.iv + encryptedBytes
            BACKUP_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            plainText
        }
    }

    fun decryptAes(encryptedText: String): String {
        val trimmed = encryptedText.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        if (!trimmed.startsWith(BACKUP_PREFIX)) {
            return trimmed
        }

        return try {
            val payload = Base64.decode(trimmed.removePrefix(BACKUP_PREFIX), Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, 12)
            val encryptedBytes = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateBackupKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            trimmed
        }
    }

    private fun isBcryptHash(value: String): Boolean {
        return value.startsWith("\$2a$") || value.startsWith("\$2b$") || value.startsWith("\$2y$")
    }

    private fun isLegacySha256(value: String): Boolean {
        return value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun legacySha256(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salted = password + "StudySpaceSecureSalt123!"
        val bytes = digest.digest(salted.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateBackupKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
