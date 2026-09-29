package com.intent.screentime.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyRollupTest {

    /** 2026-03-09 is a Monday, so the week boundaries have something to get wrong. */
    private val monday = LocalDate.parse("2026-03-09").toEpochDay()

    private fun day(offset: Int, screenTimeMs: Long, productionMs: Long = 0L, consumptionMs: Long = 0L) =
        DayTotals(
            epochDay = monday + offset,
            screenTimeMs = screenTimeMs,
            productionMs = productionMs,
            consumptionMs = consumptionMs,
            unlockCount = 0,
            focusMs = 0L,
        )

    @Test
    fun `days fold into the monday that starts their week`() {
        val weeks = WeeklyRollup.of((0..6).map { day(it, 1_000L) })

        assertEquals(1, weeks.size)
        assertEquals(monday, weeks.single().startEpochDay)
        assertEquals(7_000L, weeks.single().screenTimeMs)
        assertEquals(7, weeks.single().days)
        assertFalse(weeks.single().isPartial)
    }

    @Test
    fun `sunday belongs to the week that started on monday`() {
        val weeks = WeeklyRollup.of(listOf(day(0, 1_000L), day(6, 2_000L)))

        assertEquals(1, weeks.size)
        assertEquals(3_000L, weeks.single().screenTimeMs)
    }

    @Test
    fun `a part-finished week says how many days it holds`() {
        val weeks = WeeklyRollup.of(listOf(day(7, 1_000L), day(8, 1_000L), day(9, 1_000L)))

        assertEquals(3, weeks.single().days)
        assertTrue(weeks.single().isPartial)
    }

    @Test
    fun `a week before tracking began is absent rather than a quiet zero`() {
        // Two days in the current week, and a gap where the previous week would be.
        val weeks = WeeklyRollup.of(listOf(day(14, 1_000L), day(15, 2_000L)))

        assertEquals(1, weeks.size)
        assertEquals(monday + 14, weeks.single().startEpochDay)
    }

    @Test
    fun `weeks come back in order however the days arrive`() {
        val weeks = WeeklyRollup.of(
            listOf(day(8, 1_000L), day(1, 2_000L), day(7, 3_000L), day(0, 4_000L)),
        )

        assertEquals(listOf(monday, monday + 7), weeks.map { it.startEpochDay })
        assertEquals(6_000L, weeks[0].screenTimeMs)
        assertEquals(4_000L, weeks[1].screenTimeMs)
    }

    @Test
    fun `production and consumption ride along for the split`() {
        val weeks = WeeklyRollup.of(
            listOf(day(0, 1_000L, productionMs = 700L, consumptionMs = 300L)),
        )

        assertEquals(700L, weeks.single().productionMs)
        assertEquals(300L, weeks.single().consumptionMs)
    }

    @Test
    fun `an app's totals roll up the same way`() {
        val weeks = WeeklyRollup.ofTotals(
            listOf((monday) to 1_000L, (monday + 2) to 2_000L, (monday + 9) to 4_000L),
        )

        assertEquals(2, weeks.size)
        assertEquals(3_000L, weeks[0].screenTimeMs)
        assertEquals(2, weeks[0].days)
        assertEquals(4_000L, weeks[1].screenTimeMs)
    }

    @Test
    fun `an empty history has no weeks rather than one empty one`() {
        assertEquals(emptyList<WeeklyRollup.Week>(), WeeklyRollup.of(emptyList()))
        assertEquals(emptyList<WeeklyRollup.Week>(), WeeklyRollup.ofTotals(emptyList()))
    }

    @Test
    fun `a week carries the monday it started on as its label`() {
        val weeks = WeeklyRollup.of(listOf(day(0, 1_000L)))

        assertEquals("9 Mar", weeks.single().label)
    }
}
