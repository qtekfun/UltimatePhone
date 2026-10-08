package com.qtekfun.ultimatephone.core.calllog

/**
 * Which calls the history shows. Filters are plain data and evaluated in memory by [matches], so they are cheap to
 * test and to combine.
 *
 * Extension point: Phase 2 adds a `Spam` filter here (calls the spam engine flagged) and one `when` branch in
 * [matches]. Nothing about spam exists in Phase 1.
 */
sealed interface CallLogFilter {
    data object All : CallLogFilter

    /** Calls the user did not answer. Rejected and blocked calls are not "missed". */
    data object Missed : CallLogFilter

    /** Calls that came in, answered here or on another device. */
    data object Incoming : CallLogFilter

    data object Outgoing : CallLogFilter

    /** Calls made or received through one SIM, identified by the raw call-log account columns. */
    data class BySim(val componentName: String, val accountId: String) : CallLogFilter
}

fun CallLogFilter.matches(entry: CallLogEntry): Boolean = when (this) {
    CallLogFilter.All -> true
    CallLogFilter.Missed -> entry.type == CallType.MISSED
    CallLogFilter.Incoming -> entry.type.directionClass == DirectionClass.INCOMING
    CallLogFilter.Outgoing -> entry.type == CallType.OUTGOING
    is CallLogFilter.BySim -> entry.simComponent == componentName && entry.simId == accountId
}

fun List<CallLogEntry>.filtered(filter: CallLogFilter): List<CallLogEntry> = if (filter == CallLogFilter.All) this else filter { filter.matches(it) }
