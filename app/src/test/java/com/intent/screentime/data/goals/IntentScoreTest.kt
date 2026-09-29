package com.intent.screentime.data.goals

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentScoreTest {

    private val hour = 60 * 60_000L
    private val minute = 60_000L
    private val cap4h = 4 * hour

    @Test
    fun `the five weights add up to the whole score`() {
        assertEquals(
            IntentScore.MAX,
            IntentScore.PRODUCTION_MAX + IntentScore.CAP_MAX + IntentScore.BEDTIME_MAX +
                IntentScore.FOCUS_MAX + IntentScore.STREAK_MAX,
        )
    }

    @Test
    fun `a day inside the cap with production, focus, a streak and a kept night scores 100`() {
        val score = IntentScore.compute(
            productionMs = 2 * hour,
            consumptionMs = 0L,
            screenTimeMs = 2 * hour,
            capMs = cap4h,
            focusMs = hour,
            streakDays = 7,
            bedtimeHeld = true,
        )

        assertEquals(100, score)
    }

    @Test
    fun `a day that doubles its cap and breaks its bedtime scores zero`() {
        val score = IntentScore.compute(
            productionMs = 0L,
            consumptionMs = 2 * hour,
            screenTimeMs = 8 * hour,
            capMs = cap4h,
            focusMs = 0L,
            streakDays = 0,
            bedtimeHeld = false,
        )

        assertEquals(0, score)
    }

    @Test
    fun `equal production and consumption lands mid-scale`() {
        val score = IntentScore.compute(
            productionMs = hour,
            consumptionMs = hour,
            screenTimeMs = 2 * hour,
            capMs = cap4h,
            focusMs = 30 * minute,
            streakDays = 7,
        )

        // 10 of 20 + 20 of 20 + 10 neutral + 15 of 30 + 10 of 10 points, and the rows are
        // integers because the screen has to be able to add them up out loud.
        assertEquals(65, score)
    }

    @Test
    fun `production without consumption earns the full production weight`() {
        val score = IntentScore.compute(
            productionMs = hour,
            consumptionMs = 0L,
            screenTimeMs = hour,
            capMs = cap4h,
            focusMs = 0L,
            streakDays = 0,
        )

        // 20 + 20 + 10 neutral bedtime + 0 + 0 points.
        assertEquals(50, score)
    }

    @Test
    fun `each row is scored out of its own maximum and the rows sum to the total`() {
        val points = IntentScore.points(
            productionMs = 3 * hour,
            consumptionMs = 3 * hour,
            screenTimeMs = 6 * hour,
            capMs = 8 * hour,
            focusMs = hour,
            streakDays = 7,
            bedtimeHeld = true,
        )

        // 50% of 20, all of 20, all of 20, all of 30, all of 10.
        assertEquals(10, points.production)
        assertEquals(20, points.cap)
        assertEquals(20, points.bedtime)
        assertEquals(30, points.focus)
        assertEquals(10, points.streak)
        assertEquals(90, points.total)
        assertEquals(
            points.total,
            IntentScore.compute(
                productionMs = 3 * hour,
                consumptionMs = 3 * hour,
                screenTimeMs = 6 * hour,
                capMs = 8 * hour,
                focusMs = hour,
                streakDays = 7,
                bedtimeHeld = true,
            ),
        )
    }

    @Test
    fun `a perfect day earns every point on offer`() {
        val points = IntentScore.points(
            productionMs = 2 * hour,
            consumptionMs = 0L,
            screenTimeMs = 2 * hour,
            capMs = cap4h,
            focusMs = hour,
            streakDays = 7,
            bedtimeHeld = true,
        )

        assertEquals(IntentScore.PRODUCTION_MAX, points.production)
        assertEquals(IntentScore.CAP_MAX, points.cap)
        assertEquals(IntentScore.BEDTIME_MAX, points.bedtime)
        assertEquals(IntentScore.FOCUS_MAX, points.focus)
        assertEquals(IntentScore.STREAK_MAX, points.streak)
        assertEquals(IntentScore.MAX, points.total)
    }

    @Test
    fun `cap credit decays linearly and reaches zero at one and a half times the cap`() {
        assertEquals(1f, IntentScore.parts(0, 0, 4 * hour, cap4h, 0, 0).cap, 0.0001f)
        assertEquals(0.5f, IntentScore.parts(0, 0, 5 * hour, cap4h, 0, 0).cap, 0.0001f)
        assertEquals(0f, IntentScore.parts(0, 0, 6 * hour, cap4h, 0, 0).cap, 0.0001f)
    }

    @Test
    fun `no cap set gives half credit rather than punishing the user`() {
        assertEquals(0.5f, IntentScore.parts(0, 0, 9 * hour, null, 0, 0).cap, 0.0001f)
    }

    @Test
    fun `a kept night earns the whole bedtime weight`() {
        val points = IntentScore.points(0, 0, 0, null, 0, 0, bedtimeHeld = true)

        assertEquals(IntentScore.BEDTIME_MAX, points.bedtime)
    }

    @Test
    fun `a broken night earns none of it`() {
        val points = IntentScore.points(0, 0, 0, null, 0, 0, bedtimeHeld = false)

        assertEquals(0, points.bedtime)
    }

    @Test
    fun `no window set and a night that has not opened both give half credit`() {
        // Null is the one input for both cases: with no commitment made and with one the
        // day has not reached, the score must behave the same way rather than guessing.
        assertEquals(0.5f, IntentScore.parts(0, 0, 0, null, 0, 0, bedtimeHeld = null).bedtime, 0.0001f)
        assertEquals(0.5f, IntentScore.parts(0, 0, 0, null, 0, 0).bedtime, 0.0001f)
    }

    @Test
    fun `uncategorised time gives a neutral production ratio`() {
        assertEquals(0.5f, IntentScore.productionRatio(0L, 0L), 0.0001f)
    }

    @Test
    fun `focus earns full credit at one hour and is capped there`() {
        assertEquals(1f, IntentScore.parts(0, 0, 0, null, hour, 0).focus, 0.0001f)
        assertEquals(1f, IntentScore.parts(0, 0, 0, null, 5 * hour, 0).focus, 0.0001f)
    }

    @Test
    fun `production ratio ignores utility and unsorted time by construction`() {
        // Only production and consumption are ever passed in; a utility-heavy day should
        // therefore read as a clean 100% rather than a diluted one.
        assertEquals(1f, IntentScore.productionRatio(hour, 0L), 0.0001f)
    }
}
