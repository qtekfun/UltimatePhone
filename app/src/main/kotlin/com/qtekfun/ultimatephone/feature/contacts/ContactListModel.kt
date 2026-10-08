package com.qtekfun.ultimatephone.feature.contacts

import com.qtekfun.ultimatephone.core.contacts.ContactSummary
import com.qtekfun.ultimatephone.core.contacts.T9

/** One row of the contact list: a section header or a contact. */
sealed interface ListRow {
    /** Stable key for lazy lists. */
    val key: String

    data object FavoritesHeader : ListRow {
        override val key = "header-favorites"
    }

    data class LetterHeader(val letter: String) : ListRow {
        override val key = "header-$letter"
    }

    data class Item(val contact: ContactSummary, val favorite: Boolean) : ListRow {
        override val key = (if (favorite) "fav-" else "contact-") + contact.lookupKey
    }
}

/** What the list shows: [rows] top to bottom, the [indexLetters] for the fast scroller and where each one starts in [rows]. */
data class ContactListContent(val rows: List<ListRow>, val indexLetters: List<String>, val positions: Map<String, Int>) {
    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {
        /** Index entry that jumps to the favourites. */
        const val FAVORITES_INDEX = "★"
        const val OTHER_LETTER = "#"
        val EMPTY = ContactListContent(emptyList(), emptyList(), emptyMap())
    }
}

/** Builds the list the screen shows. Pure, so the grouping and searching rules are unit tested. */
object ContactListBuilder {
    /** The section a name belongs to: its first letter without accent, upper case, or "#" for digits, symbols and empty names. */
    fun letterOf(displayName: String): String {
        val first = T9.fold(displayName.trimStart()).firstOrNull() ?: return ContactListContent.OTHER_LETTER
        return if (first in 'a'..'z') first.uppercaseChar().toString() else ContactListContent.OTHER_LETTER
    }

    /** True when every word typed in [query] appears in the name (accents and case ignored). */
    fun nameMatches(displayName: String, query: String): Boolean {
        val tokens = T9.fold(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val name = T9.fold(displayName)
        return tokens.all { name.contains(it) }
    }

    /**
     * @param contacts in display-name order.
     * @param query what the user typed; blank shows everything with the favourites first.
     * @param numberMatches lookup keys of contacts whose phone number matched [query], so number searches work too.
     */
    fun build(contacts: List<ContactSummary>, query: String, numberMatches: Set<String> = emptySet()): ContactListContent {
        val searching = query.isNotBlank()
        val visible = if (searching) contacts.filter { it.lookupKey in numberMatches || nameMatches(it.displayName, query) } else contacts
        if (visible.isEmpty()) return ContactListContent.EMPTY

        val rows = ArrayList<ListRow>(visible.size + SECTION_HEADROOM)
        val positions = LinkedHashMap<String, Int>()

        val favorites = if (searching) emptyList() else visible.filter { it.starred }
        if (favorites.isNotEmpty()) {
            positions[ContactListContent.FAVORITES_INDEX] = rows.size
            rows += ListRow.FavoritesHeader
            favorites.forEach { rows += ListRow.Item(it, favorite = true) }
        }

        val byLetter = visible.groupBy { letterOf(it.displayName) }
        val letters = byLetter.keys.filter { it != ContactListContent.OTHER_LETTER }.sorted() +
            listOfNotNull(ContactListContent.OTHER_LETTER.takeIf { it in byLetter })
        for (letter in letters) {
            positions[letter] = rows.size
            rows += ListRow.LetterHeader(letter)
            byLetter.getValue(letter).forEach { rows += ListRow.Item(it, favorite = false) }
        }
        return ContactListContent(rows, positions.keys.toList(), positions)
    }

    private const val SECTION_HEADROOM = 32
}
