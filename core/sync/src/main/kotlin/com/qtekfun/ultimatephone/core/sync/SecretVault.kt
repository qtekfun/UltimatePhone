package com.qtekfun.ultimatephone.core.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets for storage. The implementation decides where the key lives; tests use a fake. */
interface SecretCipher {
    /** Throws [SyncException] with [SyncError.CREDENTIALS_UNAVAILABLE] when secure storage cannot be used. */
    fun encrypt(plain: ByteArray): ByteArray

    /** Null when the blob cannot be decrypted (the key is gone or the blob was altered). */
    fun decrypt(blob: ByteArray): ByteArray?
}

/**
 * Turns the app password into a string that is safe to keep in DataStore, and back. The string is Base64 of
 * `version || cipher blob`; the password never touches disk in the clear.
 */
class SecretVault(private val cipher: SecretCipher) {
    fun seal(secret: String): String {
        val bytes = secret.toByteArray(Charsets.UTF_8)
        try {
            return Base64.getEncoder().encodeToString(byteArrayOf(VERSION) + cipher.encrypt(bytes))
        } finally {
            bytes.fill(0)
        }
    }

    /** Null when [sealed] is not readable any more, for example after the key was lost: the user must enter the password again. */
    fun open(sealed: String): String? {
        val raw = try {
            Base64.getDecoder().decode(sealed)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (raw.size < 2 || raw[0] != VERSION) return null
        val plain = cipher.decrypt(raw.copyOfRange(1, raw.size)) ?: return null
        return try {
            String(plain, Charsets.UTF_8)
        } finally {
            plain.fill(0)
        }
    }

    private companion object {
        const val VERSION: Byte = 1
    }
}

/**
 * AES-256-GCM with a key generated inside the Android Keystore, which keeps it out of the app's reach and, where the
 * device has the hardware, out of the OS too. The key is created on first use and cannot be exported.
 * Blob layout: `ivLength (1 byte) || iv || ciphertext+tag`.
 */
class KeystoreSecretCipher(private val alias: String = DEFAULT_ALIAS) : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true)!!)
        val iv = cipher.iv
        byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        throw SyncException(SyncError.CREDENTIALS_UNAVAILABLE, e.javaClass.simpleName, e)
    }

    override fun decrypt(blob: ByteArray): ByteArray? = try {
        val ivLength = blob.firstOrNull()?.toInt() ?: return null
        if (ivLength <= 0 || blob.size <= 1 + ivLength) return null
        val key = key(create = false) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 1, ivLength))
        cipher.doFinal(blob, 1 + ivLength, blob.size - 1 - ivLength)
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        null
    }

    private fun key(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "ultimatephone.sync.credentials"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val KEY_BITS = 256
    }
}
