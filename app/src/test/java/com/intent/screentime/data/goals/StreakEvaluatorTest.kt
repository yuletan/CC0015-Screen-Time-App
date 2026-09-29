package com.intent.screentime.data.goals

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class StreakEvaluatorTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day0 = 20_000L
    private val utc = ZoneId.of("UTC")

    private fun bedtimeTarget(startHour: Int, endHour: Int) = TargetEntity(
        type = TargetType.BEDTIME_WINDOW,
        valueMinutes = 0,
        startMinutesOfDay = startHour * 60,
        endMinutesOfDay = endHour * 60,
    )

    private fun nightSession(
        epochDay: Long,
        startHour: Int,
        startMinute: Int,
        minutes: Long,
        packageName: String = "app.social",
    ): AppSessionEntity {
        val start = DayWindow.startOfDayMs(epochDay, utc) +
            startHour * hour + startMinute * minute
        return AppSessionEntity(
            packageName = packageName,
            startMs = start,
            endMs = start + minutes * minute,
            durationMs = minutes * minute,
            dayEpochDay = epochDay,
        )
    }

    private fun summary(
        epochDay: Long,
        screenTimeMs: Long,
        productionMs: Long = 0L,
        consumptionMs: Long = 0L,
        focusMs: Long = 0L,
    ) = DailySummaryEntity(
        dayEpochDay = epochDay,
        screenTimeMs = screenTimeMs,
        unlockCount = 0,
        productionMs = productionMs,
        consumptionMs = consumptionMs,
        focusMs = focusMs,
        topPackage = null,
    )

    private fun usage(epochDay: Long, packageName: String, totalMs: Long) =
        DailyAppUsageEntity(
            dayEpochDay = epochDay,
            packageName = packageName,
            totalMs = totalMs,
            sessionCount = 1,
            firstUseMs = null,
            lastUseMs = null,
        )

    private fun target(type: TargetType, minutes: Int, scopePackage: String? = null) =
        TargetEntity(type = type, scopePackage = scopePackage, valueMinutes = minutes)

    @Test
    fun `a day over the daily cap breaks the run and resets the streak`() {
        val targets = GoalTargets.from(listOf(target(TargetType.DAILY_SCREEN_TIME_CAP, 120)))
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(
                summary(day0, screenTimeMs = 100 * minute),
                summary(day0 + 1, screenTimeMs = 150 * minute),
                summary(day0 + 2, screenTimeMs = 90 * minute),
            ),
            usageByDay = emptyMap(),
            targets = targets,
        )

        assertTrue(outcomes[0].metCap)
        assertFalse(outcomes[1].metCap)
        assertTrue(outcomes[2].metCap)
    }

    @Test
    fun `with no targets set a day cannot fail`() {
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(summary(day0, screenTimeMs = 12 * hour)),
            usageByDay = emptyMap(),
            targets = GoalTargets(),
        )

        assertTrue(outcomes.single().metCap)
        assertTrue(outcomes.single().metGoal)
    }

    @Test
    fun `a weekly cap is judged at a seventh of its value per day`() {
        // 840 minutes a week is 120 a day.
        val targets = GoalTargets.from(listOf(target(TargetType.WEEKLY_SCREEN_TIME_CAP, 840)))
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(
                summary(day0, screenTimeMs = 119 * minute),
                summary(day0 + 1, screenTimeMs = 121 * minute),
            ),
            usageByDay = emptyMap(),
            targets = targets,
        )

        assertTrue(outcomes[0].metCap)
        assertFalse(outcomes[1].metCap)
    }

    @Test
    fun `a breached per-app cap breaks the day even when the daily total is fine`() {
        val targets = GoalTargets.from(
            listOf(target(TargetType.PER_APP_DAILY_CAP, 30, scopePackage = "app.social")),
        )
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(summary(day0, screenTimeMs = 20 * minute)),
            usageByDay = mapOf(day0 to listOf(usage(day0, "app.social", 45 * minute))),
            targets = targets,
        )

        assertFalse(outcomes.single().metCap)
    }

    @Test
    fun `a per-app cap that is respected does not break the day`() {
        val targets = GoalTargets.from(
            listOf(target(TargetType.PER_APP_DAILY_CAP, 30, scopePackage = "app.social")),
        )
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(summary(day0, screenTimeMs = 20 * minute)),
            usageByDay = mapOf(day0 to listOf(usage(day0, "app.social", 25 * minute))),
            targets = targets,
        )

        assertTrue(outcomes.single().metCap)
    }

    @Test
    fun `a weekly production goal is met by a day at a seventh of it`() {
        val targets = GoalTargets.from(
            listOf(target(TargetType.WEEKLY_PRODUCTION_GOAL, 420)),
        )
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(
                summary(day0, screenTimeMs = hour, productionMs = 90 * minute),
                summary(day0 + 1, screenTimeMs = hour, productionMs = 30 * minute),
            ),
            usageByDay = emptyMap(),
            targets = targets,
        )

        assertTrue(outcomes[0].metGoal)
        assertFalse(outcomes[1].metGoal)
    }

    @Test
    fun `current streak counts consecutive days inside the cap`() {
        val rows = listOf(
            StreakDayEntity(day0 - 1, metCap = true, metGoal = false, score = 50, productionMs = 0),
            StreakDayEntity(day0 - 2, metCap = true, metGoal = false, score = 50, productionMs = 0),
            StreakDayEntity(day0 - 3, metCap = false, metGoal = false, score = 0, productionMs = 0),
        )

        // Today has no row yet, so the streak is the two completed days behind it.
        assertEquals(2, StreakEvaluator.currentStreak(rows, day0))
    }

    @Test
    fun `an in-progress today does not break the streak`() {
        val rows = listOf(
            StreakDayEntity(day0, metCap = false, metGoal = false, score = 10, productionMs = 0),
            StreakDayEntity(day0 - 1, metCap = true, metGoal = true, score = 80, productionMs = 0),
            StreakDayEntity(day0 - 2, metCap = true, metGoal = true, score = 80, productionMs = 0),
        )

        // The day is still running: a streak that died at midnight every night is not
        // a streak, so yesterday and the day before still count.
        assertEquals(2, StreakEvaluator.currentStreak(rows, day0))
    }

    @Test
    fun `a completed today inside the cap adds to the streak`() {
        val rows = listOf(
            StreakDayEntity(day0, metCap = true, metGoal = true, score = 90, productionMs = 0),
            StreakDayEntity(day0 - 1, metCap = true, metGoal = true, score = 80, productionMs = 0),
        )

        assertEquals(2, StreakEvaluator.currentStreak(rows, day0))
    }

    @Test
    fun `best streak finds the longest run in history`() {
        val rows = listOf(
            StreakDayEntity(day0, metCap = true, metGoal = false, score = 50, productionMs = 0),
            StreakDayEntity(day0 - 1, metCap = false, metGoal = false, score = 0, productionMs = 0),
            StreakDayEntity(day0 - 2, metCap = true, metGoal = false, score = 50, productionMs = 0),
            StreakDayEntity(day0 - 3, metCap = true, metGoal = false, score = 50, productionMs = 0),
            StreakDayEntity(day0 - 4, metCap = true, metGoal = false, score = 50, productionMs = 0),
        )

        assertEquals(3, StreakEvaluator.bestStreak(rows))
    }

    @Test
    fun `milestones are the highest one reached`() {
        assertEquals(3, StreakEvaluator.milestoneReached(3))
        assertEquals(7, StreakEvaluator.milestoneReached(9))
        assertEquals(30, StreakEvaluator.milestoneReached(45))
        assertEquals(null, StreakEvaluator.milestoneReached(2))
    }

    @Test
    fun `no bedtime window set means no night can fail`() {
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(summary(day0, screenTimeMs = hour)),
            usageByDay = emptyMap(),
            targets = GoalTargets.from(listOf(target(TargetType.DAILY_SCREEN_TIME_CAP, 240))),
        )

        assertTrue(outcomes.single().metBedtime)
        assertEquals(0L, outcomes.single().bedtimeUsedMs)
    }

    @Test
    fun `a scroll in the small hours is charged to the evening before it`() {
        val targets = GoalTargets.from(listOf(bedtimeTarget(startHour = 23, endHour = 7)))
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(summary(day0, screenTimeMs = hour)),
            usageByDay = emptyMap(),
            sessions = listOf(nightSession(day0 + 1, startHour = 1, startMinute = 30, minutes = 30)),
            targets = targets,
            nowMs = DayWindow.startOfDayMs(day0 + 2, utc) + 9 * hour,
            zone = utc,
        )

        // Filing it under the day it started would have let every night look clean,
        // because the damage always happens after midnight.
        assertFalse(outcomes.single().metBedtime)
        assertEquals(30 * minute, outcomes.single().bedtimeUsedMs)
    }

    @Test
    fun `a session in the evening is not also charged to the following night`() {
        val targets = GoalTargets.from(listOf(bedtimeTarget(startHour = 23, endHour = 7)))
        val evening = nightSession(day0, startHour = 23, startMinute = 30, minutes = 20)
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(
                summary(day0, screenTimeMs = hour),
                summary(day0 + 1, screenTimeMs = hour),
            ),
            usageByDay = emptyMap(),
            sessions = listOf(evening),
            targets = targets,
            nowMs = DayWindow.startOfDayMs(day0 + 2, utc) + 9 * hour,
            zone = utc,
        )

        assertFalse(outcomes.first { it.epochDay == day0 }.metBedtime)

        // The next day's window does not open until that evening, so the same session is
        // not counted twice.
        val nextNight = outcomes.first { it.epochDay == day0 + 1 }
        assertTrue(nextNight.metBedtime)
        assertEquals(0L, nextNight.bedtimeUsedMs)
    }

    @Test
    fun `a night spent on the phone fails the day even when the cap held`() {
        val targets = GoalTargets.from(
            listOf(
                target(TargetType.DAILY_SCREEN_TIME_CAP, 240),
                bedtimeTarget(startHour = 23, endHour = 7),
            ),
        )
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(summary(day0, screenTimeMs = hour)),
            usageByDay = emptyMap(),
            sessions = listOf(nightSession(day0, 23, 30, minutes = 40)),
            targets = targets,
            nowMs = DayWindow.startOfDayMs(day0 + 1, utc) + 9 * hour,
            zone = utc,
        )

        assertTrue(outcomes.single().metCap)
        assertFalse(outcomes.single().metBedtime)
        assertEquals(40 * minute, outcomes.single().bedtimeUsedMs)
    }

    @Test
    fun `a night still open when the pass runs is judged only on what has happened`() {
        val targets = GoalTargets.from(listOf(bedtimeTarget(startHour = 23, endHour = 7)))
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(summary(day0, screenTimeMs = hour)),
            usageByDay = emptyMap(),
            sessions = listOf(nightSession(day0, 23, 50, minutes = 70)),
            targets = targets,
            // Half past midnight: the window is still running, so only the forty minutes up
            // to now count rather than the seventy the stored row records.
            nowMs = DayWindow.startOfDayMs(day0 + 1, utc) + 30 * minute,
            zone = utc,
        )

        assertEquals(40 * minute, outcomes.single().bedtimeUsedMs)
        assertFalse(outcomes.single().metBedtime)
    }

    @Test
    fun `a held night keeps the day and the score's bedtime row`() {
        val targets = GoalTargets.from(listOf(bedtimeTarget(startHour = 23, endHour = 7)))
        val outcomes = StreakEvaluator.evaluate(
            summaries = listOf(summary(day0, screenTimeMs = hour)),
            usageByDay = emptyMap(),
            sessions = listOf(nightSession(day0, 23, 0, minutes = 3)),
            targets = targets,
            nowMs = DayWindow.startOfDayMs(day0 + 1, utc) + 9 * hour,
            zone = utc,
        )

        assertTrue(outcomes.single().metBedtime)
        assertEquals(3 * minute, outcomes.single().bedtimeUsedMs)
    }
}
