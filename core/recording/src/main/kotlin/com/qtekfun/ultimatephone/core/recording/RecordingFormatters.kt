package com.qtekfun.ultimatephone.core.recording

import java.util.Locale

object RecordingFormatters {
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600
    private const val MILLIS = 1000L
    private const val KIB = 1024.0
    private val units = listOf("KB", "MB", "GB")

    /** `m:ss`, or `h:mm:ss` from one hour. Negative values count as zero. */
    fun duration(millis: Long): String {
        val total = (millis / MILLIS).coerceAtLeast(0)
        val hours = total / SECONDS_PER_HOUR
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds) else "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }

    /** `512 B`, `1.5 KB`, `2.0 MB`, `1.2 GB` (powers of 1024). */
    fun size(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < KIB) return "${bytes.coerceAtLeast(0)} B"
        var value = bytes / KIB
        var unit = 0
        while (value >= KIB && unit < units.lastIndex) {
            value /= KIB
            unit++
        }
        return "%.1f %s".format(locale, value, units[unit])
    }
}
