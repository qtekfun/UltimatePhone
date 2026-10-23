package com.qtekfun.ultimatephone.feature.dialer

import com.qtekfun.ultimatephone.core.telecom.PostDial

/** What the keypad does to the typed number. Pure, so every key and edge case is unit-tested. */
object DialerInput {
    const val MAX_LENGTH = 40
    private const val DIALABLE = "0123456789*#+" + PostDial.PAUSE + PostDial.WAIT
    private val SECRET_CODE = Regex("""\*#\*#(\d+)#\*#\*""")

    fun append(current: String, key: Char): String {
        if (key !in DIALABLE || current.length >= MAX_LENGTH) return current
        if (key == '+' && current.isNotEmpty()) return current
        // A pause or a wait needs a number before it.
        if (isPostDial(key) && current.isEmpty()) return current
        return current + key
    }

    /**
     * Long press on a key: 0 gives "+" at the start of the number, * adds a pause (","), # adds a wait (";"). Any other key
     * (and 0 in the middle of a number) types its digit. Long press on 1 with nothing typed calls the voicemail instead.
     */
    fun longPress(current: String, key: Char): String = when (key) {
        '0' -> if (current.isEmpty()) "+" else append(current, '0')
        '*' -> append(current, PostDial.PAUSE)
        '#' -> append(current, PostDial.WAIT)
        else -> append(current, key)
    }

    fun backspace(current: String): String = current.dropLast(1)

    /** Keeps what can be dialled from pasted text: "+34 (612) 345-678" becomes "+34612345678"; "912345678,22" keeps its extension. */
    fun sanitizePaste(text: String): String = text.trim().fold("") { kept, char -> append(kept, char) }

    /** The digits of the number to dial (not of the tones after a pause), for contact search. */
    fun searchDigits(number: String): String = PostDial.networkPart(number).filter { it.isDigit() }

    /** The code of an Android secret code (`*#*#4636#*#*` gives "4636"), or null when [number] is not one. */
    fun secretCode(number: String): String? = SECRET_CODE.matchEntire(number)?.groupValues?.get(1)

    private fun isPostDial(char: Char) = char == PostDial.PAUSE || char == PostDial.WAIT
}
