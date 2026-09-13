package com.intent.screentime.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WorkSchedulerTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 3, 10, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `a time still ahead today is scheduled today`() {
        val delay = WorkScheduler.nextOccurrenceDelay(
            minutesOfDay = 21 * 60,
            nowMs = at(20, 0),
            zone = zone,
        )

        assertEquals(60 * 60_000L, delay)
    }

    @Test
    fun `a time already passed is scheduled for tomorrow`() {
        val delay = WorkScheduler.nextOccurrenceDelay(
            minutesOfDay = 21 * 60,
            nowMs = at(22, 0),
            zone = zone,
        )

        assertEquals(23 * 60 * 60_000L, delay)
    }

    @Test
    fun `the exact moment is treated as passed so a job never fires twice`() {
        val delay = WorkScheduler.nextOccurrenceDelay(
            minutesOfDay = 21 * 60,
            nowMs = at(21, 0),
            zone = zone,
        )

        assertEquals(24 * 60 * 60_000L, delay)
    }

    @Test
    fun `midnight is a valid digest time`() {
        val delay = WorkScheduler.nextOccurrenceDelay(
            minutesOfDay = 0,
            nowMs = at(0, 5),
            zone = zone,
        )

        assertEquals(23 * 60 * 60_000L + 55 * 60_000L, delay)
    }
}
