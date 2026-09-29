package com.intent.screentime.data.stats

import java.time.LocalDate

/**
 * What a weekday averages out to over a multi-week window.
 *
 * A weekday average taken over a single week is one day wearing a statistic's clothes, so
 * this is fed weeks rather than whichever range the user happens to be looking at. Days
 * with no recorded usage are left out of their weekday's average and do not count towards
 * its sample size — which is why the count travels with the average: "4h 12m" means
 * something different whether it came from eight Wednesdays or two.
 */
object WeekdayAverages {

    val LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    data class Entry(val label: String, val averageMs: Long, val sampleDays: Int)

    data class Result(
        val entries: List<Entry>,
        val weekdayAverageMs: Long,
        val weekendAverageMs: Long,
    )

    /** [days] is one `(epochDay, screenTimeMs)` pair per day in the window. */
    fun of(days: List<Pair<Long, Long>>): Result {
        val totals = LongArray(LABELS.size)
        val counts = IntArray(LABELS.size)

        days.forEach { (epochDay, screenTimeMs) ->
            if (screenTimeMs <= 0L) return@forEach
            val index = LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1
            totals[index] += screenTimeMs
            counts[index] += 1
        }

        val entries = LABELS.indices.map { index ->
            Entry(
                label = LABELS[index],
                averageMs = if (counts[index] > 0) totals[index] / counts[index] else 0L,
                sampleDays = counts[index],
            )
        }

        return Result(
            entries = entries,
            weekdayAverageMs = average(totals, counts, 0..4),
            weekendAverageMs = average(totals, counts, 5..6),
        )
    }

    /**
     * Averaged over *days*, not over weekdays: a weekday that only appears once in the
     * window should not weigh as much as one that appears five times.
     */
    private fun average(totals: LongArray, counts: IntArray, indices: IntRange): Long {
        val sampleDays = indices.sumOf { counts[it] }
        if (sampleDays == 0) return 0L
        return indices.sumOf { totals[it] } / sampleDays
    }
}
