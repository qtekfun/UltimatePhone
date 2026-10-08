package com.qtekfun.ultimatephone.core.contacts

/** Where a contact is stored. [name] and [type] are null for the local "device" account. */
data class ContactAccount(val name: String?, val type: String?) {
    val isLocal: Boolean get() = name == null

    companion object {
        val LOCAL = ContactAccount(null, null)
    }
}

/** Values of the phone and e-mail "type" column; they are the platform's own constants so they can be stored as is. */
object LabelTypes {
    const val CUSTOM = 0
    const val HOME = 1
    const val MOBILE = 2
    const val WORK = 3
    const val OTHER = 7
    const val MAIN = 12

    // E-mail types share only HOME with phones.
    const val EMAIL_HOME = 1
    const val EMAIL_WORK = 2
    const val EMAIL_OTHER = 3
    const val EMAIL_MOBILE = 4

    val PHONE_CHOICES = listOf(MOBILE, HOME, WORK, MAIN, OTHER)
    val EMAIL_CHOICES = listOf(EMAIL_HOME, EMAIL_WORK, EMAIL_MOBILE, EMAIL_OTHER)
}

/** A phone number or an e-mail address with its type. [customLabel] is only meaningful when [type] is [LabelTypes.CUSTOM]. */
data class LabeledValue(val value: String, val type: Int, val customLabel: String? = null)

/** Birthday; [year] is null when the contact only has day and month. */
data class ContactDate(val year: Int?, val month: Int, val day: Int) {
    /** ISO form used by ContactsContract: `1990-05-17`, or `--05-17` without a year. */
    fun format(): String = (if (year == null) "-" else "%04d".format(year)) + "-%02d-%02d".format(month, day)

    companion object {
        private val FULL = Regex("""(\d{4})-(\d{1,2})-(\d{1,2}).*""")
        private val NO_YEAR = Regex("""--(\d{1,2})-(\d{1,2})""")
        private const val MAX_MONTH = 12
        private const val MAX_DAY = 31

        /** Reads the formats the provider holds; anything else (free text some apps store) gives null. */
        fun parse(raw: String?): ContactDate? {
            val text = raw?.trim().orEmpty()
            val full = FULL.matchEntire(text)
            val date = if (full != null) {
                val (y, m, d) = full.destructured
                ContactDate(y.toInt(), m.toInt(), d.toInt())
            } else {
                val (m, d) = NO_YEAR.matchEntire(text)?.destructured ?: return null
                ContactDate(null, m.toInt(), d.toInt())
            }
            return date.takeIf { it.month in 1..MAX_MONTH && it.day in 1..MAX_DAY }
        }
    }
}

/** A phone or e-mail row of a stored contact; [rawContactId] says which underlying (account) contact owns it. */
data class StoredValue(val value: LabeledValue, val typeLabel: String, val rawContactId: Long)

/** Everything the detail screen shows. */
data class ContactDetail(
    val lookupKey: String,
    val contactId: Long,
    val displayName: String,
    /** The stored name of the primary raw contact; empty when the contact has none and [displayName] is, say, a phone number. */
    val editableName: String,
    val organization: String,
    val phones: List<StoredValue>,
    val emails: List<StoredValue>,
    val birthday: ContactDate?,
    val notes: String,
    val photoUri: String?,
    val starred: Boolean,
    /** The raw contact edits are written to, and its account. Null only when the contact has no raw contact (should not happen). */
    val primaryRawContactId: Long?,
    val account: ContactAccount?
) {
    /**
     * The draft for editing this contact. Only values owned by the primary raw contact are included, because saving replaces
     * exactly those rows; values merged in from other accounts stay untouched.
     */
    fun toDraft(): ContactDraft = ContactDraft(
        lookupKey = lookupKey,
        name = editableName,
        phones = phones.filter { it.rawContactId == primaryRawContactId }.map { it.value },
        emails = emails.filter { it.rawContactId == primaryRawContactId }.map { it.value },
        birthday = birthday,
        notes = notes,
        organization = organization,
        account = account ?: ContactAccount.LOCAL
    )
}

/** Why a draft cannot be saved. */
enum class DraftError { NAME_OR_PHONE_REQUIRED }

sealed interface DraftValidation {
    /** [draft] is trimmed and has its blank phones and e-mails removed. */
    data class Valid(val draft: ContactDraft) : DraftValidation

    data class Invalid(val error: DraftError) : DraftValidation
}

/** A contact being created ([lookupKey] null) or edited. Raw user input: call [validate] before writing it. */
data class ContactDraft(
    val lookupKey: String? = null,
    val name: String = "",
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val birthday: ContactDate? = null,
    val notes: String = "",
    val organization: String = "",
    val account: ContactAccount = ContactAccount.LOCAL
) {
    fun validate(): DraftValidation {
        val cleaned = copy(
            name = name.trim(),
            phones = phones.mapNotNull { it.cleaned() },
            emails = emails.mapNotNull { it.cleaned() },
            notes = notes.trim(),
            organization = organization.trim()
        )
        return if (cleaned.name.isEmpty() && cleaned.phones.isEmpty()) {
            DraftValidation.Invalid(DraftError.NAME_OR_PHONE_REQUIRED)
        } else {
            DraftValidation.Valid(cleaned)
        }
    }

    private fun LabeledValue.cleaned(): LabeledValue? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val label = if (type == LabelTypes.CUSTOM) customLabel?.trim()?.ifEmpty { null } else null
        return copy(value = trimmed, customLabel = label)
    }
}
