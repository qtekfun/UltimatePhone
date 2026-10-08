package com.qtekfun.ultimatephone.core.contacts

import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.e164OrNull

/** Everything about one stored contact that merging needs. [rawContactIds] are the (not deleted) raw contacts aggregated into it. */
data class MergeSource(
    val lookupKey: String,
    val contactId: Long,
    val displayName: String,
    /** All names stored in its raw contacts (the display name may or may not be among them). */
    val names: List<String>,
    val organization: String,
    val phones: List<LabeledValue>,
    val emails: List<LabeledValue>,
    val birthday: ContactDate?,
    val notes: List<String>,
    val starred: Boolean,
    val photoUri: String?,
    val rawContactIds: List<Long>,
    val account: ContactAccount?
)

/** What the merged contact will contain. Built by [ContactMerger]; shown to the user before anything is written. */
data class MergedContact(
    /** The source whose first raw contact receives the extra data; the others are aggregated into it. */
    val primaryLookupKey: String,
    val displayName: String,
    /** Other names the contacts had; stored as nicknames so the person can still be found by them. */
    val alternativeNames: List<String>,
    val organization: String,
    /** Organisations that differ from [organization]; they stay in their raw contacts. */
    val otherOrganizations: List<String>,
    val phones: List<LabeledValue>,
    val emails: List<LabeledValue>,
    val birthday: ContactDate?,
    /** Birthdays that differ from [birthday]; they stay in their raw contacts. */
    val otherBirthdays: List<ContactDate>,
    val notes: String,
    val starred: Boolean,
    /** Lookup key of the contact whose photo the merged one shows, or null when none has one. */
    val photoSourceLookupKey: String?,
    val primaryRawContactId: Long?,
    val rawContactIds: List<Long>
) {
    /** The data operation: aggregate [rawContactIds], add [alternativeNames] as nicknames, set the notes and the star. */
    fun toPlan(): MergePlan = MergePlan(
        primaryRawContactId = primaryRawContactId,
        rawContactIds = rawContactIds,
        nicknamesToAdd = alternativeNames,
        mergedNotes = notes,
        starred = starred
    )

    fun toDraft(account: ContactAccount = ContactAccount.LOCAL): ContactDraft =
        ContactDraft(name = displayName, phones = phones, emails = emails, birthday = birthday, notes = notes, organization = organization, account = account)
}

/**
 * The write that merges contacts (see [ContactsManager.mergeContacts]). Nothing is deleted: the raw contacts keep every
 * row they have and are only tied together, so the merged contact shows the union of all of them.
 */
data class MergePlan(
    val primaryRawContactId: Long?,
    val rawContactIds: List<Long>,
    val nicknamesToAdd: List<String>,
    val mergedNotes: String,
    val starred: Boolean
)

/** Computes the result of merging duplicate contacts. Pure. */
class ContactMerger(private val normalizer: PhoneNormalizer, private val region: String?) {
    /** Merges [sources] (at least one, in the order the user sees them). The primary one is the most complete; a tie goes to the first. */
    fun merge(sources: List<MergeSource>): MergedContact {
        require(sources.isNotEmpty()) { "nothing to merge" }
        val primary = sources.withIndex().maxWith(compareBy<IndexedValue<MergeSource>> { score(it.value) }.thenBy { -it.index }).value
        val ordered = listOf(primary) + sources.filter { it !== primary }

        val displayName = ordered.map { it.displayName.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val organizations = ordered.map { it.organization.trim() }.filter { it.isNotEmpty() }.distinctBy { T9.fold(it) }
        val birthdays = ordered.mapNotNull { it.birthday }.distinct()
        return MergedContact(
            primaryLookupKey = primary.lookupKey,
            displayName = displayName,
            alternativeNames = alternativeNames(ordered, displayName),
            organization = organizations.firstOrNull().orEmpty(),
            otherOrganizations = organizations.drop(1),
            phones = distinctValues(ordered.flatMap { it.phones }, ::phoneKey),
            emails = distinctValues(ordered.flatMap { it.emails }) { it.trim().lowercase() },
            birthday = birthdays.firstOrNull(),
            otherBirthdays = birthdays.drop(1),
            notes = ordered.flatMap { it.notes }.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString("\n\n"),
            starred = ordered.any { it.starred },
            photoSourceLookupKey = ordered.firstOrNull { it.photoUri != null }?.lookupKey,
            primaryRawContactId = primary.rawContactIds.minOrNull(),
            rawContactIds = ordered.flatMap { it.rawContactIds }.distinct()
        )
    }

    private fun score(s: MergeSource): Int = s.phones.size + s.emails.size + s.notes.count { it.isNotBlank() } +
        listOf(s.photoUri != null, s.organization.isNotBlank(), s.birthday != null, s.displayName.isNotBlank()).count { it }

    private fun alternativeNames(ordered: List<MergeSource>, primaryName: String): List<String> {
        val seen = HashSet<String>().apply { add(nameKey(primaryName)) }
        val out = ArrayList<String>()
        for (source in ordered) {
            for (name in listOf(source.displayName) + source.names) {
                val trimmed = name.trim()
                if (trimmed.isNotEmpty() && seen.add(nameKey(trimmed))) out += trimmed
            }
        }
        return out
    }

    private fun nameKey(name: String): String = NameSimilarity.canonical(name).ifEmpty { T9.fold(name.trim()) }

    private fun phoneKey(raw: String): String = normalizer.normalize(raw, region).e164OrNull() ?: T9.digitsOf(raw).ifEmpty { raw.trim() }

    private fun distinctValues(values: List<LabeledValue>, key: (String) -> String): List<LabeledValue> {
        val seen = HashSet<String>()
        return values.filter { it.value.isNotBlank() && seen.add(key(it.value)) }
    }
}
