package com.intent.screentime.data.goals

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType
import com.intent.screentime.data.usage.Bedtime
import com.intent.screentime.data.usage.BedtimeWindow
import java.time.ZoneId

/**
 * The set of limits and goals a day is judged against, resolved once from the target
 * table so the evaluation itself stays pure.
 */
data class GoalTargets(
    val dailyCapMinutes: Int? = null,
    val weeklyCapMinutes: Int? = null,
    val weeklyProductionGoalMinutes: Int? = null,
    val dailyFocusGoalMinutes: Int? = null,
    val perAppCaps: Map<String, Int> = emptyMap(),
    /** The quiet window, which is a pair of times rather than an amount. */
    val bedtime: BedtimeWindow? = null,
) {
    val hasAnything: Boolean
        get() = dailyCapMinutes != null || weeklyCapMinutes != null ||
            weeklyProductionGoalMinutes != null || dailyFocusGoalMinutes != null ||
            perAppCaps.isNotEmpty() || bedtime != null

    /**
     * The cap a single day is judged against.
     *
     * A weekly cap is read at a seventh of its value, because a streak is a daily idea —
     * the same rule [StreakEvaluator] judges by, in the one place both can see it.
     */
    val effectiveDailyCapMs: Long?
        get() = dailyCapMinutes?.takeIf { it > 0 }?.toLong()?.times(60_000L)
            ?: weeklyCapMinutes?.takeIf { it > 0 }?.toLong()?.let { it * 60_000L / 7 }

    companion object {
        fun from(targets: List<TargetEntity>): GoalTargets {
            var dailyCap: Int? = null
            var weeklyCap: Int? = null
            var weeklyProduction: Int? = null
            var focusGoal: Int? = null
            var bedtimeWindow: BedtimeWindow? = null
            val perApp = HashMap<String, Int>()

            for (target in targets) {
                if (!target.enabled) continue

                // Read before the value check every other target has to pass: a bedtime
                // carries a window, and its `valueMinutes` is deliberately zero.
                if (target.type == TargetType.BEDTIME_WINDOW) {
                    val start = target.startMinutesOfDay
                    val end = target.endMinutesOfDay
                    if (start != null && end != null) {
                        val window = BedtimeWindow(start, end)
                        if (window.isSet) bedtimeWindow = window
                    }
                    continue
                }

                if (target.valueMinutes <= 0) continue
                when (target.type) {
                    TargetType.DAILY_SCREEN_TIME_CAP -> dailyCap = target.valueMinutes
                    TargetType.WEEKLY_SCREEN_TIME_CAP -> weeklyCap = target.valueMinutes
                    TargetType.WEEKLY_PRODUCTION_GOAL -> weeklyProduction = target.valueMinutes
                    TargetType.DAILY_FOCUS_GOAL -> focusGoal = target.valueMinutes
                    TargetType.PER_APP_DAILY_CAP ->
                        target.scopePackage?.let { perApp[it] = target.valueMinutes }

                    TargetType.BEDTIME_WINDOW -> Unit
                }
            }

            return GoalTargets(
                dailyCapMinutes = dailyCap,
                weeklyCapMinutes = weeklyCap,
                weeklyProductionGoalMinutes = weeklyProduction,
                dailyFocusGoalMinutes = focusGoal,
                perAppCaps = perApp,
                bedtime = bedtimeWindow,
            )
        }
    }
}

/**
 * Decides, for each day, whether the limits were respected and the goals met, and folds
 * that into the [StreakDayEntity] rows the heatmap and score read.
 *
 * Three deliberate choices:
 *
 *  - a weekly cap is judged per day at a **seventh of its value**, because a streak is a
 *    daily idea: "did today keep me on pace for the week" is answerable every evening,
 *    whereas a weekly total is only knowable on Sunday.
 *  - **no target set means the day cannot fail.** Someone who has set no cap should not
 *    see a broken streak; the streak is about the commitments the user actually made. A
 *    bedtime that has not opened yet is treated the same way, and is correctable: the
 *    nightly pass re-judges a rolling sixty days, so tonight is settled tomorrow.
 *  - a night is **partly judged while it is still running**. Only the elapsed part of the
 *    window counts, so an evening that is going well reads as going well at 23:20 instead
 *    of sitting neutral until the morning.
 */
object StreakEvaluator {

    data class DayOutcome(
        val epochDay: Long,
        val metCap: Boolean,
        val metGoal: Boolean,
        val metBedtime: Boolean,
        /** Screen time that fell inside the night's window, for the card and the goals board. */
        val bedtimeUsedMs: Long,
        val score: Int,
        val productionMs: Long,
    )

    fun evaluate(
        summaries: List<DailySummaryEntity>,
        usageByDay: Map<Long, List<DailyAppUsageEntity>>,
        /**
         * Every session in the window being judged, **not** filed by day.
         *
         * Filing them by the day they started is the obvious thing to do and it is wrong:
         * a 23:00–07:00 window opens on one day and closes the next morning, so a 01:30
         * scroll belongs to the evening before it. Grouped by start day, it would sit with
         * the following morning and every night would look clean, because the damage
         * always happens after midnight.
         *
         * The night's own millisecond bounds are what decide, and they already do that
         * correctly in [Bedtime], so the honest fix is to hand it everything and let it
         * filter.
         */
        sessions: List<AppSessionEntity> = emptyList(),
        targets: GoalTargets,
        excludedPackages: Set<String> = emptySet(),
        /** Long.MAX_VALUE judges every night as if it were over. */
        nowMs: Long = Long.MAX_VALUE,
        zone: ZoneId = DayWindow.zone,
    ): List<DayOutcome> {
        val ordered = summaries.sortedBy { it.dayEpochDay }
        val outcomes = ArrayList<DayOutcome>(ordered.size)
        var runningStreak = 0

        for (summary in ordered) {
            val metCap = capHeld(summary, usageByDay[summary.dayEpochDay].orEmpty(), targets)
            runningStreak = if (metCap) runningStreak + 1 else 0

            val bedtimeStatus = targets.bedtime?.let { window ->
                Bedtime.status(
                    window = window,
                    epochDay = summary.dayEpochDay,
                    sessions = sessions,
                    nowMs = nowMs,
                    excludedPackages = excludedPackages,
                    zone = zone,
                )
            }
            val bedtimeUsedMs = targets.bedtime?.let { window ->
                Bedtime.elapsedWindow(window, summary.dayEpochDay, nowMs, zone)?.let { (from, to) ->
                    Bedtime.usedMs(sessions, from, to, excludedPackages)
                }
            } ?: 0L

            outcomes += DayOutcome(
                epochDay = summary.dayEpochDay,
                metCap = metCap,
                metGoal = goalMet(summary, targets),
                // A night that has not begun cannot be missed, exactly as a commitment
                // that was never made cannot be broken.
                metBedtime = bedtimeStatus != Bedtime.Status.MISSED,
                bedtimeUsedMs = bedtimeUsedMs,
                score = IntentScore.compute(
                    productionMs = summary.productionMs,
                    consumptionMs = summary.consumptionMs,
                    screenTimeMs = summary.screenTimeMs,
                    capMs = targets.effectiveDailyCapMs,
                    focusMs = summary.focusMs,
                    streakDays = runningStreak,
                    bedtimeHeld = when (bedtimeStatus) {
                        null, Bedtime.Status.PENDING -> null
                        Bedtime.Status.HELD -> true
                        Bedtime.Status.MISSED -> false
                    },
                ),
                productionMs = summary.productionMs,
            )
        }

        return outcomes
    }

    /**
     * The streak the user is currently on.
     *
     * Today is allowed to be unmet without breaking the streak — the day is still
     * running, and a streak that dies at midnight every night is not a streak.
     */
    fun currentStreak(rows: List<StreakDayEntity>, todayEpochDay: Long): Int {
        val byDay = rows.associateBy { it.dayEpochDay }
        var streak = 0

        if (byDay[todayEpochDay]?.metCap == true) {
            streak = 1
        }
        var day = todayEpochDay - 1

        while (true) {
            val row = byDay[day] ?: break
            if (!row.metCap) break
            streak += 1
            day -= 1
        }

        return streak
    }

    fun bestStreak(rows: List<StreakDayEntity>): Int {
        var best = 0
        var run = 0
        var previousDay: Long? = null

        for (row in rows.sortedBy { it.dayEpochDay }) {
            val contiguous = previousDay?.let { row.dayEpochDay == it + 1 } == true
            run = when {
                !row.metCap -> 0
                contiguous || run == 0 -> run + 1
                else -> 1
            }
            if (run > best) best = run
            previousDay = row.dayEpochDay
        }

        return best
    }

    /** Milestones worth a notification, in ascending order. */
    val MILESTONES: List<Int> = listOf(3, 7, 14, 30, 60, 100, 180, 365)

    /** The highest milestone [streak] has reached, or null when it has not reached one. */
    fun milestoneReached(streak: Int): Int? =
        MILESTONES.lastOrNull { it <= streak }

    private fun capHeld(
        summary: DailySummaryEntity,
        appUsage: List<DailyAppUsageEntity>,
        targets: GoalTargets,
    ): Boolean {
        val capMs = targets.effectiveDailyCapMs
        if (capMs != null && summary.screenTimeMs > capMs) return false

        return appUsage.none { row ->
            val cap = targets.perAppCaps[row.packageName] ?: return@none false
            row.totalMs > cap * 60_000L
        }
    }

    private fun goalMet(summary: DailySummaryEntity, targets: GoalTargets): Boolean {
        val weeklyGoal = targets.weeklyProductionGoalMinutes?.takeIf { it > 0 } ?: return true
        return summary.productionMs >= weeklyGoal * 60_000L / 7
    }
}
