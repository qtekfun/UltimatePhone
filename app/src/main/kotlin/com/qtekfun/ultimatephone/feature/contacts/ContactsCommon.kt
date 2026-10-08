package com.qtekfun.ultimatephone.feature.contacts

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.contacts.ContactAccount
import com.qtekfun.ultimatephone.core.contacts.ContactDate
import com.qtekfun.ultimatephone.core.contacts.LabelTypes
import com.qtekfun.ultimatephone.core.contacts.LabeledValue
import com.qtekfun.ultimatephone.core.designsystem.Avatar
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Formatter
import java.util.Locale

/** Initials on a colour from the name, with the contact photo (when there is one) loaded over it. */
@Composable
fun ContactAvatar(name: String, photoUri: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Avatar(name = name, modifier = modifier, size = size) {
        if (photoUri != null) {
            AsyncImage(model = photoUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** A type the user can pick in the editor, with the name shown for it. */
data class LabelChoice(val type: Int, @StringRes val label: Int)

val PHONE_LABEL_CHOICES = listOf(
    LabelChoice(LabelTypes.MOBILE, R.string.label_mobile),
    LabelChoice(LabelTypes.HOME, R.string.label_home),
    LabelChoice(LabelTypes.WORK, R.string.label_work),
    LabelChoice(LabelTypes.MAIN, R.string.label_main),
    LabelChoice(LabelTypes.OTHER, R.string.label_other)
)

val EMAIL_LABEL_CHOICES = listOf(
    LabelChoice(LabelTypes.EMAIL_HOME, R.string.label_home),
    LabelChoice(LabelTypes.EMAIL_WORK, R.string.label_work),
    LabelChoice(LabelTypes.EMAIL_MOBILE, R.string.label_mobile),
    LabelChoice(LabelTypes.EMAIL_OTHER, R.string.label_other)
)

/** Text for the type of [value]: the app's own wording for the standard types, the user's text for a custom one. */
fun labelText(context: Context, choices: List<LabelChoice>, value: LabeledValue): String {
    val custom = value.customLabel?.takeIf { value.type == LabelTypes.CUSTOM && it.isNotBlank() }
    if (custom != null) return custom
    val choice = choices.firstOrNull { it.type == value.type } ?: choices.last()
    return context.getString(choice.label)
}

/** "Device (not synced)" or "name (type)" for an account. */
fun accountText(context: Context, account: ContactAccount): String = if (account.isLocal) {
    context.getString(R.string.contact_account_local)
} else {
    context.getString(R.string.contact_account_format, account.name, account.type)
}

private const val LEAP_YEAR = 2000

/** The birthday in the phone's language; the year is left out when the contact has none. */
fun birthdayText(context: Context, date: ContactDate): String {
    // A day the calendar does not have (31 February) is shown as stored instead of crashing.
    val day = try {
        LocalDate.of(date.year ?: LEAP_YEAR, date.month, date.day)
    } catch (_: DateTimeException) {
        return date.format()
    }
    val millis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val yearFlag = if (date.year == null) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR
    return DateUtils.formatDateRange(
        context,
        Formatter(StringBuilder(), Locale.getDefault()),
        millis,
        millis,
        DateUtils.FORMAT_SHOW_DATE or yearFlag,
        "UTC"
    ).toString()
}
