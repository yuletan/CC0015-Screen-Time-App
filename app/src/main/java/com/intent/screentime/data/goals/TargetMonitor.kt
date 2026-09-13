package com.intent.screentime.data.goals

import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType

/**
 * A breach worth telling the user about. [token] identifies it for the day, so the same
 * breach is never announced twice.
 */
data class CapAlert(
    val token: String,
    val title: String,
    val body: String,
)

/**
 * Watches the caps and decides when one has been passed.
 *
 * Only upper limits alert. Goals that are *behind* are shown in the app but never
 * notified — chasing someone about productive minutes misses the point of a goal, and a
 * notification that arrives every afternoon stops being read.
 *
 * Pure, so the threshold behaviour is unit tested rather than discovered on a phone.
 */
object TargetMonitor {

    fun alertsFor(
        epochDay: Long,
        summary: DailySummaryEntity?,
        appUsage: List<DailyAppUsageEntity>,
        targets: List<TargetEntity>,
        alreadySent: Set<String>,
    ): List<CapAlert> {
        val alerts = ArrayList<CapAlert>()

        val dailyCap = targets.firstOrNull {
            it.enabled && it.type == TargetType.DAILY_SCREEN_TIME_CAP && it.valueMinutes > 0
        }
        if (dailyCap != null && summary != null) {
            val capMs = dailyCap.valueMinutes * 60_000L
            if (summary.screenTimeMs > capMs) {
                val token = "daily:$epochDay"
                if (token !in alreadySent) {
                    alerts += CapAlert(
                        token = token,
                        title = "Past your daily cap",
                        body = "${DurationFormat.compact(summary.screenTimeMs)} so far — " +
                            "${DurationFormat.compact(summary.screenTimeMs - capMs)} past the " +
                            "${DurationFormat.compact(capMs)} you set.",
                    )
                }
            }
        }

        for (target in targets) {
            if (!target.enabled || target.type != TargetType.PER_APP_DAILY_CAP) continue
            if (target.valueMinutes <= 0) continue
            val packageName = target.scopePackage ?: continue

            val row = appUsage.firstOrNull { it.packageName == packageName } ?: continue
            val capMs = target.valueMinutes * 60_000L
            if (row.totalMs <= capMs) continue

            val token = "app:$epochDay:$packageName"
            if (token in alreadySent) continue

            alerts += CapAlert(
                token = token,
                title = "App cap passed",
                body = "${packageName.substringAfterLast('.')} is at " +
                    "${DurationFormat.compact(row.totalMs)} — " +
                    "${DurationFormat.compact(row.totalMs - capMs)} past its " +
                    "${DurationFormat.compact(capMs)} cap.",
            )
        }

        return alerts
    }
}
