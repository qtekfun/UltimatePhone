package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.datapacks.Manifest
import com.qtekfun.ultimatephone.core.datapacks.PackEntry
import java.util.concurrent.TimeUnit

/** What an update run has to do with packs: [install] are new packs of selected regions, [update] are installed packs with a new version. */
data class UpdatePlan(val install: List<PackEntry>, val update: List<PackEntry>) {
    val all: List<PackEntry> get() = install + update
    val isEmpty: Boolean get() = install.isEmpty() && update.isEmpty()
}

/** Decisions of the automatic update, as pure functions. */
object UpdatePlanner {
    /** Data older than this is considered expired: the app asks for an update at start-up. */
    val MAX_AGE_MILLIS: Long = TimeUnit.DAYS.toMillis(3)

    /** The pack types the app knows how to use. Anything else in a newer manifest is ignored. */
    val KNOWN_TYPES = setOf(PackTypes.BUSINESSES, PackTypes.SPAM, PackTypes.RULES)

    fun plan(manifest: Manifest, installed: Map<String, String>, selectedRegions: Set<String>, excluded: Set<String> = emptySet()): UpdatePlan {
        val regions = selectedRegions.map { it.uppercase() }.toSet()
        val known = manifest.packs.filter { it.type in KNOWN_TYPES }
        val update = known.filter { entry -> installed[entry.id]?.let { it != entry.version } == true }
        val install = known.filter { entry ->
            entry.id !in installed && entry.id !in excluded && entry.region?.uppercase() in regions
        }
        return UpdatePlan(install, update)
    }

    fun isExpired(lastSuccessMillis: Long?, nowMillis: Long): Boolean = lastSuccessMillis == null || nowMillis - lastSuccessMillis > MAX_AGE_MILLIS

    /**
     * True when the app should run an update right now, outside the daily schedule: automatic updates are on, there is
     * something to keep fresh, and the last successful update is older than [MAX_AGE_MILLIS] (or never happened).
     */
    fun shouldRunAtStart(settings: DataSettings, installed: Map<String, String>, nowMillis: Long): Boolean {
        if (!settings.autoUpdate) return false
        val hasWork = installed.isNotEmpty() || settings.selectedRegions.isNotEmpty() || settings.customSources.any { it.enabled }
        return hasWork && isExpired(settings.lastSuccessfulUpdateMillis, nowMillis)
    }

    /** Custom sources that take part in an update run. */
    fun sourcesToUpdate(settings: DataSettings): List<CustomSource> = settings.customSources.filter { it.enabled }
}

/** The `type` values of the manifest. */
object PackTypes {
    const val BUSINESSES = "businesses"
    const val SPAM = "spam"
    const val RULES = "rules"
}
