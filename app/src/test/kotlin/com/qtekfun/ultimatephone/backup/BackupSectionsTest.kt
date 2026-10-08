package com.qtekfun.ultimatephone.backup

import com.qtekfun.ultimatephone.core.designsystem.ThemeMode
import com.qtekfun.ultimatephone.core.settings.BackupException
import com.qtekfun.ultimatephone.core.settings.BackupFailure
import com.qtekfun.ultimatephone.core.settings.BackupService
import com.qtekfun.ultimatephone.core.settings.KdfParams
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.lists.EntryJsonl
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.ImportResult
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import com.qtekfun.ultimatephone.core.sync.SecretVault
import com.qtekfun.ultimatephone.screening.SpamPrefs
import com.qtekfun.ultimatephone.screening.SpamSettingsRepository
import com.qtekfun.ultimatephone.settings.SettingsRepository
import com.qtekfun.ultimatephone.settings.UserSettings
import com.qtekfun.ultimatephone.sync.DefaultSyncRepository
import com.qtekfun.ultimatephone.sync.MemoryLists
import com.qtekfun.ultimatephone.sync.MemorySyncSettings
import com.qtekfun.ultimatephone.sync.OneFileDav
import com.qtekfun.ultimatephone.sync.WebDavFactory
import com.qtekfun.ultimatephone.sync.XorCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeSettings : SettingsRepository {
    override val settings = MutableStateFlow(UserSettings())

    override suspend fun setThemeMode(mode: ThemeMode) {
        settings.value = settings.value.copy(themeMode = mode)
    }

    override suspend fun setDefaultSim(key: String?) {
        settings.value = settings.value.copy(defaultSimKey = key)
    }

    override suspend fun rememberSim(numberE164: String, simKey: String) = Unit

    override suspend fun setRegionOverride(region: String?) {
        settings.value = settings.value.copy(regionOverride = region)
    }
}

private class FakeSpamSettings : SpamSettingsRepository {
    override val prefs = MutableStateFlow(SpamPrefs())

    override suspend fun current() = prefs.value

    override suspend fun setLevelAction(level: SpamLevel, action: SpamAction) {
        prefs.value = prefs.value.copy(levelActions = prefs.value.levelActions + (level to action))
    }

    override suspend fun setRuleAction(ruleId: String, action: SpamAction?) {
        val rules = prefs.value.ruleActions
        prefs.value = prefs.value.copy(ruleActions = if (action == null) rules - ruleId else rules + (ruleId to action))
    }

    override suspend fun setIdentifiedBusinessIsNotSpam(value: Boolean) {
        prefs.value = prefs.value.copy(identifiedBusinessIsNotSpam = value)
    }

    override suspend fun deviceId() = "this-phone"
}

private class FakeLists : SpamListsRepository {
    val rows = HashMap<ListType, MutableMap<String, ListEntry>>()
    var imports = 0

    override fun observe(list: ListType): Flow<List<ListEntry>> = MutableStateFlow(Unit).map { rows[list]?.values?.filter { !it.deleted }.orEmpty() }

    override suspend fun add(list: ListType, kind: EntryKind, value: String, label: String?, note: String?): ListEntry {
        val e = ListEntry("id-$value", kind, value, label, note, 1, "dev", false)
        rows.getOrPut(list) { HashMap() }[e.id] = e
        return e
    }

    override suspend fun remove(list: ListType, id: String) = false

    override suspend fun isWhitelisted(e164: String) = false

    override suspend fun matchOwn(e164: String): ListEntry? = null

    override suspend fun entries(list: ListType) = rows[list]?.values?.toList().orEmpty()

    override suspend fun exportJsonLines(list: ListType) = EntryJsonl.encodeAll(entries(list))

    override suspend fun importJsonLines(list: ListType, lines: Sequence<String>): ImportResult {
        imports++
        val parsed = EntryJsonl.decodeAll(lines)
        parsed.entries.forEach { rows.getOrPut(list) { HashMap() }[it.id] = it }
        return ImportResult(parsed.entries.size, 0, 0, parsed.badLines)
    }
}

/** A phone: its repositories and a backup service over the real sections. */
private class Phone {
    val appSettings = FakeSettings()
    val spamSettings = FakeSpamSettings()
    val lists = FakeLists()
    val syncSettings = MemorySyncSettings()
    val sync = DefaultSyncRepository(syncSettings, SecretVault(XorCipher()), MemoryLists(), WebDavFactory { _, _ -> OneFileDav() })
    val service = BackupService(
        listOf(
            AppSettingsSection(appSettings),
            SpamSettingsSection(spamSettings),
            ListSection(ListType.OWN, lists),
            ListSection(ListType.WHITELIST, lists),
            NextcloudSection(sync)
        ),
        clock = { 123L },
        kdf = KdfParams.FOR_TESTS
    )
}

class BackupSectionsTest {
    private val secret = "fake-app-password-do-not-use"
    private val password = "backup-password".toCharArray()

    private suspend fun configured(): Phone = Phone().also { p ->
        p.appSettings.setThemeMode(ThemeMode.DARK)
        p.appSettings.setRegionOverride("PT")
        p.spamSettings.setLevelAction(SpamLevel.OWN, SpamAction.REJECT)
        p.spamSettings.setRuleAction("es-400-commercial", SpamAction.WARN)
        p.spamSettings.setIdentifiedBusinessIsNotSpam(false)
        p.lists.add(ListType.OWN, EntryKind.NUMBER, "+34600000001", "robocall", null)
        p.lists.add(ListType.WHITELIST, EntryKind.PREFIX, "+34911", "bank", null)
        p.sync.saveAccount("https://cloud.example.com", "alice", secret)
        p.sync.setEnabled(true)
    }

    @Test
    fun exportingOnOnePhoneAndImportingOnAnotherConfiguresIt() = runTest {
        val old = configured()
        val file = old.service.export(password)
        val new = Phone()
        val opened = new.service.open(file, password)
        val report = new.service.restore(opened, opened.sectionIds)
        assertEquals(emptyMap<String, String>(), report.failed)
        assertEquals(5, report.restored.size)
        assertEquals(ThemeMode.DARK, new.appSettings.settings.value.themeMode)
        assertEquals("PT", new.appSettings.settings.value.regionOverride)
        assertEquals(SpamAction.REJECT, new.spamSettings.current().actionOf(SpamLevel.OWN))
        assertEquals(SpamAction.WARN, new.spamSettings.current().actionOfRule("es-400-commercial"))
        assertFalse(new.spamSettings.current().identifiedBusinessIsNotSpam)
        assertEquals(listOf("+34600000001"), new.lists.entries(ListType.OWN).map { it.value })
        assertEquals(listOf("+34911"), new.lists.entries(ListType.WHITELIST).map { it.value })
        // The new phone is ready to sync with the same account.
        val credentials = new.sync.credentials()
        assertEquals(secret, credentials?.password)
        assertEquals("alice", credentials?.username)
        assertTrue(new.syncSettings.data.value.enabled)
        // The device id is per phone and never travels.
        assertEquals("this-phone", new.spamSettings.deviceId())
    }

    @Test
    fun theExportedFileNeverShowsCredentialsOrNumbersWithoutThePassword() = runTest {
        val file = configured().service.export(password)
        val text = String(file, Charsets.ISO_8859_1)
        for (needle in listOf(secret, "alice", "cloud.example.com", "+34600000001", "robocall", "password")) assertFalse(needle, text.contains(needle))
        // And without the right password nothing opens.
        for (wrong in listOf("wrong".toCharArray(), charArrayOf())) {
            try {
                Phone().service.open(file, wrong)
                fail("opened with a wrong password")
            } catch (e: BackupException) {
                assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, e.failure)
            }
        }
    }

    @Test
    fun aTamperedFileFailsAndTheTargetPhoneIsUntouched() = runTest {
        val file = configured().service.export(password)
        val target = Phone()
        val tampered = file.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x10).toByte() }
        try {
            target.service.open(tampered, password)
            fail("tampered file opened")
        } catch (e: BackupException) {
            assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, e.failure)
        }
        assertEquals(ThemeMode.SYSTEM, target.appSettings.settings.value.themeMode)
        assertEquals(0, target.lists.imports)
        assertEquals(null, target.sync.credentials())
    }

    @Test
    fun onlyTheChosenSectionsAreRestored() = runTest {
        val file = configured().service.export(password)
        val target = Phone()
        val opened = target.service.open(file, password)
        target.service.restore(opened, setOf(SectionIds.WHITELIST))
        assertEquals(emptyList<ListEntry>(), target.lists.entries(ListType.OWN))
        assertEquals(1, target.lists.entries(ListType.WHITELIST).size)
        assertEquals(ThemeMode.SYSTEM, target.appSettings.settings.value.themeMode)
        assertEquals(null, target.sync.credentials())
    }

    @Test
    fun anInsecureAccountInABackupIsRejectedBeforeAnythingIsApplied() = runTest {
        val bad = object : com.qtekfun.ultimatephone.core.settings.ExportSection {
            override val id = SectionIds.NEXTCLOUD

            override suspend fun export() = """{"configured":true,"serverUrl":"http://cloud.example.com","username":"a","password":"x","enabled":true}"""

            override fun validate(data: String) = Unit

            override suspend fun restore(data: String) = Unit
        }
        val file = BackupService(listOf(bad), kdf = KdfParams.FOR_TESTS).export(password)
        val target = Phone()
        val opened = target.service.open(file, password)
        val failure = try {
            target.service.restore(opened, opened.sectionIds)
            null
        } catch (e: BackupException) {
            e.failure
        }
        assertEquals(BackupFailure.INVALID_PAYLOAD, failure)
        assertEquals(null, target.sync.credentials())
    }

    @Test
    fun anAccountlessPhoneExportsASectionThatRestoresToNothing() = runTest {
        val file = Phone().service.export(password)
        val target = Phone()
        val opened = target.service.open(file, password)
        assertTrue(target.service.restore(opened, opened.sectionIds).failed.isEmpty())
        assertEquals(null, target.sync.credentials())
    }
}
