package com.qtekfun.ultimatephone.core.spam.lists

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The DAO is thin SQL; this in-memory stand-in lets the repository logic run on the JVM. */
private class FakeDao : ListEntryDao {
    val rows = MutableStateFlow<Map<String, ListEntryEntity>>(emptyMap())

    override fun observeLive(list: String): Flow<List<ListEntryEntity>> =
        rows.map { m -> m.values.filter { it.list == list && !it.deleted }.sortedByDescending { it.updatedAt } }

    override suspend fun all(list: String) = rows.value.values.filter { it.list == list }

    override suspend fun byIds(ids: List<String>) = ids.mapNotNull { rows.value[it] }

    override suspend fun find(list: String, kind: String, value: String) =
        rows.value.values.firstOrNull { it.list == list && it.kind == kind && it.value == value }

    override suspend fun liveMatching(list: String, keys: List<String>) = rows.value.values.filter { it.list == list && !it.deleted && it.value in keys }

    override suspend fun upsert(entry: ListEntryEntity) {
        rows.value = rows.value + (entry.id to entry)
    }

    override suspend fun deleteTombstones(ids: List<String>) {
        rows.value = rows.value.filterValues { !(it.id in ids && it.deleted) }
    }

    override suspend fun upsertAll(entries: List<ListEntryEntity>) {
        rows.value = rows.value + entries.associateBy { it.id }
    }
}

class RepositoryTest {
    private var now = 1_000L
    private var counter = 0
    private val dao = FakeDao()
    private val repo = RoomSpamListsRepository(dao, "device-a", { now++ }, { "id-${counter++}" })

    @Test
    fun deviceIdIsReadWhenAChangeIsMadeNotWhenTheRepositoryIsBuilt() = runTest {
        var reads = 0
        val lazy = RoomSpamListsRepository(FakeDao(), { "device-${++reads}" }, { now++ }, { "id-${counter++}" })
        assertEquals(0, reads)
        assertFalse(lazy.isWhitelisted("+34612345678"))
        assertEquals(0, reads)
        val entry = lazy.add(ListType.OWN, EntryKind.NUMBER, "+34612345678")
        assertEquals("device-1", entry.deviceId)
    }

    @Test
    fun addedNumberMatchesExactlyAndNotNeighbours() = runTest {
        repo.add(ListType.OWN, EntryKind.NUMBER, "+34612345678", "robocall")
        assertEquals("robocall", repo.matchOwn("+34612345678")?.label)
        assertNull(repo.matchOwn("+34612345679"))
        assertFalse(repo.isWhitelisted("+34612345678"))
    }

    @Test
    fun prefixEntryMatchesNumbersUnderItAndLongestWins() = runTest {
        repo.add(ListType.OWN, EntryKind.PREFIX, "+34900", "short")
        repo.add(ListType.OWN, EntryKind.PREFIX, "+349001", "long")
        assertEquals("long", repo.matchOwn("+34900123456")?.label)
        assertEquals("short", repo.matchOwn("+34900223456")?.label)
        assertNull(repo.matchOwn("+34901223456"))
    }

    @Test
    fun listsAreIndependent() = runTest {
        repo.add(ListType.WHITELIST, EntryKind.NUMBER, "+34612345678")
        assertTrue(repo.isWhitelisted("+34612345678"))
        assertNull(repo.matchOwn("+34612345678"))
    }

    @Test
    fun removeKeepsATombstoneAndStopsMatching() = runTest {
        val e = repo.add(ListType.OWN, EntryKind.NUMBER, "+34612345678")
        assertTrue(repo.remove(ListType.OWN, e.id))
        assertNull(repo.matchOwn("+34612345678"))
        assertTrue(repo.observe(ListType.OWN).first().isEmpty())
        val all = repo.entries(ListType.OWN)
        assertEquals(1, all.size)
        assertTrue(all.single().deleted)
        assertTrue(all.single().updatedAt > e.updatedAt)
        assertFalse(repo.remove(ListType.OWN, e.id))
        assertFalse(repo.remove(ListType.WHITELIST, e.id))
    }

    @Test
    fun readdingRevivesTheSameIdAndUpdatesFields() = runTest {
        val first = repo.add(ListType.OWN, EntryKind.NUMBER, "+34612345678", "a")
        repo.remove(ListType.OWN, first.id)
        val again = repo.add(ListType.OWN, EntryKind.NUMBER, "+34612345678", "b", "note")
        assertEquals(first.id, again.id)
        assertFalse(again.deleted)
        assertEquals("b", repo.matchOwn("+34612345678")?.label)
        assertEquals(1, repo.entries(ListType.OWN).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidValuesAreRejected() = runTest {
        repo.add(ListType.OWN, EntryKind.NUMBER, "612345678")
    }

    @Test
    fun exportThenImportOnAnotherDeviceReproducesTheList() = runTest {
        repo.add(ListType.OWN, EntryKind.NUMBER, "+34612345678", "a")
        val p = repo.add(ListType.OWN, EntryKind.PREFIX, "+34900", "b")
        repo.remove(ListType.OWN, p.id)
        val text = repo.exportJsonLines(ListType.OWN)

        val other = RoomSpamListsRepository(FakeDao(), "device-b")
        val result = other.importJsonLines(ListType.OWN, text.lineSequence())
        assertEquals(ImportResult(added = 2, updated = 0, unchanged = 0, badLines = 0), result)
        assertEquals("a", other.matchOwn("+34612345678")?.label)
        assertNull(other.matchOwn("+34900123456"))
        assertEquals(2, other.entries(ListType.OWN).size)
    }

    @Test
    fun importMergesByUpdatedAtAndSkipsBadLines() = runTest {
        val local = repo.add(ListType.OWN, EntryKind.NUMBER, "+34612345678", "local")
        val older = EntryJsonl.encode(local.copy(updatedAt = local.updatedAt - 1, label = "older"))
        val newer = EntryJsonl.encode(local.copy(updatedAt = local.updatedAt + 5, label = "newer"))
        val same = EntryJsonl.encode(local)

        assertEquals(ImportResult(0, 0, 1, 1), repo.importJsonLines(ListType.OWN, sequenceOf(older, "garbage")))
        assertEquals("local", repo.matchOwn("+34612345678")?.label)
        assertEquals(ImportResult(0, 0, 1, 0), repo.importJsonLines(ListType.OWN, sequenceOf(same)))
        assertEquals(ImportResult(0, 1, 0, 0), repo.importJsonLines(ListType.OWN, sequenceOf(older, newer)))
        assertEquals("newer", repo.matchOwn("+34612345678")?.label)
    }

    @Test
    fun importedTombstoneRemovesTheEntry() = runTest {
        val local = repo.add(ListType.OWN, EntryKind.NUMBER, "+34612345678")
        val dead = EntryJsonl.encode(local.copy(updatedAt = local.updatedAt + 1, deleted = true, deviceId = "other"))
        repo.importJsonLines(ListType.OWN, sequenceOf(dead))
        assertNull(repo.matchOwn("+34612345678"))
    }

    @Test
    fun adaptersExposeTheRepositoryToTheEngine() {
        runTest { repo.add(ListType.OWN, EntryKind.PREFIX, "+34900", "x") }
        runTest { repo.add(ListType.WHITELIST, EntryKind.NUMBER, "+34612345678") }
        assertEquals("x", RepositoryOwnList(repo).match("+34900123456")?.label)
        assertNull(RepositoryOwnList(repo).match("+34911123456"))
        assertTrue(RepositoryWhitelist(repo).isWhitelisted("+34612345678"))
        assertNotEquals(true, RepositoryWhitelist(repo).isWhitelisted("+34612345679"))
    }
}
