package com.qtekfun.ultimatephone.core.datapacks

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.tukaani.xz.XZInputStream

sealed interface InstallResult {
    data class Installed(val id: String, val version: String) : InstallResult

    data class Failed(val reason: InstallFailure) : InstallResult
}

enum class InstallFailure { NETWORK, SIZE_MISMATCH, CHECKSUM_MISMATCH, BAD_ARCHIVE, TOO_LARGE, UNSUPPORTED, INSECURE_URL }

/**
 * Downloads a pack, checks its size and SHA-256, unpacks it, checks the unpacked SHA-256 when the manifest has one, and
 * only then moves it into the [PackStore]. Any failure leaves the previous version of the pack untouched.
 */
class PackInstaller(private val fetcher: HttpFetcher, private val store: PackStore) {
    fun install(entry: PackEntry, onProgress: (Long) -> Unit = {}): InstallResult {
        precheck(entry)?.let { return InstallResult.Failed(it) }
        val download = store.newTempFile("dl-")
        val unpacked = store.newTempFile("db-")
        try {
            val failure = fetchAndVerify(entry, download, onProgress) ?: unpack(entry, download, unpacked)
            if (failure != null) return InstallResult.Failed(failure)
            store.install(entry.id, entry.version, unpacked)
            return InstallResult.Installed(entry.id, entry.version)
        } finally {
            download.delete()
            unpacked.delete()
        }
    }

    private fun precheck(entry: PackEntry): InstallFailure? = when {
        !entry.url.startsWith("https://") -> InstallFailure.INSECURE_URL
        entry.compression != PackEntry.COMPRESSION_XZ && entry.compression != PackEntry.COMPRESSION_NONE -> InstallFailure.UNSUPPORTED
        else -> null
    }

    private fun fetchAndVerify(entry: PackEntry, target: File, onProgress: (Long) -> Unit): InstallFailure? {
        try {
            fetcher.download(entry.url, target, onProgress)
        } catch (_: IOException) {
            return InstallFailure.NETWORK
        }
        return when {
            entry.bytes > 0 && target.length() != entry.bytes -> InstallFailure.SIZE_MISMATCH
            !Hashing.sha256Hex(target).equals(entry.sha256, ignoreCase = true) -> InstallFailure.CHECKSUM_MISMATCH
            else -> null
        }
    }

    private fun unpack(entry: PackEntry, source: File, target: File): InstallFailure? {
        // A hostile or corrupt archive must not fill the disk: never write more than the manifest announces.
        val limit = if (entry.uncompressedBytes > 0) entry.uncompressedBytes else MAX_UNPACKED_BYTES
        val total = try {
            openStream(entry, source).use { stream -> target.outputStream().use { copyLimited(stream, it, limit) } }
        } catch (_: IOException) {
            return InstallFailure.BAD_ARCHIVE
        }
        return when {
            total < 0 -> InstallFailure.TOO_LARGE
            entry.uncompressedBytes > 0 && total != entry.uncompressedBytes -> InstallFailure.SIZE_MISMATCH
            entry.uncompressedSha256 != null && !Hashing.sha256Hex(target).equals(entry.uncompressedSha256, ignoreCase = true) ->
                InstallFailure.CHECKSUM_MISMATCH
            else -> null
        }
    }

    private fun openStream(entry: PackEntry, source: File): InputStream = if (entry.compression == PackEntry.COMPRESSION_XZ) {
        XZInputStream(source.inputStream().buffered(), MAX_DICTIONARY_KB)
    } else {
        source.inputStream().buffered()
    }

    /** @return the number of bytes copied, or -1 when more than [limit] bytes would have been written. */
    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            if (total > limit) return -1
            output.write(buffer, 0, read)
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val MAX_UNPACKED_BYTES = 2L * 1024 * 1024 * 1024

        /** The pipeline uses an 8 MiB dictionary; anything much bigger is refused instead of exhausting memory. */
        const val MAX_DICTIONARY_KB = 32 * 1024
    }
}
