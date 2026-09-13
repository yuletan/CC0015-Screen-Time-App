package com.intent.screentime.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Thin wrapper over [UsageStatsManager].
 *
 * Deliberately does **not** use `queryUsageStats(INTERVAL_DAILY, …)`: its docs say the
 * requested range "may be expanded to the nearest whole interval period", so asking for
 * "today" returns a bucket that does not line up with midnight. That is the root cause
 * of the wrong daily totals reported by many screen-time apps. We read raw events and
 * build sessions ourselves instead.
 */
class UsageStatsDataSource(private val context: Context) {

    private val manager: UsageStatsManager? =
        context.getSystemService(UsageStatsManager::class.java)

    init {
        // Guards the pure-Kotlin mirror in UsageEventTypes against ever drifting from
        // the framework values. A silent mismatch here would corrupt all session maths.
        check(UsageEventTypes.ACTIVITY_RESUMED == UsageEvents.Event.ACTIVITY_RESUMED)
        check(UsageEventTypes.ACTIVITY_PAUSED == UsageEvents.Event.ACTIVITY_PAUSED)
        check(UsageEventTypes.SCREEN_INTERACTIVE == UsageEvents.Event.SCREEN_INTERACTIVE)
        check(UsageEventTypes.SCREEN_NON_INTERACTIVE == UsageEvents.Event.SCREEN_NON_INTERACTIVE)
        check(UsageEventTypes.KEYGUARD_SHOWN == UsageEvents.Event.KEYGUARD_SHOWN)
        check(UsageEventTypes.KEYGUARD_HIDDEN == UsageEvents.Event.KEYGUARD_HIDDEN)
        check(UsageEventTypes.DEVICE_SHUTDOWN == UsageEvents.Event.DEVICE_SHUTDOWN)
        check(UsageEventTypes.DEVICE_STARTUP == UsageEvents.Event.DEVICE_STARTUP)
    }

    data class RawEvent(
        val packageName: String,
        val eventType: Int,
        val timestampMs: Long,
        val className: String?,
    )

    /**
     * Raw events in `[startMs, endMs)`, or `null` if the data could not be read.
     *
     * The distinction matters: from Android R onwards this API returns null while the
     * device is locked, and an empty list would be indistinguishable from genuinely
     * having no events. Callers must **not** advance their watermark on null, otherwise
     * the locked window would be skipped permanently.
     */
    fun eventsBetween(startMs: Long, endMs: Long): List<RawEvent>? {
        val events = manager?.queryEvents(startMs, endMs) ?: return null
        val out = ArrayList<RawEvent>()
        val cursor = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(cursor)
            out += RawEvent(
                packageName = cursor.packageName,
                eventType = cursor.eventType,
                timestampMs = cursor.timeStamp,
                className = cursor.className,
            )
        }
        return out
    }

    /** Aggregated counts per event type, straight from the OS. */
    fun eventCounts(startMs: Long, endMs: Long): Map<Int, Int> {
        val stats = manager?.queryEventStats(
            UsageStatsManager.INTERVAL_DAILY,
            startMs,
            endMs,
        ) ?: return emptyMap()

        val totals = HashMap<Int, Int>()
        for (stat in stats) {
            totals[stat.eventType] = (totals[stat.eventType] ?: 0) + stat.count
        }
        return totals
    }

    /** Number of times the device was unlocked in the given range. */
    fun unlockCount(startMs: Long, endMs: Long): Int =
        eventCounts(startMs, endMs)[UsageEventTypes.KEYGUARD_HIDDEN] ?: 0

    /** Screen-on milliseconds, derived from the alternating interactive events. */
    fun screenOnMs(startMs: Long, endMs: Long): Long {
        val events = eventsBetween(startMs, endMs)
            ?.filter {
                it.eventType == UsageEventTypes.SCREEN_INTERACTIVE ||
                    it.eventType == UsageEventTypes.SCREEN_NON_INTERACTIVE
            }
            ?.sortedBy { it.timestampMs }
            ?: return 0L

        var total = 0L
        var onSince: Long? = null
        for (event in events) {
            when (event.eventType) {
                UsageEventTypes.SCREEN_INTERACTIVE -> if (onSince == null) onSince = event.timestampMs
                UsageEventTypes.SCREEN_NON_INTERACTIVE -> {
                    onSince?.let { total += (event.timestampMs - it).coerceAtLeast(0L) }
                    onSince = null
                }
            }
        }
        // Screen still on at the end of the range.
        onSince?.let { total += (endMs - it).coerceAtLeast(0L) }
        return total
    }

    fun isAvailable(): Boolean = manager != null
}
