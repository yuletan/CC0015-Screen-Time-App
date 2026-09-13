package com.intent.screentime.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayWindowTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(day: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val day10 = LocalDate.parse("2026-03-10").toEpochDay()
    private val day11 = LocalDate.parse("2026-03-11").toEpochDay()

    @Test
    fun `overlap is the full span when it sits inside the day`() {
        val start = at("2026-03-10", 9)
        val end = at("2026-03-10", 10)
        assertEquals(60 * 60_000L, DayWindow.overlapMs(start, end, day10, zone))
    }

    @Test
    fun `overlap is zero for a span on another day`() {
        val start = at("2026-03-09", 9)
        val end = at("2026-03-09", 10)
        assertEquals(0L, DayWindow.overlapMs(start, end, day10, zone))
    }

    @Test
    fun `overlap clips a span running past midnight`() {
        val start = at("2026-03-10", 23, 30)
        val end = at("2026-03-11", 0, 45)
        assertEquals(30 * 60_000L, DayWindow.overlapMs(start, end, day10, zone))
        assertEquals(45 * 60_000L, DayWindow.overlapMs(start, end, day11, zone))
    }

    @Test
    fun `overlap clips a span that started the previous day`() {
        val start = at("2026-03-09", 22)
        val end = at("2026-03-10", 1)
        assertEquals(60 * 60_000L, DayWindow.overlapMs(start, end, day10, zone))
    }

    @Test
    fun `day boundaries are midnight to midnight`() {
        assertEquals(at("2026-03-10", 0), DayWindow.startOfDayMs(day10, zone))
        assertEquals(at("2026-03-11", 0), DayWindow.endOfDayMs(day10, zone))
    }

    @Test
    fun `daysSpanned covers every touched day`() {
        val start = at("2026-03-10", 23, 0)
        val end = at("2026-03-12", 1, 0)
        assertEquals(listOf(day10, day10 + 1, day10 + 2), DayWindow.daysSpanned(start, end, zone).toList())
    }

    @Test
    fun `daysSpanned of a zero length span is a single day`() {
        val instant = at("2026-03-10", 12)
        assertEquals(listOf(day10), DayWindow.daysSpanned(instant, instant, zone).toList())
    }
}
