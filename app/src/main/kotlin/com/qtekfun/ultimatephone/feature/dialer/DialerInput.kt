package com.qtekfun.ultimatephone.feature.dialer

/** What the keypad does to the typed number. Pure, so every key and edge case is unit-tested. */
object DialerInput {
    const val MAX_LENGTH = 40
    private const val DIALABLE = "0123456789*#+"

    fun append(current: String, key: Char): String {
        if (key !in DIALABLE || current.length >= MAX_LENGTH) return current
        if (key == '+' && current.isNotEmpty()) return current
        return current + key
    }

    /** Long-press on 0: the "0" just typed becomes "+", only at the start of the number. */
    fun plusInsteadOfZero(current: String): String = if (current == "0") "+" else current

    fun backspace(current: String): String = current.dropLast(1)

    /** Keeps what can be dialled from pasted text: "+34 (612) 345-678" becomes "+34612345678". */
    fun sanitizePaste(text: String): String {
        val kept = buildString {
            text.trim().forEach { char ->
                if (char in DIALABLE && (char != '+' || isEmpty())) append(char)
            }
        }
        return kept.take(MAX_LENGTH)
    }

    /** The digits only, for contact search. */
    fun searchDigits(number: String): String = number.filter { it.isDigit() }
}
