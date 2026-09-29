package com.intent.screentime.data.usage

import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HourlyBreakdownRangeTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val day10 = LocalDate.parse("2026-03-10").toEpochDay()
    private val day11 = LocalDate.parse("2026-03-11").toEpochDay()

    private fun at(date: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun session(pkg: String, date: String, startMs: Long, endMs: Long) = AppSessionEntity(
        packageName = pkg,
        startMs = startMs,
        endMs = endMs,
        durationMs = endMs - startMs,
        dayEpochDay = LocalDate.parse(date).toEpochDay(),
    )

    @Test
    fun `the same hour on different days lands in one bucket`() {
        val sessions = listOf(
            session("app.a", "2026-03-10", at("2026-03-10", 9), at("2026-03-10", 9, 30)),
            session("app.a", "2026-03-11", at("2026-03-11", 9), at("2026-03-11", 9, 15)),
        )

        val buckets = HourlyBreakdown.ofRange(sessions, day10, day11, zone = zone)

        assertEquals(45 * 60_000L, buckets[9].totalMs)
    }

    @Test
    fun `an overnight session contributes to both days' hours`() {
        val overnight = session("app.a", "2026-03-10", at("2026-03-10", 23), at("2026-03-11", 1))

        val buckets = HourlyBreakdown.ofRange(listOf(overnight), day10, day11, zone = zone)

        // An hour before midnight, an hour after; nothing lands in the 01:00 slot.
        assertEquals(60 * 60_000L, buckets[23].totalMs)
        assertEquals(60 * 60_000L, buckets[0].totalMs)
        assertEquals(0L, buckets[1].totalMs)
    }

    @Test
    fun `time outside the range is left out`() {
        val sessions = listOf(
            session("app.a", "2026-03-09", at("2026-03-09", 9), at("2026-03-09", 10)),
            session("app.a", "2026-03-10", at("2026-03-10", 9), at("2026-03-10", 9, 30)),
            session("app.a", "2026-03-12", at("2026-03-12", 9), at("2026-03-12", 10)),
        )

        val buckets = HourlyBreakdown.ofRange(sessions, day10, day11, zone = zone)

        assertEquals(30 * 60_000L, buckets[9].totalMs)
        assertEquals(30 * 60_000L, buckets.sumOf { it.totalMs })
    }

    @Test
    fun `each hour is coloured by the kind that dominated it`() {
        val sessions = listOf(
            session("work", "2026-03-10", at("2026-03-10", 9), at("2026-03-10", 9, 40)),
            session("social", "2026-03-10", at("2026-03-10", 9, 40), at("2026-03-10", 10, 30)),
        )
        val kinds = mapOf(
            "work" to CategoryKind.PRODUCTION,
            "social" to CategoryKind.CONSUMPTION,
        )

        val buckets = HourlyBreakdown.ofRange(sessions, day10, day10, kinds, zone)

        // 09:00 is 40 minutes of work against 20 of social; 10:00 is social's on its own.
        assertEquals(CategoryKind.PRODUCTION, buckets[9].dominantKind)
        assertEquals(CategoryKind.CONSUMPTION, buckets[10].dominantKind)
    }
}
