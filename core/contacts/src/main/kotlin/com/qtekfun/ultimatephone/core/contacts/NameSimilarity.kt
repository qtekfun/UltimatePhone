package com.qtekfun.ultimatephone.core.contacts

/**
 * Name comparison used to find duplicate contacts. Pure functions.
 *
 * The measure is a **token-sort ratio** over an optimal-string-alignment (Damerau-Levenshtein) distance: names are folded
 * (case, accents, punctuation removed), split into words, the words are sorted, joined with a single space, and
 * `similarity = 1 - distance / max(length)`. Sorting makes "García, Ana", "Ana García" and "garcia ana" identical; the
 * edit distance tolerates one-letter typos and transposed letters ("Ana Garica").
 *
 * Threshold: [THRESHOLD] (0.85) for names of two or more words, [SINGLE_WORD_THRESHOLD] (0.90) for a single word, which
 * must also have at least [MIN_SINGLE_WORD_LENGTH] letters. Rationale: "Ana García" vs "Ana Gómez" scores 0.5 (different
 * people), a one-letter typo in a 10-letter name scores 0.9 (same person), while a single-word nickname is far more
 * ambiguous, so it needs an (almost) exact match.
 */
object NameSimilarity {
    const val THRESHOLD = 0.85
    const val SINGLE_WORD_THRESHOLD = 0.90
    const val MIN_SINGLE_WORD_LENGTH = 4

    /** The sorted, folded words of [name] joined by one space; empty when the name has no letters (for example a phone number). */
    fun canonical(name: String): String = tokens(name).sorted().joinToString(" ")

    /** Folded words of [name]; words made only of digits are dropped, so a number shown as a name never counts as a name. */
    fun tokens(name: String): List<String> = T9.words(name).filter { word -> word.any { it in 'a'..'z' } }

    /** Similarity of two names in 0.0..1.0 (1.0 = same words in any order). Blank or letterless names score 0. */
    fun similarity(a: String, b: String): Double = canonicalSimilarity(canonical(a), canonical(b))

    /** [similarity] for two already [canonical] names. */
    fun canonicalSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val longest = maxOf(a.length, b.length)
        return 1.0 - distance(a, b).toDouble() / longest
    }

    /** True when two names very likely belong to the same person, per the thresholds in the class documentation. */
    fun areSimilar(a: String, b: String): Boolean = canonicalMatch(canonical(a), canonical(b))

    /** [areSimilar] for two already [canonical] names. */
    fun canonicalMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val singleWord = ' ' !in a || ' ' !in b
        if (singleWord && (a.length < MIN_SINGLE_WORD_LENGTH || b.length < MIN_SINGLE_WORD_LENGTH)) return false
        val limit = if (singleWord) SINGLE_WORD_THRESHOLD else THRESHOLD
        return canonicalSimilarity(a, b) >= limit
    }

    /** Optimal string alignment distance: insertions, deletions, substitutions and swaps of two adjacent letters cost 1. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var twoBack = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) best = minOf(best, twoBack[j - 2] + 1)
                current[j] = best
            }
            val recycled = twoBack
            twoBack = previous
            previous = current
            current = recycled
        }
        return previous[b.length]
    }
}
