package com.intent.screentime.data.usage

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.FocusSessionEntity
import java.time.ZoneId

/**
 * Focus inferred from what was actually on screen.
 *
 * The timer is a claim; this is evidence. A stretch of uninterrupted work in a
 * producing-category app is counted even when no timer was ever started, which is what
 * keeps "focused: 0m" off a day that was spent typing in Docs.
 *
 * Deliberately conservative, because an inflated focus figure would flatter rather than
 * inform:
 *  - only PRODUCTION apps count — games, video and social never do, however long they run
 *  - a run has to last [MIN_RUN_MS] to count at all; a two-minute glance is not focus
 *  - a gap longer than [MAX_GAP_MS] between two producing apps breaks the run, and the
 *    gap itself is never counted: the clock only runs while an app is in the foreground
 *  - time in any other kind of app ends the run outright
 *
 * Pure, so every one of those statements is unit tested.
 */
object FocusStretches {

    data class Interval(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    /** Below this, a stretch is a glance rather than a session of work. */
    const val MIN_RUN_MS = 10 * 60_000L

    /** A pause this short between two producing apps does not break the run. */
    const val MAX_GAP_MS = 2 * 60_000L

    /** Uninterrupted producing-app runs on [epochDay], clipped at the day's edges. */
    fun detected(
        sessions: List<AppSessionEntity>,
        epochDay: Long,
        kindByPackage: Map<String, CategoryKind>,
        excludedPackages: Set<String> = emptySet(),
        zone: ZoneId = DayWindow.zone,
    ): List<Interval> {
        val dayStart = DayWindow.startOfDayMs(epochDay, zone)
        val dayEnd = DayWindow.endOfDayMs(epochDay, zone)

        // Excluded packages — our own app, the system UI, the launcher — are invisible
        // here rather than interrupting: swiping the notification shade is not leaving
        // the work, and the same packages are already absent from screen time.
        val daySessions = sessions
            .filterNot { it.packageName in excludedPackages }
            .filter { it.startMs < dayEnd && it.endMs > dayStart }
            .sortedBy { it.startMs }

        val runs = ArrayList<Interval>()
        var current = ArrayList<Interval>()
        var currentMs = 0L
        var runEnd = 0L

        // A run only counts once it clears the threshold, and it contributes only the
        // intervals actually spent in an app — the tolerance gap is a pause, not focus.
        fun flush() {
            if (currentMs >= MIN_RUN_MS) runs += current
            current = ArrayList()
            currentMs = 0L
            runEnd = 0L
        }

        for (session in daySessions) {
            val from = maxOf(session.startMs, dayStart)
            val to = minOf(session.endMs, dayEnd)
            if (to <= from) continue

            // Anything that is not producing ends the run outright — a scroll through a
            // social app is not a pause in the work, it is the end of that stretch.
            if (kindByPackage[session.packageName] != CategoryKind.PRODUCTION) {
                flush()
                continue
            }

            if (currentMs > 0L && from - runEnd > MAX_GAP_MS) flush()

            // Overlapping sessions (a resumed event that never paused) continue the run
            // rather than double-counting: only time past runEnd is added.
            val start = maxOf(from, runEnd)
            if (to > start) {
                current += Interval(start, to)
                currentMs += to - start
                runEnd = to
            }
        }
        flush()

        return runs
    }

    /**
     * Completed timer sessions on [epochDay], clipped at the day's edges.
     *
     * Cancelled sessions are deliberately absent. Ending early writes the row down as a
     * record, but the app's own contract is "recorded, but not counted", and a day's
     * focus figure has to agree with the Focus screen's list.
     */
    fun manual(
        sessions: List<FocusSessionEntity>,
        epochDay: Long,
        zone: ZoneId = DayWindow.zone,
    ): List<Interval> {
        val dayStart = DayWindow.startOfDayMs(epochDay, zone)
        val dayEnd = DayWindow.endOfDayMs(epochDay, zone)

        return sessions.mapNotNull { session ->
            val end = session.endMs ?: return@mapNotNull null
            if (!session.completed) return@mapNotNull null

            val from = maxOf(session.startMs, dayStart)
            val to = minOf(end, dayEnd)
            if (to <= from) null else Interval(from, to)
        }
    }

    /**
     * Total time covered by [intervals], merging overlaps.
     *
     * A timer running inside a detected stretch is one stretch of focus, not two, so the
     * union is what goes in the day's row.
     */
    fun unionMs(intervals: List<Interval>): Long {
        if (intervals.isEmpty()) return 0L

        val sorted = intervals.sortedBy { it.startMs }
        var total = 0L
        var covered = sorted.first().startMs

        for (interval in sorted) {
            val from = maxOf(interval.startMs, covered)
            if (interval.endMs > from) {
                total += interval.endMs - from
                covered = interval.endMs
            }
        }

        return total
    }
}
