package com.qtekfun.ultimatephone.core.contacts

/** The parts of a name; [formatted] is the display name as written (vCard FN). */
data class VCardName(
    val family: String = "",
    val given: String = "",
    val middle: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val formatted: String = ""
) {
    /** The name to show: [formatted] when present, otherwise the parts in reading order. */
    val display: String
        get() = formatted.trim().ifEmpty { listOf(prefix, given, middle, family, suffix).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") }

    val hasParts: Boolean get() = listOf(family, given, middle, prefix, suffix).any { it.isNotBlank() }
}

/** A postal address kept as the free text the user sees; [type] is a StructuredPostal TYPE (1 home, 2 work, 3 other). */
data class VCardAddress(val formatted: String, val type: Int = ADDRESS_OTHER) {
    companion object {
        const val ADDRESS_HOME = 1
        const val ADDRESS_WORK = 2
        const val ADDRESS_OTHER = 3
    }
}

/** Photo bytes (JPEG, PNG or GIF) with content equality, so contacts holding photos compare properly. */
class VCardPhoto(val bytes: ByteArray) {
    /** MIME type guessed from the first bytes; JPEG when unknown. */
    val mimeType: String
        get() = when {
            bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
            bytes.size > 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
            else -> "image/jpeg"
        }

    override fun equals(other: Any?): Boolean = other is VCardPhoto && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "VCardPhoto(${bytes.size} bytes)"
}

/** A contact as a vCard describes it; the common ground between the system contacts and `.vcf` files. */
data class VCardContact(
    val name: VCardName = VCardName(),
    val nicknames: List<String> = emptyList(),
    val organization: String = "",
    val title: String = "",
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val birthday: ContactDate? = null,
    val note: String = "",
    val photo: VCardPhoto? = null,
    val websites: List<String> = emptyList(),
    val addresses: List<VCardAddress> = emptyList()
) {
    val displayName: String get() = name.display

    /** A card with none of these is not worth importing. */
    val isUsable: Boolean get() = displayName.isNotBlank() || phones.isNotEmpty() || emails.isNotEmpty() || organization.isNotBlank()

    /** For [DuplicateDetector]; [index] numbers the imported cards so their ids are negative and distinct. */
    fun toDuplicateContact(index: Int): DuplicateContact = DuplicateContact(
        contactId = -(index + 1).toLong(),
        lookupKey = "import:$index",
        displayName = displayName,
        phones = phones.map { it.value },
        emails = emails.map { it.value }
    )
}

/** Cards read from a file and how many were unusable. */
data class VCardParseResult(val contacts: List<VCardContact>, val skipped: Int)
