package com.qtekfun.ultimatephone.core.settings

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Why a backup file could not be read. None of these ever leaves the app in a partly applied state. */
enum class BackupFailure {
    /** The file does not start with the backup magic bytes. */
    NOT_A_BACKUP,

    /** Written by a newer version of the app than this one understands. */
    UNSUPPORTED_VERSION,

    /** Truncated, or the header holds values no file of ours has. */
    CORRUPT,

    /** Authentication failed: the password is wrong, or the file was altered. The two cannot be told apart. */
    WRONG_PASSWORD_OR_ALTERED,

    /** The contents decrypted fine but are not a valid backup payload. */
    INVALID_PAYLOAD
}

class BackupException(val failure: BackupFailure, message: String? = null, cause: Throwable? = null) : Exception(message ?: failure.name, cause)

/** Argon2id cost. Stored in the file header (and authenticated), so it can be raised later without breaking old files. */
data class KdfParams(val memoryKiB: Int, val iterations: Int, val lanes: Int) {
    companion object {
        /** 32 MiB, 4 passes, one lane: above the OWASP floor for Argon2id and comfortable on a low-end phone. */
        val DEFAULT = KdfParams(memoryKiB = 32 * 1024, iterations = 4, lanes = 1)

        // Limits applied when reading a file, so a crafted header cannot make the app allocate gigabytes or spin.
        const val MAX_MEMORY_KIB = 128 * 1024
        const val MAX_ITERATIONS = 16
        const val MAX_LANES = 8
        const val MIN_MEMORY_KIB = 8

        /** Fast settings for unit tests. Never used by the app. */
        val FOR_TESTS = KdfParams(memoryKiB = 64, iterations = 1, lanes = 1)
    }
}

/**
 * The encrypted container: a header, then AES-256-GCM ciphertext with its tag.
 *
 * ```
 * magic "UPBK" (4) | version (1) | kdf id (1: Argon2id v1.3) | memoryKiB (u32) | iterations (u32) | lanes (1)
 * | saltLength (1) | salt | nonceLength (1) | nonce | ciphertext || 16-byte tag
 * ```
 *
 * The whole header is the GCM associated data: changing any header byte, including the KDF cost, makes the
 * authentication fail. The key comes from the password with Argon2id (BouncyCastle's `Argon2BytesGenerator`, a
 * pure-Java implementation). The password is normalised to Unicode NFC first so the same text typed on another
 * keyboard layout or device gives the same key.
 */
object BackupCrypto {
    const val VERSION = 1
    private val MAGIC = byteArrayOf('U'.code.toByte(), 'P'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val KDF_ARGON2ID = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val KEY_BYTES = 32
    private const val FIXED_HEADER = 4 + 1 + 1 + 4 + 4 + 1

    fun encrypt(plain: ByteArray, password: CharArray, params: KdfParams = KdfParams.DEFAULT, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = header(params, salt, nonce)
        val key = deriveKey(password, params, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plain)
        } finally {
            key.fill(0)
        }
    }

    /** Returns the plaintext, or throws [BackupException]. Nothing is returned for a file that fails authentication. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        val parsed = parse(file)
        val key = deriveKey(password, parsed.params, parsed.salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, parsed.nonce))
            cipher.updateAAD(file, 0, parsed.headerLength)
            return cipher.doFinal(file, parsed.headerLength, file.size - parsed.headerLength)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupFailure.WRONG_PASSWORD_OR_ALTERED, cause = e)
        } finally {
            key.fill(0)
        }
    }

    private class Parsed(val params: KdfParams, val salt: ByteArray, val nonce: ByteArray, val headerLength: Int)

    @Suppress("ThrowsCount") // Each distinct way a header can be wrong is a distinct typed failure.
    private fun parse(file: ByteArray): Parsed {
        if (file.size < MAGIC.size || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw BackupException(BackupFailure.NOT_A_BACKUP)
        if (file.size < FIXED_HEADER + 1) throw BackupException(BackupFailure.CORRUPT, "Truncated header")
        val buffer = ByteBuffer.wrap(file)
        buffer.position(MAGIC.size)
        val version = buffer.get().toInt() and 0xff
        if (version > VERSION) throw BackupException(BackupFailure.UNSUPPORTED_VERSION, "Version $version")
        if (version != VERSION) throw BackupException(BackupFailure.CORRUPT, "Unknown version $version")
        if ((buffer.get().toInt() and 0xff) != KDF_ARGON2ID) throw BackupException(BackupFailure.CORRUPT, "Unknown KDF")
        val memory = buffer.int
        val iterations = buffer.int
        val lanes = buffer.get().toInt() and 0xff
        val params = KdfParams(memory, iterations, lanes)
        if (memory !in KdfParams.MIN_MEMORY_KIB..KdfParams.MAX_MEMORY_KIB || memory < 8 * lanes ||
            iterations !in 1..KdfParams.MAX_ITERATIONS || lanes !in 1..KdfParams.MAX_LANES
        ) {
            throw BackupException(BackupFailure.CORRUPT, "KDF cost out of range")
        }
        val salt = readBlock(buffer, SALT_BYTES)
        val nonce = readBlock(buffer, NONCE_BYTES)
        val headerLength = buffer.position()
        if (file.size - headerLength < TAG_BYTES) throw BackupException(BackupFailure.CORRUPT, "Truncated")
        return Parsed(params, salt, nonce, headerLength)
    }

    private fun readBlock(buffer: ByteBuffer, expected: Int): ByteArray {
        if (buffer.remaining() < 1) throw BackupException(BackupFailure.CORRUPT, "Truncated header")
        val length = buffer.get().toInt() and 0xff
        if (length != expected || buffer.remaining() < length) throw BackupException(BackupFailure.CORRUPT, "Bad header field")
        return ByteArray(length).also(buffer::get)
    }

    private fun header(params: KdfParams, salt: ByteArray, nonce: ByteArray): ByteArray {
        require(params.memoryKiB in KdfParams.MIN_MEMORY_KIB..KdfParams.MAX_MEMORY_KIB && params.iterations in 1..KdfParams.MAX_ITERATIONS)
        require(params.lanes in 1..KdfParams.MAX_LANES)
        val buffer = ByteBuffer.allocate(FIXED_HEADER + 1 + salt.size + 1 + nonce.size)
        buffer.put(MAGIC).put(VERSION.toByte()).put(KDF_ARGON2ID.toByte())
        buffer.putInt(params.memoryKiB).putInt(params.iterations).put(params.lanes.toByte())
        buffer.put(salt.size.toByte()).put(salt).put(nonce.size.toByte()).put(nonce)
        return buffer.array()
    }

    private fun deriveKey(password: CharArray, params: KdfParams, salt: ByteArray): ByteArray {
        val normalised = Normalizer.normalize(String(password), Normalizer.Form.NFC)
        val passwordBytes = normalised.toByteArray(Charsets.UTF_8)
        try {
            val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.lanes)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(argon)
            return ByteArray(KEY_BYTES).also { generator.generateBytes(passwordBytes, it) }
        } finally {
            passwordBytes.fill(0)
        }
    }
}
