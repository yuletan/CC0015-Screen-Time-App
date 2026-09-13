package com.intent.screentime.data.goals

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentScoreTest {

    private val hour = 60 * 60_000L
    private val minute = 60_000L
    private val cap4h = 4 * hour

    @Test
    fun `a day inside the cap with production, focus and a streak scores 100`() {
        val score = IntentScore.compute(
            productionMs = 2 * hour,
            consumptionMs = 0L,
            screenTimeMs = 2 * hour,
            capMs = cap4h,
            focusMs = hour,
            streakDays = 7,
        )

        assertEquals(100, score)
    }

    @Test
    fun `a day that doubles its cap with nothing produced scores zero`() {
        val score = IntentScore.compute(
            productionMs = 0L,
            consumptionMs = 2 * hour,
            screenTimeMs = 8 * hour,
            capMs = cap4h,
            focusMs = 0L,
            streakDays = 0,
        )

        assertEquals(0, score)
    }

    @Test
    fun `equal production and consumption with focus and a streak lands mid-scale`() {
        val score = IntentScore.compute(
            productionMs = hour,
            consumptionMs = hour,
            screenTimeMs = 2 * hour,
            capMs = cap4h,
            focusMs = 30 * minute,
            streakDays = 7,
        )

        // 0.40*0.5 + 0.25*1 + 0.20*0.5 + 0.15*1 = 0.70
        assertEquals(70, score)
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

        // 0.40 + 0.25 + 0 + 0 = 0.65
        assertEquals(65, score)
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
