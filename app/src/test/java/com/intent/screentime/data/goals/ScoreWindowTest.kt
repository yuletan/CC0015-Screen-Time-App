package com.intent.screentime.data.goals

import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class ScoreWindowTest {

    private val hour = 60 * 60_000L
    private val minute = 60_000L
    private val cap4h = 4 * hour

    @Test
    fun `a single day scores exactly what it scores on its own`() {
        val day = 20_000L
        val summary = summary(
            day = day,
            screenTimeMs = 5 * hour,
            productionMs = 2 * hour,
            consumptionMs = hour,
            focusMs = 40 * minute,
        )
        val targets = dailyCap(240)

        val result = ScoreWindow.of(
            ScoreWindow.inputs(
                summaries = listOf(summary),
                targets = targets,
                streakRows = listOf(verdict(day, metCap = true)),
                todayEpochDay = day + 1,
            ),
        )!!

        assertEquals(
            IntentScore.compute(
                productionMs = 2 * hour,
                consumptionMs = hour,
                screenTimeMs = 5 * hour,
                capMs = cap4h,
                focusMs = 40 * minute,
                streakDays = 1,
            ),
            result.score,
        )
        assertTrue(result.isSingleDay)
    }

    @Test
    fun `a single day agrees with the score the nightly pass stored for it`() {
        val day = 20_000L
        val summary = summary(
            day = day,
            screenTimeMs = 5 * hour,
            productionMs = 2 * hour,
            consumptionMs = hour,
            focusMs = 40 * minute,
        )
        val targets = dailyCap(240)

        val stored = StreakEvaluator.evaluate(listOf(summary), emptyMap(), targets = targets)
            .single()

        val result = ScoreWindow.of(
            ScoreWindow.inputs(
                summaries = listOf(summary),
                targets = targets,
                streakRows = listOf(stored.asRow()),
                todayEpochDay = day + 1,
            ),
        )!!

        assertEquals(stored.score, result.score)
    }

    @Test
    fun `the five rows of a range add up to the ring`() {
        val start = 20_000L
        val days = (0 until 7).map { offset ->
            summary(
                day = start + offset,
                screenTimeMs = (3 + offset % 3) * hour,
                productionMs = (1 + offset % 2) * hour,
                consumptionMs = hour,
                focusMs = (offset % 3) * 30 * minute,
            )
        }

        val result = ScoreWindow.of(
            ScoreWindow.inputs(days, dailyCap(240), emptyList(), todayEpochDay = start + 30),
        )!!

        assertEquals(result.points.total, result.score)
        assertEquals(result.score, result.contributions.sumOf { it.earned })
        assertEquals(IntentScore.MAX, result.contributions.sumOf { it.available })
    }

    @Test
    fun `a range is the average of its days, not a score of its totals`() {
        val start = 20_000L
        // A perfect day and an impossible one. Their totals would read as an ordinary
        // afternoon; their average is what a reader actually lived.
        val perfect = summary(
            day = start,
            screenTimeMs = 2 * hour,
            productionMs = 2 * hour,
            consumptionMs = 0L,
            focusMs = hour,
        )
        val wrecked = summary(
            day = start + 1,
            screenTimeMs = 8 * hour,
            productionMs = 0L,
            consumptionMs = 2 * hour,
            focusMs = 0L,
        )
        val rows = listOf(
            verdict(start, metCap = true),
            verdict(start + 1, metCap = false),
        )

        val result = ScoreWindow.of(
            ScoreWindow.inputs(listOf(perfect, wrecked), dailyCap(240), rows, todayEpochDay = start + 5),
        )!!

        // Streaks run 1, then 0 after the breach, so the two days are not quite 100 and 0.
        val good = IntentScore.points(2 * hour, 0L, 2 * hour, cap4h, hour, 1)
        val bad = IntentScore.points(0L, 2 * hour, 8 * hour, cap4h, 0L, 0)

        assertEquals(mean(good.production, bad.production), result.points.production)
        assertEquals(mean(good.cap, bad.cap), result.points.cap)
        assertEquals(mean(good.focus, bad.focus), result.points.focus)

        val best = result.best!!
        val worst = result.worst!!
        assertTrue(result.score > worst.score)
        assertTrue(result.score < best.score)
        assertEquals(start, best.epochDay)
        assertEquals(start + 1, worst.epochDay)
    }

    @Test
    fun `the best and worst days are the highest and lowest scoring ones`() {
        val start = 20_000L
        val quiet = summary(start, 1 * hour, hour, 0L, hour)
        val heavy = summary(start + 1, 6 * hour, 0L, 3 * hour, 0L)
        val middle = summary(start + 2, 3 * hour, hour, hour, 20 * minute)

        val result = ScoreWindow.of(
            ScoreWindow.inputs(
                summaries = listOf(quiet, heavy, middle),
                targets = dailyCap(240),
                streakRows = emptyList(),
                todayEpochDay = start + 10,
            ),
        )!!

        assertEquals(start, result.best?.epochDay)
        assertEquals(start + 1, result.worst?.epochDay)
        assertTrue(result.best!!.score > result.worst!!.score)
    }

    @Test
    fun `tonight is neutral rather than counted as a broken night`() {
        val day = 20_000L
        val summary = summary(day, 3 * hour, hour, hour, 0L)

        val result = ScoreWindow.of(
            ScoreWindow.inputs(
                summaries = listOf(summary),
                targets = bedtime(),
                streakRows = emptyList(),
                // The day being read is today: its night has not been judged yet.
                todayEpochDay = day,
            ),
        )!!

        assertEquals(IntentScore.BEDTIME_MAX / 2, result.points.bedtime)
        assertEquals(
            IntentScore.ContributionTone.NEUTRAL,
            result.contributions.single { it.kind == IntentScore.ContributionKind.BEDTIME }.tone,
        )
    }

    @Test
    fun `a night held and a night broken read opposite on a past day`() {
        val day = 20_000L
        val summary = summary(day, 3 * hour, hour, hour, 0L)

        fun toneOf(held: Boolean): IntentScore.ContributionTone {
            val result = ScoreWindow.of(
                ScoreWindow.inputs(
                    summaries = listOf(summary),
                    targets = bedtime(),
                    streakRows = listOf(verdict(day, metCap = true, metBedtime = held)),
                    todayEpochDay = day + 1,
                ),
            )!!
            return result.contributions
                .single { it.kind == IntentScore.ContributionKind.BEDTIME }
                .tone
        }

        assertEquals(IntentScore.ContributionTone.POSITIVE, toneOf(held = true))
        assertEquals(IntentScore.ContributionTone.NEGATIVE, toneOf(held = false))
    }

    @Test
    fun `a window with a mixture of kept and broken nights reads neutral`() {
        val start = 20_000L
        val days = listOf(
            summary(start, 3 * hour, hour, hour, 0L),
            summary(start + 1, 3 * hour, hour, hour, 0L),
        )

        val result = ScoreWindow.of(
            ScoreWindow.inputs(
                summaries = days,
                targets = bedtime(),
                streakRows = listOf(
                    verdict(start, metCap = true, metBedtime = true),
                    verdict(start + 1, metCap = true, metBedtime = false),
                ),
                todayEpochDay = start + 5,
            ),
        )!!

        val bedtime = result.contributions
            .single { it.kind == IntentScore.ContributionKind.BEDTIME }
        assertEquals(IntentScore.ContributionTone.NEUTRAL, bedtime.tone)
        assertEquals(IntentScore.BEDTIME_MAX / 2, bedtime.earned)
    }

    @Test
    fun `a window with no days has no score`() {
        assertNull(ScoreWindow.of(emptyList()))
    }

    @Test
    fun `days without a cap stay neutral on the cap row`() {
        val start = 20_000L
        val summary = summary(start, 9 * hour, hour, 3 * hour, 0L)

        val result = ScoreWindow.of(
            ScoreWindow.inputs(
                summaries = listOf(summary),
                targets = GoalTargets(),
                streakRows = emptyList(),
                todayEpochDay = start + 1,
            ),
        )!!

        val cap = result.contributions.single { it.kind == IntentScore.ContributionKind.CAP }
        assertEquals(IntentScore.ContributionTone.NEUTRAL, cap.tone)
        // Half of 20, the way the neutral rows round.
        assertEquals(IntentScore.CAP_MAX / 2, cap.earned)
    }

    /** Row means are rounded the way a single day's rows are, so half points go up. */
    private fun mean(first: Int, second: Int): Int =
        ((first + second) / 2.0).roundToInt()

    private fun summary(
        day: Long,
        screenTimeMs: Long,
        productionMs: Long,
        consumptionMs: Long,
        focusMs: Long,
    ) = DailySummaryEntity(
        dayEpochDay = day,
        screenTimeMs = screenTimeMs,
        unlockCount = 0,
        productionMs = productionMs,
        consumptionMs = consumptionMs,
        focusMs = focusMs,
        topPackage = null,
    )

    private fun verdict(
        day: Long,
        metCap: Boolean,
        metBedtime: Boolean = false,
    ) = StreakDayEntity(
        dayEpochDay = day,
        metCap = metCap,
        metGoal = true,
        score = 0,
        productionMs = 0L,
        metBedtime = metBedtime,
        bedtimeUsedMs = 0L,
    )

    private fun StreakEvaluator.DayOutcome.asRow() = StreakDayEntity(
        dayEpochDay = epochDay,
        metCap = metCap,
        metGoal = metGoal,
        score = score,
        productionMs = productionMs,
        metBedtime = metBedtime,
        bedtimeUsedMs = bedtimeUsedMs,
    )

    private fun dailyCap(minutes: Int) = GoalTargets.from(
        listOf(TargetEntity(type = TargetType.DAILY_SCREEN_TIME_CAP, valueMinutes = minutes)),
    )

    private fun bedtime() = GoalTargets.from(
        listOf(
            TargetEntity(
                type = TargetType.BEDTIME_WINDOW,
                valueMinutes = 0,
                startMinutesOfDay = 23 * 60,
                endMinutesOfDay = 7 * 60,
            ),
        ),
    )
}
