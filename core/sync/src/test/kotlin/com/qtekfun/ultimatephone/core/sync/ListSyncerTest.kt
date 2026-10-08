package com.qtekfun.ultimatephone.core.sync

import com.qtekfun.ultimatephone.core.spam.lists.EntryJsonl
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListSyncerTest {
    private val server = FakeDavServer()
    private val a = Device("phone-a", server)
    private val b = Device("phone-b", server)
    private val own = ListType.OWN
    private val white = ListType.WHITELIST

    @Test
    fun firstSyncCreatesTheFolderAndBothFiles() = runTest {
        a.add(own, "+34600000001", "robocall")
        val result = a.sync()
        assertTrue(result.isSuccess)
        assertTrue(server.requests.contains("MKCOL UltimatePhone"))
        assertEquals(1, EntryJsonl.decodeAll(server.text("UltimatePhone/spam-list.jsonl")!!).entries.size)
        assertEquals(0, EntryJsonl.decodeAll(server.text("UltimatePhone/whitelist.jsonl")!!).entries.size)
        assertEquals(1, result.counters.pushed)
    }

    @Test
    fun aNumberAddedOnAAppearsOnB() = runTest {
        a.add(own, "+34600000001")
        a.sync()
        val result = b.sync()
        assertEquals(setOf("+34600000001"), b.local.live(own))
        assertEquals(1, result.counters.pulled)
    }

    @Test
    fun aDeletionOnBRemovesTheNumberOnA() = runTest {
        a.add(own, "+34600000001")
        a.sync()
        b.sync()
        b.remove(own, "+34600000001")
        b.sync()
        a.sync()
        assertEquals(emptySet<String>(), a.local.live(own))
        assertEquals(emptySet<String>(), b.local.live(own))
    }

    @Test
    fun whitelistAndOwnListAreSeparateFiles() = runTest {
        a.add(own, "+34600000001")
        a.add(white, "+34600000002")
        a.sync()
        b.sync()
        assertEquals(setOf("+34600000001"), b.local.live(own))
        assertEquals(setOf("+34600000002"), b.local.live(white))
    }

    @Test
    fun simultaneousChangesToTheSameNumberResolveWithoutLosingOthers() = runTest {
        a.add(own, "+34600000001", "first")
        a.sync()
        b.sync()
        // Both edit the same number while offline and each also adds an unrelated one.
        a.add(own, "+34600000001", "edited on A")
        a.add(own, "+34600000010")
        b.now += 1_000
        b.add(own, "+34600000001", "edited on B")
        b.add(own, "+34600000020")
        a.sync()
        b.sync()
        a.sync()
        val expected = setOf("+34600000001", "+34600000010", "+34600000020")
        assertEquals(expected, a.local.live(own))
        assertEquals(expected, b.local.live(own))
        // The later edit (B) wins on both.
        val label = { d: Device -> d.local.rows[own]!!.values.first { it.value == "+34600000001" }.label }
        assertEquals("edited on B", label(a))
        assertEquals("edited on B", label(b))
    }

    @Test
    fun theSameNumberAddedIndependentlyEndsAsOneLiveEntryEverywhere() = runTest {
        a.add(own, "+34600000001", "from A")
        b.add(own, "+34600000001", "from B")
        a.sync()
        b.sync()
        a.sync()
        listOf(a, b).forEach { d ->
            assertEquals(1, d.local.rows[own]!!.values.count { !it.deleted })
            assertEquals(setOf("+34600000001"), d.local.live(own))
        }
        assertEquals(
            a.local.rows[own]!!.values.filter { !it.deleted }.map { it.id },
            b.local.rows[own]!!.values.filter { !it.deleted }.map { it.id }
        )
    }

    @Test
    fun aLostRaceRetriesWithBackoffAndKeepsBothChanges() = runTest {
        a.add(own, "+34600000001")
        a.sync()
        b.sync()
        a.add(own, "+34600000002")
        b.add(own, "+34600000003")
        // B uploads between A's download and A's upload.
        var fired = false
        server.beforePut = {
            if (!fired) {
                fired = true
                server.beforePut = null
                b.sync()
            }
        }
        val result = a.sync()
        assertTrue(result.isSuccess)
        assertEquals(1, result.counters.retries)
        assertEquals(1, a.pauses.size)
        b.sync()
        val all = setOf("+34600000001", "+34600000002", "+34600000003")
        assertEquals(all, a.local.live(own))
        assertEquals(all, b.local.live(own))
    }

    @Test
    fun retriesAreBoundedAndEndInConflictExhausted() = runTest {
        val busy = Device("busy", server, attempts = 3)
        busy.add(own, "+34600000001")
        // Another writer changes the file before every upload.
        var n = 0
        server.beforePut = { path -> server.plant(path, EntryJsonl.encode(entry("x${n++}", "+3469000000$n", at = 5, device = "z")) + "\n") }
        val result = busy.sync()
        assertEquals(SyncError.CONFLICT_EXHAUSTED, result.error)
        assertEquals(3, busy.pauses.size)
        assertTrue(busy.pauses.zipWithNext().all { (x, y) -> y >= x })
    }

    @Test
    fun offlineChangesWaitAndGoOutOnTheNextSync() = runTest {
        a.add(own, "+34600000001")
        a.sync()
        assertFalse(a.syncer.isDirty())
        server.failWith = SyncException(SyncError.NETWORK, "down")
        a.add(own, "+34600000002")
        assertTrue(a.syncer.isDirty())
        val failed = a.sync()
        assertEquals(SyncError.NETWORK, failed.error)
        assertTrue(a.syncer.isDirty())
        server.failWith = null
        assertTrue(a.sync().isSuccess)
        assertFalse(a.syncer.isDirty())
        b.sync()
        assertEquals(setOf("+34600000001", "+34600000002"), b.local.live(own))
    }

    @Test
    fun anUnchangedSyncUsesAConditionalGetAndWritesNothing() = runTest {
        a.add(own, "+34600000001")
        a.sync()
        server.requests.clear()
        val result = a.sync()
        assertTrue(result.isSuccess)
        assertEquals(SyncCounters(), result.counters)
        assertTrue(server.requests.none { it.startsWith("PUT") })
    }

    @Test
    fun aLocalEditDuringTheSyncIsNotLostAndStaysDirty() = runTest {
        b.add(own, "+34600000009")
        b.sync()
        a.add(own, "+34600000001")
        a.local.beforeApply = {
            a.local.beforeApply = null
            a.add(own, "+34600000002")
        }
        assertTrue(a.sync().isSuccess)
        assertTrue(a.local.live(own).contains("+34600000002"))
        assertTrue(a.syncer.isDirty())
        a.sync()
        b.sync()
        assertTrue(b.local.live(own).contains("+34600000002"))
    }

    @Test
    fun serverWithoutEtagsIsTooOld() = runTest {
        a.add(own, "+34600000001")
        a.sync()
        server.omitEtags = true
        assertEquals(SyncError.SERVER_TOO_OLD, b.sync().error)
    }

    @Test
    fun aRemoteFileThatIsNotOursIsNeverOverwritten() = runTest {
        server.plant("UltimatePhone/spam-list.jsonl", "<html>captive portal</html>\n")
        a.add(own, "+34600000001")
        val result = a.sync()
        assertEquals(SyncError.REMOTE_CORRUPT, result.error)
        assertEquals("<html>captive portal</html>\n", server.text("UltimatePhone/spam-list.jsonl"))
    }

    @Test
    fun corruptLinesAmongGoodOnesAreSkippedAndCounted() = runTest {
        val good = EntryJsonl.encode(entry("1", "+34600000001", device = "z"))
        server.plant("UltimatePhone/spam-list.jsonl", "$good\nnot json\n{\"id\":1}\n")
        server.plant("UltimatePhone/whitelist.jsonl", "")
        val result = a.sync()
        assertTrue(result.isSuccess)
        assertEquals(2, result.counters.badLines)
        assertEquals(setOf("+34600000001"), a.local.live(own))
    }

    @Test
    fun errorsFromTheServerAreReportedWithoutDetailsOfTheLists() = runTest {
        a.add(own, "+34600000001")
        server.failWith = SyncException(SyncError.AUTH, "HTTP 401")
        val result = a.sync()
        assertEquals(SyncError.AUTH, result.error)
        assertFalse(result.toString().contains("34600000001"))
    }

    @Test
    fun expiredTombstonesAreRemovedFromTheDeviceToo() = runTest {
        a.add(own, "+34600000001")
        a.remove(own, "+34600000001")
        a.sync()
        a.now += 200 * ListMerge.DAY_MS
        // Something else changes so the sync does not take the unchanged shortcut.
        a.add(own, "+34600000002")
        a.sync()
        assertEquals(listOf("+34600000002"), a.local.rows[own]!!.values.map { it.value })
        assertEquals(1, EntryJsonl.decodeAll(server.text("UltimatePhone/spam-list.jsonl")!!).entries.size)
    }

    @Test
    fun threeDevicesConverge() = runTest {
        val c = Device("phone-c", server)
        a.add(own, "+34600000001")
        b.add(own, "+34600000002")
        c.add(own, "+34600000003")
        c.add(own, "+34600000001")
        listOf(a, b, c, a, b, c).forEach { it.sync() }
        val all = setOf("+34600000001", "+34600000002", "+34600000003")
        listOf(a, b, c).forEach { assertEquals(all, it.local.live(own)) }
    }
}
