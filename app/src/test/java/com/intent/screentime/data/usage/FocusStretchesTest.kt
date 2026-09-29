package com.intent.screentime.data.usage

import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.FocusSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusStretchesTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val day = LocalDate.parse("2026-03-10").toEpochDay()

    private fun at(hour: Int, minute: Int = 0): Long =
        LocalDate.parse("2026-03-10").atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun app(pkg: String, startMs: Long, endMs: Long) = AppSessionEntity(
        packageName = pkg,
        startMs = startMs,
        endMs = endMs,
        durationMs = endMs - startMs,
        dayEpochDay = day,
    )

    private fun focus(startMs: Long, endMs: Long, completed: Boolean) = FocusSessionEntity(
        id = 0L,
        startMs = startMs,
        plannedMs = endMs - startMs,
        endMs = endMs,
        completed = completed,
        label = null,
    )

    private val kinds = mapOf(
        "docs" to CategoryKind.PRODUCTION,
        "drive" to CategoryKind.PRODUCTION,
        "instagram" to CategoryKind.CONSUMPTION,
        "game" to CategoryKind.CONSUMPTION,
        "maps" to CategoryKind.UTILITY,
    )

    @Test
    fun `ten uninterrupted minutes in a work app is focus`() {
        val sessions = listOf(app("docs", at(9), at(9, 10)))

        val detected = FocusStretches.detected(sessions, day, kinds, zone = zone)

        assertEquals(1, detected.size)
        assertEquals(10 * 60_000L, FocusStretches.unionMs(detected))
    }

    @Test
    fun `nine minutes is a glance, not a stretch`() {
        val sessions = listOf(app("docs", at(9), at(9, 9)))

        assertTrue(FocusStretches.detected(sessions, day, kinds, zone = zone).isEmpty())
    }

    @Test
    fun `a few minutes between two work apps do not break the stretch`() {
        val sessions = listOf(
            app("docs", at(9), at(9, 12)),
            app("drive", at(9, 13), at(9, 20)),
        )

        val detected = FocusStretches.detected(sessions, day, kinds, zone = zone)

        // 12 + 7 minutes of app time; the one-minute gap is a pause, not focus.
        assertEquals(19 * 60_000L, FocusStretches.unionMs(detected))
    }

    @Test
    fun `a long silence breaks the stretch and the silence is not counted`() {
        val sessions = listOf(
            app("docs", at(9), at(9, 8)),
            app("docs", at(10), at(10, 8)),
        )

        // Neither 8-minute run reaches the threshold on its own.
        assertTrue(FocusStretches.detected(sessions, day, kinds, zone = zone).isEmpty())
    }

    @Test
    fun `time in a consuming app ends the stretch`() {
        val sessions = listOf(
            app("docs", at(9), at(9, 12)),
            app("instagram", at(9, 12), at(9, 13)),
            app("docs", at(9, 13), at(9, 20)),
        )

        val detected = FocusStretches.detected(sessions, day, kinds, zone = zone)

        // The second sitting is only seven minutes, so the twelve before the scroll is
        // the one stretch of the morning.
        assertEquals(1, detected.size)
        assertEquals(12 * 60_000L, FocusStretches.unionMs(detected))
    }

    @Test
    fun `a game is never focus however long it runs`() {
        val sessions = listOf(app("game", at(19), at(21)))

        assertTrue(FocusStretches.detected(sessions, day, kinds, zone = zone).isEmpty())
    }

    @Test
    fun `system ui passing through does not break the stretch`() {
        // Two short sittings that only clear the threshold together. If the system UI
        // counted as an interruption, neither would reach ten minutes on its own.
        val sessions = listOf(
            app("docs", at(9), at(9, 8)),
            app("com.android.systemui", at(9, 8), at(9, 9)),
            app("docs", at(9, 9), at(9, 16)),
        )

        val detected = FocusStretches.detected(
            sessions = sessions,
            epochDay = day,
            kindByPackage = kinds,
            excludedPackages = setOf("com.android.systemui"),
            zone = zone,
        )

        // One run, made of the two sittings either side of the swipe.
        assertTrue(detected.isNotEmpty())
        assertEquals(15 * 60_000L, FocusStretches.unionMs(detected))
    }

    @Test
    fun `utility time is not focus either`() {
        val sessions = listOf(app("maps", at(9), at(10)))

        assertTrue(FocusStretches.detected(sessions, day, kinds, zone = zone).isEmpty())
    }

    @Test
    fun `a cancelled timer session is recorded but not counted`() {
        val sessions = listOf(
            focus(at(9), at(9, 30), completed = false),
            focus(at(14), at(14, 30), completed = true),
        )

        val manual = FocusStretches.manual(sessions, day, zone)

        assertEquals(1, manual.size)
        assertEquals(30 * 60_000L, FocusStretches.unionMs(manual))
    }

    @Test
    fun `union merges a timer running inside a detected stretch`() {
        val manual = listOf(FocusStretches.Interval(at(9, 2), at(9, 8)))
        val detected = listOf(FocusStretches.Interval(at(9), at(9, 15)))

        // 9:00–9:15 detected, with the timer's minutes inside it: fifteen, not twenty-one.
        assertEquals(15 * 60_000L, FocusStretches.unionMs(manual + detected))
    }

    @Test
    fun `an overnight stretch is clipped at midnight`() {
        val next = LocalDate.parse("2026-03-11").toEpochDay()
        val start = at(23, 50)
        val overnight = AppSessionEntity(
            packageName = "docs",
            startMs = start,
            endMs = start + 40 * 60_000L,
            durationMs = 40 * 60_000L,
            dayEpochDay = day,
        )

        val first = FocusStretches.detected(listOf(overnight), day, kinds, zone = zone)
        val second = FocusStretches.detected(listOf(overnight), next, kinds, zone = zone)

        // 10 minutes before midnight, 30 after.
        assertEquals(10 * 60_000L, FocusStretches.unionMs(first))
        assertEquals(30 * 60_000L, FocusStretches.unionMs(second))
    }

    @Test
    fun `overlapping sessions are not double counted`() {
        val sessions = listOf(
            app("docs", at(9), at(9, 15)),
            app("drive", at(9, 10), at(9, 20)),
        )

        val detected = FocusStretches.detected(sessions, day, kinds, zone = zone)

        assertEquals(20 * 60_000L, FocusStretches.unionMs(detected))
    }
}
