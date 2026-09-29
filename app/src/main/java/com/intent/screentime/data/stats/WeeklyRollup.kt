package com.intent.screentime.data.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Days folded into Monday-start weeks.
 *
 * Forty-two daily points is not a readable line and ninety is a smear, so a range long
 * enough to hold a behaviour change is plotted a week at a time. A week is also the unit
 * the targets are already written in, which is what lets the weekly cap be drawn against
 * these points without converting anything.
 *
 * Weeks with no rows are omitted rather than emitted as zero: a week before tracking began
 * is an absence, and drawing it as a quiet week would invent a taper that never happened.
 * Each week carries how many days it was built from, so a part-finished current week
 * announces itself instead of pretending to be low.
 */
object WeeklyRollup {

    data class Week(
        val startEpochDay: Long,
        val label: String,
        val screenTimeMs: Long,
        val productionMs: Long,
        val consumptionMs: Long,
        val days: Int,
    ) {
        val isPartial: Boolean get() = days < WeekOverWeek.DAYS_PER_WEEK
    }

    fun of(days: List<DayTotals>): List<Week> = roll(
        days.associate { day ->
            day.epochDay to Triple(day.screenTimeMs, day.productionMs, day.consumptionMs)
        },
    )

    /** Screen time only, for series that have no category split — a single app's history. */
    fun ofTotals(days: List<Pair<Long, Long>>): List<Week> = roll(
        days.associate { (epochDay, totalMs) -> epochDay to Triple(totalMs, 0L, 0L) },
    )

    private fun roll(byDay: Map<Long, Triple<Long, Long, Long>>): List<Week> = byDay.entries
        .groupBy { (epochDay, _) -> mondayOf(epochDay) }
        .map { (monday, entries) ->
            Week(
                startEpochDay = monday,
                label = WEEK_LABEL.format(LocalDate.ofEpochDay(monday)),
                screenTimeMs = entries.sumOf { it.value.first },
                productionMs = entries.sumOf { it.value.second },
                consumptionMs = entries.sumOf { it.value.third },
                days = entries.size,
            )
        }
        .sortedBy { it.startEpochDay }

    private fun mondayOf(epochDay: Long): Long = LocalDate.ofEpochDay(epochDay)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .toEpochDay()

    private val WEEK_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
}
