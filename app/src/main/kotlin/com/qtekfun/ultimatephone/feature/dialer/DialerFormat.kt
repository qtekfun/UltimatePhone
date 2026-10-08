package com.qtekfun.ultimatephone.feature.dialer

/** Font size, in sp, for the number on the keypad: long numbers shrink in steps so they stay on one line. */
internal fun numberFontSizeSp(length: Int): Int = when {
    length <= SHORT_NUMBER -> 32
    length <= MEDIUM_NUMBER -> 28
    length <= LONG_NUMBER -> 24
    length <= VERY_LONG_NUMBER -> 20
    else -> 18
}

private const val SHORT_NUMBER = 10
private const val MEDIUM_NUMBER = 13
private const val LONG_NUMBER = 16
private const val VERY_LONG_NUMBER = 20
