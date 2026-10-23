package com.qtekfun.ultimatephone.core.telecom

/**
 * Pause and wait characters after the number, for extensions and menus: "," waits a moment and then sends the digits
 * after it as tones, ";" waits until the user confirms. Telecom handles both; only the number before them is dialled.
 */
object PostDial {
    const val PAUSE = ','
    const val WAIT = ';'

    /** The part of [number] that the network dials: everything before the first pause or wait. */
    fun networkPart(number: String): String = number.takeWhile { it != PAUSE && it != WAIT }

    fun hasPostDial(number: String): Boolean = number.any { it == PAUSE || it == WAIT }
}
