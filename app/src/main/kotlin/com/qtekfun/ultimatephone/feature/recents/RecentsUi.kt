package com.qtekfun.ultimatephone.feature.recents

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.calllog.CallType
import com.qtekfun.ultimatephone.core.calllog.DaySection
import com.qtekfun.ultimatephone.core.designsystem.StatusChip
import com.qtekfun.ultimatephone.core.telecom.SimAccount
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun CallType.icon(): ImageVector = when (this) {
    CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
    CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
    CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed
    CallType.REJECTED -> Icons.Filled.CallEnd
    CallType.BLOCKED -> Icons.Filled.Block
    CallType.VOICEMAIL -> Icons.Filled.Voicemail
    CallType.ANSWERED_EXTERNALLY -> Icons.AutoMirrored.Filled.PhoneCallback
    CallType.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
}

@StringRes
fun CallType.label(): Int = when (this) {
    CallType.INCOMING -> R.string.recents_type_incoming
    CallType.OUTGOING -> R.string.recents_type_outgoing
    CallType.MISSED -> R.string.recents_type_missed
    CallType.REJECTED -> R.string.recents_type_rejected
    CallType.BLOCKED -> R.string.recents_type_blocked
    CallType.VOICEMAIL -> R.string.recents_type_voicemail
    CallType.ANSWERED_EXTERNALLY -> R.string.recents_type_answered_elsewhere
    CallType.UNKNOWN -> R.string.recents_type_unknown
}

/** Missed calls are the only ones that ask for attention. */
@Composable
fun CallType.tint(): Color = if (this == CallType.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun CallTypeIcon(type: CallType, modifier: Modifier = Modifier) {
    Icon(imageVector = type.icon(), contentDescription = null, tint = type.tint(), modifier = modifier)
}

/** "SIM 2" from the physical slot, else the label the phone gave the account. */
@Composable
fun simText(sim: SimAccount): String = if (sim.slot >= 0) stringResource(R.string.recents_sim_slot, sim.slot + 1) else sim.label

@Composable
fun SimBadge(sim: SimAccount, modifier: Modifier = Modifier) {
    StatusChip(text = simText(sim), icon = Icons.Filled.SimCard, modifier = modifier)
}

@Composable
fun DaySection.text(): String = when (this) {
    DaySection.Today -> stringResource(R.string.recents_today)
    DaySection.Yesterday -> stringResource(R.string.recents_yesterday)
    is DaySection.Date -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
}

/** Clock time following the phone's 12/24 hour setting. */
@Composable
fun timeText(millis: Long): String = DateUtils.formatDateTime(LocalContext.current, millis, DateUtils.FORMAT_SHOW_TIME)

@Composable
fun dateTimeText(millis: Long): String =
    DateUtils.formatDateTime(LocalContext.current, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR)
