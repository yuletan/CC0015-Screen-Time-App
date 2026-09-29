package com.intent.screentime.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekdayAveragesTest {

    /** 2026-03-09 is a Monday, so the index maths has something to get wrong. */
    private val monday = LocalDate.parse("2026-03-09").toEpochDay()

    private fun day(offset: Int, ms: Long): Pair<Long, Long> = (monday + offset) to ms

    @Test
    fun `each day lands on its own weekday`() {
        val result = WeekdayAverages.of(
            listOf(
                day(0, 3_600_000L), // Mon
                day(1, 7_200_000L), // Tue
                day(6, 1_800_000L), // Sun
            ),
        )

        assertEquals(3_600_000L, result.entries[0].averageMs)
        assertEquals(7_200_000L, result.entries[1].averageMs)
        assertEquals(1_800_000L, result.entries[6].averageMs)
    }

    @Test
    fun `repeated weekdays are averaged and counted`() {
        val result = WeekdayAverages.of(
            listOf(
                day(0, 3_600_000L),
                day(7, 5_400_000L), // the next Monday
            ),
        )

        assertEquals(4_500_000L, result.entries[0].averageMs)
        assertEquals(2, result.entries[0].sampleDays)
    }

    @Test
    fun `a weekday with no usage reports zero across zero samples`() {
        val result = WeekdayAverages.of(listOf(day(2, 3_600_000L))) // Wednesday only

        assertEquals(0L, result.entries[1].averageMs)
        assertEquals(0, result.entries[1].sampleDays)
    }

    @Test
    fun `days with no usage do not drag an average down`() {
        val result = WeekdayAverages.of(
            listOf(
                day(0, 7_200_000L),
                day(7, 0L), // a Monday the phone was never unlocked
            ),
        )

        assertEquals(7_200_000L, result.entries[0].averageMs)
        assertEquals(1, result.entries[0].sampleDays)
    }

    @Test
    fun `weekend and weekday averages are weighted by samples`() {
        val result = WeekdayAverages.of(
            listOf(
                day(0, 3_600_000L),
                day(1, 3_600_000L),
                day(2, 3_600_000L),
                day(3, 3_600_000L),
                day(4, 3_600_000L),
                day(5, 10_800_000L), // Sat
                day(6, 14_400_000L), // Sun
            ),
        )

        assertEquals(3_600_000L, result.weekdayAverageMs)
        assertEquals(12_600_000L, result.weekendAverageMs)
    }

    @Test
    fun `an empty window has no averages rather than zeroes that look measured`() {
        val result = WeekdayAverages.of(emptyList())

        assertEquals(0L, result.weekdayAverageMs)
        assertEquals(0L, result.weekendAverageMs)
        assertEquals(7, result.entries.size)
        assertEquals(0, result.entries.sumOf { it.sampleDays })
    }
}
