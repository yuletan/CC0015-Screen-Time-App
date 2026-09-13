package com.intent.screentime.data.focus

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.FocusSessionEntity
import java.time.ZoneId

/**
 * Derived focus figures the Focus screen reads.
 *
 * Pure and separate from the manager, because "which day does this session belong to"
 * and "does today's unfinished day break the streak" are the same midnight-boundary
 * questions the rest of the app has to get right, and they are testable here.
 */
object FocusStats {

    /** Focus time from completed sessions on [epochDay], clipped at the day's edges. */
    fun completedMsOn(
        sessions: List<FocusSessionEntity>,
        epochDay: Long,
        zone: ZoneId = DayWindow.zone,
    ): Long = sessions.sumOf { session ->
        val end = session.endMs ?: return@sumOf 0L
        if (!session.completed) return@sumOf 0L
        DayWindow.overlapMs(session.startMs, end, epochDay, zone)
    }

    /**
     * Consecutive days ending today (or yesterday, if today has not had a session yet)
     * with at least one completed session.
     */
    fun streak(
        sessions: List<FocusSessionEntity>,
        todayEpochDay: Long,
        zone: ZoneId = DayWindow.zone,
    ): Int {
        val days = sessions
            .filter { it.completed }
            .mapTo(HashSet()) { DayWindow.epochDayOf(it.startMs, zone) }

        var streak = if (todayEpochDay in days) 1 else 0
        var day = todayEpochDay - 1

        while (day in days) {
            streak += 1
            day -= 1
        }

        return streak
    }
}
