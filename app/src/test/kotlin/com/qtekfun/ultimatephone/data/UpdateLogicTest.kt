package com.qtekfun.ultimatephone.data

import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePlannerTest {
    private val manifest = manifest(
        entry("businesses-es", version = "2026.11.01"),
        entry("rules-es", PackTypes.RULES, version = "2026.10.01"),
        entry("businesses-de", version = "2026.11.01"),
        entry("spam-lists", PackTypes.SPAM, region = null, version = "2026.11.02"),
        entry("mystery-es", type = "future-type")
    )

    @Test
    fun installedPacksWithAnotherVersionAreUpdated() {
        val plan = UpdatePlanner.plan(manifest, installed = mapOf("businesses-es" to "2026.10.01", "rules-es" to "2026.10.01"), selectedRegions = setOf("ES"))
        assertEquals(listOf("businesses-es"), plan.update.map { it.id })
        assertTrue(plan.install.isEmpty())
    }

    @Test
    fun newPacksOfSelectedRegionsAreInstalledButOtherRegionsAreNot() {
        val plan = UpdatePlanner.plan(manifest, installed = mapOf("rules-es" to "2026.10.01"), selectedRegions = setOf("es"))
        assertEquals(listOf("businesses-es"), plan.install.map { it.id })
        assertTrue("DE was not selected", plan.all.none { it.id.endsWith("-de") })
    }

    @Test
    fun unknownTypesAndRegionlessPacksAreNeverInstalledAutomatically() {
        val plan = UpdatePlanner.plan(manifest, installed = emptyMap(), selectedRegions = setOf("ES"))
        assertEquals(setOf("businesses-es", "rules-es"), plan.install.map { it.id }.toSet())
    }

    @Test
    fun packsTheUserRemovedStayRemoved() {
        val plan = UpdatePlanner.plan(manifest, installed = emptyMap(), selectedRegions = setOf("ES"), excluded = setOf("businesses-es"))
        assertEquals(listOf("rules-es"), plan.install.map { it.id })
    }

    @Test
    fun nothingToDoGivesAnEmptyPlan() {
        val plan = UpdatePlanner.plan(manifest, installed = mapOf("businesses-es" to "2026.11.01"), selectedRegions = emptySet())
        assertTrue(plan.isEmpty)
    }

    @Test
    fun dataExpiresAfterThreeDays() {
        val now = 10_000_000_000L
        assertFalse(UpdatePlanner.isExpired(now - TimeUnit.DAYS.toMillis(3), now))
        assertTrue(UpdatePlanner.isExpired(now - TimeUnit.DAYS.toMillis(3) - 1, now))
        assertTrue("never updated is expired", UpdatePlanner.isExpired(null, now))
    }

    @Test
    fun startupRunsAnUpdateOnlyWhenEnabledStaleAndThereIsSomethingToKeepFresh() {
        val now = 10_000_000_000L
        val stale = now - TimeUnit.DAYS.toMillis(4)
        val fresh = now - TimeUnit.HOURS.toMillis(5)
        val installed = mapOf("businesses-es" to "1")
        val base = DataSettings(lastSuccessfulUpdateMillis = stale)

        assertTrue(UpdatePlanner.shouldRunAtStart(base, installed, now))
        assertFalse("fresh data", UpdatePlanner.shouldRunAtStart(base.copy(lastSuccessfulUpdateMillis = fresh), installed, now))
        assertFalse("automatic updates off", UpdatePlanner.shouldRunAtStart(base.copy(autoUpdate = false), installed, now))
        assertFalse("nothing installed, nothing selected", UpdatePlanner.shouldRunAtStart(base, emptyMap(), now))
        val regionsOnly = DataSettings(selectedRegions = setOf("ES"))
        assertTrue("regions chosen but never downloaded", UpdatePlanner.shouldRunAtStart(regionsOnly, emptyMap(), now))
        val withSource = DataSettings(customSources = listOf(source()))
        assertTrue("an enabled custom source counts", UpdatePlanner.shouldRunAtStart(withSource, emptyMap(), now))
        val disabled = DataSettings(customSources = listOf(source().copy(enabled = false)))
        assertFalse("a disabled one does not", UpdatePlanner.shouldRunAtStart(disabled, emptyMap(), now))
    }

    @Test
    fun onlyEnabledSourcesAreUpdated() {
        val settings = DataSettings(customSources = listOf(source("a"), source("b").copy(enabled = false)))
        assertEquals(listOf("a"), UpdatePlanner.sourcesToUpdate(settings).map { it.id })
    }

    private fun source(id: String = "s1") = CustomSource(id = id, name = id, url = "https://example.org/$id.txt")
}

class PackCatalogTest {
    private val manifest = manifest(
        entry("rules-es", PackTypes.RULES, bytes = 2_000, uncompressed = 2_000),
        entry("businesses-es", bytes = 30_000_000, uncompressed = 90_000_000),
        entry("businesses-de", bytes = 80_000_000, uncompressed = 250_000_000),
        entry("spam-community", PackTypes.SPAM, region = null, bytes = 500_000, uncompressed = 1_500_000),
        entry("businesses-at", bytes = 9_000_000, uncompressed = 30_000_000)
    )

    @Test
    fun regionsAreOrderedByCountryNameAndRegionlessPacksComeLast() {
        val groups = PackCatalog.group(manifest, emptyMap(), Locale.ENGLISH)
        assertEquals(listOf("AT", "DE", "ES", null), groups.map { it.region })
    }

    @Test
    fun orderFollowsTheLocaleOfTheCountryNames() {
        // Spanish: Alemania, Austria, España.
        assertEquals(listOf("DE", "AT", "ES", null), PackCatalog.group(manifest, emptyMap(), Locale.forLanguageTag("es")).map { it.region })
    }

    @Test
    fun packsOfARegionAreListedBusinessesFirstAndSizesAreSummed() {
        val es = PackCatalog.group(manifest, emptyMap(), Locale.ENGLISH).first { it.region == "ES" }
        assertEquals(listOf("businesses-es", "rules-es"), es.packs.map { it.entry.id })
        assertEquals(30_002_000L, es.downloadBytes)
        assertEquals(90_002_000L, es.installedBytes)
    }

    @Test
    fun installedStateAndPendingBytesFollowTheInstalledVersions() {
        val installed = mapOf("rules-es" to "2026.10.01", "businesses-es" to "2025.01.01")
        val es = PackCatalog.group(manifest, installed, Locale.ENGLISH).first { it.region == "ES" }
        assertTrue(es.isPartlyInstalled)
        assertTrue(es.isFullyInstalled)
        assertTrue(es.hasUpdate)
        assertEquals(30_000_000L, es.pendingDownloadBytes)
        val business = es.packs.first { it.entry.id == "businesses-es" }
        assertTrue(business.isInstalled && business.updateAvailable)
    }

    @Test
    fun unknownPackTypesAreHidden() {
        val groups = PackCatalog.group(manifest(entry("x-es", type = "future")), emptyMap(), Locale.ENGLISH)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun installedSizeFallsBackToTheDownloadSize() {
        val item = PackItem(entry("businesses-es", bytes = 5, uncompressed = 0), null)
        assertEquals(5L, item.installedBytes)
    }

    @Test
    fun packsForRegionsCoverEveryPackOfThoseRegions() {
        assertEquals(setOf("rules-es", "businesses-es", "businesses-at"), PackCatalog.packsFor(manifest, setOf("es", "AT")).map { it.id }.toSet())
    }

    @Test
    fun countryNamesAreHumanAndUnknownRegionsKeepTheirCode() {
        assertEquals("Spain", PackCatalog.regionName("ES", Locale.ENGLISH))
        assertEquals("España", PackCatalog.regionName("ES", Locale.forLanguageTag("es")))
        assertEquals("XX", PackCatalog.regionName("XX", Locale.ENGLISH))
    }
}

class SizeFormatTest {
    @Test
    fun usesDecimalUnits() {
        assertEquals("0 B", SizeFormat.format(0, Locale.US))
        assertEquals("999 B", SizeFormat.format(999, Locale.US))
        assertEquals("1 kB", SizeFormat.format(1_000, Locale.US))
        assertEquals("12.3 MB", SizeFormat.format(12_300_000, Locale.US))
        assertEquals("1.50 GB", SizeFormat.format(1_500_000_000, Locale.US))
    }

    @Test
    fun followsTheLocaleDecimalSeparator() {
        assertEquals("12,3 MB", SizeFormat.format(12_300_000, Locale.forLanguageTag("es")))
    }

    @Test
    fun negativeSizesAreShownAsZero() {
        assertEquals("0 B", SizeFormat.format(-5, Locale.US))
    }
}

class ErrorsAndPolicyTest {
    @Test
    fun transientErrorsAreRetriedAndPermanentOnesAreNot() {
        assertTrue(UpdateErrors.isTransient(UpdateErrors.NETWORK))
        assertTrue(UpdateErrors.isTransient(UpdateErrors.http(503)))
        assertTrue(UpdateErrors.isTransient(UpdateErrors.http(429)))
        assertTrue(UpdateErrors.isTransient(UpdateErrors.install("NETWORK")))
        assertFalse(UpdateErrors.isTransient(UpdateErrors.http(404)))
        assertFalse(UpdateErrors.isTransient(UpdateErrors.install("CHECKSUM_MISMATCH")))
        assertFalse(UpdateErrors.isTransient(UpdateErrors.SIGNATURE))
        assertFalse(UpdateErrors.isTransient(UpdateErrors.TOO_LARGE))
    }

    @Test
    fun codesCarryAnOptionalDetail() {
        assertEquals("http", UpdateErrors.kind(UpdateErrors.http(404)))
        assertEquals("404", UpdateErrors.detail(UpdateErrors.http(404)))
        assertNull(UpdateErrors.detail(UpdateErrors.NETWORK))
    }

    @Test
    fun wifiOnlyBlocksMeteredConnectionsOnly() {
        assertEquals(DownloadPermission.ALLOWED, NetworkPolicy.permission(wifiOnly = true, state = NetworkState.UNMETERED))
        assertEquals(DownloadPermission.BLOCKED_BY_WIFI_ONLY, NetworkPolicy.permission(wifiOnly = true, state = NetworkState.METERED))
        assertEquals(DownloadPermission.ALLOWED, NetworkPolicy.permission(wifiOnly = false, state = NetworkState.METERED))
        assertEquals(DownloadPermission.NO_NETWORK, NetworkPolicy.permission(wifiOnly = false, state = NetworkState.NONE))
        assertEquals(DownloadPermission.NO_NETWORK, NetworkPolicy.permission(wifiOnly = true, state = NetworkState.NONE))
    }

    @Test
    fun onlyHttpsUrlsWithAHostAreValidSources() {
        assertTrue(SourceUrls.isValid("https://example.org/list.txt"))
        assertTrue(SourceUrls.isValid("  https://example.org/list.txt  "))
        assertFalse(SourceUrls.isValid("http://example.org/list.txt"))
        assertFalse(SourceUrls.isValid("ftp://example.org/list.txt"))
        assertFalse(SourceUrls.isValid("https://"))
        assertFalse(SourceUrls.isValid("https://user:pass@example.org/list.txt"))
        assertFalse(SourceUrls.isValid("https://exa mple.org"))
        assertFalse(SourceUrls.isValid(""))
        assertFalse(SourceUrls.isValid("file:///sdcard/list.txt"))
    }

    @Test
    fun workResultsRetryOnlyTransientFailuresAndGiveUpAfterFiveAttempts() {
        assertEquals(WorkResults.Kind.SUCCESS, WorkResults.kind(UpdateOutcome.Success(true), 0))
        assertEquals(WorkResults.Kind.RETRY, WorkResults.kind(UpdateOutcome.Retry("network"), 0))
        assertEquals(WorkResults.Kind.RETRY, WorkResults.kind(UpdateOutcome.Blocked(DownloadPermission.BLOCKED_BY_WIFI_ONLY), 3))
        assertEquals(WorkResults.Kind.FAILURE, WorkResults.kind(UpdateOutcome.Retry("network"), 4))
        assertEquals(WorkResults.Kind.FAILURE, WorkResults.kind(UpdateOutcome.Failed("signature"), 0))
    }

    @Test
    fun wifiOnlySelectsTheUnmeteredWorkConstraint() {
        assertEquals(androidx.work.NetworkType.UNMETERED, WorkManagerUpdateScheduler.networkType(wifiOnly = true))
        assertEquals(androidx.work.NetworkType.CONNECTED, WorkManagerUpdateScheduler.networkType(wifiOnly = false))
    }
}
