package com.qtekfun.ultimatephone.core.contacts

import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMergerTest {
    private val merger = ContactMerger(FakeNormalizer(), "ES")

    private fun source(
        key: String,
        name: String,
        phones: List<String> = emptyList(),
        emails: List<String> = emptyList(),
        raw: List<Long> = listOf(key.removePrefix("k").toLong()),
        organization: String = "",
        birthday: ContactDate? = null,
        notes: List<String> = emptyList(),
        starred: Boolean = false,
        photo: String? = null,
        names: List<String> = listOf(name)
    ) = MergeSource(
        lookupKey = key,
        contactId = raw.first(),
        displayName = name,
        names = names,
        organization = organization,
        phones = phones.map { LabeledValue(it, LabelTypes.MOBILE) },
        emails = emails.map { LabeledValue(it, LabelTypes.EMAIL_HOME) },
        birthday = birthday,
        notes = notes,
        starred = starred,
        photoUri = photo,
        rawContactIds = raw,
        account = null
    )

    @Test
    fun `merging two duplicates loses no phone or email`() {
        val a = source("k1", "Ana García", phones = listOf("600111222", "+34911234567"), emails = listOf("ana@home.org"))
        val b = source("k2", "García, Ana", phones = listOf("+34600111222", "611999888"), emails = listOf("ANA@HOME.ORG", "ana@work.org"))
        val merged = merger.merge(listOf(a, b))

        val normalizer = FakeNormalizer()
        fun key(p: String) = (normalizer.normalize(p, "ES") as NormalizedNumber.Valid).e164
        val expectedPhones = (a.phones + b.phones).map { key(it.value) }.toSet()
        assertEquals(expectedPhones, merged.phones.map { key(it.value) }.toSet())
        assertEquals(merged.phones.size, expectedPhones.size) // and no duplicates
        val expectedEmails = (a.emails + b.emails).map { it.value.lowercase() }.toSet()
        assertEquals(expectedEmails, merged.emails.map { it.value.lowercase() }.toSet())
        assertEquals(merged.emails.size, expectedEmails.size)
    }

    @Test
    fun `no value is lost with many contacts and odd data`() {
        val sources = (1..6).map { i ->
            source("k$i", "Person $i", phones = listOf("60000000$i", "no number $i", "+3491000000$i"), emails = listOf("p$i@x.org", "shared@x.org"))
        }
        val merged = merger.merge(sources)
        for (s in sources) {
            s.phones.forEach { p -> assertTrue(p.value, merged.phones.any { it.value == p.value }) }
            s.emails.forEach { e -> assertTrue(merged.emails.any { it.value.equals(e.value, ignoreCase = true) }) }
        }
        assertEquals(1, merged.emails.count { it.value == "shared@x.org" })
    }

    @Test
    fun `the most complete contact is the primary and provides the name`() {
        val sparse = source("k1", "A. García", phones = listOf("600111222"))
        val rich = source("k2", "Ana García", phones = listOf("600111222", "611111111"), emails = listOf("a@x.org"), photo = "content://photo")
        val merged = merger.merge(listOf(sparse, rich))
        assertEquals("k2", merged.primaryLookupKey)
        assertEquals("Ana García", merged.displayName)
        assertEquals(listOf("A. García"), merged.alternativeNames)
        assertEquals(2L, merged.primaryRawContactId)
        assertEquals("k2", merged.photoSourceLookupKey)
    }

    @Test
    fun `a tie keeps the first contact as primary`() {
        val merged = merger.merge(listOf(source("k1", "Ana", phones = listOf("600111222")), source("k2", "Ana", phones = listOf("600111223"))))
        assertEquals("k1", merged.primaryLookupKey)
        assertTrue(merged.alternativeNames.isEmpty())
    }

    @Test
    fun `names that differ only in order, case or accents are not alternatives`() {
        val merged = merger.merge(listOf(source("k1", "Ana García", phones = listOf("1")), source("k2", "garcia ana")))
        assertTrue(merged.alternativeNames.isEmpty())
    }

    @Test
    fun `other names are kept as alternatives once`() {
        val a = source("k1", "Ana García", phones = listOf("600111222"), names = listOf("Ana García", "Ana G."))
        val b = source("k2", "Anita", names = listOf("Anita", "Ana G."))
        val merged = merger.merge(listOf(a, b))
        assertEquals(listOf("Ana G.", "Anita"), merged.alternativeNames)
    }

    @Test
    fun `notes are joined without repeating, organisation birthday and star are kept`() {
        val a = source("k1", "Ana", phones = listOf("600111222"), notes = listOf("Likes tea"), starred = false)
        val b = source(
            "k2",
            "Ana",
            organization = "ACME",
            birthday = ContactDate(1990, 5, 17),
            notes = listOf("likes tea", "Met in Madrid"),
            starred = true
        )
        val merged = merger.merge(listOf(a, b))
        // The second contact is the more complete one, so its notes come first; the repeated note (any case) appears once.
        assertEquals("likes tea\n\nMet in Madrid", merged.notes)
        assertEquals("ACME", merged.organization)
        assertEquals(ContactDate(1990, 5, 17), merged.birthday)
        assertTrue(merged.starred)
    }

    @Test
    fun `differing organisations and birthdays are reported, the first wins`() {
        val a = source("k1", "Ana", phones = listOf("600111222"), organization = "ACME", birthday = ContactDate(null, 5, 17))
        val b = source("k2", "Ana", organization = "Globex", birthday = ContactDate(1990, 5, 17))
        val merged = merger.merge(listOf(a, b))
        assertEquals("ACME", merged.organization)
        assertEquals(listOf("Globex"), merged.otherOrganizations)
        assertEquals(ContactDate(null, 5, 17), merged.birthday)
        assertEquals(listOf(ContactDate(1990, 5, 17)), merged.otherBirthdays)
    }

    @Test
    fun `plan aggregates every raw contact and nothing else`() {
        val a = source("k1", "Ana", phones = listOf("600111222"), raw = listOf(10, 11))
        val b = source("k2", "Anita", raw = listOf(20))
        val plan = merger.merge(listOf(a, b)).toPlan()
        assertEquals(10L, plan.primaryRawContactId)
        assertEquals(listOf(10L, 11L, 20L), plan.rawContactIds)
        assertEquals(listOf("Anita"), plan.nicknamesToAdd)
    }

    @Test
    fun `no photo means no photo source`() {
        assertNull(merger.merge(listOf(source("k1", "A", phones = listOf("1")), source("k2", "B"))).photoSourceLookupKey)
    }

    @Test
    fun `a single contact merges to itself`() {
        val merged = merger.merge(listOf(source("k1", "Ana", phones = listOf("600111222"), emails = listOf("a@x.org"))))
        assertEquals(1, merged.phones.size)
        assertEquals("Ana", merged.toDraft().name)
    }
}
