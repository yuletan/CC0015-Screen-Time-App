package com.intent.screentime.data.goals

import androidx.room.withTransaction
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.IntentDatabase
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.prefs.UserPreferences
import com.intent.screentime.data.usage.UsageIngestor
import com.intent.screentime.notify.NotificationHelper
import kotlinx.coroutines.flow.first

/**
 * Everything the app does without the user watching: finalising days, judging them
 * against the targets, and deciding when a notification is actually warranted.
 *
 * The three entry points map exactly onto the three background triggers:
 *  - [checkCapAlerts] after each harvest, so a breach is announced while it is still fresh
 *  - [runNightly] after midnight, so yesterday's row is final and the streak is judged
 *  - [postDigest] at the user's chosen time, so the day ends with a number
 */
class GoalTracker(
    private val database: IntentDatabase,
    private val ingestor: UsageIngestor,
    private val preferences: UserPreferences,
    private val notifier: NotificationHelper,
    private val labelOf: (String) -> String,
) {

    data class RollupReport(
        val daysEvaluated: Int,
        val streakDays: Int,
        val milestone: Int?,
    )

    /**
     * Judges the last [STREAK_WINDOW_DAYS] of summaries against the current targets and
     * writes the streak rows the heatmap, score and milestones all read.
     *
     * A window rather than a single day: when the user changes a cap, past days should
     * immediately reflect the new commitment instead of keeping a stale verdict.
     */
    suspend fun evaluateStreaks(fromDay: Long, toDay: Long): Int {
        val summaries = database.dailySummaryDao().between(fromDay, toDay)
        if (summaries.isEmpty()) return 0

        val usageByDay = summaries.associate { summary ->
            summary.dayEpochDay to database.dailyAppUsageDao().forDay(summary.dayEpochDay)
        }
        val targets = GoalTargets.from(database.targetDao().enabled())
        val outcomes = StreakEvaluator.evaluate(summaries, usageByDay, targets)

        database.withTransaction {
            for (outcome in outcomes) {
                database.streakDayDao().upsert(
                    StreakDayEntity(
                        dayEpochDay = outcome.epochDay,
                        metCap = outcome.metCap,
                        metGoal = outcome.metGoal,
                        score = outcome.score,
                        productionMs = outcome.productionMs,
                    ),
                )
            }
        }

        return outcomes.size
    }

    /** The nightly pass: harvest, finalise yesterday, judge the recent past, celebrate. */
    suspend fun runNightly(): RollupReport {
        ingestor.ingest()

        val today = DayWindow.todayEpochDay()
        // Two days, not one: a harvest just after midnight may still be completing
        // yesterday's last session, and re-finalising today costs nothing.
        ingestor.reaggregateFrom(today - 1)

        val daysEvaluated = evaluateStreaks(today - STREAK_WINDOW_DAYS, today)

        val rows = database.streakDayDao().recent(STREAK_LOOKBACK_DAYS)
        val streak = StreakEvaluator.currentStreak(rows, today)
        val milestone = StreakEvaluator.milestoneReached(streak)
        val celebrated = preferences.lastMilestoneStreak.first()

        if (milestone != null && milestone > celebrated) {
            notifier.notifyMilestone(streak)
            preferences.setLastMilestoneStreak(milestone)
        }

        return RollupReport(
            daysEvaluated = daysEvaluated,
            streakDays = streak,
            milestone = milestone?.takeIf { it > celebrated },
        )
    }

    /**
     * Called after every harvest. Alerts once per breach per day — the marker is stored,
     * so the fifteenth harvest of the afternoon stays silent.
     */
    suspend fun checkCapAlerts(): Int {
        val today = DayWindow.todayEpochDay()
        val summary = database.dailySummaryDao().getDay(today) ?: return 0
        val usage = database.dailyAppUsageDao().forDay(today)
        val targets = database.targetDao().enabled()
        val alreadySent = preferences.capAlertTokens.first()

        val alerts = TargetMonitor.alertsFor(
            epochDay = today,
            summary = summary,
            appUsage = usage,
            targets = targets,
            alreadySent = alreadySent,
        )
        if (alerts.isEmpty()) return 0

        notifier.notifyCapAlerts(alerts)
        preferences.addCapAlertTokens(alerts.map { it.token }.toSet(), today)
        return alerts.size
    }

    /**
     * The daily digest. Harvests first so the number is current, and stays quiet on a
     * day with no usage rather than announcing zeroes.
     */
    suspend fun postDigest(): Boolean {
        val today = DayWindow.todayEpochDay()
        ingestor.ingest()
        ingestor.reaggregateFrom(today)

        val summary = database.dailySummaryDao().getDay(today) ?: return false
        if (summary.screenTimeMs <= 0L) return false

        val topPackage = summary.topPackage
        val topMs = topPackage?.let { packageName ->
            database.dailyAppUsageDao().forDay(today)
                .firstOrNull { it.packageName == packageName }
                ?.totalMs
        } ?: 0L

        notifier.notifyDigest(
            screenTimeMs = summary.screenTimeMs,
            topLabel = topPackage?.let(labelOf),
            topMs = topMs,
        )
        preferences.setLastDigestEpochDay(today)
        return true
    }

    companion object {
        const val STREAK_WINDOW_DAYS = 60L
        const val STREAK_LOOKBACK_DAYS = 90
    }
}
