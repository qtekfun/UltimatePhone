package com.qtekfun.ultimatephone.core.contacts

import java.text.Normalizer

/** Text helpers for matching names typed as digits on a phone keypad. Pure functions, no Android types. */
object T9 {
    private val SPECIAL_FOLDS = mapOf('ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'ø' to "o", 'ł' to "l", 'đ' to "d", 'ð' to "d", 'þ' to "th", 'ı' to "i")
    private val NON_WORD = Regex("[^a-z0-9]+")
    private const val LETTER_GROUPS = "abc def ghi jkl mno pqrs tuv wxyz"

    private val keyForLetter: Map<Char, Char> = buildMap {
        LETTER_GROUPS.split(' ').forEachIndexed { index, letters ->
            val key = '2' + index
            letters.forEach { put(it, key) }
        }
    }

    /** Lower-cases [text] and removes accents, so "Émilie" and "emilie" compare equal. */
    fun fold(text: String): String {
        val lower = text.lowercase()
        val expanded = StringBuilder(lower.length)
        for (c in lower) {
            val special = SPECIAL_FOLDS[c]
            if (special != null) expanded.append(special) else expanded.append(c)
        }
        val decomposed = Normalizer.normalize(expanded, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        for (c in decomposed) {
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) out.append(c)
        }
        return out.toString()
    }

    /** The keypad digit of [c] (2 for a, b, c ... 9 for w, x, y, z); digits map to themselves, anything else to null. */
    fun keyOf(c: Char): Char? {
        val folded = fold(c.toString()).singleOrNull() ?: return null
        return if (folded in '0'..'9') folded else keyForLetter[folded]
    }

    /** The words of a name, folded: separators are anything that is not a latin letter or a digit. */
    fun words(name: String): List<String> = fold(name).split(NON_WORD).filter { it.isNotEmpty() }

    /** Keypad digits of [word]; characters without a key are skipped. */
    fun encode(word: String): String {
        val out = StringBuilder(word.length)
        for (c in fold(word)) keyOf(c)?.let(out::append)
        return out.toString()
    }

    /** The digits of [number], formatting removed: "+34 600-123 456" gives "34600123456". */
    fun digitsOf(number: String): String = number.filter { it in '0'..'9' }
}
