package com.intent.screentime.data.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * This week against last, for the handful of numbers that describe a week.
 *
 * The one decision that matters here is *which* days are compared. A running week is
 * usually unfinished, and a three-day week against a seven-day week reports a triumphant
 * drop every single week until Sunday — the exact kind of flattering arithmetic this app
 * exists to avoid. So the two windows cover the same elapsed days: on a Wednesday, Monday
 * to Wednesday against last Monday to Wednesday. Per-day figures travel with every metric
 * as a second guard, so a length mismatch can never quietly become a conclusion.
 *
 * Weeks start Monday, matching the streak heatmap and the weekly targets, so "this week"
 * means the same seven days everywhere in the app.
 */
object WeekOverWeek {

    val DAYS_PER_WEEK = 7

    enum class Direction { UP, DOWN, FLAT }

    /** One metric across the two windows. */
    data class Metric(
        val current: Long,
        val previous: Long,
        val currentDays: Int,
        val previousDays: Int,
    ) {
        val delta: Long get() = current - previous

        val direction: Direction get() = when {
            current > previous -> Direction.UP
            current < previous -> Direction.DOWN
            else -> Direction.FLAT
        }

        /** Averaged over tracked days, which is what makes two different week lengths honest. */
        val currentPerDay: Long get() = if (currentDays > 0) current / currentDays else 0L

        val previousPerDay: Long get() = if (previousDays > 0) previous / previousDays else 0L

        /**
         * Null rather than a number when there was nothing to rise from: "up 100%" against
         * a zero baseline is not growth, it is a first appearance, and it should be read
         * that way.
         */
        val percent: Int? get() = if (previous == 0L) {
            null
        } else {
            (delta.toDouble() / previous * 100.0).roundToLong().toInt()
        }
    }

    /** One app's move between the two windows. */
    data class AppMovement(
        val packageName: String,
        val currentMs: Long,
        val previousMs: Long,
    ) {
        val deltaMs: Long get() = currentMs - previousMs
    }

    /** The three windows a week-over-week comparison needs. */
    data class Windows(
        /** Monday of the running week, through today. */
        val current: LongRange,
        /** Monday of last week, through the same elapsed weekday. */
        val previous: LongRange,
        /** The whole of last week, for the "last week finished at…" context line. */
        val previousFull: LongRange,
    )

    data class Result(
        val currentDays: Int,
        val previousDays: Int,
        val screenTime: Metric,
        val screenTimePerDay: Metric,
        val productionShare: Metric,
        val unlocks: Metric,
        val focus: Metric,
        val mindlessOpens: Metric,
        /** Ranked by size of move, either direction. */
        val movers: List<AppMovement>,
        val previousWeekTotalMs: Long,
        val previousWeekDays: Int,
    ) {
        /** False on a first week with nothing behind it to measure against. */
        val hasPrevious: Boolean get() = previousDays > 0

        /** True while the running week is still short of seven tracked days. */
        val isCurrentPartial: Boolean get() = currentDays in 1 until DAYS_PER_WEEK
    }

    fun windowsFor(toDay: Long): Windows {
        val thisMonday = mondayOf(toDay)
        val lastMonday = thisMonday - DAYS_PER_WEEK
        val elapsed = toDay - thisMonday

        return Windows(
            current = thisMonday..toDay,
            previous = lastMonday..(lastMonday + elapsed),
            previousFull = lastMonday..(lastMonday + DAYS_PER_WEEK - 1),
        )
    }

    /**
     * [currentApps] and [previousApps] are `(packageName, totalMs)` for each window;
     * [currentMindlessOpens] and [previousMindlessOpens] are the count of prompts answered
     * with a drifting or bored reason.
     */
    fun compare(
        current: List<DayTotals>,
        previous: List<DayTotals>,
        previousWeek: List<DayTotals>,
        currentApps: List<Pair<String, Long>> = emptyList(),
        previousApps: List<Pair<String, Long>> = emptyList(),
        currentMindlessOpens: Int = 0,
        previousMindlessOpens: Int = 0,
    ): Result {
        val currentDays = current.size
        val previousDays = previous.size

        return Result(
            currentDays = currentDays,
            previousDays = previousDays,
            screenTime = metric(current.sumOf { it.screenTimeMs }, previous.sumOf { it.screenTimeMs }, currentDays, previousDays),
            screenTimePerDay = metric(
                average(current) { it.screenTimeMs },
                average(previous) { it.screenTimeMs },
                currentDays,
                previousDays,
            ),
            productionShare = metric(productionShare(current), productionShare(previous), currentDays, previousDays),
            unlocks = metric(
                current.sumOf { it.unlockCount.toLong() },
                previous.sumOf { it.unlockCount.toLong() },
                currentDays,
                previousDays,
            ),
            focus = metric(current.sumOf { it.focusMs }, previous.sumOf { it.focusMs }, currentDays, previousDays),
            mindlessOpens = metric(currentMindlessOpens.toLong(), previousMindlessOpens.toLong(), currentDays, previousDays),
            movers = movers(currentApps, previousApps),
            previousWeekTotalMs = previousWeek.sumOf { it.screenTimeMs },
            previousWeekDays = previousWeek.size,
        )
    }

    /**
     * Ranked by how far each app moved, in either direction, with the ones that did not
     * move left out. A list of everything is not a finding; "the two apps that changed"
     * is.
     */
    fun movers(
        current: List<Pair<String, Long>>,
        previous: List<Pair<String, Long>>,
        limit: Int = DEFAULT_MOVERS,
    ): List<AppMovement> {
        val currentByPackage = current.toMap()
        val previousByPackage = previous.toMap()

        return (currentByPackage.keys + previousByPackage.keys)
            .map { packageName ->
                AppMovement(
                    packageName = packageName,
                    currentMs = currentByPackage[packageName] ?: 0L,
                    previousMs = previousByPackage[packageName] ?: 0L,
                )
            }
            .filter { it.deltaMs != 0L }
            .sortedByDescending { abs(it.deltaMs) }
            .take(limit)
    }

    /**
     * Share of accountable time that was producing, as a whole percent.
     *
     * Utility and unsorted time are left out of the denominator rather than counted as
     * consumption: a banking app is neither proof nor damage, and quietly scoring it as
     * damage would make the ratio say more than the user did.
     */
    private fun productionShare(days: List<DayTotals>): Long {
        val production = days.sumOf { it.productionMs }
        val accountable = days.sumOf { it.productionMs + it.consumptionMs }
        if (accountable == 0L) return 0L
        return (production.toDouble() / accountable * 100.0).roundToLong()
    }

    private fun average(days: List<DayTotals>, value: (DayTotals) -> Long): Long {
        if (days.isEmpty()) return 0L
        return days.sumOf(value) / days.size
    }

    private fun metric(current: Long, previous: Long, currentDays: Int, previousDays: Int) = Metric(
        current = current,
        previous = previous,
        currentDays = currentDays,
        previousDays = previousDays,
    )

    private fun mondayOf(epochDay: Long): Long = LocalDate.ofEpochDay(epochDay)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .toEpochDay()

    private const val DEFAULT_MOVERS = 5
}
