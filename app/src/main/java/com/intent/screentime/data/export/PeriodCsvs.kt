package com.intent.screentime.data.export

import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.DayNoteEntity
import com.intent.screentime.data.local.entity.DayReflection
import com.intent.screentime.data.local.entity.IntentLogEntity
import com.intent.screentime.data.stats.WeeklyRollup
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * CSV builders for a period download.
 *
 * Pure — no database, no context — so the format a spreadsheet has to agree with
 * is unit tested. The exporter only feeds these already-loaded rows.
 *
 * `weekly.csv` is rolled up from the same daily rows that `daily.csv` lists, so a
 * weekly file always contains its days' numbers too. A month is just a longer
 * period: same files, more rows, plus one [WeeklyRollup.Week] row per Mon–Sun week.
 */
object PeriodCsvs {

    data class DayRow(
        val summary: DailySummaryEntity,
        val sessionCount: Int = 0,
        val prompts: Int = 0,
        val promptsAnswered: Int = 0,
        val note: DayNoteEntity? = null,
    )

    // --- daily.csv ---------------------------------------------------------

    const val DAILY_HEADER =
        "date,day_of_week,screen_time_minutes,production_minutes,consumption_minutes," +
            "focus_minutes,auto_focus_minutes,unlocks,sessions,top_app," +
            "prompts,prompts_answered,reflection,note"

    fun daily(rows: List<DayRow>): String = buildString {
        appendLine(DAILY_HEADER)
        for ((summary, sessions, prompts, answered, note) in
            rows.sortedBy { it.summary.dayEpochDay }) {
            val date = LocalDate.ofEpochDay(summary.dayEpochDay)
            appendLine(
                listOf(
                    date.toString(),
                    date.dayOfWeek.name.lowercase(),
                    PeriodExport.minutes(summary.screenTimeMs),
                    PeriodExport.minutes(summary.productionMs),
                    PeriodExport.minutes(summary.consumptionMs),
                    PeriodExport.minutes(summary.focusMs),
                    PeriodExport.minutes(summary.autoFocusMs),
                    summary.unlockCount.toString(),
                    sessions.toString(),
                    PeriodExport.escape(summary.topPackage.orEmpty()),
                    prompts.toString(),
                    answered.toString(),
                    PeriodExport.escape(
                        DayReflection.fromKey(note?.reflection)?.label.orEmpty(),
                    ),
                    PeriodExport.escape(note?.note.orEmpty()),
                ).joinToString(","),
            )
        }
    }

    // --- weekly.csv --------------------------------------------------------

    const val WEEKLY_HEADER =
        "week_start,week_end,days_tracked,total_minutes,daily_avg_minutes," +
            "production_minutes,consumption_minutes,focus_minutes,unlocks," +
            "heaviest_day_minutes,lightest_day_minutes"

    /**
     * One row per Mon–Sun [Week]. Daily figures come from [summaries] so the weekly
     * file always agrees with `daily.csv`.
     */
    fun weekly(
        weeks: List<WeeklyRollup.Week>,
        summaries: List<DailySummaryEntity>,
    ): String = buildString {
        appendLine(WEEKLY_HEADER)
        val byDay = summaries.associateBy { it.dayEpochDay }
        for (week in weeks.sortedBy { it.startEpochDay }) {
            // Days of this Mon–Sun week that were actually tracked.
            val days = (week.startEpochDay until week.startEpochDay + 7)
                .mapNotNull { byDay[it] }
            if (days.isEmpty()) continue
            val end = week.startEpochDay + 6
            val total = days.sumOf { it.screenTimeMs }
            val tracked = days.size
            appendLine(
                listOf(
                    LocalDate.ofEpochDay(week.startEpochDay).toString(),
                    LocalDate.ofEpochDay(end).toString(),
                    tracked.toString(),
                    PeriodExport.minutes(total),
                    PeriodExport.minutes(if (tracked == 0) 0L else total / tracked),
                    PeriodExport.minutes(days.sumOf { it.productionMs }),
                    PeriodExport.minutes(days.sumOf { it.consumptionMs }),
                    PeriodExport.minutes(days.sumOf { it.focusMs }),
                    days.sumOf { it.unlockCount }.toString(),
                    PeriodExport.minutes(days.maxOf { it.screenTimeMs }),
                    PeriodExport.minutes(days.minOf { it.screenTimeMs }),
                ).joinToString(","),
            )
        }
    }

    // --- apps.csv ----------------------------------------------------------

    data class AppRow(
        val packageName: String,
        val label: String,
        val totalMs: Long,
        val sessionCount: Int,
    )

    const val APPS_HEADER =
        "package_name,app_label,total_minutes,sessions,share_percent"

    fun apps(rows: List<AppRow>): String = buildString {
        appendLine(APPS_HEADER)
        val sorted = rows.sortedByDescending { it.totalMs }
        val total = sorted.sumOf { it.totalMs }.coerceAtLeast(1L)
        for (row in sorted) {
            val share = (row.totalMs * 100.0 / total).toLong()
            appendLine(
                listOf(
                    PeriodExport.escape(row.packageName),
                    PeriodExport.escape(row.label),
                    PeriodExport.minutes(row.totalMs),
                    row.sessionCount.toString(),
                    share.toString(),
                ).joinToString(","),
            )
        }
    }

    // --- intents.csv -------------------------------------------------------

    const val INTENTS_HEADER =
        "timestamp,date,time,package_name,app_label,reason,skipped"

    fun intents(
        logs: List<IntentLogEntity>,
        labelOf: (String) -> String = { it },
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        appendLine(INTENTS_HEADER)
        for (log in logs.sortedBy { it.timestampMs }) {
            val at = Instant.ofEpochMilli(log.timestampMs).atZone(zone)
            appendLine(
                listOf(
                    log.timestampMs.toString(),
                    at.toLocalDate().toString(),
                    at.toLocalTime().toString().substring(0, 5),
                    PeriodExport.escape(log.packageName),
                    PeriodExport.escape(labelOf(log.packageName)),
                    PeriodExport.escape(log.intentLabel),
                    if (log.skipped) "1" else "0",
                ).joinToString(","),
            )
        }
    }
}
