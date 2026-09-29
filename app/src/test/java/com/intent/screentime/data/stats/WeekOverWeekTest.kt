package com.intent.screentime.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekOverWeekTest {

    /** 2026-03-09 is a Monday, so the window maths has something to get wrong. */
    private val monday = LocalDate.parse("2026-03-09").toEpochDay()

    private fun day(
        offset: Int,
        screenTimeMs: Long = 0L,
        productionMs: Long = 0L,
        consumptionMs: Long = 0L,
        unlocks: Int = 0,
        focusMs: Long = 0L,
    ) = DayTotals(
        epochDay = monday + offset,
        screenTimeMs = screenTimeMs,
        productionMs = productionMs,
        consumptionMs = consumptionMs,
        unlockCount = unlocks,
        focusMs = focusMs,
    )

    private fun compare(
        current: List<DayTotals> = emptyList(),
        previous: List<DayTotals> = emptyList(),
        previousWeek: List<DayTotals> = emptyList(),
        currentApps: List<Pair<String, Long>> = emptyList(),
        previousApps: List<Pair<String, Long>> = emptyList(),
        mindless: Pair<Int, Int> = 0 to 0,
    ) = WeekOverWeek.compare(
        current = current,
        previous = previous,
        previousWeek = previousWeek,
        currentApps = currentApps,
        previousApps = previousApps,
        currentMindlessOpens = mindless.first,
        previousMindlessOpens = mindless.second,
    )

    @Test
    fun `a part-finished week is compared against the same days of last week`() {
        val windows = WeekOverWeek.windowsFor(monday + 9) // the Wednesday of week two

        assertEquals(monday + 7, windows.current.first)
        assertEquals(monday + 9, windows.current.last)
        assertEquals(monday, windows.previous.first)
        assertEquals(monday + 2, windows.previous.last)
        assertEquals(monday + 6, windows.previousFull.last)
    }

    @Test
    fun `windows start on monday whatever day they are asked about`() {
        (0..6).forEach { offset ->
            assertEquals(monday, WeekOverWeek.windowsFor(monday + offset).current.first)
        }
        (7..13).forEach { offset ->
            assertEquals(monday + 7, WeekOverWeek.windowsFor(monday + offset).current.first)
        }
    }

    @Test
    fun `a three day week is not read as a win against a full one`() {
        val result = compare(
            current = listOf(day(7, 3_600_000L), day(8, 3_600_000L), day(9, 3_600_000L)),
            previous = (0..6).map { day(it, 3_600_000L) },
        )

        // The totals differ by a week's worth of days, and say nothing.
        assertEquals(10_800_000L, result.screenTime.current)
        assertEquals(25_200_000L, result.screenTime.previous)

        // The per-day figures are what the comparison is actually read from.
        assertEquals(3_600_000L, result.screenTimePerDay.current)
        assertEquals(3_600_000L, result.screenTimePerDay.previous)
        assertEquals(WeekOverWeek.Direction.FLAT, result.screenTimePerDay.direction)
        assertTrue(result.isCurrentPartial)
    }

    @Test
    fun `a day the tracker recorded but the phone stayed idle counts as a zero`() {
        val result = compare(
            current = listOf(day(7, 0L), day(8, 3_600_000L)),
            previous = listOf(day(0, 3_600_000L), day(1, 3_600_000L)),
        )

        assertEquals(2, result.currentDays)
        assertEquals(1_800_000L, result.screenTimePerDay.current)
        assertEquals(3_600_000L, result.screenTimePerDay.previous)
    }

    @Test
    fun `a week with nothing behind it has no comparison to make`() {
        val result = compare(
            current = listOf(day(7, 3_600_000L)),
            previous = emptyList(),
        )

        assertFalse(result.hasPrevious)
        assertEquals(0, result.previousDays)
    }

    @Test
    fun `screen time falling reads as down`() {
        val result = compare(
            current = listOf(day(7, 10_800_000L), day(8, 0L)),
            previous = listOf(day(0, 10_800_000L), day(1, 10_800_000L)),
        )

        assertEquals(WeekOverWeek.Direction.DOWN, result.screenTime.direction)
        assertEquals(-10_800_000L, result.screenTime.delta)
        assertEquals(-50, result.screenTime.percent!!)
    }

    @Test
    fun `there is no percentage to report when last week recorded nothing`() {
        val result = compare(
            current = listOf(day(7, 3_600_000L)),
            previous = listOf(day(0, 0L)), // tracked, but never used
        )

        assertTrue(result.hasPrevious)
        assertEquals(WeekOverWeek.Direction.UP, result.screenTime.direction)
        assertNull(result.screenTime.percent)
    }

    @Test
    fun `production share leaves utility time out of the denominator`() {
        val result = compare(
            current = listOf(day(7, productionMs = 3_600_000L, consumptionMs = 3_600_000L)),
            previous = listOf(day(0, productionMs = 1_800_000L, consumptionMs = 5_400_000L)),
        )

        assertEquals(50L, result.productionShare.current)
        assertEquals(25L, result.productionShare.previous)
        assertEquals(WeekOverWeek.Direction.UP, result.productionShare.direction)
    }

    @Test
    fun `a week with nothing accountable in it reports no share at all`() {
        val result = compare(
            current = listOf(day(7, screenTimeMs = 3_600_000L)),
            previous = listOf(day(0, screenTimeMs = 3_600_000L)),
        )

        assertEquals(0L, result.productionShare.current)
        assertEquals(0L, result.productionShare.previous)
    }

    @Test
    fun `movers are ranked by how far they moved and unchanged apps are dropped`() {
        val result = compare(
            currentApps = listOf("a" to 1_000L, "b" to 5_000L, "c" to 7_000L),
            previousApps = listOf("a" to 1_000L, "b" to 9_000L, "d" to 2_000L),
            previous = listOf(day(0, 1L)),
        )

        assertEquals(listOf("c", "b", "d"), result.movers.map { it.packageName })
        assertEquals(7_000L, result.movers[0].deltaMs)
        assertEquals(-4_000L, result.movers[1].deltaMs)
    }

    @Test
    fun `an app new this week is a pure increase`() {
        val result = compare(
            currentApps = listOf("new" to 5_000L),
            previousApps = emptyList(),
            previous = listOf(day(0, 1L)),
        )

        assertEquals(5_000L, result.movers.single().deltaMs)
        assertEquals(0L, result.movers.single().previousMs)
    }

    @Test
    fun `the movers list is capped`() {
        val movers = WeekOverWeek.movers(
            current = listOf("a" to 1_000L, "b" to 2_000L, "c" to 3_000L),
            previous = emptyList(),
            limit = 2,
        )

        assertEquals(2, movers.size)
        assertEquals(listOf("c", "b"), movers.map { it.packageName })
    }

    @Test
    fun `last week's finished total travels with the comparison`() {
        val result = compare(
            current = listOf(day(7, 3_600_000L)),
            previous = listOf(day(0, 3_600_000L)),
            previousWeek = (0..6).map { day(it, 3_600_000L) },
        )

        assertEquals(25_200_000L, result.previousWeekTotalMs)
        assertEquals(7, result.previousWeekDays)
    }

    @Test
    fun `focus and unlocks are compared like anything else`() {
        val result = compare(
            current = listOf(day(7, unlocks = 40, focusMs = 1_800_000L)),
            previous = listOf(day(0, unlocks = 80, focusMs = 900_000L)),
        )

        assertEquals(WeekOverWeek.Direction.DOWN, result.unlocks.direction)
        assertEquals(-40L, result.unlocks.delta)
        assertEquals(WeekOverWeek.Direction.UP, result.focus.direction)
    }

    @Test
    fun `mindless opens are carried through as a count`() {
        val result = compare(
            current = listOf(day(7, 3_600_000L)),
            previous = listOf(day(0, 3_600_000L)),
            mindless = 6 to 14,
        )

        assertEquals(6L, result.mindlessOpens.current)
        assertEquals(14L, result.mindlessOpens.previous)
        assertEquals(WeekOverWeek.Direction.DOWN, result.mindlessOpens.direction)
    }
}
