package com.intent.screentime.data.goals

import com.intent.screentime.data.local.entity.StreakDayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeNudgeTest {

    private val today = 20_000L

    private fun night(day: Long, metBedtime: Boolean) = StreakDayEntity(
        dayEpochDay = day,
        metCap = true,
        metGoal = true,
        score = 60,
        productionMs = 0L,
        metBedtime = metBedtime,
    )

    @Test
    fun `three broken nights in the last week is a window worth revisiting`() {
        val rows = listOf(
            night(today - 1, metBedtime = false),
            night(today - 2, metBedtime = false),
            night(today - 3, metBedtime = false),
            night(today - 4, metBedtime = true),
        )

        val nudge = BedtimeNudge.from(rows, today)

        assertEquals(3, nudge.missedNights)
        assertEquals(4, nudge.judgedNights)
        assertTrue(nudge.repeated)
    }

    @Test
    fun `two broken nights is a week, not a pattern`() {
        val rows = listOf(
            night(today - 1, metBedtime = false),
            night(today - 2, metBedtime = false),
            night(today - 3, metBedtime = true),
            night(today - 4, metBedtime = true),
        )

        assertFalse(BedtimeNudge.from(rows, today).repeated)
    }

    @Test
    fun `tonight is not counted, because it is still running`() {
        val rows = listOf(
            night(today, metBedtime = false),
            night(today - 1, metBedtime = false),
            night(today - 2, metBedtime = false),
            night(today - 3, metBedtime = false),
        )

        val nudge = BedtimeNudge.from(rows, today)

        assertEquals(3, nudge.missedNights)
        assertEquals(3, nudge.judgedNights)
    }

    @Test
    fun `the window reaches back seven nights and no further`() {
        val rows = listOf(
            night(today - 1, metBedtime = false),
            night(today - 7, metBedtime = false),
            night(today - 8, metBedtime = false),
            night(today - 40, metBedtime = false),
        )

        val nudge = BedtimeNudge.from(rows, today)

        assertEquals(2, nudge.missedNights)
        assertEquals(2, nudge.judgedNights)
    }

    @Test
    fun `no judged nights reads as no evidence rather than a broken window`() {
        val nudge = BedtimeNudge.from(emptyList(), today)

        assertEquals(0, nudge.missedNights)
        assertEquals(0, nudge.judgedNights)
        assertFalse(nudge.repeated)
    }
}
