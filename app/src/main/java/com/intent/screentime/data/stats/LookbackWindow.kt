package com.intent.screentime.data.stats

/**
 * Where a lookback window sits relative to today.
 *
 * The range selector chooses how long a window is; this chooses which one. Offset 0 is
 * the window ending today, offset 1 the one ending the day before it begins, and so on —
 * so stepping back is the same arithmetic whatever unit the charts are drawn in, and a
 * week at offset 3 covers exactly the seven days the week at offset 2 does not.
 *
 * Pure, so the arithmetic is unit tested rather than eyeballed on a device.
 */
object LookbackWindow {

    /** The [days]-long window ending [offset] windows before [today]. */
    fun of(today: Long, days: Long, offset: Int): LongRange {
        val end = today - offset * days
        return (end - days + 1)..end
    }

    /**
     * How many windows back there is anything to look at.
     *
     * Counted from the first tracked day, so the stepper stops where the history does
     * instead of walking into a run of empty windows.
     */
    fun maxOffset(today: Long, earliestDay: Long?, days: Long): Int {
        if (days <= 0L || earliestDay == null || earliestDay >= today) return 0
        return ((today - earliestDay) / days).toInt().coerceAtLeast(0)
    }
}
