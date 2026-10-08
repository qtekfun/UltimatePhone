package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.datapacks.Manifest
import com.qtekfun.ultimatephone.core.datapacks.PackEntry
import java.util.Locale

/** A pack of the manifest and what is installed of it. */
data class PackItem(val entry: PackEntry, val installedVersion: String?) {
    val isInstalled: Boolean get() = installedVersion != null
    val updateAvailable: Boolean get() = installedVersion != null && installedVersion != entry.version

    /** Compressed size of the download. */
    val downloadBytes: Long get() = entry.bytes

    /** Size on disk once unpacked; the compressed size when the manifest does not say. */
    val installedBytes: Long get() = if (entry.uncompressedBytes > 0) entry.uncompressedBytes else entry.bytes
}

/** Packs of one region ([region] is null for packs that belong to no country, such as a global spam list). */
data class RegionGroup(val region: String?, val packs: List<PackItem>) {
    val downloadBytes: Long get() = packs.sumOf { it.downloadBytes }
    val installedBytes: Long get() = packs.sumOf { it.installedBytes }
    val isFullyInstalled: Boolean get() = packs.isNotEmpty() && packs.all { it.isInstalled }
    val isPartlyInstalled: Boolean get() = packs.any { it.isInstalled }
    val hasUpdate: Boolean get() = packs.any { it.updateAvailable }

    /** Download size of what is still missing or outdated. */
    val pendingDownloadBytes: Long get() = packs.filter { !it.isInstalled || it.updateAvailable }.sumOf { it.downloadBytes }
}

object PackCatalog {
    private val TYPE_ORDER = listOf(PackTypes.BUSINESSES, PackTypes.RULES, PackTypes.SPAM)

    /** Regions in alphabetical order of their country name, packs without a region last. Unknown pack types are left out. */
    fun group(manifest: Manifest, installed: Map<String, String>, locale: Locale = Locale.getDefault()): List<RegionGroup> {
        val items = manifest.packs.filter { it.type in UpdatePlanner.KNOWN_TYPES }.map { PackItem(it, installed[it.id]) }
        val byRegion = items.groupBy { it.entry.region?.uppercase() }
        return byRegion.map { (region, packs) ->
            RegionGroup(region, packs.sortedWith(compareBy({ TYPE_ORDER.indexOf(it.entry.type) }, { it.entry.id })))
        }.sortedWith(compareBy({ it.region == null }, { it.region?.let { r -> regionName(r, locale).lowercase(locale) } }))
    }

    /** The packs to fetch to cover [regions]: every pack of those regions. */
    fun packsFor(manifest: Manifest, regions: Set<String>): List<PackEntry> {
        val wanted = regions.map { it.uppercase() }.toSet()
        return manifest.packs.filter { it.type in UpdatePlanner.KNOWN_TYPES && it.region?.uppercase() in wanted }
    }

    /** Human name of an ISO region in [locale] ("Spain", "España"); the code itself when the platform does not know it. */
    fun regionName(region: String, locale: Locale = Locale.getDefault()): String {
        val name = Locale.Builder().setRegion(region).build().getDisplayCountry(locale)
        return name.ifBlank { region }
    }
}

/** Sizes as people read them: decimal units, one decimal from megabytes up. */
object SizeFormat {
    private const val KILO = 1000.0

    fun format(bytes: Long, locale: Locale = Locale.getDefault()): String {
        val value = bytes.coerceAtLeast(0).toDouble()
        return when {
            value < KILO -> String.format(locale, "%d B", bytes.coerceAtLeast(0))
            value < KILO * KILO -> String.format(locale, "%.0f kB", value / KILO)
            value < KILO * KILO * KILO -> String.format(locale, "%.1f MB", value / (KILO * KILO))
            else -> String.format(locale, "%.2f GB", value / (KILO * KILO * KILO))
        }
    }
}
