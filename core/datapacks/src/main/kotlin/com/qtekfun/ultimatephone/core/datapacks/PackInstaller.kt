package com.qtekfun.ultimatephone.core.datapacks

import java.io.File
import java.io.IOException
import java.io.InputStream
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
        if (!entry.url.startsWith("https://")) return InstallResult.Failed(InstallFailure.INSECURE_URL)
        if (entry.compression != PackEntry.COMPRESSION_XZ && entry.compression != PackEntry.COMPRESSION_NONE) {
            return InstallResult.Failed(InstallFailure.UNSUPPORTED)
        }
        val download = store.newTempFile("dl-")
        val unpacked = store.newTempFile("db-")
        try {
            try {
                fetcher.download(entry.url, download, onProgress)
            } catch (_: IOException) {
                return InstallResult.Failed(InstallFailure.NETWORK)
            }
            if (entry.bytes > 0 && download.length() != entry.bytes) return InstallResult.Failed(InstallFailure.SIZE_MISMATCH)
            if (!Hashing.sha256Hex(download).equals(entry.sha256, ignoreCase = true)) {
                return InstallResult.Failed(InstallFailure.CHECKSUM_MISMATCH)
            }
            val failure = unpack(entry, download, unpacked)
            if (failure != null) return InstallResult.Failed(failure)
            store.install(entry.id, entry.version, unpacked)
            return InstallResult.Installed(entry.id, entry.version)
        } finally {
            download.delete()
            unpacked.delete()
        }
    }

    private fun unpack(entry: PackEntry, source: File, target: File): InstallFailure? {
        // A hostile or corrupt archive must not fill the disk: never write more than the manifest announces.
        val limit = if (entry.uncompressedBytes > 0) entry.uncompressedBytes else MAX_UNPACKED_BYTES
        try {
            val input: InputStream = if (entry.compression == PackEntry.COMPRESSION_XZ) {
                XZInputStream(source.inputStream().buffered(), MAX_DICTIONARY_KB)
            } else {
                source.inputStream().buffered()
            }
            var total = 0L
            input.use { stream ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > limit) return InstallFailure.TOO_LARGE
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (entry.uncompressedBytes > 0 && total != entry.uncompressedBytes) return InstallFailure.SIZE_MISMATCH
        } catch (_: IOException) {
            return InstallFailure.BAD_ARCHIVE
        }
        val expected = entry.uncompressedSha256
        if (expected != null && !Hashing.sha256Hex(target).equals(expected, ignoreCase = true)) return InstallFailure.CHECKSUM_MISMATCH
        return null
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val MAX_UNPACKED_BYTES = 2L * 1024 * 1024 * 1024

        /** The pipeline uses an 8 MiB dictionary; anything much bigger is refused instead of exhausting memory. */
        const val MAX_DICTIONARY_KB = 32 * 1024
    }
}
