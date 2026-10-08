package com.qtekfun.ultimatephone.core.datapacks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The signed index of every pack published by UltimatePhone-data. Mirrors `manifest.json` of that repository. */
@Serializable
data class Manifest(val schema: Int, val generatedAt: String, val packs: List<PackEntry>) {
    fun find(id: String): PackEntry? = packs.firstOrNull { it.id == id }

    companion object {
        const val SUPPORTED_SCHEMA = 1
    }
}

@Serializable
data class PackEntry(
    val id: String,
    /** "businesses", "spam" or "rules". */
    val type: String,
    val region: String? = null,
    val version: String,
    val url: String,
    /** SHA-256 (hex) and size of the file served at [url], which is compressed. */
    val sha256: String,
    val bytes: Long = 0,
    val compression: String = COMPRESSION_XZ,
    /** SHA-256 (hex) and size of the file inside the compressed one; checked after unpacking when present. */
    val uncompressedSha256: String? = null,
    val uncompressedBytes: Long = 0,
    val entries: Long = 0,
    val license: String = "",
    val attribution: String = "",
    val dependsOn: List<String> = emptyList(),
    /** Optional free text for the pack list, for example the coverage note. */
    @SerialName("note") val note: String? = null
) {
    companion object {
        const val COMPRESSION_XZ = "xz"
        const val COMPRESSION_NONE = "none"
    }
}

object ManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /** @throws ManifestException when the bytes are not a manifest this version of the app understands. */
    fun parse(bytes: ByteArray): Manifest {
        val manifest = decode(bytes)
        if (manifest.schema != Manifest.SUPPORTED_SCHEMA) throw ManifestException("Unsupported manifest schema ${manifest.schema}")
        val ids = manifest.packs.map { it.id }
        if (ids.size != ids.toSet().size) throw ManifestException("Duplicate pack ids")
        manifest.packs.forEach { PackIds.require(it.id) }
        return manifest
    }

    // SerializationException is an IllegalArgumentException, so this also covers bad field values.
    private fun decode(bytes: ByteArray): Manifest = try {
        json.decodeFromString(Manifest.serializer(), bytes.decodeToString())
    } catch (e: IllegalArgumentException) {
        throw ManifestException("Malformed manifest", e)
    }
}

class ManifestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Pack ids become file names, so only a conservative set of characters is accepted. */
object PackIds {
    private val valid = Regex("[a-z0-9][a-z0-9._-]{0,63}")

    fun isValid(id: String): Boolean = valid.matches(id) && !id.contains("..")

    fun require(id: String) {
        if (!isValid(id)) throw ManifestException("Invalid pack id")
    }
}
