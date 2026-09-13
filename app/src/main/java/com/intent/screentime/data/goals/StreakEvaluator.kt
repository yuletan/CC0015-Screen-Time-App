package com.intent.screentime.data.goals

import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType

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
) {
    val hasAnything: Boolean
        get() = dailyCapMinutes != null || weeklyCapMinutes != null ||
            weeklyProductionGoalMinutes != null || dailyFocusGoalMinutes != null ||
            perAppCaps.isNotEmpty()

    companion object {
        fun from(targets: List<TargetEntity>): GoalTargets {
            var dailyCap: Int? = null
            var weeklyCap: Int? = null
            var weeklyProduction: Int? = null
            var focusGoal: Int? = null
            val perApp = HashMap<String, Int>()

            for (target in targets) {
                if (!target.enabled || target.valueMinutes <= 0) continue
                when (target.type) {
                    TargetType.DAILY_SCREEN_TIME_CAP -> dailyCap = target.valueMinutes
                    TargetType.WEEKLY_SCREEN_TIME_CAP -> weeklyCap = target.valueMinutes
                    TargetType.WEEKLY_PRODUCTION_GOAL -> weeklyProduction = target.valueMinutes
                    TargetType.DAILY_FOCUS_GOAL -> focusGoal = target.valueMinutes
                    TargetType.PER_APP_DAILY_CAP ->
                        target.scopePackage?.let { perApp[it] = target.valueMinutes }
                }
            }

            return GoalTargets(
                dailyCapMinutes = dailyCap,
                weeklyCapMinutes = weeklyCap,
                weeklyProductionGoalMinutes = weeklyProduction,
                dailyFocusGoalMinutes = focusGoal,
                perAppCaps = perApp,
            )
        }
    }
}

/**
 * Decides, for each day, whether the limits were respected and the goals met, and folds
 * that into the [StreakDayEntity] rows the heatmap and score read.
 *
 * Two deliberate choices:
 *
 *  - a weekly cap is judged per day at a **seventh of its value**, because a streak is a
 *    daily idea: "did today keep me on pace for the week" is answerable every evening,
 *    whereas a weekly total is only knowable on Sunday.
 *  - **no target set means the day cannot fail.** Someone who has set no cap should not
 *    see a broken streak; the streak is about the commitments the user actually made.
 */
object StreakEvaluator {

    data class DayOutcome(
        val epochDay: Long,
        val metCap: Boolean,
        val metGoal: Boolean,
        val score: Int,
        val productionMs: Long,
    )

    fun evaluate(
        summaries: List<DailySummaryEntity>,
        usageByDay: Map<Long, List<DailyAppUsageEntity>>,
        targets: GoalTargets,
    ): List<DayOutcome> {
        val ordered = summaries.sortedBy { it.dayEpochDay }
        val outcomes = ArrayList<DayOutcome>(ordered.size)
        var runningStreak = 0

        for (summary in ordered) {
            val metCap = capHeld(summary, usageByDay[summary.dayEpochDay].orEmpty(), targets)
            runningStreak = if (metCap) runningStreak + 1 else 0

            outcomes += DayOutcome(
                epochDay = summary.dayEpochDay,
                metCap = metCap,
                metGoal = goalMet(summary, targets),
                score = IntentScore.compute(
                    productionMs = summary.productionMs,
                    consumptionMs = summary.consumptionMs,
                    screenTimeMs = summary.screenTimeMs,
                    capMs = effectiveCapMs(targets),
                    focusMs = summary.focusMs,
                    streakDays = runningStreak,
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

    private fun effectiveCapMs(targets: GoalTargets): Long? {
        val dailyCap = targets.dailyCapMinutes?.takeIf { it > 0 }?.toLong()
        if (dailyCap != null) return dailyCap * 60_000L

        return targets.weeklyCapMinutes?.takeIf { it > 0 }?.let { it * 60_000L / 7 }
    }

    private fun capHeld(
        summary: DailySummaryEntity,
        appUsage: List<DailyAppUsageEntity>,
        targets: GoalTargets,
    ): Boolean {
        val capMs = effectiveCapMs(targets)
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
