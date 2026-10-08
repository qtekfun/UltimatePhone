package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.datapacks.InstallFailure
import com.qtekfun.ultimatephone.core.datapacks.RefreshResult
import com.qtekfun.ultimatephone.core.phonenumber.LibPhoneNormalizer
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.sources.SourceFormat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataManagerTest {
    private val published = manifest(
        entry("businesses-es", version = "2026.11.01"),
        entry("rules-es", PackTypes.RULES, version = "2026.10.15"),
        entry("businesses-de", version = "2026.11.01")
    )
    private val backend = FakeBackend(published = published)
    private val settings = FakeDataSettings(DataSettings(selectedRegions = setOf("ES")))
    private val reloader = FakeReloader()
    private val network = FakeNetwork()
    private val fetcher = FakeSourceFetcher()
    private var now = 5_000_000L

    private fun TestScope.manager(name: String): DataManager {
        val io = StandardTestDispatcher(testScheduler)
        val updater = CustomSourceUpdater(fetcher, LibPhoneNormalizer(), { "ES" }, testDir(name), clock = { now }, pause = { }, io = io)
        return DataManager(backend, settings, reloader, updater, network, scope = this, clock = { now }, io = io)
    }

    @Test
    fun updateInstallsNewPacksOfSelectedRegionsAndUpdatesChangedOnes() = runTest {
        backend.installedPacks["rules-es"] = "2026.09.01"
        val manager = manager("dm-update")
        val outcome = manager.updateAll()

        assertEquals(UpdateOutcome.Success(changed = true), outcome)
        assertEquals(mapOf("businesses-es" to "2026.11.01", "rules-es" to "2026.10.15"), backend.installedPacks)
        assertFalse("DE was not selected", "businesses-de" in backend.installRequests)
        assertEquals(now, settings.state.value.lastSuccessfulUpdateMillis)
        assertEquals(now, settings.state.value.lastManifestRefreshMillis)
        assertNull(settings.state.value.lastError)
        assertEquals(1, reloader.reloads)
        assertEquals(backend.installedPacks, manager.catalog.value.installed)
    }

    @Test
    fun anUpToDateInstallationChangesNothingButStillCountsAsSuccessfulUpdate() = runTest {
        backend.installedPacks.putAll(mapOf("businesses-es" to "2026.11.01", "rules-es" to "2026.10.15"))
        val outcome = manager("dm-noop").updateAll()
        assertEquals(UpdateOutcome.Success(changed = false), outcome)
        assertTrue(backend.installRequests.isEmpty())
        assertEquals(now, settings.state.value.lastSuccessfulUpdateMillis)
        assertEquals(0, reloader.reloads)
    }

    @Test
    fun wifiOnlyOnAMeteredConnectionBlocksTheUpdateBeforeAnyTraffic() = runTest {
        network.state = NetworkState.METERED
        val outcome = manager("dm-metered").updateAll()
        assertEquals(UpdateOutcome.Blocked(DownloadPermission.BLOCKED_BY_WIFI_ONLY), outcome)
        assertTrue(backend.installRequests.isEmpty())
        assertNull(settings.state.value.lastSuccessfulUpdateMillis)
    }

    @Test
    fun withWifiOnlyOffMobileDataIsFine() = runTest {
        settings.state.value = settings.state.value.copy(wifiOnly = false)
        network.state = NetworkState.METERED
        assertTrue(manager("dm-mobile").updateAll() is UpdateOutcome.Success)
    }

    @Test
    fun noNetworkIsBlockedAsWell() = runTest {
        network.state = NetworkState.NONE
        assertEquals(UpdateOutcome.Blocked(DownloadPermission.NO_NETWORK), manager("dm-none").updateAll())
    }

    @Test
    fun offlineManifestMeansRetryLaterAndNothingIsInstalled() = runTest {
        backend.published = null
        val outcome = manager("dm-offline").updateAll()
        assertEquals(UpdateOutcome.Retry(UpdateErrors.NETWORK), outcome)
        assertTrue(backend.installRequests.isEmpty())
        assertEquals(UpdateErrors.NETWORK, settings.state.value.lastError)
        assertNull("the last successful update does not move", settings.state.value.lastSuccessfulUpdateMillis)
    }

    @Test
    fun aManifestWithABadSignatureStopsEverythingForGood() = runTest {
        backend.refreshResult = RefreshResult.InvalidSignature
        val outcome = manager("dm-signature").updateAll()
        assertEquals(UpdateOutcome.Failed(UpdateErrors.SIGNATURE), outcome)
        assertTrue(backend.installRequests.isEmpty())
        assertEquals(UpdateErrors.SIGNATURE, settings.state.value.lastError)
    }

    @Test
    fun aMalformedManifestIsAPermanentFailure() = runTest {
        backend.refreshResult = RefreshResult.Malformed("bad")
        assertEquals(UpdateOutcome.Failed(UpdateErrors.MANIFEST), manager("dm-malformed").updateAll())
    }

    @Test
    fun aCorruptDownloadFailsForGoodButAnInterruptedOneIsRetried() = runTest {
        val manager = manager("dm-install-fail")
        backend.failInstall = InstallFailure.CHECKSUM_MISMATCH
        assertEquals(UpdateOutcome.Failed("install:CHECKSUM_MISMATCH"), manager.updateAll())
        backend.failInstall = InstallFailure.NETWORK
        assertEquals(UpdateOutcome.Retry("install:NETWORK"), manager.updateAll())
        assertNull(settings.state.value.lastSuccessfulUpdateMillis)
    }

    @Test
    fun packsTheUserRemovedAreNotBroughtBack() = runTest {
        settings.state.value = settings.state.value.copy(excludedPacks = setOf("businesses-es"))
        manager("dm-excluded").updateAll()
        assertEquals(listOf("rules-es"), backend.installRequests)
    }

    @Test
    fun customSourcesAreRefreshedAndTheirBookkeepingIsSaved() = runTest {
        val source = CustomSource(id = "s1", name = "List", url = "https://example.org/l.txt", level = SpamLevel.RULE)
        settings.state.value = settings.state.value.copy(customSources = listOf(source, source.copy(id = "s2", enabled = false)))
        fetcher.reply(FakeSourceFetcher.Reply.Body("912345678\n911223344\n", etag = "\"e1\""))
        val outcome = manager("dm-custom").updateAll()

        assertEquals(UpdateOutcome.Success(changed = true), outcome)
        assertEquals(1, fetcher.requests.size)
        val stored = settings.state.value.customSources
        assertEquals(2, stored.first { it.id == "s1" }.entries)
        assertEquals("\"e1\"", stored.first { it.id == "s1" }.etag)
        assertNull("disabled sources are not touched", stored.first { it.id == "s2" }.lastUpdateMillis)
        assertTrue(reloader.reloads >= 1)
    }

    @Test
    fun aFailingSourceDoesNotStopThePacksButIsReported() = runTest {
        settings.state.value = settings.state.value.copy(customSources = listOf(CustomSource(id = "s1", name = "L", url = "https://example.org/l.txt")))
        fetcher.reply(FakeSourceFetcher.Reply.Http(404))
        val outcome = manager("dm-custom-fail").updateAll()
        assertEquals(UpdateOutcome.Failed(UpdateErrors.http(404)), outcome)
        assertTrue("packs were installed anyway", "businesses-es" in backend.installedPacks)
        assertEquals(UpdateErrors.http(404), settings.state.value.customSources.single().lastError)
        assertNull(settings.state.value.lastSuccessfulUpdateMillis)
    }

    @Test
    fun installingRecordsTheRegionAndShowsNoProgressAfterwards() = runTest {
        settings.state.value = DataSettings()
        val manager = manager("dm-install")
        manager.load()
        manager.refreshCatalog()
        manager.install(listOf("businesses-de"))
        advanceUntilIdle()

        assertEquals("2026.11.01", backend.installedPacks["businesses-de"])
        assertEquals(setOf("DE"), settings.state.value.selectedRegions)
        assertTrue(manager.progress.value.isEmpty())
        assertEquals(1, reloader.reloads)
    }

    @Test
    fun aFailedInstallStaysVisibleUntilDismissed() = runTest {
        backend.failInstall = InstallFailure.BAD_ARCHIVE
        val manager = manager("dm-install-failed")
        manager.refreshCatalog()
        manager.install(listOf("businesses-es"))
        advanceUntilIdle()

        assertEquals(PackProgress.Failed("install:BAD_ARCHIVE"), manager.progress.value["businesses-es"])
        manager.dismissFailure("businesses-es")
        assertTrue(manager.progress.value.isEmpty())
    }

    @Test
    fun installingAnUnknownPackFailsCleanly() = runTest {
        val manager = manager("dm-unknown")
        manager.refreshCatalog()
        manager.install(listOf("businesses-xx"))
        advanceUntilIdle()
        assertTrue(manager.progress.value["businesses-xx"] is PackProgress.Failed)
    }

    @Test
    fun removingPacksExcludesThemAndDropsEmptyRegions() = runTest {
        backend.installedPacks.putAll(mapOf("businesses-es" to "1", "rules-es" to "1"))
        val manager = manager("dm-remove")
        manager.refreshCatalog()
        manager.remove(listOf("businesses-es", "rules-es"))
        advanceUntilIdle()

        assertTrue(backend.installedPacks.isEmpty())
        assertEquals(setOf("businesses-es", "rules-es"), settings.state.value.excludedPacks)
        assertTrue("the region has no packs left", settings.state.value.selectedRegions.isEmpty())
        assertTrue(reloader.reloads >= 1)
    }

    @Test
    fun reinstallingAPackClearsItsExclusion() = runTest {
        settings.state.value = settings.state.value.copy(excludedPacks = setOf("businesses-es"))
        val manager = manager("dm-reinstall")
        manager.refreshCatalog()
        manager.install(listOf("businesses-es"))
        advanceUntilIdle()
        assertTrue(settings.state.value.excludedPacks.isEmpty())
    }

    @Test
    fun theCatalogShowsTheCachedManifestWhenOfflineAndSaysSo() = runTest {
        backend.cached = published
        backend.published = null
        val manager = manager("dm-cache")
        manager.load()
        assertNotNull(manager.catalog.value.manifest)
        assertTrue(manager.catalog.value.fromCache)

        val error = manager.refreshCatalog()
        assertEquals(UpdateErrors.NETWORK, error)
        assertNotNull("the cached list stays on screen", manager.catalog.value.manifest)
        assertTrue(manager.catalog.value.fromCache)
        assertEquals(UpdateErrors.NETWORK, manager.catalog.value.refreshError)
    }

    @Test
    fun aFreshManifestReplacesTheCachedOne() = runTest {
        val manager = manager("dm-fresh")
        assertNull(manager.refreshCatalog())
        assertFalse(manager.catalog.value.fromCache)
        assertEquals(3, manager.catalog.value.manifest!!.packs.size)
        assertNull(manager.catalog.value.refreshError)
    }

    @Test
    fun addingASourceStoresItAndDownloadsItOnce() = runTest {
        fetcher.reply(FakeSourceFetcher.Reply.Body("912345678\n"))
        val manager = manager("dm-add")
        val added = manager.addSource("  ", "https://spam.example.org/list.txt", SourceFormat.TXT, SpamLevel.COMMUNITY)

        assertNotNull(added)
        assertEquals("spam.example.org", added!!.name)
        assertEquals(1, added.entries)
        assertEquals(1, settings.state.value.customSources.size)
        assertEquals(1, fetcher.requests.size)
        assertTrue(reloader.reloads >= 1)
    }

    @Test
    fun aNonHttpsSourceIsRefusedAndNeverStored() = runTest {
        val manager = manager("dm-add-http")
        assertNull(manager.addSource("x", "http://example.org/list.txt", SourceFormat.TXT, SpamLevel.COMMUNITY))
        assertTrue(settings.state.value.customSources.isEmpty())
        assertTrue(fetcher.requests.isEmpty())
    }

    @Test
    fun removingASourceDeletesItsPack() = runTest {
        fetcher.reply(FakeSourceFetcher.Reply.Body("912345678\n"))
        val manager = manager("dm-remove-source")
        val added = manager.addSource("L", "https://example.org/list.txt", SourceFormat.TXT, SpamLevel.COMMUNITY)!!
        val reloadsBefore = reloader.reloads
        manager.removeSource(added.id)

        assertTrue(settings.state.value.customSources.isEmpty())
        val packFiles = java.io.File(System.getProperty("user.dir"), "build/test-work/dm-remove-source").listFiles()!!
        assertTrue(packFiles.none { it.name.endsWith(".nums") })
        assertTrue(reloader.reloads > reloadsBefore)
    }

    @Test
    fun disablingASourceReloadsTheLookups() = runTest {
        settings.state.value = settings.state.value.copy(customSources = listOf(CustomSource(id = "s1", name = "L", url = "https://example.org/l.txt")))
        val manager = manager("dm-disable")
        manager.setSourceEnabled("s1", false)
        assertFalse(settings.state.value.customSources.single().enabled)
        assertEquals(1, reloader.reloads)
    }

    @Test
    fun startupRunsAnUpdateSoonOnlyWhenTheDataIsStale() = runTest {
        class Scheduler : UpdateScheduler {
            var applied = 0
            var soon = 0

            override fun apply(settings: DataSettings) {
                applied++
            }

            override fun runSoon(settings: DataSettings) {
                soon++
            }
        }
        val scheduler = Scheduler()
        backend.installedPacks["businesses-es"] = "2026.11.01"
        val manager = manager("dm-startup")

        settings.state.value = settings.state.value.copy(lastSuccessfulUpdateMillis = now - 1_000)
        DataStartup(settings, manager, scheduler, clock = { now }).run()
        assertEquals(1, scheduler.applied)
        assertEquals(0, scheduler.soon)

        settings.state.value = settings.state.value.copy(lastSuccessfulUpdateMillis = now - 4L * 24 * 3600 * 1000)
        DataStartup(settings, manager, scheduler, clock = { now }).run()
        assertEquals(1, scheduler.soon)
    }
}
