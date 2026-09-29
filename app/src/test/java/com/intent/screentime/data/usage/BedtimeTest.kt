package com.intent.screentime.data.usage

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.AppSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The bedtime rules, checked rather than discovered at midnight.
 *
 * 2026-03-10 is a Tuesday, so the day arithmetic has something to get wrong, and every
 * case runs in UTC so "23:00" means the same instant on every machine.
 */
class BedtimeTest {

    private val utc = ZoneId.of("UTC")
    private val minute = 60_000L
    private val hour = 60 * minute

    private val day = LocalDate.parse("2026-03-10").toEpochDay()

    private val night = BedtimeWindow(startMinutesOfDay = 23 * 60, endMinutesOfDay = 7 * 60)
    private val morning = DayWindow.startOfDayMs(day + 1, utc) + 9 * hour

    private fun timeOn(epochDay: Long, hourOfDay: Int, minuteOfHour: Int = 0): Long =
        DayWindow.startOfDayMs(epochDay, utc) + hourOfDay * hour + minuteOfHour * minute

    private fun session(startMs: Long, endMs: Long, packageName: String = "app.social") =
        AppSessionEntity(
            packageName = packageName,
            startMs = startMs,
            endMs = endMs,
            durationMs = endMs - startMs,
            dayEpochDay = DayWindow.epochDayOf(startMs, utc),
        )

    @Test
    fun `a window past midnight runs from tonight to tomorrow morning`() {
        assertEquals(8 * 60, night.durationMinutes)

        val (from, to) = Bedtime.windowMs(night, day, utc)
        assertEquals(timeOn(day, 23), from)
        assertEquals(timeOn(day + 1, 7), to)
    }

    @Test
    fun `a window inside one day does not wrap`() {
        val early = BedtimeWindow(startMinutesOfDay = 1 * 60, endMinutesOfDay = 7 * 60)

        assertEquals(6 * 60, early.durationMinutes)
        val (from, to) = Bedtime.windowMs(early, day, utc)
        assertEquals(timeOn(day, 1), from)
        assertEquals(timeOn(day, 7), to)
    }

    @Test
    fun `a window with no length is not a commitment`() {
        assertFalse(BedtimeWindow(startMinutesOfDay = 23 * 60, endMinutesOfDay = 23 * 60).isSet)
        assertTrue(night.isSet)
    }

    @Test
    fun `a night is judged with the evening it starts on`() {
        val sessions = listOf(session(timeOn(day, 23, 30), timeOn(day, 23, 50)))

        assertEquals(
            Bedtime.Status.MISSED,
            Bedtime.status(night, day, sessions, nowMs = morning, zone = utc),
        )

        // The same minutes do not also fail tomorrow: tomorrow's window opens tomorrow
        // night, by which point nothing has happened yet.
        assertEquals(
            Bedtime.Status.PENDING,
            Bedtime.status(night, day + 1, sessions, nowMs = morning, zone = utc),
        )
    }

    @Test
    fun `five minutes inside the window still keeps the night`() {
        val sessions = listOf(session(timeOn(day, 23, 0), timeOn(day, 23, 5)))

        assertEquals(
            Bedtime.Status.HELD,
            Bedtime.status(night, day, sessions, nowMs = morning, zone = utc),
        )
    }

    @Test
    fun `one minute past the grace breaks it`() {
        val sessions = listOf(session(timeOn(day, 23, 0), timeOn(day, 23, 6)))

        assertEquals(
            Bedtime.Status.MISSED,
            Bedtime.status(night, day, sessions, nowMs = morning, zone = utc),
        )
    }

    @Test
    fun `time before the window opens is not counted against it`() {
        val sessions = listOf(session(timeOn(day, 22, 0), timeOn(day, 22, 30)))

        assertEquals(
            Bedtime.Status.HELD,
            Bedtime.status(night, day, sessions, nowMs = morning, zone = utc),
        )
    }

    @Test
    fun `a window that has not opened yet is pending rather than missed`() {
        val sessions = listOf(session(timeOn(day, 20, 0), timeOn(day, 22, 0)))
        val justBefore = timeOn(day, 22, 30)

        assertEquals(
            Bedtime.Status.PENDING,
            Bedtime.status(night, day, sessions, nowMs = justBefore, zone = utc),
        )
        assertNull(Bedtime.elapsedWindow(night, day, justBefore, utc))
    }

    @Test
    fun `a window that is still open judges only the part that has happened`() {
        val withinGrace = listOf(session(timeOn(day, 23, 0), timeOn(day, 23, 3)))
        assertEquals(
            Bedtime.Status.HELD,
            Bedtime.status(night, day, withinGrace, nowMs = timeOn(day, 23, 10), zone = utc),
        )

        val overGrace = listOf(session(timeOn(day, 23, 0), timeOn(day, 23, 10)))
        assertEquals(
            Bedtime.Status.MISSED,
            Bedtime.status(night, day, overGrace, nowMs = timeOn(day, 23, 10), zone = utc),
        )
    }

    @Test
    fun `overlapping sessions count once`() {
        val from = timeOn(day, 23, 30)
        val to = timeOn(day + 1, 0, 15)
        val sessions = listOf(
            session(from, timeOn(day + 1, 0, 0)),
            session(timeOn(day, 23, 45), to),
        )

        // 45 minutes of wall clock, not the 60 that adding the two rows would produce.
        assertEquals(45 * minute, Bedtime.usedMs(sessions, from, to))
    }

    @Test
    fun `our own app and the launcher do not count as picking up the phone`() {
        val sessions = listOf(
            session(
                startMs = timeOn(day, 23, 0),
                endMs = timeOn(day, 23, 30),
                packageName = "com.intent.screentime",
            ),
        )

        assertEquals(
            Bedtime.Status.HELD,
            Bedtime.status(
                window = night,
                epochDay = day,
                sessions = sessions,
                nowMs = morning,
                excludedPackages = setOf("com.intent.screentime"),
                zone = utc,
            ),
        )
    }

    @Test
    fun `a session straddling the wake time counts only the part inside the window`() {
        val (from, to) = Bedtime.windowMs(night, day, utc)
        val sessions = listOf(session(timeOn(day + 1, 6, 50), timeOn(day + 1, 7, 40)))

        // Ten minutes inside the window; the forty after it are the morning.
        assertEquals(10 * minute, Bedtime.usedMs(sessions, from, to))
    }
}
