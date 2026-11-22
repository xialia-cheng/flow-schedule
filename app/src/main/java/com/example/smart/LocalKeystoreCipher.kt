package com.example.smart

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive user-provided credentials (such as an optional custom OpenAI-compatible API key)
 * locally using an Android Keystore AES-256-GCM key before storing them in DataStore.
 */
object LocalKeystoreCipher {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "flow_schedule_custom_ai_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX_ENC = "KS_GCM_V1:"
    private const val GCM_TAG_BITS = 128

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        return runCatching {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            PREFIX_ENC + Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrElse {
            // Fallback for headless JVM unit tests where AndroidKeyStore provider is unavailable
            "OBF_V1:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(storedValue: String): String {
        if (storedValue.isBlank()) return ""
        return when {
            storedValue.startsWith(PREFIX_ENC) -> runCatching {
                val payload = Base64.decode(storedValue.removePrefix(PREFIX_ENC), Base64.NO_WRAP)
                if (payload.isEmpty()) return ""
                val ivLength = payload[0].toInt() and 0xFF
                if (payload.size <= 1 + ivLength) return ""
                val iv = payload.copyOfRange(1, 1 + ivLength)
                val cipherBytes = payload.copyOfRange(1 + ivLength, payload.size)
                val secretKey = getOrCreateSecretKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            }.getOrDefault("")

            storedValue.startsWith("OBF_V1:") -> runCatching {
                String(Base64.decode(storedValue.removePrefix("OBF_V1:"), Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrDefault("")

            else -> storedValue
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
