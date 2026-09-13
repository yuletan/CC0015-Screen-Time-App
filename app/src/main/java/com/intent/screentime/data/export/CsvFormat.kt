package com.intent.screentime.data.export

import com.intent.screentime.data.local.entity.DailySummaryEntity
import java.time.LocalDate

/**
 * Turns stored rows into CSV.
 *
 * Pure and separate from the file writing so the format — the part a spreadsheet
 * actually has to agree with — is unit tested. Minutes rather than milliseconds: nobody
 * opens a spreadsheet to read 14,820,000.
 */
object CsvFormat {

    private const val HEADER =
        "date,day,screen_time_minutes,production_minutes,consumption_minutes," +
            "focus_minutes,unlocks,top_app"

    fun dailySummaries(rows: List<DailySummaryEntity>): String = buildString {
        appendLine(HEADER)
        for (row in rows.sortedBy { it.dayEpochDay }) {
            val date = LocalDate.ofEpochDay(row.dayEpochDay)
            appendLine(
                listOf(
                    date.toString(),
                    date.dayOfWeek.name.lowercase(),
                    minutes(row.screenTimeMs),
                    minutes(row.productionMs),
                    minutes(row.consumptionMs),
                    minutes(row.focusMs),
                    row.unlockCount.toString(),
                    escape(row.topPackage.orEmpty()),
                ).joinToString(","),
            )
        }
    }

    private fun minutes(ms: Long): String = (ms / 60_000.0).toLong().toString()

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
