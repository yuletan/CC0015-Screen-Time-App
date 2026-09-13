package com.intent.screentime.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * All day-boundary maths lives here.
 *
 * Screen-time totals only look right if a session that runs past midnight is split
 * between the two days, so every aggregation path goes through [overlapMs].
 *
 * The zone is cached rather than re-read per call, because aggregation loops over
 * hundreds of sessions. [refreshZone] exists for the rare case of the device
 * changing timezone while the app is running.
 */
object DayWindow {
    @Volatile
    var zone: ZoneId = ZoneId.systemDefault()
        private set

    fun refreshZone() {
        zone = ZoneId.systemDefault()
    }

    fun epochDayOf(timestampMs: Long, zone: ZoneId = this.zone): Long =
        Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate().toEpochDay()

    fun startOfDayMs(epochDay: Long, zone: ZoneId = this.zone): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayMs(epochDay: Long, zone: ZoneId = this.zone): Long =
        LocalDate.ofEpochDay(epochDay + 1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun todayEpochDay(zone: ZoneId = this.zone): Long = LocalDate.now(zone).toEpochDay()

    /**
     * Milliseconds of `[startMs, endMs)` that fall inside [epochDay].
     * Returns 0 when the span and the day do not intersect at all.
     */
    fun overlapMs(
        startMs: Long,
        endMs: Long,
        epochDay: Long,
        zone: ZoneId = this.zone,
    ): Long {
        val dayStart = startOfDayMs(epochDay, zone)
        val dayEnd = endOfDayMs(epochDay, zone)
        val from = maxOf(startMs, dayStart)
        val to = minOf(endMs, dayEnd)
        return (to - from).coerceAtLeast(0L)
    }

    /** Every epoch day touched by the span, inclusive. */
    fun daysSpanned(startMs: Long, endMs: Long, zone: ZoneId = this.zone): LongRange =
        epochDayOf(startMs, zone)..epochDayOf(maxOf(startMs, endMs - 1), zone)
}
