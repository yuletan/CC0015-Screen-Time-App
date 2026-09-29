package com.intent.screentime.data.export

import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.DayNoteEntity
import com.intent.screentime.data.stats.DayTotals
import com.intent.screentime.data.stats.WeeklyRollup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodExportTest {

    private val mon = LocalDate.parse("2026-09-14").toEpochDay() // a Monday

    private fun summary(day: Long, minutes: Long) = DailySummaryEntity(
        dayEpochDay = day,
        screenTimeMs = minutes * 60_000L,
        unlockCount = 5,
        productionMs = minutes * 60_000L / 2,
        consumptionMs = minutes * 60_000L / 2,
        focusMs = 0L,
        topPackage = "com.example.app",
    )

    @Test
    fun `single day stem carries date and day`() {
        assertEquals("2026-09-20_day", PeriodExport.rangeStem(day("2026-09-20"), day("2026-09-20")))
        assertEquals(
            "2026-09-20_day.png",
            PeriodExport.dailyPhotoName(day("2026-09-20")),
        )
    }

    @Test
    fun `week stem carries range and week`() {
        assertEquals(
            "2026-09-14_to_2026-09-20_week",
            PeriodExport.rangeStem(day("2026-09-14"), day("2026-09-20")),
        )
        assertEquals(
            "intent-2026-09-14_to_2026-09-20_week.zip",
            PeriodExport.zipName(day("2026-09-14"), day("2026-09-20")),
        )
    }

    @Test
    fun `month stem carries range and month`() {
        assertEquals(
            "2026-08-22_to_2026-09-20_month",
            PeriodExport.rangeStem(day("2026-08-22"), day("2026-09-20")),
        )
    }

    @Test
    fun `daily csv lists every tracked day with notes`() {
        val csv = PeriodCsvs.daily(
            listOf(
                PeriodCsvs.DayRow(summary(mon, 120), sessionCount = 9),
                PeriodCsvs.DayRow(
                    summary(mon + 1, 60),
                    note = DayNoteEntity(mon + 1, note = "late, night", reflection = "yes"),
                ),
            ),
        )
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("date,day_of_week"))
        assertTrue(lines[1].contains("2026-09-14,monday,120"))
        // Comma in the note is quoted so the spreadsheet still parses.
        assertTrue(lines[2].contains("\"late, night\""))
        assertTrue(lines[2].contains("Yes"))
    }

    @Test
    fun `weekly csv rolls daily rows up per week`() {
        val summaries = listOf(summary(mon, 120), summary(mon + 1, 60))
        val weeks = WeeklyRollup.of(
            summaries.map {
                DayTotals(it.dayEpochDay, it.screenTimeMs, it.productionMs, it.consumptionMs, 0, 0)
            },
        )
        val csv = PeriodCsvs.weekly(weeks, summaries)
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        // 180 total, 90 avg, 2 days tracked.
        assertTrue(lines[1].contains("2026-09-14,2026-09-20,2,180,90"))
    }

    @Test
    fun `apps csv ranks biggest first with share`() {
        val csv = PeriodCsvs.apps(
            listOf(
                PeriodCsvs.AppRow("a", "A", 60_000L, 1),
                PeriodCsvs.AppRow("b", "B", 180_000L, 3),
            ),
        )
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains("B"))
        assertTrue(lines[1].contains("75"))
    }

    private fun day(iso: String): Long = LocalDate.parse(iso).toEpochDay()
}
