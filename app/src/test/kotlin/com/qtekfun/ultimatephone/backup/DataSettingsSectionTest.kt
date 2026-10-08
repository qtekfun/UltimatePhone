package com.qtekfun.ultimatephone.backup

import com.qtekfun.ultimatephone.core.settings.BackupException
import com.qtekfun.ultimatephone.core.settings.BackupFailure
import com.qtekfun.ultimatephone.core.settings.BackupService
import com.qtekfun.ultimatephone.core.settings.KdfParams
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.sources.SourceFormat
import com.qtekfun.ultimatephone.data.CustomSource
import com.qtekfun.ultimatephone.data.DataSettings
import com.qtekfun.ultimatephone.data.DataSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeDataSettings(initial: DataSettings = DataSettings()) : DataSettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun current() = settings.value

    override suspend fun update(transform: (DataSettings) -> DataSettings) {
        settings.value = transform(settings.value)
    }
}

class DataSettingsSectionTest {
    private val password = "backup-password".toCharArray()
    private val refreshed = ArrayList<List<String>>()
    private var counter = 0

    private fun section(repo: DataSettingsRepository) = DataSettingsSection(repo, { refreshed += it }, { "new${counter++}" })

    private val source = CustomSource(
        id = "abc",
        name = "My list",
        url = "https://example.com/spam.txt",
        format = SourceFormat.CSV,
        level = SpamLevel.OWN,
        enabled = false,
        etag = "etag-1",
        lastModified = "yesterday",
        contentSha256 = "hash",
        lastUpdateMillis = 5L,
        lastError = "network",
        entries = 42
    )

    private val configured = DataSettings(
        selectedRegions = setOf("ES", "PT"),
        excludedPacks = setOf("business-es"),
        wifiOnly = false,
        autoUpdate = false,
        customSources = listOf(source),
        lastManifestRefreshMillis = 9L,
        lastSuccessfulUpdateMillis = 8L,
        lastError = "boom",
        onboardingCompleted = true
    )

    @Test
    fun exportHasTheChoicesButNoPerDeviceState() = runTest {
        val text = section(FakeDataSettings(configured)).export()
        for (forbidden in listOf(
            "etag",
            "hash",
            "network",
            "boom",
            "lastUpdate",
            "onboarding",
            "entries",
            "abc"
        )) {
            assertFalse(forbidden, text.contains(forbidden))
        }
        for (expected in listOf(
            "ES",
            "PT",
            "business-es",
            "https://example.com/spam.txt",
            "CSV",
            "OWN",
            "My list"
        )) {
            assertTrue(expected, text.contains(expected))
        }
    }

    @Test
    fun restoreOnAFreshPhoneAppliesChoicesAndRefreshesNewSources() = runTest {
        val data = section(FakeDataSettings(configured)).export()
        val target = FakeDataSettings(DataSettings(onboardingCompleted = true, lastError = "kept"))
        section(target).restore(data)
        val s = target.current()
        assertEquals(setOf("ES", "PT"), s.selectedRegions)
        assertEquals(setOf("business-es"), s.excludedPacks)
        assertFalse(s.wifiOnly)
        assertFalse(s.autoUpdate)
        val restored = s.customSources.single()
        assertEquals(CustomSource("new0", "My list", "https://example.com/spam.txt", SourceFormat.CSV, SpamLevel.OWN, false), restored)
        assertEquals(listOf(listOf("new0")), refreshed)
        // Per-device state of the target is untouched.
        assertTrue(s.onboardingCompleted)
        assertEquals("kept", s.lastError)
    }

    @Test
    fun aKnownAddressKeepsItsBookkeepingAndIsNotRefreshed() = runTest {
        val data = section(FakeDataSettings(configured)).export()
        val local = source.copy(id = "local", name = "Old", enabled = true, level = SpamLevel.COMMUNITY, format = SourceFormat.TXT, entries = 7)
        val target = FakeDataSettings(DataSettings(customSources = listOf(local)))
        section(target).restore(data)
        val merged = target.current().customSources.single()
        assertEquals("local", merged.id)
        assertEquals("My list", merged.name)
        assertEquals(SpamLevel.OWN, merged.level)
        assertFalse(merged.enabled)
        assertEquals(7, merged.entries)
        assertEquals("etag-1", merged.etag)
        assertTrue(refreshed.isEmpty())
    }

    @Test
    fun insecureOrBrokenDataFailsValidation() {
        val good = """{"selectedRegions":["ES"],"excludedPacks":[],"wifiOnly":true,"autoUpdate":true,"customSources":[]}"""
        section(FakeDataSettings()).validate(good)
        val bad = listOf(
            good.replace("[]}", """[{"name":"x","url":"http://example.com/a.txt","format":"TXT","level":"COMMUNITY","enabled":true}]}"""),
            good.replace("""["ES"]""", """["Spain"]"""),
            good.replace("\"wifiOnly\":true,", ""),
            good.replace("""[]}""", """[{"name":"x","url":"https://example.com/a","format":"XML","level":"COMMUNITY","enabled":true}]}"""),
            "[]"
        )
        for (text in bad) {
            try {
                section(FakeDataSettings()).validate(text)
                fail("accepted: $text")
            } catch (_: Exception) {
                // Expected.
            }
        }
    }

    @Test
    fun nothingIsAppliedWhenTheBackupSectionIsInvalid() = runTest {
        val bad = object : com.qtekfun.ultimatephone.core.settings.ExportSection {
            override val id = SectionIds.DATA_SETTINGS

            override suspend fun export() = """{"selectedRegions":["ES"],"excludedPacks":[],"wifiOnly":false,"autoUpdate":true,""" +
                """"customSources":[{"name":"x","url":"http://example.com/a.txt","format":"TXT","level":"COMMUNITY","enabled":true}]}"""

            override fun validate(data: String) = Unit

            override suspend fun restore(data: String) = Unit
        }
        val file = BackupService(listOf(bad), kdf = KdfParams.FOR_TESTS).export(password)
        val target = FakeDataSettings()
        val service = BackupService(listOf(section(target)), kdf = KdfParams.FOR_TESTS)
        val opened = service.open(file, password)
        val failure = try {
            service.restore(opened, opened.sectionIds)
            null
        } catch (e: BackupException) {
            e.failure
        }
        assertEquals(BackupFailure.INVALID_PAYLOAD, failure)
        assertTrue(target.current().selectedRegions.isEmpty())
        assertTrue(target.current().wifiOnly)
        assertNull(target.current().customSources.firstOrNull())
        assertTrue(refreshed.isEmpty())
    }
}
