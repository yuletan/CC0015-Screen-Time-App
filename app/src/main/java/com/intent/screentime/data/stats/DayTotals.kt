package com.intent.screentime.data.stats

/**
 * One day's totals, in the shape the window statistics work on.
 *
 * Deliberately not a Room entity: the comparisons below are pure arithmetic over numbers
 * that have already been read, which is what makes them testable without a database.
 *
 * A day is present here only because the tracker wrote a row for it. That distinction is
 * load-bearing — a day the phone was never picked up is a real zero and counts, while a
 * day before tracking began has no row at all and is absent rather than zero. Nothing in
 * this package has to filter for it, because the storage layer already drew the line.
 */
data class DayTotals(
    val epochDay: Long,
    val screenTimeMs: Long,
    val productionMs: Long,
    val consumptionMs: Long,
    val unlockCount: Int,
    val focusMs: Long,
)
