package com.qtekfun.ultimatephone.core.datapacks

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Installed packs on disk: `<root>/<id>.db` plus `installed.txt` with `id<TAB>version` lines. A pack is replaced by
 * moving a finished file over the old one, so a reader never sees a half-written pack.
 */
class PackStore(private val root: File) {
    private val index = File(root, "installed.txt")

    init {
        root.mkdirs()
    }

    fun fileFor(id: String): File {
        PackIds.require(id)
        return File(root, "$id.db")
    }

    fun installed(): Map<String, String> {
        if (!index.exists()) return emptyMap()
        return index.readLines().mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size == 2 && PackIds.isValid(parts[0]) && parts[1].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()
    }

    fun versionOf(id: String): String? = installed()[id]

    /** Moves [finished] into place as pack [id] and records [version]. */
    @Throws(IOException::class)
    fun install(id: String, version: String, finished: File) {
        val target = fileFor(id)
        Files.move(finished.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        writeIndex(installed() + (id to version))
    }

    fun remove(id: String) {
        fileFor(id).delete()
        writeIndex(installed() - id)
    }

    fun sizeOnDisk(): Long = root.listFiles { f -> f.name.endsWith(".db") }.orEmpty().sumOf { it.length() }

    /** Temporary files live next to the packs, so the final move never crosses a file system. */
    fun newTempFile(prefix: String): File = File.createTempFile(prefix, ".part", root)

    private fun writeIndex(map: Map<String, String>) {
        val temp = File(root, "installed.txt.part")
        temp.writeText(map.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value}" })
        Files.move(temp.toPath(), index.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
