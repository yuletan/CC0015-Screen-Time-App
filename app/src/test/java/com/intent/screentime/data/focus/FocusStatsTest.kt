package com.intent.screentime.data.focus

import com.intent.screentime.data.local.entity.FocusSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusStatsTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(day: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        startMs: Long,
        minutes: Int,
        completed: Boolean = true,
    ) = FocusSessionEntity(
        startMs = startMs,
        endMs = startMs + minutes * 60_000L,
        plannedMs = minutes * 60_000L,
        completed = completed,
    )

    @Test
    fun `completed focus time is summed for the day`() {
        val sessions = listOf(
            session(at("2026-03-10", 9), minutes = 25),
            session(at("2026-03-10", 14), minutes = 45),
            session(at("2026-03-09", 14), minutes = 90),
        )

        val today = LocalDate.parse("2026-03-10").toEpochDay()
        assertEquals(70 * 60_000L, FocusStats.completedMsOn(sessions, today, zone))
    }

    @Test
    fun `a session spanning midnight is split across both days`() {
        val lateNight = session(at("2026-03-10", 23, 40), minutes = 50)

        val day10 = LocalDate.parse("2026-03-10").toEpochDay()
        val day11 = LocalDate.parse("2026-03-11").toEpochDay()

        assertEquals(20 * 60_000L, FocusStats.completedMsOn(listOf(lateNight), day10, zone))
        assertEquals(30 * 60_000L, FocusStats.completedMsOn(listOf(lateNight), day11, zone))
    }

    @Test
    fun `a session that was ended early does not count`() {
        val sessions = listOf(session(at("2026-03-10", 9), minutes = 25, completed = false))
        val today = LocalDate.parse("2026-03-10").toEpochDay()

        assertEquals(0L, FocusStats.completedMsOn(sessions, today, zone))
    }

    @Test
    fun `an unfinished session does not count`() {
        val running = FocusSessionEntity(
            startMs = at("2026-03-10", 9),
            endMs = null,
            plannedMs = 25 * 60_000L,
            completed = false,
        )
        val today = LocalDate.parse("2026-03-10").toEpochDay()

        assertEquals(0L, FocusStats.completedMsOn(listOf(running), today, zone))
    }

    @Test
    fun `streak counts consecutive days with a completed session`() {
        val sessions = listOf(
            session(at("2026-03-10", 9), minutes = 25),
            session(at("2026-03-09", 9), minutes = 25),
            session(at("2026-03-08", 9), minutes = 25),
        )
        val today = LocalDate.parse("2026-03-10").toEpochDay()

        assertEquals(3, FocusStats.streak(sessions, today, zone))
    }

    @Test
    fun `today without a session yet does not break the streak`() {
        val sessions = listOf(
            session(at("2026-03-09", 9), minutes = 25),
            session(at("2026-03-08", 9), minutes = 25),
        )
        val today = LocalDate.parse("2026-03-10").toEpochDay()

        assertEquals(2, FocusStats.streak(sessions, today, zone))
    }

    @Test
    fun `a missed day ends the streak`() {
        val sessions = listOf(
            session(at("2026-03-10", 9), minutes = 25),
            session(at("2026-03-09", 9), minutes = 25),
            session(at("2026-03-07", 9), minutes = 25),
        )
        val today = LocalDate.parse("2026-03-10").toEpochDay()

        assertEquals(2, FocusStats.streak(sessions, today, zone))
    }
}
