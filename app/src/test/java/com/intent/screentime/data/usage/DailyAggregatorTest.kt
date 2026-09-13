package com.intent.screentime.data.usage

import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DailyAggregatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val day = LocalDate.parse("2026-03-10").toEpochDay()

    private fun at(day: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun session(pkg: String, startMs: Long, endMs: Long) = AppSessionEntity(
        packageName = pkg,
        startMs = startMs,
        endMs = endMs,
        durationMs = endMs - startMs,
        dayEpochDay = LocalDate.parse("1970-01-01").toEpochDay() + (startMs / 86_400_000L),
    )

    @Test
    fun `sums a day's sessions per app`() {
        val sessions = listOf(
            session("app.a", at("2026-03-10", 9), at("2026-03-10", 9, 20)),
            session("app.a", at("2026-03-10", 14), at("2026-03-10", 14, 10)),
            session("app.b", at("2026-03-10", 12), at("2026-03-10", 12, 5)),
        )

        val usage = DailyAggregator.appUsageForDay(sessions, day, zone = zone)

        assertEquals(2, usage.size)
        val appA = usage.first { it.packageName == "app.a" }
        assertEquals(30 * 60_000L, appA.totalMs)
        assertEquals(2, appA.sessionCount)
        assertEquals(5 * 60_000L, usage.first { it.packageName == "app.b" }.totalMs)
    }

    @Test
    fun `session straddling midnight is split across both days without double counting`() {
        val overnight = session(
            "app.a",
            at("2026-03-10", 23, 40),
            at("2026-03-11", 0, 30),
        )

        val day10 = LocalDate.parse("2026-03-10").toEpochDay()
        val day11 = LocalDate.parse("2026-03-11").toEpochDay()

        val usage10 = DailyAggregator.appUsageForDay(listOf(overnight), day10, zone = zone)
        val usage11 = DailyAggregator.appUsageForDay(listOf(overnight), day11, zone = zone)

        // 20 minutes before midnight, 30 minutes after.
        assertEquals(20 * 60_000L, usage10.single().totalMs)
        assertEquals(30 * 60_000L, usage11.single().totalMs)
        assertEquals(overnight.durationMs, usage10.single().totalMs + usage11.single().totalMs)
    }

    @Test
    fun `a session on another day contributes nothing`() {
        val sessions = listOf(session("app.a", at("2026-03-09", 9), at("2026-03-09", 10)))
        val usage = DailyAggregator.appUsageForDay(sessions, day, zone = zone)
        assertEquals(0, usage.size)
    }

    @Test
    fun `excluded packages are left out`() {
        val sessions = listOf(
            session("app.a", at("2026-03-10", 9), at("2026-03-10", 9, 20)),
            session("com.android.systemui", at("2026-03-10", 10), at("2026-03-10", 10, 20)),
        )

        val usage = DailyAggregator.appUsageForDay(
            sessions = sessions,
            epochDay = day,
            excludedPackages = setOf("com.android.systemui"),
            zone = zone,
        )

        assertEquals(1, usage.size)
        assertEquals("app.a", usage.single().packageName)
    }

    @Test
    fun `summary splits production from consumption`() {
        val sessions = listOf(
            session("app.work", at("2026-03-10", 9), at("2026-03-10", 9, 30)),
            session("app.social", at("2026-03-10", 10), at("2026-03-10", 11)),
        )
        val usage = DailyAggregator.appUsageForDay(sessions, day, zone = zone)

        val summary = DailyAggregator.summarize(
            epochDay = day,
            appUsage = usage,
            kindByPackage = mapOf(
                "app.work" to CategoryKind.PRODUCTION,
                "app.social" to CategoryKind.CONSUMPTION,
            ),
            unlockCount = 12,
            focusMs = 0L,
        )

        assertEquals(30 * 60_000L, summary.productionMs)
        assertEquals(60 * 60_000L, summary.consumptionMs)
        assertEquals(90 * 60_000L, summary.screenTimeMs)
        assertEquals(12, summary.unlockCount)
        assertEquals("app.social", summary.topPackage)
    }

    @Test
    fun `utility time counts as screen time but in neither side of the split`() {
        val sessions = listOf(session("app.maps", at("2026-03-10", 9), at("2026-03-10", 9, 20)))
        val usage = DailyAggregator.appUsageForDay(sessions, day, zone = zone)

        val summary = DailyAggregator.summarize(
            epochDay = day,
            appUsage = usage,
            kindByPackage = mapOf("app.maps" to CategoryKind.UTILITY),
            unlockCount = 0,
            focusMs = 0L,
        )

        assertEquals(20 * 60_000L, summary.screenTimeMs)
        assertEquals(0L, summary.productionMs)
        assertEquals(0L, summary.consumptionMs)
    }

    @Test
    fun `unknown packages are treated as neutral`() {
        val sessions = listOf(session("app.mystery", at("2026-03-10", 9), at("2026-03-10", 9, 5)))
        val usage = DailyAggregator.appUsageForDay(sessions, day, zone = zone)

        val summary = DailyAggregator.summarize(
            epochDay = day,
            appUsage = usage,
            kindByPackage = emptyMap(),
            unlockCount = 0,
            focusMs = 0L,
        )

        assertEquals(5 * 60_000L, summary.screenTimeMs)
        assertEquals(0L, summary.productionMs)
        assertEquals(0L, summary.consumptionMs)
    }

    @Test
    fun `empty day has no top package`() {
        val summary = DailyAggregator.summarize(
            epochDay = day,
            appUsage = emptyList(),
            kindByPackage = emptyMap(),
            unlockCount = 0,
            focusMs = 0L,
        )

        assertEquals(0L, summary.screenTimeMs)
        assertNull(summary.topPackage)
    }
}
