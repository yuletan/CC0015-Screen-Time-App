package com.intent.screentime.data.goals

import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetMonitorTest {

    private val minute = 60_000L
    private val day = 20_000L

    private fun summary(screenTimeMs: Long) = DailySummaryEntity(
        dayEpochDay = day,
        screenTimeMs = screenTimeMs,
        unlockCount = 0,
        productionMs = 0L,
        consumptionMs = 0L,
        focusMs = 0L,
        topPackage = null,
    )

    private fun usage(packageName: String, totalMs: Long) = DailyAppUsageEntity(
        dayEpochDay = day,
        packageName = packageName,
        totalMs = totalMs,
        sessionCount = 1,
        firstUseMs = null,
        lastUseMs = null,
    )

    @Test
    fun `a daily cap that is passed raises exactly one alert`() {
        val alerts = TargetMonitor.alertsFor(
            epochDay = day,
            summary = summary(screenTimeMs = 270 * minute),
            appUsage = emptyList(),
            targets = listOf(
                TargetEntity(type = TargetType.DAILY_SCREEN_TIME_CAP, valueMinutes = 240),
            ),
            alreadySent = emptySet(),
        )

        assertEquals(1, alerts.size)
        assertEquals("daily:$day", alerts.single().token)
    }

    @Test
    fun `a cap that has already been called out today stays quiet`() {
        val alerts = TargetMonitor.alertsFor(
            epochDay = day,
            summary = summary(screenTimeMs = 5 * 60 * minute),
            appUsage = emptyList(),
            targets = listOf(
                TargetEntity(type = TargetType.DAILY_SCREEN_TIME_CAP, valueMinutes = 240),
            ),
            alreadySent = setOf("daily:$day"),
        )

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `being under the cap raises nothing`() {
        val alerts = TargetMonitor.alertsFor(
            epochDay = day,
            summary = summary(screenTimeMs = 100 * minute),
            appUsage = emptyList(),
            targets = listOf(
                TargetEntity(type = TargetType.DAILY_SCREEN_TIME_CAP, valueMinutes = 240),
            ),
            alreadySent = emptySet(),
        )

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `a per-app cap that is passed raises an alert scoped to that app`() {
        val alerts = TargetMonitor.alertsFor(
            epochDay = day,
            summary = summary(screenTimeMs = 60 * minute),
            appUsage = listOf(usage("app.social", 52 * minute)),
            targets = listOf(
                TargetEntity(
                    type = TargetType.PER_APP_DAILY_CAP,
                    scopePackage = "app.social",
                    valueMinutes = 45,
                ),
            ),
            alreadySent = emptySet(),
        )

        assertEquals(1, alerts.size)
        assertEquals("app:$day:app.social", alerts.single().token)
    }

    @Test
    fun `a per-app cap under its limit raises nothing`() {
        val alerts = TargetMonitor.alertsFor(
            epochDay = day,
            summary = summary(screenTimeMs = 60 * minute),
            appUsage = listOf(usage("app.social", 30 * minute)),
            targets = listOf(
                TargetEntity(
                    type = TargetType.PER_APP_DAILY_CAP,
                    scopePackage = "app.social",
                    valueMinutes = 45,
                ),
            ),
            alreadySent = emptySet(),
        )

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `a disabled target is ignored`() {
        val alerts = TargetMonitor.alertsFor(
            epochDay = day,
            summary = summary(screenTimeMs = 10 * 60 * minute),
            appUsage = emptyList(),
            targets = listOf(
                TargetEntity(
                    type = TargetType.DAILY_SCREEN_TIME_CAP,
                    valueMinutes = 240,
                    enabled = false,
                ),
            ),
            alreadySent = emptySet(),
        )

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `goals that are behind never alert - only caps do`() {
        val alerts = TargetMonitor.alertsFor(
            epochDay = day,
            summary = summary(screenTimeMs = 10 * minute),
            appUsage = emptyList(),
            targets = listOf(
                TargetEntity(type = TargetType.WEEKLY_PRODUCTION_GOAL, valueMinutes = 600),
                TargetEntity(type = TargetType.DAILY_FOCUS_GOAL, valueMinutes = 60),
            ),
            alreadySent = emptySet(),
        )

        assertTrue(alerts.isEmpty())
    }
}
