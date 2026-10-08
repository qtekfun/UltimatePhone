package com.qtekfun.ultimatephone.core.calllog

/**
 * Which calls the history shows. Filters are plain data and evaluated in memory by [matches], so they are cheap to
 * test and to combine.
 *
 * [Spam] needs knowledge this module does not have (what the spam engine decided), so [matches] takes a predicate
 * for it; every other filter ignores the predicate.
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

    /** Calls whose number the spam engine flagged. Evaluated through the `isFlagged` predicate of [matches]. */
    data object Spam : CallLogFilter
}

fun CallLogFilter.matches(entry: CallLogEntry, isFlagged: (CallLogEntry) -> Boolean = { false }): Boolean = when (this) {
    CallLogFilter.All -> true
    CallLogFilter.Missed -> entry.type == CallType.MISSED
    CallLogFilter.Incoming -> entry.type.directionClass == DirectionClass.INCOMING
    CallLogFilter.Outgoing -> entry.type == CallType.OUTGOING
    is CallLogFilter.BySim -> entry.simComponent == componentName && entry.simId == accountId
    CallLogFilter.Spam -> isFlagged(entry)
}

fun List<CallLogEntry>.filtered(filter: CallLogFilter, isFlagged: (CallLogEntry) -> Boolean = { false }): List<CallLogEntry> =
    if (filter == CallLogFilter.All) this else filter { filter.matches(it, isFlagged) }
