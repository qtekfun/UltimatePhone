package com.qtekfun.ultimatephone.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.sources.SourceFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSettingsMapperTest {
    @Test
    fun emptyPreferencesGiveTheDocumentedDefaults() {
        val settings = DataSettingsMapper.read(mutablePreferencesOf())
        assertTrue("Wi-Fi only is on by default", settings.wifiOnly)
        assertTrue("automatic updates are on by default", settings.autoUpdate)
        assertTrue(settings.selectedRegions.isEmpty())
        assertTrue(settings.customSources.isEmpty())
        assertFalse(settings.onboardingCompleted)
        assertNull(settings.lastSuccessfulUpdateMillis)
        assertNull(settings.lastManifestRefreshMillis)
        assertNull(settings.lastError)
    }

    @Test
    fun everyFieldSurvivesAWriteAndARead() {
        val source = CustomSource(
            id = "a1",
            name = "Mine",
            url = "https://example.org/l.csv",
            format = SourceFormat.CSV,
            level = SpamLevel.RULE,
            enabled = false,
            etag = "\"x\"",
            lastModified = "Mon, 05 Oct 2026 10:00:00 GMT",
            contentSha256 = "ab",
            lastUpdateMillis = 123L,
            lastError = "http:404",
            entries = 77
        )
        val original = DataSettings(
            selectedRegions = setOf("ES", "DE"),
            excludedPacks = setOf("businesses-de"),
            wifiOnly = false,
            autoUpdate = false,
            customSources = listOf(source, source.copy(id = "a2", format = SourceFormat.JSONL, level = SpamLevel.COMMUNITY)),
            lastManifestRefreshMillis = 5L,
            lastSuccessfulUpdateMillis = 6L,
            lastError = "network",
            onboardingCompleted = true
        )
        val prefs = mutablePreferencesOf()
        DataSettingsMapper.write(prefs, original)
        assertEquals(original, DataSettingsMapper.read(prefs))
    }

    @Test
    fun clearingOptionalFieldsRemovesThem() {
        val prefs = mutablePreferencesOf()
        DataSettingsMapper.write(prefs, DataSettings(lastError = "network", lastSuccessfulUpdateMillis = 1L))
        DataSettingsMapper.write(prefs, DataSettings())
        val read = DataSettingsMapper.read(prefs)
        assertNull(read.lastError)
        assertNull(read.lastSuccessfulUpdateMillis)
    }

    @Test
    fun regionsAreAlwaysUpperCase() {
        val prefs = mutablePreferencesOf()
        DataSettingsMapper.write(prefs, DataSettings(selectedRegions = setOf("es")))
        assertEquals(setOf("ES"), DataSettingsMapper.read(prefs).selectedRegions)
    }

    @Test
    fun damagedSourceListsReadAsEmptyInsteadOfCrashing() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("custom_sources") to "{ this is not json")
        assertTrue(DataSettingsMapper.read(prefs).customSources.isEmpty())
        assertTrue(DataSettingsMapper.decodeSources("[{\"id\":1}]").isEmpty())
        assertTrue(DataSettingsMapper.decodeSources(null).isEmpty())
    }

    @Test
    fun sourcesFromAnOlderVersionWithMissingFieldsStillLoad() {
        val sources = DataSettingsMapper.decodeSources("""[{"id":"a","name":"A","url":"https://example.org/a","unknownFutureField":1}]""")
        assertEquals(1, sources.size)
        assertEquals(SourceFormat.TXT, sources.single().format)
        assertTrue(sources.single().enabled)
        assertEquals("custom-a", sources.single().packId)
    }
}
