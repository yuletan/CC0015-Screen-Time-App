package com.intent.screentime.data.usage

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.CategoryKind
import java.time.ZoneId

/**
 * Splits a day's sessions into 24 hourly buckets for the timeline strip.
 *
 * This is done here rather than in SQL because a single session can straddle several
 * hours, and the strip is the one place the *shape* of the day is visible: a morning
 * block of deep work reads completely differently from the same total spread across ten
 * evening pickups.
 *
 * Bucket boundaries are pure millisecond offsets from the start of the day. That is
 * deliberate — [DayWindow.startOfDayMs] is an instant, so offset arithmetic stays
 * self-consistent. On a daylight-saving transition a bucket may represent slightly more
 * or less than a wall-clock hour, which is the honest representation of that day.
 */
object HourlyBreakdown {

    const val HOURS = 24
    private const val HOUR_MS = 3_600_000L

    data class Bucket(
        val hour: Int,
        val totalMs: Long,
        /** Which kind dominated this hour, used to colour the bar. */
        val dominantKind: CategoryKind?,
    )

    fun of(
        sessions: List<AppSessionEntity>,
        epochDay: Long,
        kindByPackage: Map<String, CategoryKind> = emptyMap(),
        zone: ZoneId = DayWindow.zone,
    ): List<Bucket> {
        val dayStart = DayWindow.startOfDayMs(epochDay, zone)
        val totals = LongArray(HOURS)
        val kindTotals = Array(HOURS) { HashMap<CategoryKind, Long>() }

        for (session in sessions) {
            val firstHour = hourIndexOf(session.startMs, dayStart)
            val lastHour = hourIndexOf(session.endMs - 1, dayStart)
            if (lastHour < 0 || firstHour > HOURS - 1) continue

            for (hour in maxOf(0, firstHour)..minOf(HOURS - 1, lastHour)) {
                val hourStart = dayStart + hour * HOUR_MS
                val from = maxOf(session.startMs, hourStart)
                val to = minOf(session.endMs, hourStart + HOUR_MS)
                val slice = to - from
                if (slice <= 0L) continue

                totals[hour] += slice
                kindByPackage[session.packageName]?.let { kind ->
                    kindTotals[hour][kind] = (kindTotals[hour][kind] ?: 0L) + slice
                }
            }
        }

        return (0 until HOURS).map { hour ->
            Bucket(
                hour = hour,
                totalMs = totals[hour],
                dominantKind = kindTotals[hour].maxByOrNull { it.value }?.key,
            )
        }
    }

    private fun hourIndexOf(timestampMs: Long, dayStartMs: Long): Int =
        Math.floorDiv(timestampMs - dayStartMs, HOUR_MS).toInt()
}
