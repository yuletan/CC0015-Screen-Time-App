package com.intent.screentime.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DayRangeFormatTest {

    private val locale = Locale.ENGLISH

    private fun day(date: String): Long = LocalDate.parse(date).toEpochDay()

    @Test
    fun `a single day carries its weekday`() {
        assertEquals(
            "Mon 8 Sep",
            DayRangeFormat.label(day("2025-09-08"), day("2025-09-08"), locale),
        )
    }

    @Test
    fun `days inside one month share the month name`() {
        assertEquals(
            "8–14 Sep",
            DayRangeFormat.label(day("2025-09-08"), day("2025-09-14"), locale),
        )
    }

    @Test
    fun `a window across months names both`() {
        assertEquals(
            "28 Jul – 7 Sep",
            DayRangeFormat.label(day("2025-07-28"), day("2025-09-07"), locale),
        )
    }

    @Test
    fun `a window across years says which years`() {
        assertEquals(
            "20 Dec 2025 – 3 Jan 2026",
            DayRangeFormat.label(day("2025-12-20"), day("2026-01-03"), locale),
        )
    }
}
