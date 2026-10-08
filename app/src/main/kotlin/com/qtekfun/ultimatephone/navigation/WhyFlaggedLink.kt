package com.qtekfun.ultimatephone.navigation

import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatephone.MainActivity

/**
 * How the call screen (a separate activity) asks the main task to open "why was this flagged" for a number. An explicit
 * intent to [MainActivity] with the number as an extra: it never leaves the app and the number is never logged.
 */
object WhyFlaggedLink {
    const val ACTION = "com.qtekfun.ultimatephone.action.WHY_FLAGGED"
    private const val EXTRA_NUMBER = "com.qtekfun.ultimatephone.extra.WHY_NUMBER"

    fun intent(context: Context, number: String): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION)
        .putExtra(EXTRA_NUMBER, number)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The number to explain, taken out of [intent] so a later re-delivery (recents, rotation) does not reopen the screen. */
    fun consume(intent: Intent?): String? {
        if (intent?.action != ACTION) return null
        val number = intent.getStringExtra(EXTRA_NUMBER)
        intent.removeExtra(EXTRA_NUMBER)
        intent.action = null
        return number?.takeIf { it.any(Char::isDigit) }
    }
}
