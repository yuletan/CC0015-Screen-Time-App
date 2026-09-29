package com.intent.screentime.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekVerdictTest {

    private val monday = LocalDate.parse("2026-03-09").toEpochDay()

    private fun day(
        offset: Int,
        screenTimeMs: Long = 0L,
        productionMs: Long = 0L,
        consumptionMs: Long = 0L,
    ) = DayTotals(
        epochDay = monday + offset,
        screenTimeMs = screenTimeMs,
        productionMs = productionMs,
        consumptionMs = consumptionMs,
        unlockCount = 0,
        focusMs = 0L,
    )

    private fun rank(
        current: DayTotals,
        previous: DayTotals,
        mindless: Pair<Int, Int> = 0 to 0,
    ): List<WeekVerdict.Finding> = WeekVerdict.rank(
        WeekOverWeek.compare(
            current = listOf(current),
            previous = listOf(previous),
            previousWeek = listOf(previous),
            currentMindlessOpens = mindless.first,
            previousMindlessOpens = mindless.second,
        ),
    )

    /** Ten hours, so a percentage is easy to read in the assertions. */
    private val tenHours = 10 * 3_600_000L

    @Test
    fun `a first week has no verdict to give, only a baseline`() {
        val findings = WeekVerdict.rank(
            WeekOverWeek.compare(
                current = listOf(day(7, tenHours)),
                previous = emptyList(),
                previousWeek = emptyList(),
            ),
        )

        assertEquals(emptyList<WeekVerdict.Finding>(), findings)
    }

    @Test
    fun `a move inside the stall band is reported as stalled, not as progress`() {
        val findings = rank(
            current = day(7, tenHours * 96 / 100),
            previous = day(0, tenHours),
        )

        val screenTime = findings.single { it.metric == WeekVerdict.Metric.SCREEN_TIME }
        assertEquals(WeekVerdict.Tone.STALLED, screenTime.tone)
        assertEquals(4, screenTime.magnitudePercent)
        assertTrue(screenTime.sentence.startsWith("Screen time is level"))
    }

    @Test
    fun `one point past the stall band is a direction again`() {
        val findings = rank(
            current = day(7, tenHours * 94 / 100),
            previous = day(0, tenHours),
        )

        val screenTime = findings.single { it.metric == WeekVerdict.Metric.SCREEN_TIME }
        assertEquals(WeekVerdict.Tone.IMPROVED, screenTime.tone)
        assertEquals(6, screenTime.magnitudePercent)
    }

    @Test
    fun `the biggest move leads, whichever direction it went`() {
        val findings = rank(
            current = day(7, screenTimeMs = tenHours * 90 / 100, productionMs = 21L, consumptionMs = 79L),
            previous = day(0, screenTimeMs = tenHours, productionMs = 20L, consumptionMs = 80L),
            mindless = 30 to 10, // three times as many, and the largest move on the board
        )

        assertEquals(
            listOf(
                WeekVerdict.Metric.MINDLESS_OPENS,
                WeekVerdict.Metric.SCREEN_TIME,
                WeekVerdict.Metric.PRODUCTION_SHARE,
            ),
            findings.map { it.metric },
        )
        assertEquals(WeekVerdict.Tone.SLIPPED, findings[0].tone)
        assertEquals(WeekVerdict.Tone.IMPROVED, findings[1].tone)
        assertEquals(WeekVerdict.Tone.STALLED, findings[2].tone)
    }

    @Test
    fun `screen time falling is an improvement`() {
        val findings = rank(
            current = day(7, tenHours * 80 / 100),
            previous = day(0, tenHours),
        )

        val screenTime = findings.single { it.metric == WeekVerdict.Metric.SCREEN_TIME }
        assertEquals(WeekVerdict.Tone.IMPROVED, screenTime.tone)
        assertEquals("Screen time down 20% against the same point last week.", screenTime.sentence)
    }

    @Test
    fun `screen time rising is a slip`() {
        val findings = rank(
            current = day(7, tenHours * 130 / 100),
            previous = day(0, tenHours),
        )

        val screenTime = findings.single { it.metric == WeekVerdict.Metric.SCREEN_TIME }
        assertEquals(WeekVerdict.Tone.SLIPPED, screenTime.tone)
        assertEquals("Screen time up 30% on the same point last week.", screenTime.sentence)
    }

    @Test
    fun `production share rising is an improvement, unlike the other two`() {
        val findings = rank(
            current = day(7, tenHours, productionMs = 40L, consumptionMs = 60L),
            previous = day(0, tenHours, productionMs = 20L, consumptionMs = 80L),
        )

        val share = findings.single { it.metric == WeekVerdict.Metric.PRODUCTION_SHARE }
        assertEquals(WeekVerdict.Tone.IMPROVED, share.tone)
        assertEquals("More of your time went on producing things — 40% against 20%.", share.sentence)
    }

    @Test
    fun `fewer drift opens is an improvement`() {
        val findings = rank(
            current = day(7, tenHours),
            previous = day(0, tenHours),
            mindless = 4 to 16,
        )

        val opens = findings.single { it.metric == WeekVerdict.Metric.MINDLESS_OPENS }
        assertEquals(WeekVerdict.Tone.IMPROVED, opens.tone)
        assertEquals("Fewer opens that were drift or boredom — 4 against 16.", opens.sentence)
    }

    @Test
    fun `a share of zero on both sides is left out rather than reported as held`() {
        val findings = rank(
            current = day(7, tenHours),
            previous = day(0, tenHours),
        )

        assertTrue(findings.none { it.metric == WeekVerdict.Metric.PRODUCTION_SHARE })
    }

    @Test
    fun `a move up from nothing is reported as large rather than as a percentage`() {
        val findings = rank(
            current = day(7, tenHours),
            previous = day(0, 0L), // tracked, but the phone was never used
        )

        val screenTime = findings.single { it.metric == WeekVerdict.Metric.SCREEN_TIME }
        assertEquals(WeekVerdict.Tone.SLIPPED, screenTime.tone)
        assertEquals(100, screenTime.magnitudePercent)
        assertEquals("Screen time where last week recorded none.", screenTime.sentence)
    }

    @Test
    fun `a week of nothing on both sides has nothing to say`() {
        val findings = rank(current = day(7, 0L), previous = day(0, 0L))

        val screenTime = findings.single { it.metric == WeekVerdict.Metric.SCREEN_TIME }
        assertEquals(WeekVerdict.Tone.STALLED, screenTime.tone)
        assertEquals(0, screenTime.magnitudePercent)
    }

    @Test
    fun `equal moves fall back to the order of the metrics`() {
        val findings = rank(
            current = day(7, tenHours * 110 / 100),
            previous = day(0, tenHours),
            mindless = 11 to 10, // the same tenth as the screen time move
        )

        assertEquals(
            listOf(WeekVerdict.Metric.SCREEN_TIME, WeekVerdict.Metric.MINDLESS_OPENS),
            findings.map { it.metric },
        )
    }
}
