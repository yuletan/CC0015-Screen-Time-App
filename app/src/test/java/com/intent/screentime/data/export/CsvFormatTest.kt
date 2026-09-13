package com.intent.screentime.data.export

import com.intent.screentime.data.local.entity.DailySummaryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvFormatTest {

    private val day = LocalDate.parse("2026-03-10").toEpochDay()

    private fun row(
        epochDay: Long,
        screenTimeMs: Long,
        productionMs: Long = 0L,
        consumptionMs: Long = 0L,
        focusMs: Long = 0L,
        topPackage: String? = null,
    ) = DailySummaryEntity(
        dayEpochDay = epochDay,
        screenTimeMs = screenTimeMs,
        unlockCount = 7,
        productionMs = productionMs,
        consumptionMs = consumptionMs,
        focusMs = focusMs,
        topPackage = topPackage,
    )

    @Test
    fun `writes a header and one line per day in minutes`() {
        val csv = CsvFormat.dailySummaries(
            listOf(
                row(
                    epochDay = day,
                    screenTimeMs = 206 * 60_000L,
                    productionMs = 61 * 60_000L,
                    consumptionMs = 90 * 60_000L,
                    focusMs = 25 * 60_000L,
                    topPackage = "com.example.app",
                ),
            ),
        )

        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertEquals(
            "date,day,screen_time_minutes,production_minutes,consumption_minutes," +
                "focus_minutes,unlocks,top_app",
            lines[0],
        )
        assertEquals("2026-03-10,tuesday,206,61,90,25,7,com.example.app", lines[1])
    }

    @Test
    fun `days are written oldest first regardless of input order`() {
        val csv = CsvFormat.dailySummaries(
            listOf(
                row(epochDay = day + 1, screenTimeMs = 60_000L),
                row(epochDay = day, screenTimeMs = 60_000L),
            ),
        )

        val dates = csv.trim().lines().drop(1).map { it.substringBefore(',') }
        assertEquals(listOf("2026-03-10", "2026-03-11"), dates)
    }

    @Test
    fun `values containing a comma are quoted`() {
        val csv = CsvFormat.dailySummaries(
            listOf(row(epochDay = day, screenTimeMs = 60_000L, topPackage = "weird,app")),
        )

        assertTrue(csv.trim().lines().last().endsWith("\"weird,app\""))
    }

    @Test
    fun `an empty top package is left blank`() {
        val csv = CsvFormat.dailySummaries(
            listOf(row(epochDay = day, screenTimeMs = 0L)),
        )

        assertTrue(csv.trim().lines().last().endsWith(","))
    }
}
