package com.qtekfun.ultimatephone.core.contacts

import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Treats 9 digit numbers as Spanish national numbers and "+" numbers as international; anything else is not a number. */
class FakeNormalizer : PhoneNormalizer {
    override fun normalize(raw: String?, defaultRegion: String?): NormalizedNumber {
        val text = raw.orEmpty().trim()
        val digits = text.filter { it.isDigit() }
        return when {
            text.startsWith("+") && digits.length >= 8 -> NormalizedNumber.Valid("+$digits", null)
            text.startsWith("00") && digits.length >= 10 -> NormalizedNumber.Valid("+" + digits.drop(2), null)
            digits.length == 9 -> NormalizedNumber.Valid("+34$digits", "ES")
            else -> NormalizedNumber.Unknown
        }
    }

    override fun formatForDisplay(raw: String?, defaultRegion: String?): String = raw.orEmpty()
}

class DuplicateDetectorTest {
    private val detector = DuplicateDetector(FakeNormalizer(), "ES")

    private fun contact(id: Long, name: String, phones: List<String> = emptyList(), emails: List<String> = emptyList()) =
        DuplicateContact(id, "key$id", name, phones, emails)

    private fun names(cluster: DuplicateCluster) = cluster.members.map { it.displayName }.toSet()

    @Test
    fun `no contacts and one contact give nothing`() {
        assertTrue(detector.detect(emptyList()).isEmpty())
        assertTrue(detector.detect(listOf(contact(1, "Ana", listOf("600111222")))).isEmpty())
    }

    @Test
    fun `same number in different formats is a high confidence duplicate`() {
        val clusters = detector.detect(
            listOf(contact(1, "Ana", listOf("600 111 222")), contact(2, "Anita", listOf("+34600111222")), contact(3, "Other", listOf("611111111")))
        )
        assertEquals(1, clusters.size)
        assertEquals(setOf("Ana", "Anita"), names(clusters[0]))
        assertEquals(setOf(DuplicateReason.SAME_NUMBER), clusters[0].reasons)
        assertEquals(DuplicateConfidence.HIGH, clusters[0].confidence)
    }

    @Test
    fun `international prefix 00 equals plus`() {
        val clusters = detector.detect(listOf(contact(1, "A", listOf("0034600111222")), contact(2, "B", listOf("+34 600 111 222"))))
        assertEquals(1, clusters.size)
    }

    @Test
    fun `numbers that cannot be normalised are compared by digits, short codes never`() {
        val long = detector.detect(listOf(contact(1, "A", listOf("(12) 34-5678")), contact(2, "B", listOf("12345678"))))
        assertEquals(1, long.size)
        val short = detector.detect(listOf(contact(1, "A", listOf("112")), contact(2, "B", listOf("112"))))
        assertTrue(short.isEmpty())
    }

    @Test
    fun `same email ignoring case and blanks`() {
        val clusters = detector.detect(listOf(contact(1, "Bob", emails = listOf("Bob@Example.COM ")), contact(2, "Robert", emails = listOf("bob@example.com"))))
        assertEquals(1, clusters.size)
        assertEquals(setOf(DuplicateReason.SAME_EMAIL), clusters[0].reasons)
        assertEquals(DuplicateConfidence.HIGH, clusters[0].confidence)
    }

    @Test
    fun `similar name alone is medium confidence`() {
        val clusters = detector.detect(
            listOf(
                contact(1, "Ana García", listOf("600111222")),
                contact(2, "García, Ana", listOf("611222333")),
                contact(3, "ana garcia", listOf("622333444")),
                contact(4, "Ana Gómez", listOf("633444555"))
            )
        )
        assertEquals(1, clusters.size)
        assertEquals(setOf("Ana García", "García, Ana", "ana garcia"), names(clusters[0]))
        assertEquals(setOf(DuplicateReason.SIMILAR_NAME), clusters[0].reasons)
        assertEquals(DuplicateConfidence.MEDIUM, clusters[0].confidence)
    }

    @Test
    fun `different people with a common first name are not clustered`() {
        val clusters = detector.detect(listOf(contact(1, "Ana García"), contact(2, "Ana Gómez"), contact(3, "Pedro García")))
        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `links are transitive and the reasons accumulate`() {
        val clusters = detector.detect(
            listOf(
                contact(1, "Ana", listOf("600111222")),
                contact(2, "Ana G", listOf("600111222"), listOf("ana@x.org")),
                contact(3, "Anita", emails = listOf("ANA@x.org")),
                contact(4, "Zed", listOf("699999999"))
            )
        )
        assertEquals(1, clusters.size)
        assertEquals(3, clusters[0].members.size)
        assertEquals(setOf(DuplicateReason.SAME_NUMBER, DuplicateReason.SAME_EMAIL), clusters[0].reasons)
    }

    @Test
    fun `entries of one aggregated contact are never duplicates of each other`() {
        val sameContact = listOf(
            DuplicateContact(7, "k7", "Ana", listOf("600111222"), emptyList()),
            DuplicateContact(7, "k7", "Ana", listOf("600111222"), listOf("a@x.org"))
        )
        assertTrue(detector.detect(sameContact).isEmpty())
        // ... but the combined data of that contact still links it to another contact.
        val other = contact(8, "Other", emails = listOf("a@x.org"))
        assertEquals(1, detector.detect(sameContact + other).size)
    }

    @Test
    fun `contacts with the same number twice are listed once`() {
        val clusters = detector.detect(listOf(contact(1, "A", listOf("600111222", "600 111 222")), contact(2, "B", listOf("600111222"))))
        assertEquals(1, clusters.size)
        assertEquals(2, clusters[0].members.size)
    }

    @Test
    fun `ignored pairs are not suggested again`() {
        val a = contact(1, "Ana", listOf("600111222"))
        val b = contact(2, "Anita", listOf("600111222"))
        val ignored = setOf(DuplicatePairs.key(a.lookupKey, b.lookupKey))
        assertTrue(detector.detect(listOf(a, b), ignored).isEmpty())
        assertEquals(ignored, DuplicatePairs.keysOf(listOf(b.lookupKey, a.lookupKey)))
    }

    @Test
    fun `an ignored pair can still be joined through a third contact`() {
        val a = contact(1, "Ana", listOf("600111222"))
        val b = contact(2, "Anita", listOf("600111222"))
        val c = contact(3, "Annie", listOf("600111222"))
        val ignored = setOf(DuplicatePairs.key(a.lookupKey, b.lookupKey))
        val clusters = detector.detect(listOf(a, b, c), ignored)
        assertEquals(1, clusters.size)
        assertEquals(3, clusters[0].members.size)
        // Ignoring every pair of the cluster removes it.
        assertTrue(detector.detect(listOf(a, b, c), DuplicatePairs.keysOf(listOf("key1", "key2", "key3"))).isEmpty())
    }

    @Test
    fun `clusters are ordered high confidence first`() {
        val clusters = detector.detect(
            listOf(
                contact(1, "Maria Lopez"),
                contact(2, "Lopez Maria"),
                contact(3, "Zoe", listOf("600000001")),
                contact(4, "Zed", listOf("600000001"))
            )
        )
        assertEquals(listOf(DuplicateConfidence.HIGH, DuplicateConfidence.MEDIUM), clusters.map { it.confidence })
    }

    @Test
    fun `huge buckets are still handled`() {
        // 500 contacts with the same number: one cluster, no quadratic blow-up.
        val many = (1L..500L).map { contact(it, "Contact $it", listOf("600111222")) }
        val clusters = detector.detect(many)
        assertEquals(1, clusters.size)
        assertEquals(500, clusters[0].members.size)
    }

    @Test
    fun `ten thousand contacts are analysed in about a second`() {
        val random = Random(42)
        val first = listOf("ana", "luis", "maria", "pedro", "lucia", "carlos", "sofia", "diego", "elena", "pablo")
        val syllables = listOf("ba", "ce", "di", "fo", "gu", "ha", "je", "ki", "lo", "mu", "na", "pe", "qui", "ro", "su", "ta", "ve", "xu", "yo", "zi")

        fun surname() = (1..3).joinToString("") { syllables[random.nextInt(syllables.size)] }
        val contacts = (1L..10_000L).map { id ->
            val phones = listOf("6" + random.nextInt(100_000_000).toString().padStart(8, '0'), "9" + random.nextInt(100_000_000).toString().padStart(8, '0'))
            contact(id, first[random.nextInt(first.size)] + " " + surname() + id % 7, phones, listOf("user$id@example.org"))
        }
        // A few planted duplicates, so the result is not trivially empty.
        val planted = contacts.take(50).mapIndexed { i, c -> c.copy(contactId = 20_000L + i, lookupKey = "dup$i", phones = c.phones.take(1)) }
        val all = contacts + planted

        detector.detect(all.take(1_000)) // warm up the JIT
        val started = System.nanoTime()
        val clusters = detector.detect(all)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $millis ms", millis < 1_000)
        assertTrue(clusters.size >= 50)
    }
}
