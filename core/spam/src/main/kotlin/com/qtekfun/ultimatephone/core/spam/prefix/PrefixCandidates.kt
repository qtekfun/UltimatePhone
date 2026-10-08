package com.qtekfun.ultimatephone.core.spam.prefix

/** Candidate prefixes of an E.164 number, for one indexed `value IN (...)` query. */
object PrefixCandidates {
    /** A prefix needs the `+` and at least the first digit of the country calling code. */
    private const val MIN_LENGTH = 2

    /**
     * Every proper prefix of [e164] from the longest to the shortest (`+34600123456` gives `+3460012345` ... `+3`).
     * The number itself is not included: that is the exact lookup.
     */
    fun of(e164: String): List<String> {
        if (e164.length <= MIN_LENGTH) return emptyList()
        return (e164.length - 1 downTo MIN_LENGTH).map { e164.substring(0, it) }
    }
}
