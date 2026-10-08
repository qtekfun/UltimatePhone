package com.qtekfun.ultimatephone.core.settings

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * One independently restorable part of a backup (app settings, the own list, the Nextcloud account...). Features that
 * own settings register an implementation; the backup screens and the file format need no change for a new one.
 *
 * Contents are an opaque string chosen by the section.
 */
interface ExportSection {
    /** Stable id stored in the file. Never reuse or rename one. */
    val id: String

    suspend fun export(): String

    /** Throws when [data] cannot be restored. Must not change anything. */
    fun validate(data: String)

    /** Applies [data] as a whole or not at all. Called only after [validate] accepted it. */
    suspend fun restore(data: String)
}

/** A decrypted and checked backup, ready for the user to choose sections from. */
class OpenedBackup internal constructor(val createdAt: Long, internal val data: Map<String, String>) {
    val sectionIds: Set<String> get() = data.keys
}

/** What a restore did: sections applied, and sections that failed (each failed as a unit, leaving that section as it was). */
data class RestoreReport(val restored: List<String>, val failed: Map<String, String>, val unknown: List<String>)

/** Builds and reads backup files from the registered [ExportSection]s. CPU-heavy calls should run off the main thread. */
class BackupService(
    private val sections: Collection<ExportSection>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val kdf: KdfParams = KdfParams.DEFAULT
) {
    val sectionIds: List<String> get() = sections.map { it.id }

    /** The encrypted file for [selected] sections (all by default). */
    suspend fun export(password: CharArray, selected: Set<String>? = null): ByteArray {
        val data = LinkedHashMap<String, String>()
        for (section in sections) if (selected == null || section.id in selected) data[section.id] = section.export()
        val plain = BackupPayload.encode(clock(), data)
        try {
            return BackupCrypto.encrypt(plain, password, kdf)
        } finally {
            plain.fill(0)
        }
    }

    /** Decrypts and validates [file]. Throws [BackupException] for a wrong password, an altered or unsupported file. */
    fun open(file: ByteArray, password: CharArray): OpenedBackup {
        val plain = BackupCrypto.decrypt(file, password)
        try {
            return BackupPayload.decode(plain)
        } finally {
            plain.fill(0)
        }
    }

    /**
     * Restores [selected] sections. Every selected section is validated before the first one is applied, so a bad
     * section changes nothing at all; after that each section is applied as a unit and a failure in one is reported
     * without stopping the others.
     */
    suspend fun restore(backup: OpenedBackup, selected: Set<String>): RestoreReport {
        val known = sections.associateBy { it.id }
        val todo = backup.data.filterKeys { it in selected && it in known }
        for ((id, data) in todo) {
            try {
                known.getValue(id).validate(data)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                throw BackupException(BackupFailure.INVALID_PAYLOAD, "Section $id is not valid", e)
            }
        }
        val restored = ArrayList<String>()
        val failed = LinkedHashMap<String, String>()
        for ((id, data) in todo) {
            try {
                known.getValue(id).restore(data)
                restored += id
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                failed[id] = e.javaClass.simpleName
            }
        }
        return RestoreReport(restored, failed, unknown = backup.data.keys.filter { it !in known })
    }
}

/** The plaintext inside the container: `{"format":1,"createdAt":ms,"sections":{"id":"data"}}`. */
internal object BackupPayload {
    const val FORMAT = 1

    fun encode(createdAt: Long, sections: Map<String, String>): ByteArray = buildJsonObject {
        put("format", FORMAT)
        put("createdAt", createdAt)
        put("sections", buildJsonObject { sections.forEach { (id, data) -> put(id, data) } })
    }.toString().toByteArray(Charsets.UTF_8)

    private fun invalid(cause: Throwable? = null): Nothing = throw BackupException(BackupFailure.INVALID_PAYLOAD, cause = cause)

    fun decode(bytes: ByteArray): OpenedBackup {
        try {
            val root = Json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject ?: invalid()
            val format = (root["format"] as? JsonPrimitive)?.int ?: invalid()
            if (format > FORMAT) throw BackupException(BackupFailure.UNSUPPORTED_VERSION, "Payload format $format")
            val createdAt = (root["createdAt"] as? JsonPrimitive)?.long ?: 0L
            val sections = root["sections"] as? JsonObject ?: invalid()
            val data = LinkedHashMap<String, String>()
            for ((id, value) in sections) {
                data[id] = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: invalid()
            }
            return OpenedBackup(createdAt, data)
        } catch (e: SerializationException) {
            invalid(e)
        } catch (e: IllegalArgumentException) {
            invalid(e)
        }
    }
}
