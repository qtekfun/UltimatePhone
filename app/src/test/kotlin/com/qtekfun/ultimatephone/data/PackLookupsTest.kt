package com.qtekfun.ultimatephone.data

import app.cash.turbine.test
import com.qtekfun.ultimatephone.core.datapacks.PackStore
import com.qtekfun.ultimatephone.core.spam.decision.BusinessMatch
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRule
import com.qtekfun.ultimatephone.core.spam.sources.PackDatabase
import com.qtekfun.ultimatephone.core.spam.sources.PackEntry as PackRow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackLookupsTest {
    private val rulesJson = """
        {"schema":1,"version":"2026.10.01","rules":[
          {"id":"es-400-commercial","country":"ES","prefix":"400","level":"RULE","informational":true}
        ]}
    """.trimIndent()

    private class Env(name: String, scope: TestScope) {
        val dir = testDir(name)
        val store = PackStore(dir)
        val dbs = mutableMapOf<String, PackDatabase>()
        var sources: List<CustomSource> = emptyList()
        var searchedFiles: List<Pair<String, File>>? = null
        val opened = mutableListOf<String>()
        val lookups = DefaultInstalledPackLookups(
            store = store,
            packsDir = dir,
            customSources = { sources },
            opener = { file ->
                opened += file.name
                dbs[file.name]
            },
            builtInRules = { emptyList() },
            searchFactory = { files ->
                searchedFiles = files
                NoBusinessNameSearch
            },
            scope = scope,
            io = StandardTestDispatcher(scope.testScheduler),
            closeDelayMillis = 1_000
        )

        fun install(id: String, version: String = "1", content: String = "x") {
            val file = store.newTempFile("tmp-")
            file.writeText(content)
            store.install(id, version, file)
        }

        fun customPack(source: CustomSource, vararg numbers: String) {
            val writer = NumberPackFile.Writer()
            numbers.forEach(writer::add)
            writer.writeTo(File(dir, "${source.packId}${CustomSourceUpdater.EXTENSION}"))
        }
    }

    @Test
    fun businessPacksAnswerWithNameAndCategory() = runTest {
        val env = Env("lk-business", this)
        env.dbs["businesses-es.db"] = FakePackDb(mapOf("+34912345678" to PackRow("+34912345678", name = "Farmacia Sol", category = "health")))
        env.install("businesses-es")
        env.lookups.reload()
        assertEquals(BusinessMatch("Farmacia Sol", "health", "businesses-es"), env.lookups.businesses.find("+34912345678"))
        assertNull(env.lookups.businesses.find("+34900000000"))
        assertEquals(listOf("businesses-es"), env.searchedFiles!!.map { it.first })
    }

    @Test
    fun spamPacksAnswerWithTheirSourceId() = runTest {
        val env = Env("lk-spam", this)
        env.dbs["spam-community.db"] = FakePackDb(mapOf("+34900111222" to PackRow("+34900111222", label = "Telemarketer")))
        env.install("spam-community")
        env.lookups.reload()
        val hit = env.lookups.sources.find("+34900111222")!!
        assertEquals("community", hit.sourceId)
        assertEquals("Telemarketer", hit.label)
        assertEquals(SpamLevel.COMMUNITY, hit.level)
    }

    @Test
    fun rulesPacksAreReadAsPrefixRules() = runTest {
        val env = Env("lk-rules", this)
        env.install("rules-es", content = rulesJson)
        env.lookups.reload()
        val match = env.lookups.prefixRules.match("+34400123456", "ES")!!
        assertEquals("es-400-commercial", match.ruleId)
        assertTrue(match.informational)
        assertNull(env.lookups.prefixRules.match("+34600123456", "ES"))
    }

    @Test
    fun aDamagedRulesFileIsIgnoredAndTheOtherPacksStillWork() = runTest {
        val env = Env("lk-bad-rules", this)
        env.install("rules-es", content = "{ not json")
        env.dbs["spam-a.db"] = FakePackDb(mapOf("+34900111222" to PackRow("+34900111222")))
        env.install("spam-a")
        env.lookups.reload()
        assertNull(env.lookups.prefixRules.match("+34400123456", "ES"))
        assertNotNull(env.lookups.sources.find("+34900111222"))
    }

    @Test
    fun installedRulesBeatTheOnesCompiledIntoTheApp() = runTest {
        val env = Env("lk-precedence", this)
        val lookups = DefaultInstalledPackLookups(
            store = env.store,
            packsDir = env.dir,
            customSources = { emptyList() },
            opener = { null },
            builtInRules = { listOf(PrefixRule("builtin-400", "ES", "400", SpamLevel.RULE, informational = true)) },
            scope = this,
            io = StandardTestDispatcher(testScheduler)
        )
        lookups.reload()
        assertEquals("builtin-400", lookups.prefixRules.match("+34400123456", "ES")!!.ruleId)
        env.install("rules-es", content = rulesJson)
        lookups.reload()
        assertEquals("es-400-commercial", lookups.prefixRules.match("+34400123456", "ES")!!.ruleId)
    }

    @Test
    fun theBuiltInRulesResourceLoads() {
        assertTrue(DefaultInstalledPackLookups.loadBuiltInRules().isNotEmpty())
    }

    @Test
    fun enabledCustomSourcesAreSpamSourcesWithTheirLevelAndDisabledOnesAreNot() = runTest {
        val env = Env("lk-custom", this)
        val on = CustomSource(id = "on", name = "On list", url = "https://example.org/a", level = SpamLevel.RULE)
        val off = CustomSource(id = "off", name = "Off list", url = "https://example.org/b", enabled = false)
        env.customPack(on, "+34900000001")
        env.customPack(off, "+34900000002")
        env.sources = listOf(on, off)
        env.lookups.reload()

        val hit = env.lookups.sources.find("+34900000001")!!
        assertEquals("custom-on", hit.sourceId)
        assertEquals(SpamLevel.RULE, hit.level)
        assertEquals("On list", hit.label)
        assertNull(env.lookups.sources.find("+34900000002"))

        env.sources = listOf(on, off.copy(enabled = true))
        env.lookups.reload()
        assertNotNull(env.lookups.sources.find("+34900000002"))
    }

    @Test
    fun aMissingCustomPackFileIsSkipped() = runTest {
        val env = Env("lk-custom-missing", this)
        env.sources = listOf(CustomSource(id = "x", name = "X", url = "https://example.org/a"))
        env.lookups.reload()
        assertNull(env.lookups.sources.find("+34900000001"))
    }

    @Test
    fun removingAPackAndReloadingStopsAnsweringFromIt() = runTest {
        val env = Env("lk-remove", this)
        env.dbs["spam-a.db"] = FakePackDb(mapOf("+34900111222" to PackRow("+34900111222")))
        env.install("spam-a")
        env.lookups.reload()
        assertNotNull(env.lookups.sources.find("+34900111222"))
        env.store.remove("spam-a")
        env.lookups.reload()
        assertNull(env.lookups.sources.find("+34900111222"))
    }

    @Test
    fun anUpdateIsSeenByTheSameLookupObjects() = runTest {
        val env = Env("lk-update", this)
        val sources = env.lookups.sources
        env.dbs["spam-a.db"] = FakePackDb(mapOf("+34900111222" to PackRow("+34900111222")))
        env.install("spam-a", "1")
        env.lookups.reload()
        assertNotNull(sources.find("+34900111222"))
        env.dbs["spam-a.db"] = FakePackDb(mapOf("+34900333444" to PackRow("+34900333444")))
        env.install("spam-a", "2")
        env.lookups.reload()
        assertNull(sources.find("+34900111222"))
        assertNotNull(sources.find("+34900333444"))
    }

    @Test
    fun replacedDatabasesAreClosedOnlyAfterTheGracePeriod() = runTest {
        val env = Env("lk-close", this)
        val first = FakePackDb()
        env.dbs["spam-a.db"] = first
        env.install("spam-a")
        env.lookups.reload()
        env.dbs["spam-a.db"] = FakePackDb()
        env.lookups.reload()
        assertFalse("a reader may still hold the old snapshot", first.closed)
        advanceTimeBy(1_500)
        assertTrue(first.closed)
    }

    @Test
    fun everyReloadAnnouncesAChange() = runTest {
        val env = Env("lk-changes", this)
        env.lookups.changes.test {
            env.lookups.reload()
            awaitItem()
            env.lookups.reload()
            awaitItem()
        }
    }

    @Test
    fun aDatabaseThatIsClosedUnderTheReaderGivesNoMatchInsteadOfCrashing() = runTest {
        val env = Env("lk-closed", this)
        env.dbs["spam-a.db"] = object : PackDatabase {
            override fun lookup(e164: String): PackRow? = error("attempt to re-open an already-closed object")

            override fun prefixRules() = emptyList<PrefixRule>()

            override fun close() = Unit
        }
        env.install("spam-a")
        env.lookups.reload()
        assertNull(env.lookups.sources.find("+34900111222"))
    }

    @Test
    fun theFirstLookupBuildsTheSnapshotItself() {
        val dir = testDir("lk-lazy")
        val store = PackStore(dir)
        val file = store.newTempFile("tmp-").also { it.writeText("x") }
        store.install("spam-a", "1", file)
        val lookups = DefaultInstalledPackLookups(
            store = store,
            packsDir = dir,
            customSources = { emptyList() },
            opener = { FakePackDb(mapOf("+34900111222" to PackRow("+34900111222"))) },
            builtInRules = { emptyList() },
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.IO
        )
        assertNotNull("no warm-up was needed", lookups.sources.find("+34900111222"))
    }
}
