package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRule
import com.qtekfun.ultimatephone.core.spam.sources.PackDatabase
import com.qtekfun.ultimatephone.core.spam.sources.PackEntry
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * The local pack made from a custom source: a sorted list of E.164 numbers, each stored as the 64-bit integer of its
 * digits. Layout: magic `UPNP`, format version, count (three big-endian ints), then `count` sorted longs.
 * A million numbers take 8 MB on disk, and a lookup is a binary search over the memory-mapped file.
 */
object NumberPackFile {
    internal const val MAGIC = 0x5550_4E50
    internal const val VERSION = 1
    internal const val HEADER_BYTES = 12
    internal const val ENTRY_BYTES = 8
    private const val MAX_DIGITS = 15

    /** `+34600123456` becomes 34600123456; anything that is not a plausible E.164 number gives null. */
    fun toKey(e164: String): Long? {
        val digits = e164.removePrefix("+")
        if (digits.isEmpty() || digits.length > MAX_DIGITS || !digits.all { it in '0'..'9' }) return null
        return digits.toLong()
    }

    /** Collects numbers and writes them as a pack. Duplicates collapse; invalid numbers are ignored. */
    class Writer {
        private var keys = LongArray(INITIAL_CAPACITY)
        private var size = 0

        fun add(e164: String) {
            val key = toKey(e164) ?: return
            if (size == keys.size) keys = keys.copyOf(keys.size * 2)
            keys[size++] = key
        }

        /** Writes the pack to [target] through a temporary file, so a reader never sees a half-written pack. @return the number of distinct numbers. */
        @Throws(IOException::class)
        fun writeTo(target: File): Int {
            val sorted = keys.copyOf(size).also { it.sort() }
            var distinct = 0
            for (i in sorted.indices) {
                if (i == 0 || sorted[i] != sorted[i - 1]) sorted[distinct++] = sorted[i]
            }
            val temp = File.createTempFile("pack-", ".part", target.absoluteFile.parentFile)
            try {
                FileChannel.open(temp.toPath(), StandardOpenOption.WRITE).use { channel ->
                    val header = ByteBuffer.allocate(HEADER_BYTES)
                    header.putInt(MAGIC).putInt(VERSION).putInt(distinct)
                    header.flip()
                    while (header.hasRemaining()) channel.write(header)
                    val chunk = ByteBuffer.allocate(CHUNK_ENTRIES * ENTRY_BYTES)
                    var index = 0
                    while (index < distinct) {
                        chunk.clear()
                        val end = minOf(distinct, index + CHUNK_ENTRIES)
                        while (index < end) chunk.putLong(sorted[index++])
                        chunk.flip()
                        while (chunk.hasRemaining()) channel.write(chunk)
                    }
                }
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                temp.delete()
            }
            return distinct
        }

        private companion object {
            const val INITIAL_CAPACITY = 1024
            const val CHUNK_ENTRIES = 8192
        }
    }

    /** Opens [file], or returns null when it is missing or is not a pack of this format. */
    fun open(file: File, label: String?): SortedNumberPack? {
        if (!file.isFile || file.length() < HEADER_BYTES) return null
        return try {
            FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                val valid = buffer.getInt(0) == MAGIC && buffer.getInt(Int.SIZE_BYTES) == VERSION &&
                    HEADER_BYTES + buffer.getInt(2 * Int.SIZE_BYTES).toLong() * ENTRY_BYTES == channel.size()
                if (valid) SortedNumberPack(buffer, buffer.getInt(2 * Int.SIZE_BYTES), label) else null
            }
        } catch (_: IOException) {
            null
        }
    }
}

/** A custom-source pack as a [PackDatabase]. Every hit carries [label] (the source's name) for the "why was this flagged" screen. */
class SortedNumberPack internal constructor(private val data: ByteBuffer, val count: Int, private val label: String?) : PackDatabase {
    override fun lookup(e164: String): PackEntry? {
        val key = NumberPackFile.toKey(e164) ?: return null
        var low = 0
        var high = count - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val value = data.getLong(NumberPackFile.HEADER_BYTES + middle * NumberPackFile.ENTRY_BYTES)
            when {
                value < key -> low = middle + 1
                value > key -> high = middle - 1
                else -> return PackEntry(e164, label = label)
            }
        }
        return null
    }

    override fun prefixRules(): List<PrefixRule> = emptyList()

    /** Nothing to release: the mapping is unmapped when the buffer is garbage collected. */
    override fun close() = Unit
}
