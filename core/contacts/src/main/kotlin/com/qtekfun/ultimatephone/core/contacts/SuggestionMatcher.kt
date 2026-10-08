package com.qtekfun.ultimatephone.core.contacts

/**
 * One searchable (contact, number) pair. The expensive parts (digits, T9 codes of each name word) are computed once
 * when the entry is built, so matching a query is a few string comparisons.
 */
class SuggestionEntry(val lookupKey: String, val displayName: String, val number: String, val photoThumbUri: String?) {
    val numberDigits: String = T9.digitsOf(number)
    val wordCodes: List<String> = T9.words(displayName).map(T9::encode)
    private val international = number.trimStart().startsWith("+")

    /** True when [query] starts the number, also when the number is international and [query] starts its national part. */
    internal fun numberStartsWith(query: String): Boolean {
        if (numberDigits.startsWith(query)) return true
        if (!international) return false
        return (1..MAX_COUNTRY_CODE_DIGITS).any { skip -> numberDigits.length - skip >= query.length && numberDigits.startsWith(query, skip) }
    }

    private companion object {
        const val MAX_COUNTRY_CODE_DIGITS = 3
    }
}

/** Matching and ranking behind the dialer's search-as-you-type. Pure and allocation-light: it must stay under 100 ms for 10 000 contacts. */
object SuggestionMatcher {
    private const val TIER_NAME_START = 0
    private const val TIER_NUMBER_START = 1
    private const val TIER_NAME_WORD = 2
    private const val TIER_NUMBER_CONTAINS = 3
    private const val NO_MATCH = Int.MAX_VALUE

    /**
     * @param entries sorted by display name; that order breaks ties between equal ranks.
     * @param query digits typed on the dialer; formatting characters are ignored, an empty query matches nothing.
     * Ranking: name starts with the query (T9) and number starts with the query come first, then a later name word starting with
     * it, then the query inside the number. Each contact appears once, with the number that matched (or its first number).
     */
    fun suggest(entries: List<SuggestionEntry>, query: String, limit: Int): List<DialerSuggestion> {
        val digits = T9.digitsOf(query)
        if (digits.isEmpty() || limit <= 0) return emptyList()
        val best = LinkedHashMap<String, Pair<Int, SuggestionEntry>>()
        for (entry in entries) {
            val tier = tierOf(entry, digits)
            if (tier == NO_MATCH) continue
            val current = best[entry.lookupKey]
            if (current == null || tier < current.first) best[entry.lookupKey] = tier to entry
        }
        return best.values
            .sortedBy { it.first }
            .take(limit)
            .map { (_, e) -> DialerSuggestion(e.lookupKey, e.displayName, e.number, e.photoThumbUri) }
    }

    private fun tierOf(entry: SuggestionEntry, digits: String): Int {
        var tier = NO_MATCH
        val codes = entry.wordCodes
        for (i in codes.indices) {
            if (codes[i].startsWith(digits)) {
                if (i == 0) return TIER_NAME_START
                tier = TIER_NAME_WORD
            }
        }
        if (entry.numberStartsWith(digits)) return minOf(tier, TIER_NUMBER_START)
        if (tier == NO_MATCH && entry.numberDigits.contains(digits)) tier = TIER_NUMBER_CONTAINS
        return tier
    }
}
