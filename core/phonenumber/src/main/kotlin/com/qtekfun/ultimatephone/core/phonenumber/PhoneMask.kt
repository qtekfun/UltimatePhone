package com.qtekfun.ultimatephone.core.phonenumber

/** Phone numbers never reach a log in full: only the last [VISIBLE] digits are kept. */
object PhoneMask {
    private const val VISIBLE = 3

    fun mask(number: String?): String {
        val digits = number?.filter { it.isDigit() }.orEmpty()
        return if (digits.isEmpty()) "unknown" else "***" + digits.takeLast(VISIBLE)
    }
}
