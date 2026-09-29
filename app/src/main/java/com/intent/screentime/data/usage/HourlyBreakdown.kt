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
        /** Which kind dominated this hour, used to name the hour in the read-out. */
        val dominantKind: CategoryKind?,
        /** Which app took the most of this hour, for the read-out under the value. */
        val dominantPackage: String? = null,
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
        val packageTotals = Array(HOURS) { HashMap<String, Long>() }

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
                packageTotals[hour][session.packageName] =
                    (packageTotals[hour][session.packageName] ?: 0L) + slice
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
                dominantPackage = packageTotals[hour].maxByOrNull { it.value }?.key,
            )
        }
    }

    /**
     * The same 24 slots summed over a range of days.
     *
     * "When do I use this" over a month is a question about hours of the day rather than
     * about dates, so every day's minutes land in the same twenty-four buckets. Each day
     * is clipped separately, which is what makes an overnight session contribute to the
     * right hour on both sides of midnight.
     */
    fun ofRange(
        sessions: List<AppSessionEntity>,
        fromDay: Long,
        toDay: Long,
        kindByPackage: Map<String, CategoryKind> = emptyMap(),
        zone: ZoneId = DayWindow.zone,
    ): List<Bucket> {
        val rangeStart = DayWindow.startOfDayMs(fromDay, zone)
        val rangeEnd = DayWindow.endOfDayMs(toDay, zone)
        val totals = LongArray(HOURS)
        val kindTotals = Array(HOURS) { HashMap<CategoryKind, Long>() }
        val packageTotals = Array(HOURS) { HashMap<String, Long>() }

        for (session in sessions) {
            val clippedStart = maxOf(session.startMs, rangeStart)
            val clippedEnd = minOf(session.endMs, rangeEnd)
            if (clippedEnd <= clippedStart) continue

            val kind = kindByPackage[session.packageName]
            var day = DayWindow.epochDayOf(clippedStart, zone)
            val lastDay = DayWindow.epochDayOf(clippedEnd - 1, zone)

            while (day <= lastDay) {
                val dayStart = DayWindow.startOfDayMs(day, zone)
                val from = maxOf(clippedStart, dayStart)
                val to = minOf(clippedEnd, DayWindow.endOfDayMs(day, zone))
                if (to > from) {
                    addSlice(
                        from = from,
                        to = to,
                        dayStart = dayStart,
                        packageName = session.packageName,
                        kind = kind,
                        totals = totals,
                        kindTotals = kindTotals,
                        packageTotals = packageTotals,
                    )
                }
                day += 1
            }
        }

        return (0 until HOURS).map { hour ->
            Bucket(
                hour = hour,
                totalMs = totals[hour],
                dominantKind = kindTotals[hour].maxByOrNull { it.value }?.key,
                dominantPackage = packageTotals[hour].maxByOrNull { it.value }?.key,
            )
        }
    }

    private fun addSlice(
        from: Long,
        to: Long,
        dayStart: Long,
        packageName: String,
        kind: CategoryKind?,
        totals: LongArray,
        kindTotals: Array<HashMap<CategoryKind, Long>>,
        packageTotals: Array<HashMap<String, Long>>,
    ) {
        val firstHour = hourIndexOf(from, dayStart).coerceIn(0, HOURS - 1)
        val lastHour = hourIndexOf(to - 1, dayStart).coerceIn(0, HOURS - 1)

        for (hour in firstHour..lastHour) {
            val hourStart = dayStart + hour * HOUR_MS
            val slice = minOf(to, hourStart + HOUR_MS) - maxOf(from, hourStart)
            if (slice <= 0L) continue

            totals[hour] += slice
            packageTotals[hour][packageName] = (packageTotals[hour][packageName] ?: 0L) + slice
            kind?.let { kindTotals[hour][it] = (kindTotals[hour][it] ?: 0L) + slice }
        }
    }

    private fun hourIndexOf(timestampMs: Long, dayStartMs: Long): Int =
        Math.floorDiv(timestampMs - dayStartMs, HOUR_MS).toInt()
}
