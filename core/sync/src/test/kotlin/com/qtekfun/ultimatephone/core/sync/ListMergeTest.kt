package com.qtekfun.ultimatephone.core.sync

import com.qtekfun.ultimatephone.core.spam.lists.EntryJsonl
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun entry(
    id: String,
    value: String = "+34600000001",
    at: Long = 1_000,
    device: String = "a",
    deleted: Boolean = false,
    label: String? = null,
    note: String? = null,
    kind: EntryKind = EntryKind.NUMBER
) = ListEntry(id, kind, value, label, note, at, device, deleted)

class ListMergeTest {
    private val now = 10_000_000_000L

    private fun merge(l: Collection<ListEntry>, r: Collection<ListEntry>) = ListMerge.merge(l, r, now)

    @Test
    fun newestUpdatedAtWins() {
        val old = entry("1", at = 100, label = "old")
        val new = entry("1", at = 200, label = "new", device = "b")
        assertEquals(listOf(new), merge(listOf(old), listOf(new)))
        assertEquals(listOf(new), merge(listOf(new), listOf(old)))
    }

    @Test
    fun tieGoesToTheGreaterDeviceIdOnBothSides() {
        val a = entry("1", at = 100, device = "device-a", label = "from a")
        val b = entry("1", at = 100, device = "device-b", label = "from b")
        assertEquals(listOf(b), merge(listOf(a), listOf(b)))
        assertEquals(listOf(b), merge(listOf(b), listOf(a)))
    }

    @Test
    fun fullTieOnIdTimeAndDeviceStillResolvesSymmetrically() {
        val live = entry("1", at = 100, device = "d", label = "x")
        val dead = live.copy(deleted = true)
        val other = live.copy(label = "y")
        assertEquals(merge(listOf(live), listOf(dead)), merge(listOf(dead), listOf(live)))
        assertTrue(merge(listOf(live), listOf(dead)).single().deleted)
        assertEquals(merge(listOf(live), listOf(other)), merge(listOf(other), listOf(live)))
    }

    @Test
    fun unrelatedEntriesFromBothSidesAreKept() {
        val l = listOf(entry("1", "+34600000001"), entry("2", "+34600000002"))
        val r = listOf(entry("3", "+34600000003"), entry("2", "+34600000002", at = 50))
        assertEquals(listOf("1", "2", "3"), merge(l, r).map { it.id })
    }

    @Test
    fun tombstoneBeatsOlderLiveEntry() {
        val live = entry("1", at = 100)
        val gone = entry("1", at = 200, device = "b", deleted = true)
        assertTrue(merge(listOf(live), listOf(gone)).single().deleted)
        assertTrue(merge(listOf(gone), listOf(live)).single().deleted)
    }

    @Test
    fun newerLiveEntryRevivesATombstone() {
        val gone = entry("1", at = 100, deleted = true)
        val back = entry("1", at = 300, device = "b")
        assertEquals(false, merge(listOf(gone), listOf(back)).single().deleted)
    }

    @Test
    fun tombstonesAreKeptForTheRetentionThenDropped() {
        val day = ListMerge.DAY_MS
        val recent = entry("1", at = now - 179 * day, deleted = true)
        val expired = entry("2", value = "+34600000002", at = now - 181 * day, deleted = true)
        val result = ListMerge.merge(listOf(recent, expired), emptyList(), now)
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun retentionIsConfigurable() {
        val gone = entry("1", at = now - 2 * ListMerge.DAY_MS, deleted = true)
        assertEquals(1, ListMerge.merge(listOf(gone), emptyList(), now, 3 * ListMerge.DAY_MS).size)
        assertEquals(0, ListMerge.merge(listOf(gone), emptyList(), now, ListMerge.DAY_MS).size)
    }

    @Test
    fun anExpiredTombstoneDoesNotLetAnOlderLiveCopyComeBack() {
        val day = ListMerge.DAY_MS
        val expired = entry("1", at = now - 300 * day, deleted = true)
        val staleLive = entry("1", at = now - 400 * day)
        assertEquals(emptyList<ListEntry>(), ListMerge.merge(listOf(expired), listOf(staleLive), now))
    }

    @Test
    fun theSameNumberAddedOnTwoDevicesCollapsesDeterministically() {
        val onA = entry("id-a", at = 100, device = "a", label = "A")
        val onB = entry("id-b", at = 150, device = "b", label = "B")
        val ab = merge(listOf(onA), listOf(onB))
        val ba = merge(listOf(onB), listOf(onA))
        assertEquals(ab, ba)
        assertEquals(listOf("id-b"), ab.filter { !it.deleted }.map { it.id })
        assertEquals(true, ab.single { it.id == "id-a" }.deleted)
    }

    @Test
    fun collapsedLoserStaysDeadWhenMergedAgainWithItsOriginal() {
        val onA = entry("id-a", at = 100, device = "a")
        val onB = entry("id-b", at = 100, device = "b")
        val first = merge(listOf(onA), listOf(onB))
        val again = merge(first, listOf(onA))
        assertEquals(first, again)
        assertEquals(1, again.count { !it.deleted })
    }

    @Test
    fun samePrefixAndNumberValuesAreDifferentGroups() {
        val number = entry("1", value = "+34900123456")
        val prefix = entry("2", value = "+34900123456", kind = EntryKind.PREFIX)
        assertEquals(2, merge(listOf(number), listOf(prefix)).count { !it.deleted })
    }

    @Test
    fun resultIsSortedAndEncodesIdentically() {
        val l = listOf(entry("b"), entry("a", "+34600000002"))
        val r = listOf(entry("c", "+34600000003"))
        assertEquals(ListMerge.canonical(merge(l, r)), ListMerge.canonical(merge(r, l)))
        assertEquals(listOf("a", "b", "c"), merge(l, r).map { it.id })
    }

    @Test
    fun unknownFieldsAndCorruptLinesAreHandledByTheReader() {
        val text = """
            {"id":"1","kind":"number","value":"+34600000001","label":null,"note":null,"updatedAt":5,"deviceId":"a","deleted":false,"future":{"x":1}}
            this is not json
            {"id":"2","kind":"number","value":"not-a-number","updatedAt":5}

            {"id":"3","kind":"prefix","value":"+34900","updatedAt":7,"deviceId":"b"}
        """.trimIndent()
        val parsed = EntryJsonl.decodeAll(text)
        assertEquals(listOf("1", "3"), parsed.entries.map { it.id })
        assertEquals(2, parsed.badLines)
    }

    @Test
    fun fingerprintFollowsContentNotOrder() {
        val a = entry("1")
        val b = entry("2", "+34600000002")
        assertEquals(ListMerge.fingerprint(listOf(a, b)), ListMerge.fingerprint(listOf(b, a)))
        assertTrue(ListMerge.fingerprint(listOf(a)) != ListMerge.fingerprint(listOf(a.copy(label = "x"))))
    }

    // Property-style checks over random histories with a fixed seed, so a failure reproduces.

    private fun randomList(random: Random, devices: List<String>, ids: List<String>, values: List<String>): List<ListEntry> =
        ids.filter { random.nextInt(3) != 0 }.map { id ->
            val deleted = random.nextInt(4) == 0
            entry(
                id = id,
                value = values[ids.indexOf(id) % values.size],
                at = random.nextLong(1, 6) * 1_000 + now - 20_000,
                device = devices.random(random),
                deleted = deleted,
                label = if (random.nextBoolean()) "l${random.nextInt(3)}" else null,
                note = if (random.nextInt(4) == 0) "n" else null
            )
        }

    private fun randomHistories(seed: Int, count: Int = 300, body: (List<ListEntry>, List<ListEntry>, List<ListEntry>) -> Unit) {
        val random = Random(seed)
        val devices = listOf("dev-a", "dev-b", "dev-c")
        val ids = (1..6).map { "id$it" }
        // Fewer values than ids, so independent additions of one number do happen.
        val values = listOf("+34600000001", "+34600000002", "+34600000003", "+34600000004")
        repeat(count) {
            body(randomList(random, devices, ids, values), randomList(random, devices, ids, values), randomList(random, devices, ids, values))
        }
    }

    @Test
    fun mergeIsCommutative() = randomHistories(1) { a, b, _ ->
        assertEquals(merge(a, b), merge(b, a))
    }

    @Test
    fun mergeIsIdempotent() = randomHistories(2) { a, b, _ ->
        val m = merge(a, b)
        assertEquals(m, merge(m, m))
        assertEquals(m, merge(m, a))
        assertEquals(m, merge(m, b))
        assertEquals(m, merge(a, m))
    }

    /** Every device merges with every other until nothing changes: what a few rounds of syncing through a server do. */
    private fun gossip(start: List<List<ListEntry>>, order: List<Pair<Int, Int>>): List<List<ListEntry>> {
        var states = start.map { merge(it, emptyList()) }
        repeat(4) {
            for ((i, j) in order) {
                val m = merge(states[i], states[j])
                states = states.toMutableList().also { s ->
                    s[i] = m
                    s[j] = m
                }
            }
        }
        return states
    }

    @Test
    fun threeDevicesConvergeWhateverTheOrderOfSyncing() = randomHistories(3) { a, b, c ->
        val one = gossip(listOf(a, b, c), listOf(0 to 1, 1 to 2, 0 to 2))
        val two = gossip(listOf(a, b, c), listOf(2 to 0, 1 to 0, 2 to 1))
        assertEquals(1, one.toSet().size)
        assertEquals(1, two.toSet().size)
        // Which of two ids that carry one number survives may depend on the schedule when one of them is deleted
        // meanwhile; every device still ends in the same state, which is what sync promises.
    }

    @Test
    fun collapsingNeverResurrectsAndOnlyMovesIdsUp() = randomHistories(6) { a, b, _ ->
        val m = merge(a, b).associateBy { it.id }
        for (input in a + b) {
            val out = m.getValue(input.id)
            assertEquals(out, ListMerge.winner(out, input))
        }
    }

    @Test
    fun noLiveValueIsLostAndNoValueIsLiveTwice() = randomHistories(4) { a, b, _ ->
        val m = merge(a, b)
        val liveValues = m.filter { !it.deleted }.map { it.kind to it.value }
        assertEquals(liveValues.size, liveValues.toSet().size)
        // Every id of either input survives as itself or as a newer entry.
        (a + b).forEach { input -> assertTrue(m.any { it.id == input.id }) }
    }

    @Test
    fun anEditOnOneSideNeverRemovesEntriesTheOtherSideKept() = randomHistories(5) { a, b, _ ->
        val m = merge(a, b)
        val ids = m.map { it.id }.toSet()
        assertTrue(ids.containsAll((a + b).map { it.id }))
    }

    @Test
    fun largeListsMergeQuickly() {
        val l = (0 until 20_000).map { entry("l$it", "+3460${"%07d".format(it)}", at = it.toLong() + 1) }
        val r = (0 until 20_000).map { entry("r$it", "+3461${"%07d".format(it)}", at = it.toLong() + 1) }
        assertEquals(40_000, merge(l, r).size)
    }
}
