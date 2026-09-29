package com.intent.screentime.data.goals

import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import kotlin.math.roundToInt

/**
 * The Intent Score for a window of days, rather than for one.
 *
 * A single day is its own score, exactly as the Today screen computes it. A range is the
 * **average of its days**, taken row by row: the Producing row is the mean of each day's
 * Producing points, and so on. Two consequences make this the honest way to do it —
 *
 *  - the five rows still add up to the ring, because the ring is summed from the rounded
 *    row means rather than rounded on its own, which is the same rule [IntentScore.compute]
 *    follows for a day;
 *  - a one-day window is not a special case, it is the same arithmetic with one sample, so
 *    a past day read here reads the same as it did on the Today screen or on its day card.
 *
 * Nothing here invents a "monthly score". Every point shown was earned by a real day under
 * the same five weights, and the rows a reader can see are the rows that were averaged.
 *
 * Pure: it takes rows that have already been read, so all of it is unit tested.
 */
object ScoreWindow {

    /** Everything one day needs to be scored, already read. */
    data class Input(
        val epochDay: Long,
        val productionMs: Long,
        val consumptionMs: Long,
        val screenTimeMs: Long,
        val focusMs: Long,
        val capMs: Long?,
        val streakDays: Int,
        val bedtimeHeld: Boolean?,
    )

    data class Day(val epochDay: Long, val score: Int)

    data class Result(
        /** The ring: the sum of the rounded row means, so the rows add up to it. */
        val score: Int,
        val points: IntentScore.Points,
        val contributions: List<IntentScore.Contribution>,
        val days: Int,
        val best: Day?,
        val worst: Day?,
        /** Nights the window can be judged on, and how many of them were kept. */
        val nightsJudged: Int = 0,
        val nightsHeld: Int = 0,
        /**
         * The longest run of days inside the cap the window reached.
         *
         * The highest streak any of its days was on, counting the run a day inherited from
         * before the window — a run is not shorter for having started earlier.
         */
        val longestStreak: Int = 0,
    ) {
        val isSingleDay: Boolean get() = days == 1
    }

    /**
     * One [Input] per day that was tracked.
     *
     * A day is present only because the tracker wrote a row for it, exactly as everywhere
     * else: a day the phone was never picked up is a real zero and counts, and a day before
     * tracking began is absent rather than zero.
     */
    fun inputs(
        summaries: List<DailySummaryEntity>,
        targets: GoalTargets,
        streakRows: List<StreakDayEntity>,
        todayEpochDay: Long,
    ): List<Input> {
        val verdicts = streakRows.associateBy { it.dayEpochDay }
        val capMs = targets.effectiveDailyCapMs
        val runs = runningStreaks(streakRows)

        return summaries.map { summary ->
            val day = summary.dayEpochDay
            Input(
                epochDay = day,
                productionMs = summary.productionMs,
                consumptionMs = summary.consumptionMs,
                screenTimeMs = summary.screenTimeMs,
                focusMs = summary.focusMs,
                capMs = capMs,
                streakDays = streakOn(day, runs, streakRows, todayEpochDay),
                bedtimeHeld = bedtimeHeld(day, targets, verdicts, todayEpochDay),
            )
        }
    }

    /** Null when the window has no day rows at all — there is nothing to average. */
    fun of(days: List<Input>): Result? {
        if (days.isEmpty()) return null

        val scored = days.map { day ->
            Scored(
                epochDay = day.epochDay,
                points = IntentScore.points(
                    productionMs = day.productionMs,
                    consumptionMs = day.consumptionMs,
                    screenTimeMs = day.screenTimeMs,
                    capMs = day.capMs,
                    focusMs = day.focusMs,
                    streakDays = day.streakDays,
                    bedtimeHeld = day.bedtimeHeld,
                ),
                parts = IntentScore.parts(
                    productionMs = day.productionMs,
                    consumptionMs = day.consumptionMs,
                    screenTimeMs = day.screenTimeMs,
                    capMs = day.capMs,
                    focusMs = day.focusMs,
                    streakDays = day.streakDays,
                    bedtimeHeld = day.bedtimeHeld,
                ),
            )
        }

        val points = IntentScore.Points(
            production = scored.meanInt { it.points.production },
            cap = scored.meanInt { it.points.cap },
            bedtime = scored.meanInt { it.points.bedtime },
            focus = scored.meanInt { it.points.focus },
            streak = scored.meanInt { it.points.streak },
        )
        val fractions = IntentScore.Parts(
            production = scored.meanFloat { it.parts.production.toDouble() },
            cap = scored.meanFloat { it.parts.cap.toDouble() },
            // Judged nights only, so a window of nights nobody judged does not read as a
            // window of nights nobody kept.
            bedtime = judgedNights(days).let { nights ->
                if (nights.isEmpty()) 0.5f else nights.count { it }.toFloat() / nights.size
            },
            focus = scored.meanFloat { it.parts.focus.toDouble() },
            streak = scored.meanFloat { it.parts.streak.toDouble() },
        )

        val totals = scored.map { Day(it.epochDay, it.points.total) }
        val nights = judgedNights(days)

        return Result(
            score = points.total,
            points = points,
            contributions = contributions(points, fractions, days.first().capMs, nights),
            days = days.size,
            best = totals.maxByOrNull { it.score },
            worst = totals.minByOrNull { it.score },
            nightsJudged = nights.size,
            nightsHeld = nights.count { it },
            longestStreak = days.maxOf { it.streakDays },
        )
    }

    private data class Scored(
        val epochDay: Long,
        val points: IntentScore.Points,
        val parts: IntentScore.Parts,
    )

    /**
     * The five rows, in the same order and with the same meanings as a single day's.
     *
     * The tone is read from the averaged fraction rather than from the earned total, so a
     * one-day window gets exactly the tone [IntentScore.contributions] would have given it:
     * Producing turns positive past half, the cap turns positive only when the average day
     * was inside it, and focus and streak turn positive as soon as any was earned.
     */
    private fun contributions(
        points: IntentScore.Points,
        fractions: IntentScore.Parts,
        capMs: Long?,
        nights: List<Boolean>,
    ): List<IntentScore.Contribution> = listOf(
        contribution(
            kind = IntentScore.ContributionKind.FOCUS,
            earned = points.focus,
            available = IntentScore.FOCUS_MAX,
            fraction = fractions.focus,
            tone = if (fractions.focus > 0f) {
                IntentScore.ContributionTone.POSITIVE
            } else {
                IntentScore.ContributionTone.NEGATIVE
            },
        ),
        contribution(
            kind = IntentScore.ContributionKind.PRODUCTION,
            earned = points.production,
            available = IntentScore.PRODUCTION_MAX,
            fraction = fractions.production,
            tone = when {
                fractions.production > 0.5f -> IntentScore.ContributionTone.POSITIVE
                fractions.production < 0.5f -> IntentScore.ContributionTone.NEGATIVE
                else -> IntentScore.ContributionTone.NEUTRAL
            },
        ),
        contribution(
            kind = IntentScore.ContributionKind.CAP,
            earned = points.cap,
            available = IntentScore.CAP_MAX,
            fraction = fractions.cap,
            tone = when {
                capMs == null -> IntentScore.ContributionTone.NEUTRAL
                fractions.cap >= 1f -> IntentScore.ContributionTone.POSITIVE
                else -> IntentScore.ContributionTone.NEGATIVE
            },
        ),
        contribution(
            kind = IntentScore.ContributionKind.BEDTIME,
            earned = points.bedtime,
            available = IntentScore.BEDTIME_MAX,
            fraction = fractions.bedtime,
            // A window with some nights kept and some broken is genuinely mixed, so it reads
            // neutral rather than claiming the majority carried the night.
            tone = when {
                nights.isEmpty() -> IntentScore.ContributionTone.NEUTRAL
                nights.all { it } -> IntentScore.ContributionTone.POSITIVE
                nights.none { it } -> IntentScore.ContributionTone.NEGATIVE
                else -> IntentScore.ContributionTone.NEUTRAL
            },
        ),
        contribution(
            kind = IntentScore.ContributionKind.STREAK,
            earned = points.streak,
            available = IntentScore.STREAK_MAX,
            fraction = fractions.streak,
            tone = if (fractions.streak > 0f) {
                IntentScore.ContributionTone.POSITIVE
            } else {
                IntentScore.ContributionTone.NEGATIVE
            },
        ),
    )

    private fun contribution(
        kind: IntentScore.ContributionKind,
        earned: Int,
        available: Int,
        fraction: Float,
        tone: IntentScore.ContributionTone,
    ) = IntentScore.Contribution(
        kind = kind,
        earned = earned,
        available = available,
        tone = tone,
        fraction = fraction,
    )

    /**
     * The streak as it stood **on that day**, which is not what [StreakEvaluator.currentStreak]
     * answers.
     *
     * That function is written for today, where an unmet day is still running and must not
     * break the run a reader can see — so it reports the streak carried *into* the day. A
     * settled day is judged differently: if it went over the cap the run was 0 by the time
     * the day ended, which is exactly what the nightly pass stored for it. Past days
     * therefore use the chain, and today keeps the running streak the Today screen shows.
     */
    private fun streakOn(
        day: Long,
        runs: Map<Long, Int>,
        streakRows: List<StreakDayEntity>,
        todayEpochDay: Long,
    ): Int = if (day >= todayEpochDay) {
        StreakEvaluator.currentStreak(streakRows, day)
    } else {
        runs[day] ?: 0
    }

    /** Consecutive days inside the cap, counted forwards through the verdicts. */
    private fun runningStreaks(rows: List<StreakDayEntity>): Map<Long, Int> {
        val runs = HashMap<Long, Int>(rows.size)
        var run = 0

        for (row in rows.sortedBy { it.dayEpochDay }) {
            run = if (row.metCap) run + 1 else 0
            runs[row.dayEpochDay] = run
        }

        return runs
    }

    /**
     * Whether that night's window was kept, or null when it cannot be judged.
     *
     * A commitment that was never made cannot be broken, and a night that has not happened
     * yet cannot be kept — which is why the day that is still running stays neutral here,
     * exactly as the Today screen shows it while the window is pending. The stored verdict
     * is the settled one, written by the nightly pass over the last sixty days.
     */
    private fun bedtimeHeld(
        day: Long,
        targets: GoalTargets,
        verdicts: Map<Long, StreakDayEntity>,
        todayEpochDay: Long,
    ): Boolean? = when {
        targets.bedtime == null -> null
        day >= todayEpochDay -> null
        else -> verdicts[day]?.metBedtime
    }

    private fun judgedNights(days: List<Input>): List<Boolean> =
        days.mapNotNull { it.bedtimeHeld }

    private fun List<Scored>.meanInt(select: (Scored) -> Int): Int =
        (sumOf { select(it) }.toDouble() / size).roundToInt()

    private fun List<Scored>.meanFloat(select: (Scored) -> Double): Float =
        (sumOf { select(it) } / size).toFloat()
}
