package com.qtekfun.ultimatephone.core.contacts

import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.e164OrNull

/**
 * A contact as the duplicate detector sees it. [contactId] is the aggregated ContactsContract contact: Android already
 * merged the raw contacts that share a [contactId], so those are never reported as duplicates of each other.
 * Imported (not yet stored) contacts use negative ids.
 */
data class DuplicateContact(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList()
)

/** Why contacts were put together. */
enum class DuplicateReason { SAME_NUMBER, SAME_EMAIL, SIMILAR_NAME }

/** How sure the detector is: a shared number or e-mail is [HIGH], a similar name alone is [MEDIUM]. */
enum class DuplicateConfidence { HIGH, MEDIUM }

/** Contacts that probably are the same person, with every reason that linked (directly or through others) members. */
data class DuplicateCluster(val members: List<DuplicateContact>, val reasons: Set<DuplicateReason>) {
    val confidence: DuplicateConfidence
        get() = if (DuplicateReason.SAME_NUMBER in reasons || DuplicateReason.SAME_EMAIL in reasons) DuplicateConfidence.HIGH else DuplicateConfidence.MEDIUM

    /** Stable identifier: the sorted lookup keys of the members. */
    val id: String get() = members.map { it.lookupKey }.sorted().joinToString(",")
}

/** Pairs of contacts the user said are not duplicates, as an unordered key. */
object DuplicatePairs {
    private const val SEPARATOR = '\u001F'

    fun key(a: String, b: String): String = if (a <= b) "$a$SEPARATOR$b" else "$b$SEPARATOR$a"

    /** All pairs among [lookupKeys], to ignore a whole cluster at once. */
    fun keysOf(lookupKeys: List<String>): Set<String> {
        val sorted = lookupKeys.distinct()
        return buildSet { for (i in sorted.indices) for (j in i + 1 until sorted.size) add(key(sorted[i], sorted[j])) }
    }

    /** Both lookup keys of a [key]. */
    fun split(key: String): List<String> = key.split(SEPARATOR)
}

/**
 * Finds candidate duplicate contacts. Pure and fast: contacts are put into buckets by key (normalised number, e-mail,
 * name word prefixes) and only contacts in the same bucket are looked at, so 10 000 contacts take well under a second.
 * Links found are joined transitively (union-find) into clusters.
 *
 * Rules:
 *  - same number: equal E.164 form of the number in the SIM [region]; a number that cannot be normalised is compared by its
 *    digits when it has at least [MIN_FALLBACK_DIGITS] of them (short codes are never compared);
 *  - same e-mail: equal ignoring case and surrounding blanks;
 *  - similar name: [NameSimilarity.areSimilar] on contacts whose names share the first three letters of two words (any order).
 *    Very large buckets (more than [MAX_PAIRWISE_BUCKET]) are compared with their [NAME_WINDOW] nearest neighbours in sorted order.
 */
class DuplicateDetector(private val normalizer: PhoneNormalizer, private val region: String?) {
    /**
     * @param ignoredPairs keys from [DuplicatePairs]; those pairs never link, but two contacts may still end in the same
     * cluster through a third contact.
     */
    fun detect(contacts: List<DuplicateContact>, ignoredPairs: Set<String> = emptySet()): List<DuplicateCluster> {
        val merged = mergeSameContact(contacts)
        if (merged.size < 2) return emptyList()
        val run = Run(merged, ignoredPairs)
        run.linkByNumber()
        run.linkByEmail()
        run.linkByName()
        return run.clusters()
    }

    /** Several entries with one [DuplicateContact.contactId] (one per raw contact, say) become one. */
    private fun mergeSameContact(contacts: List<DuplicateContact>): List<DuplicateContact> {
        val byId = LinkedHashMap<Long, DuplicateContact>()
        for (c in contacts) {
            val seen = byId[c.contactId]
            byId[c.contactId] = if (seen == null) c else seen.copy(phones = seen.phones + c.phones, emails = seen.emails + c.emails)
        }
        return byId.values.toList()
    }

    private inner class Run(val contacts: List<DuplicateContact>, ignored: Set<String>) {
        private val n = contacts.size
        private val parent = IntArray(n) { it }
        private val reasonBits = IntArray(n)
        private val ignoredPairs = ignored
        private val involved: BooleanArray = involvedIn(ignored)

        private fun involvedIn(pairs: Set<String>): BooleanArray {
            val flags = BooleanArray(n)
            if (pairs.isEmpty()) return flags
            val keys = HashSet<String>()
            pairs.forEach { keys += DuplicatePairs.split(it) }
            contacts.forEachIndexed { i, c -> flags[i] = c.lookupKey in keys }
            return flags
        }

        private fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var node = x
            while (parent[node] != root) {
                val next = parent[node]
                parent[node] = root
                node = next
            }
            return root
        }

        private fun isIgnored(i: Int, j: Int) = ignoredPairs.isNotEmpty() && DuplicatePairs.key(contacts[i].lookupKey, contacts[j].lookupKey) in ignoredPairs

        private fun link(i: Int, j: Int, reason: DuplicateReason) {
            if (isIgnored(i, j)) return
            val bit = 1 shl reason.ordinal
            reasonBits[i] = reasonBits[i] or bit
            reasonBits[j] = reasonBits[j] or bit
            val a = find(i)
            val b = find(j)
            if (a != b) parent[a] = b
        }

        /** Links all of [members]: a chain for the normal case, every pair for members that have ignored pairs. */
        private fun linkBucket(members: List<Int>, reason: DuplicateReason) {
            if (members.size < 2) return
            val base = members.firstOrNull { !involved[it] }
            for (m in members) {
                if (involved[m]) {
                    for (other in members) if (other != m) link(m, other, reason)
                } else if (base != null && m != base) {
                    link(m, base, reason)
                }
            }
        }

        fun linkByNumber() {
            val cache = HashMap<String, String?>()
            val buckets = HashMap<String, MutableList<Int>>()
            contacts.forEachIndexed { i, c ->
                for (raw in c.phones) {
                    val key = cache.getOrPut(raw) { phoneKey(raw) } ?: continue
                    val list = buckets.getOrPut(key) { ArrayList(2) }
                    if (list.isEmpty() || list.last() != i) list += i
                }
            }
            buckets.values.forEach { linkBucket(it, DuplicateReason.SAME_NUMBER) }
        }

        fun linkByEmail() {
            val buckets = HashMap<String, MutableList<Int>>()
            contacts.forEachIndexed { i, c ->
                for (raw in c.emails) {
                    val key = raw.trim().lowercase()
                    if ('@' !in key) continue
                    val list = buckets.getOrPut(key) { ArrayList(2) }
                    if (list.isEmpty() || list.last() != i) list += i
                }
            }
            buckets.values.forEach { linkBucket(it, DuplicateReason.SAME_EMAIL) }
        }

        fun linkByName() {
            val canonical = Array(n) { NameSimilarity.canonical(contacts[it].displayName) }
            val buckets = HashMap<String, MutableList<Int>>()
            for (i in 0 until n) {
                if (canonical[i].isEmpty()) continue
                for (key in nameKeys(canonical[i])) {
                    val list = buckets.getOrPut(key) { ArrayList(2) }
                    if (list.isEmpty() || list.last() != i) list += i
                }
            }
            for (members in buckets.values) {
                if (members.size < 2) continue
                if (members.size <= MAX_PAIRWISE_BUCKET) {
                    for (x in members.indices) for (y in x + 1 until members.size) compareNames(members[x], members[y], canonical)
                } else {
                    val sorted = members.sortedBy { canonical[it] }
                    for (x in sorted.indices) for (y in x + 1 until minOf(sorted.size, x + 1 + NAME_WINDOW)) compareNames(sorted[x], sorted[y], canonical)
                }
            }
        }

        private fun compareNames(i: Int, j: Int, canonical: Array<String>) {
            if (find(i) == find(j) && !involved[i] && !involved[j]) {
                // Already together through another link; remember that the name agrees too, which costs nothing.
                if (canonical[i] == canonical[j]) {
                    reasonBits[i] = reasonBits[i] or (1 shl DuplicateReason.SIMILAR_NAME.ordinal)
                    reasonBits[j] = reasonBits[j] or (1 shl DuplicateReason.SIMILAR_NAME.ordinal)
                }
                return
            }
            if (NameSimilarity.canonicalMatch(canonical[i], canonical[j])) link(i, j, DuplicateReason.SIMILAR_NAME)
        }

        fun clusters(): List<DuplicateCluster> {
            val groups = HashMap<Int, MutableList<Int>>()
            for (i in 0 until n) groups.getOrPut(find(i)) { ArrayList(2) } += i
            return groups.values.filter { it.size > 1 }.map { indices ->
                var bits = 0
                indices.forEach { bits = bits or reasonBits[it] }
                val reasons = DuplicateReason.entries.filter { bits and (1 shl it.ordinal) != 0 }.toSet()
                DuplicateCluster(indices.map { contacts[it] }.sortedWith(compareBy({ it.displayName.lowercase() }, { it.lookupKey })), reasons)
            }.sortedWith(
                compareBy<DuplicateCluster> { it.confidence.ordinal }
                    .thenByDescending { it.members.size }
                    .thenBy { it.members.first().displayName.lowercase() }
                    .thenBy { it.id }
            )
        }
    }

    private fun phoneKey(raw: String): String? {
        val e164 = normalizer.normalize(raw, region).e164OrNull()
        if (e164 != null) return e164
        val digits = T9.digitsOf(raw)
        return if (digits.length >= MIN_FALLBACK_DIGITS) "d:$digits" else null
    }

    /** Bucket keys of a canonical name: a single word's first letters, or the first letters of every pair of its words. */
    private fun nameKeys(canonical: String): List<String> {
        val words = canonical.split(' ').take(MAX_KEY_WORDS)
        if (words.size == 1) return listOf("1:" + words[0].take(PREFIX))
        return buildList {
            for (a in words.indices) for (b in a + 1 until words.size) add("2:" + words[a].take(PREFIX) + "|" + words[b].take(PREFIX))
        }
    }

    companion object {
        const val MIN_FALLBACK_DIGITS = 6
        const val MAX_PAIRWISE_BUCKET = 200
        const val NAME_WINDOW = 25
        private const val PREFIX = 3
        private const val MAX_KEY_WORDS = 6
    }
}
