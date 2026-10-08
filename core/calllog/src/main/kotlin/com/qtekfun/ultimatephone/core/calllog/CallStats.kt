package com.qtekfun.ultimatephone.core.calllog

/** Basic numbers for the detail screen. Rejected and blocked calls count towards [total] only. */
data class CallStats(val total: Int, val missed: Int, val incoming: Int, val outgoing: Int, val totalDurationSeconds: Long)

fun statsOf(entries: List<CallLogEntry>): CallStats = CallStats(
    total = entries.size,
    missed = entries.count { it.type == CallType.MISSED },
    incoming = entries.count { it.type.directionClass == DirectionClass.INCOMING },
    outgoing = entries.count { it.type == CallType.OUTGOING },
    totalDurationSeconds = entries.sumOf { it.durationSeconds.coerceAtLeast(0) }
)

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/** `m:ss`, or `h:mm:ss` from one hour up. Locale independent on purpose: it reads like a stopwatch. */
fun formatDurationClock(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / SECONDS_PER_HOUR
    val m = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val s = total % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
