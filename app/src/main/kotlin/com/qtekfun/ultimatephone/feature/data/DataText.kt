package com.qtekfun.ultimatephone.feature.data

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.datapacks.PackEntry
import com.qtekfun.ultimatephone.data.PackTypes
import com.qtekfun.ultimatephone.data.UpdateErrors

/** User-facing text for a code from [UpdateErrors]. */
@Composable
fun errorText(code: String): String = when (UpdateErrors.kind(code)) {
    UpdateErrors.NETWORK -> stringResource(R.string.data_error_network)
    UpdateErrors.SIGNATURE -> stringResource(R.string.data_error_signature)
    UpdateErrors.MANIFEST -> stringResource(R.string.data_error_manifest)
    UpdateErrors.INVALID_URL -> stringResource(R.string.data_error_invalid_url)
    UpdateErrors.TOO_LARGE -> stringResource(R.string.data_error_too_large)
    UpdateErrors.NO_NUMBERS -> stringResource(R.string.data_error_no_numbers)
    UpdateErrors.WIFI_ONLY -> stringResource(R.string.data_error_wifi_only)
    UpdateErrors.STORAGE -> stringResource(R.string.data_error_storage)
    UpdateErrors.HTTP -> stringResource(R.string.data_error_http, UpdateErrors.detail(code).orEmpty())
    UpdateErrors.INSTALL -> stringResource(installErrorRes(UpdateErrors.detail(code)))
    else -> stringResource(R.string.data_error_unknown)
}

private fun installErrorRes(reason: String?): Int = when (reason) {
    "NETWORK" -> R.string.data_install_network
    "UNSUPPORTED", "INSECURE_URL" -> R.string.data_install_unsupported
    else -> R.string.data_install_corrupt
}

/** Name of a pack in lists: what it contains, not its file name. */
@Composable
fun packTitle(entry: PackEntry): String = when (entry.type) {
    PackTypes.BUSINESSES -> stringResource(R.string.data_pack_businesses)
    PackTypes.RULES -> stringResource(R.string.data_pack_rules)
    else -> stringResource(R.string.data_pack_spam, entry.id.removePrefix("spam-"))
}

/** Date and time in the user's format. */
@Composable
fun dateTimeText(millis: Long): String =
    DateUtils.formatDateTime(LocalContext.current, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR)
