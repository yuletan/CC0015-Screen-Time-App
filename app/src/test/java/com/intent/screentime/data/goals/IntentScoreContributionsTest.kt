package com.intent.screentime.data.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentScoreContributionsTest {
    private val hour = 60 * 60_000L
    private val cap = 4 * hour

    @Test
    fun `contributions retain the score weights and identify positive components`() {
        val contributions = IntentScore.contributions(
            productionMs = 3 * hour,
            consumptionMs = hour,
            screenTimeMs = 3 * hour,
            capMs = cap,
            focusMs = hour,
            streakDays = 7,
            bedtimeHeld = true,
        )

        assertEquals(5, contributions.size)
        // 75% of 20, all of 20, all of 20, all of 30, all of 10.
        assertEquals(95, contributions.sumOf { it.earned })
        assertTrue(contributions.all { it.tone == IntentScore.ContributionTone.POSITIVE })
    }

    @Test
    fun `every row names its own maximum, and they add up to the hundred`() {
        val contributions = IntentScore.contributions(
            productionMs = hour,
            consumptionMs = hour,
            screenTimeMs = 2 * hour,
            capMs = cap,
            focusMs = 0L,
            streakDays = 0,
        )

        assertEquals(
            IntentScore.MAX,
            contributions.sumOf { it.available },
        )
        assertEquals(
            listOf(
                IntentScore.ContributionKind.FOCUS,
                IntentScore.ContributionKind.PRODUCTION,
                IntentScore.ContributionKind.CAP,
                IntentScore.ContributionKind.BEDTIME,
                IntentScore.ContributionKind.STREAK,
            ),
            contributions.map { it.kind },
        )
    }

    @Test
    fun `over cap, consumption majority and a broken night are negative`() {
        val contributions = IntentScore.contributions(
            productionMs = hour,
            consumptionMs = 3 * hour,
            screenTimeMs = 5 * hour,
            capMs = cap,
            focusMs = 0L,
            streakDays = 0,
            bedtimeHeld = false,
        )

        assertEquals(
            IntentScore.ContributionTone.NEGATIVE,
            contributions.first { it.kind == IntentScore.ContributionKind.PRODUCTION }.tone,
        )
        assertEquals(
            IntentScore.ContributionTone.NEGATIVE,
            contributions.first { it.kind == IntentScore.ContributionKind.CAP }.tone,
        )
        assertEquals(
            IntentScore.ContributionTone.NEGATIVE,
            contributions.first { it.kind == IntentScore.ContributionKind.BEDTIME }.tone,
        )
        assertEquals(
            IntentScore.ContributionTone.NEGATIVE,
            contributions.first { it.kind == IntentScore.ContributionKind.FOCUS }.tone,
        )
        assertEquals(
            IntentScore.ContributionTone.NEGATIVE,
            contributions.first { it.kind == IntentScore.ContributionKind.STREAK }.tone,
        )
    }

    @Test
    fun `no cap, no window and no categorised time remain neutral where data is missing`() {
        val contributions = IntentScore.contributions(
            productionMs = 0L,
            consumptionMs = 0L,
            screenTimeMs = 2 * hour,
            capMs = null,
            focusMs = 0L,
            streakDays = 0,
        )

        assertEquals(
            IntentScore.ContributionTone.NEUTRAL,
            contributions.first { it.kind == IntentScore.ContributionKind.PRODUCTION }.tone,
        )
        assertEquals(
            IntentScore.ContributionTone.NEUTRAL,
            contributions.first { it.kind == IntentScore.ContributionKind.CAP }.tone,
        )
        assertEquals(
            IntentScore.ContributionTone.NEUTRAL,
            contributions.first { it.kind == IntentScore.ContributionKind.BEDTIME }.tone,
        )
    }

    @Test
    fun `partial progress is positive when it exists`() {
        val contributions = IntentScore.contributions(
            productionMs = hour,
            consumptionMs = hour,
            screenTimeMs = 2 * hour,
            capMs = cap,
            focusMs = 30 * 60_000L,
            streakDays = 2,
            bedtimeHeld = true,
        )

        assertEquals(
            IntentScore.ContributionTone.POSITIVE,
            contributions.first { it.kind == IntentScore.ContributionKind.FOCUS }.tone,
        )
        assertEquals(
            IntentScore.ContributionTone.POSITIVE,
            contributions.first { it.kind == IntentScore.ContributionKind.STREAK }.tone,
        )
        assertEquals(
            IntentScore.ContributionTone.POSITIVE,
            contributions.first { it.kind == IntentScore.ContributionKind.BEDTIME }.tone,
        )
    }
}
