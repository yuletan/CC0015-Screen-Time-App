package com.intent.screentime.data.goals

import com.intent.screentime.data.local.entity.StreakDayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HeatmapBuilderTest {

    /** A Tuesday, so the alignment logic has something to get wrong. */
    private val today = LocalDate.parse("2026-03-10").toEpochDay()

    private fun row(epochDay: Long, metCap: Boolean = true, metGoal: Boolean = false) =
        StreakDayEntity(
            dayEpochDay = epochDay,
            metCap = metCap,
            metGoal = metGoal,
            score = 50,
            productionMs = 0L,
        )

    @Test
    fun `the grid has one column per week and seven rows`() {
        val weeks = HeatmapBuilder.weeks(emptyList(), today, weeks = 12)

        assertEquals(12, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
    }

    @Test
    fun `every column starts on a Monday`() {
        val weeks = HeatmapBuilder.weeks(emptyList(), today, weeks = 4)

        weeks.forEach { week ->
            assertEquals(
                DayOfWeek.MONDAY,
                LocalDate.ofEpochDay(week.first().epochDay).dayOfWeek,
            )
        }
    }

    @Test
    fun `today lands in the last column and is marked`() {
        val weeks = HeatmapBuilder.weeks(emptyList(), today, weeks = 3)
        val todayCells = weeks.flatten().filter { it.isToday }

        assertEquals(1, todayCells.size)
        assertEquals(today, todayCells.single().epochDay)
        assertTrue(weeks.last().any { it.isToday })
    }

    @Test
    fun `days with no row are reported as absent rather than failed`() {
        val weeks = HeatmapBuilder.weeks(emptyList(), today, weeks = 2)
        val cells = weeks.flatten()

        assertTrue(cells.all { !it.hasData && !it.metCap })
    }

    @Test
    fun `a judged day carries its verdict into the right cell`() {
        val yesterday = today - 1
        val weeks = HeatmapBuilder.weeks(
            rows = listOf(row(yesterday, metCap = true, metGoal = true)),
            todayEpochDay = today,
            weeks = 2,
        )

        val cell = weeks.flatten().single { it.epochDay == yesterday }
        assertTrue(cell.hasData)
        assertTrue(cell.metCap)
        assertTrue(cell.metGoal)
    }
}
