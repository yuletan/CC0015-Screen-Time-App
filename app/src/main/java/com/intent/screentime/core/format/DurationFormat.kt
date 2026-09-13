package com.intent.screentime.core.format

import java.util.Locale
import kotlin.math.roundToLong

object DurationFormat {
    /** "1h 42m", "42m", "18s" — for headlines and cards. */
    fun compact(ms: Long): String {
        val totalSeconds = (ms / 1000.0).roundToLong().coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    /** "1:42" or "0:07" — compact axis labels for charts. */
    fun clock(ms: Long): String {
        val totalMinutes = (ms / 60_000.0).roundToLong().coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", totalMinutes / 60, totalMinutes % 60)
    }

    /** Hours with one decimal — "3.4h" — for trend axes. */
    fun hoursDecimal(ms: Long): String =
        String.format(Locale.US, "%.1fh", ms / 3_600_000.0)

    fun minutesRounded(ms: Long): Long = (ms / 60_000.0).roundToLong()

    /** "07:30" — the digest time, stored as minutes past midnight. */
    fun timeOfDay(minutesOfDay: Int): String {
        val minutes = minutesOfDay.coerceIn(0, 24 * 60 - 1)
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
    }
}
