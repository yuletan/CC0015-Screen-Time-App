package com.intent.screentime.core.format

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A window of days as a person would write it: "Mon 8 Sep", "8–14 Sep", "28 Jul – 7 Sep".
 *
 * Written once so every screen that can step through history names the same seven days
 * the same way, and so a window that crosses a month or a year still reads as a range
 * rather than as two dates pretending to be one.
 */
object DayRangeFormat {

    private val DAY = DateTimeFormatter.ofPattern("d MMM")
    private val WEEKDAY_DAY = DateTimeFormatter.ofPattern("EEE d MMM")
    private val FULL_DAY = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun label(fromDay: Long, toDay: Long, locale: Locale = Locale.getDefault()): String {
        val from = LocalDate.ofEpochDay(fromDay)
        val to = LocalDate.ofEpochDay(toDay)

        if (from == to) return WEEKDAY_DAY.withLocale(locale).format(from)

        return when {
            from.year != to.year ->
                "${FULL_DAY.withLocale(locale).format(from)} – " +
                    FULL_DAY.withLocale(locale).format(to)

            from.month == to.month ->
                "${from.dayOfMonth}–${DAY.withLocale(locale).format(to)}"

            else ->
                "${DAY.withLocale(locale).format(from)} – ${DAY.withLocale(locale).format(to)}"
        }
    }
}
