package com.intent.screentime.data.usage

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import java.time.ZoneId

/**
 * Folds sessions into the per-app and per-day tables that the UI reads.
 *
 * Pure and side-effect free so the midnight-clipping behaviour can be unit tested
 * without a database.
 */
object DailyAggregator {

    private class Accumulator {
        var totalMs: Long = 0
        var sessionCount: Int = 0
        var firstUseMs: Long? = null
        var lastUseMs: Long? = null
    }

    /**
     * Per-app totals for a single day.
     *
     * A session that crosses midnight contributes only the portion inside [epochDay],
     * which is why two adjacent days will not double-count an overnight session.
     */
    fun appUsageForDay(
        sessions: List<AppSessionEntity>,
        epochDay: Long,
        excludedPackages: Set<String> = emptySet(),
        zone: ZoneId = DayWindow.zone,
    ): List<DailyAppUsageEntity> {
        val dayStart = DayWindow.startOfDayMs(epochDay, zone)
        val dayEnd = DayWindow.endOfDayMs(epochDay, zone)

        val byPackage = LinkedHashMap<String, Accumulator>()
        for (session in sessions) {
            if (session.packageName in excludedPackages) continue
            val overlap = DayWindow.overlapMs(session.startMs, session.endMs, epochDay, zone)
            if (overlap <= 0L) continue

            val acc = byPackage.getOrPut(session.packageName) { Accumulator() }
            acc.totalMs += overlap
            acc.sessionCount += 1

            val from = maxOf(session.startMs, dayStart)
            val to = minOf(session.endMs, dayEnd)
            if (acc.firstUseMs == null || from < acc.firstUseMs!!) acc.firstUseMs = from
            if (acc.lastUseMs == null || to > acc.lastUseMs!!) acc.lastUseMs = to
        }

        return byPackage
            .map { (packageName, acc) ->
                DailyAppUsageEntity(
                    dayEpochDay = epochDay,
                    packageName = packageName,
                    totalMs = acc.totalMs,
                    sessionCount = acc.sessionCount,
                    firstUseMs = acc.firstUseMs,
                    lastUseMs = acc.lastUseMs,
                )
            }
            .sortedByDescending { it.totalMs }
    }

    /**
     * Rolls a day up into the single row behind the Today screen.
     *
     * Total screen time is the sum of app time rather than screen-on time, so it
     * matches what the user sees in Digital Wellbeing. UTILITY and NEUTRAL time is
     * counted in screen time but in neither side of the production/consumption split.
     *
     * [focusMs] is the union of timer sessions and detected stretches; [autoFocusMs] is
     * how much of it the detector contributed, and is carried so the UI can attribute it
     * rather than showing focus time the user cannot account for.
     */
    fun summarize(
        epochDay: Long,
        appUsage: List<DailyAppUsageEntity>,
        kindByPackage: Map<String, CategoryKind>,
        unlockCount: Int,
        focusMs: Long,
        autoFocusMs: Long = 0L,
    ): DailySummaryEntity {
        var productionMs = 0L
        var consumptionMs = 0L

        for (row in appUsage) {
            when (kindByPackage[row.packageName] ?: CategoryKind.NEUTRAL) {
                CategoryKind.PRODUCTION -> productionMs += row.totalMs
                CategoryKind.CONSUMPTION -> consumptionMs += row.totalMs
                CategoryKind.UTILITY, CategoryKind.NEUTRAL -> Unit
            }
        }

        return DailySummaryEntity(
            dayEpochDay = epochDay,
            screenTimeMs = appUsage.sumOf { it.totalMs },
            unlockCount = unlockCount,
            productionMs = productionMs,
            consumptionMs = consumptionMs,
            focusMs = focusMs,
            autoFocusMs = autoFocusMs,
            topPackage = appUsage.maxByOrNull { it.totalMs }?.packageName,
        )
    }
}
