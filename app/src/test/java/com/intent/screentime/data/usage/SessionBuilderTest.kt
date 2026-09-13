package com.intent.screentime.data.usage

import com.intent.screentime.data.local.entity.UsageEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SessionBuilderTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(day: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun event(pkg: String, type: Int, timestampMs: Long) = UsageEventEntity(
        packageName = pkg,
        eventType = type,
        timestampMs = timestampMs,
    )

    private val resumed = UsageEventTypes.ACTIVITY_RESUMED
    private val paused = UsageEventTypes.ACTIVITY_PAUSED

    @Test
    fun `pairs a resume with its pause`() {
        val start = at("2026-03-10", 9)
        val end = at("2026-03-10", 9, 30)

        val sessions = SessionBuilder.build(
            events = listOf(event("app.a", resumed, start), event("app.a", paused, end)),
            windowStartMs = start,
            nowMs = end,
            zone = zone,
        )

        assertEquals(1, sessions.size)
        assertEquals("app.a", sessions[0].packageName)
        assertEquals(30 * 60_000L, sessions[0].durationMs)
    }

    @Test
    fun `switching apps closes the previous session`() {
        val aStart = at("2026-03-10", 9)
        val bStart = at("2026-03-10", 9, 10)
        val bEnd = at("2026-03-10", 9, 20)

        val sessions = SessionBuilder.build(
            events = listOf(
                event("app.a", resumed, aStart),
                event("app.b", resumed, bStart),
                event("app.b", paused, bEnd),
            ),
            windowStartMs = aStart,
            nowMs = bEnd,
            zone = zone,
        )

        assertEquals(2, sessions.size)
        assertEquals("app.a", sessions[0].packageName)
        assertEquals(10 * 60_000L, sessions[0].durationMs)
        assertEquals("app.b", sessions[1].packageName)
        assertEquals(10 * 60_000L, sessions[1].durationMs)
    }

    @Test
    fun `redundant resume for the app already in front does not split the session`() {
        val start = at("2026-03-10", 9)
        val again = at("2026-03-10", 9, 5)
        val end = at("2026-03-10", 9, 30)

        val sessions = SessionBuilder.build(
            events = listOf(
                event("app.a", resumed, start),
                event("app.a", resumed, again),
                event("app.a", paused, end),
            ),
            windowStartMs = start,
            nowMs = end,
            zone = zone,
        )

        assertEquals(1, sessions.size)
        assertEquals(30 * 60_000L, sessions[0].durationMs)
    }

    @Test
    fun `pause from an app that is not in front is ignored`() {
        val start = at("2026-03-10", 9)
        val end = at("2026-03-10", 9, 30)

        val sessions = SessionBuilder.build(
            events = listOf(
                event("app.a", resumed, start),
                event("app.background", paused, at("2026-03-10", 9, 10)),
                event("app.a", paused, end),
            ),
            windowStartMs = start,
            nowMs = end,
            zone = zone,
        )

        assertEquals(1, sessions.size)
        assertEquals(30 * 60_000L, sessions[0].durationMs)
    }

    @Test
    fun `screen turning off closes the open session`() {
        val start = at("2026-03-10", 9)
        val screenOff = at("2026-03-10", 9, 15)

        val sessions = SessionBuilder.build(
            events = listOf(
                event("app.a", resumed, start),
                event("", UsageEventTypes.SCREEN_NON_INTERACTIVE, screenOff),
            ),
            windowStartMs = start,
            nowMs = at("2026-03-10", 12),
            zone = zone,
        )

        assertEquals(1, sessions.size)
        assertEquals(15 * 60_000L, sessions[0].durationMs)
    }

    @Test
    fun `session still open at harvest time is closed at now`() {
        val start = at("2026-03-10", 9)
        val now = at("2026-03-10", 9, 42)

        val sessions = SessionBuilder.build(
            events = listOf(event("app.a", resumed, start)),
            windowStartMs = start,
            nowMs = now,
            zone = zone,
        )

        assertEquals(1, sessions.size)
        assertEquals(42 * 60_000L, sessions[0].durationMs)
    }

    @Test
    fun `excluded app is dropped but still closes the previous session`() {
        val start = at("2026-03-10", 9)
        val launcher = at("2026-03-10", 9, 12)

        val sessions = SessionBuilder.build(
            events = listOf(
                event("app.a", resumed, start),
                event("com.android.launcher", resumed, launcher),
            ),
            windowStartMs = start,
            nowMs = at("2026-03-10", 10),
            excludedPackages = setOf("com.android.launcher"),
            zone = zone,
        )

        // The launcher itself must not appear, but app.a must stop at the switch.
        assertEquals(1, sessions.size)
        assertEquals("app.a", sessions[0].packageName)
        assertEquals(12 * 60_000L, sessions[0].durationMs)
    }

    @Test
    fun `session records the day it started on`() {
        val lateNight = at("2026-03-10", 23, 50)

        val sessions = SessionBuilder.build(
            events = listOf(event("app.a", resumed, lateNight)),
            windowStartMs = lateNight,
            nowMs = at("2026-03-11", 0, 30),
            zone = zone,
        )

        assertEquals(1, sessions.size)
        assertEquals(LocalDate.parse("2026-03-10").toEpochDay(), sessions[0].dayEpochDay)
    }

    @Test
    fun `no events produces no sessions`() {
        val sessions = SessionBuilder.build(
            events = emptyList(),
            windowStartMs = 0L,
            nowMs = 1_000_000L,
            zone = zone,
        )
        assertTrue(sessions.isEmpty())
    }
}
