package com.intent.screentime.data.usage

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.AppSessionEntity
import java.time.ZoneId

private const val MINUTES_PER_DAY = 24 * 60

/**
 * A night's quiet window, as minutes past midnight.
 *
 * Stored rather than derived, because 23:00–07:00 and 00:00–07:00 are the same eight hours
 * shifted by one, and only the user knows which one they meant.
 */
data class BedtimeWindow(
    val startMinutesOfDay: Int,
    val endMinutesOfDay: Int,
) {
    /**
     * How long the window runs, wrapping past midnight when it has to: a bedtime of 23:00
     * ending at 07:00 is eight hours, and a 01:00 to 07:00 night is six.
     */
    val durationMinutes: Int
        get() = (endMinutesOfDay - startMinutesOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY

    /** A window with no length is not a commitment. */
    val isSet: Boolean get() = durationMinutes > 0
}

/**
 * Whether a night kept its quiet window, from the sessions already on disk.
 *
 * Two choices worth stating plainly:
 *
 *  - **A night belongs to the evening it starts on.** A 23:00–07:00 window is judged with
 *    the day it opened, so a 23:40 scroll is that evening's rather than the following
 *    morning's. Anything else would let the same minutes belong to whichever day read
 *    better.
 *  - **The window is a millisecond offset from the day's midnight**, exactly as
 *    [HourlyBreakdown] treats an hour, so the two views of a night cannot disagree. On a
 *    daylight-saving transition the window sits an hour off the wall clock, which is the
 *    honest representation of that day rather than a bug to paper over.
 *
 * Pure and zone-injectable, so every rule here is unit tested rather than discovered on a
 * phone at midnight.
 */
object Bedtime {

    /** No more than this much screen time inside the window still counts as a kept night. */
    const val GRACE_MS = 5 * 60_000L

    enum class Status {
        /** The window has not opened yet. A night that has not begun cannot be broken. */
        PENDING,
        HELD,
        MISSED,
    }

    /** The window that opens on [epochDay], as absolute millisecond bounds. */
    fun windowMs(
        window: BedtimeWindow,
        epochDay: Long,
        zone: ZoneId = DayWindow.zone,
    ): Pair<Long, Long> {
        val dayStart = DayWindow.startOfDayMs(epochDay, zone)
        val from = dayStart + window.startMinutesOfDay * 60_000L
        return from to from + window.durationMinutes * 60_000L
    }

    /**
     * The part of [epochDay]'s window that has actually happened by [nowMs], or null when
     * the window has not opened.
     *
     * Judging only the elapsed part is what makes tonight's row live rather than pinned at
     * neutral until the morning: at 23:20 the app can already say the night is going well.
     */
    fun elapsedWindow(
        window: BedtimeWindow,
        epochDay: Long,
        nowMs: Long,
        zone: ZoneId = DayWindow.zone,
    ): Pair<Long, Long>? {
        val (from, to) = windowMs(window, epochDay, zone)
        if (nowMs <= from) return null
        return from to minOf(nowMs, to)
    }

    /**
     * Screen time inside a span.
     *
     * A union rather than a sum, because two overlapping sessions — a resumed event that
     * never paused, or two app activities both logging the foreground — are one stretch of
     * screen time, and adding them would fail a night that was fine.
     *
     * Our own app, the system UI and the launcher are excluded rather than counted as use,
     * matching the rule screen time itself follows: swiping the shade is not picking up
     * the phone.
     */
    fun usedMs(
        sessions: List<AppSessionEntity>,
        fromMs: Long,
        toMs: Long,
        excludedPackages: Set<String> = emptySet(),
    ): Long {
        if (toMs <= fromMs) return 0L

        val intervals = sessions
            .asSequence()
            .filterNot { it.packageName in excludedPackages }
            .filter { it.startMs < toMs && it.endMs > fromMs }
            .map { FocusStretches.Interval(maxOf(it.startMs, fromMs), minOf(it.endMs, toMs)) }
            .sortedBy { it.startMs }
            .toList()

        return FocusStretches.unionMs(intervals)
    }

    fun status(
        window: BedtimeWindow,
        epochDay: Long,
        sessions: List<AppSessionEntity>,
        nowMs: Long,
        excludedPackages: Set<String> = emptySet(),
        zone: ZoneId = DayWindow.zone,
    ): Status {
        val elapsed = elapsedWindow(window, epochDay, nowMs, zone) ?: return Status.PENDING
        val used = usedMs(sessions, elapsed.first, elapsed.second, excludedPackages)
        return if (used <= GRACE_MS) Status.HELD else Status.MISSED
    }
}
