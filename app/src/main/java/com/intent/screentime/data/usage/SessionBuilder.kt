package com.intent.screentime.data.usage

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.UsageEventEntity
import java.time.ZoneId

/**
 * Turns the raw event stream into foreground sessions by pairing
 * ACTIVITY_RESUMED with the next event that ends the session.
 *
 * This is where screen-time correctness is won or lost, so it is:
 *  - pure (no Android, no I/O) and therefore fully unit-testable
 *  - driven by a "single foreground app" model, because only one app is meaningfully
 *    in front of the user at a time and that is what screen time means
 *  - self-healing: an unmatched RESUMED is closed by the next app's RESUMED, so
 *    missing events degrade gracefully instead of corrupting totals
 */
object SessionBuilder {

    fun build(
        events: List<UsageEventEntity>,
        windowStartMs: Long,
        nowMs: Long,
        excludedPackages: Set<String> = emptySet(),
        zone: ZoneId = DayWindow.zone,
    ): List<AppSessionEntity> {
        if (events.isEmpty()) return emptyList()

        val ordered = events.sortedBy { it.timestampMs }
        val sessions = ArrayList<AppSessionEntity>()

        var openPackage: String? = null
        var openStartMs: Long = 0L

        fun close(atMs: Long) {
            val packageName = openPackage ?: return
            val end = atMs.coerceAtLeast(openStartMs)
            if (end > openStartMs) {
                sessions += AppSessionEntity(
                    packageName = packageName,
                    startMs = openStartMs,
                    endMs = end,
                    durationMs = end - openStartMs,
                    dayEpochDay = DayWindow.epochDayOf(openStartMs, zone),
                )
            }
            openPackage = null
        }

        for (event in ordered) {
            when (event.eventType) {
                UsageEventTypes.ACTIVITY_RESUMED -> {
                    // Ignore a redundant resume for the app already in front.
                    if (openPackage != event.packageName) {
                        close(event.timestampMs)
                        openPackage = event.packageName
                        openStartMs = event.timestampMs
                    }
                }

                UsageEventTypes.ACTIVITY_PAUSED -> {
                    if (openPackage == event.packageName) close(event.timestampMs)
                }

                in UsageEventTypes.SESSION_CLOSING -> close(event.timestampMs)
            }
        }

        // Still in the foreground at harvest time.
        close(nowMs)

        // Filtering at the end (not during the loop) matters: switching to an excluded
        // app such as the launcher must still close the previous app's session.
        return if (excludedPackages.isEmpty()) {
            sessions
        } else {
            sessions.filterNot { it.packageName in excludedPackages }
        }
    }
}
