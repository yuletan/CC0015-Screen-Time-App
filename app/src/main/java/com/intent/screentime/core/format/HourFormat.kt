package com.intent.screentime.core.format

/**
 * Hours of the day as chart ticks: "12a", "9a", "12p", "11p".
 *
 * Shared rather than re-declared per screen, because the hour strip, the insight clock and
 * an hourly trend axis must agree on what 13:00 is called.
 */
object HourFormat {
    fun short(hour: Int): String = when (val h = hour.mod(24)) {
        0 -> "12a"
        12 -> "12p"
        in 1..11 -> "${h}a"
        else -> "${h - 12}p"
    }
}
