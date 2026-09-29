package com.intent.screentime.data.export

import java.time.LocalDate

/**
 * Naming + layout for a period download.
 *
 * One zip, two folders, exactly as requested:
 *
 * ```
 * intent-2026-09-14_to_2026-09-20_week.zip
 * ├── insights.md
 * ├── csv/
 * │   ├── daily.csv     — one row per tracked day (all the numbers)
 * │   ├── weekly.csv    — one row per Mon–Sun week (daily rows rolled up)
 * │   ├── apps.csv      — per-app totals for the whole period
 * │   └── intents.csv   — every prompt raised in the period
 * └── photos/
 *     ├── <range>_trend.png / _split.png / _top-apps.png / _hours.png
 *     ├── <week>_week_trend.png  — one per week when the period spans weeks
 *     └── <date>_day.png          — one per tracked day
 * ```
 *
 * Photos are named by date: a single day carries just its date
 * (`2026-09-20_day.png`), a week or month carries its range
 * (`2026-09-14_to_2026-09-20_week_trend.png`).
 */
object PeriodExport {

    /** Day = 1 day, Week = 7 days, Month = 30 days. Longer views export as `custom`. */
    enum class Kind(val days: Long, val label: String) {
        DAY(1L, "day"),
        WEEK(7L, "week"),
        MONTH(30L, "month"),
    }

    fun kindFor(fromDay: Long, toDay: Long): Kind? {
        val days = toDay - fromDay + 1
        return Kind.entries.firstOrNull { it.days == days }
    }

    fun iso(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

    /**
     * `2026-09-20_day` for a single day, `2026-09-14_to_2026-09-20_week` for a week,
     * `2026-08-22_to_2026-09-20_month` for a month, otherwise
     * `2026-08-01_to_2026-09-20_51d`.
     */
    fun rangeStem(fromDay: Long, toDay: Long): String {
        if (fromDay == toDay) return "${iso(fromDay)}_day"
        val kind = kindFor(fromDay, toDay)
        val range = "${iso(fromDay)}_to_${iso(toDay)}"
        return when (kind) {
            Kind.WEEK -> "${range}_week"
            Kind.MONTH -> "${range}_month"
            else -> {
                val days = toDay - fromDay + 1
                if (kind == Kind.DAY) "${iso(fromDay)}_day" else "${range}_${days}d"
            }
        }
    }

    fun zipName(fromDay: Long, toDay: Long): String =
        "intent-${rangeStem(fromDay, toDay)}.zip"

    /** `2026-09-20_day.png` or `2026-09-14_to_2026-09-20_week_trend.png`. */
    fun photoName(fromDay: Long, toDay: Long, suffix: String): String =
        "${rangeStem(fromDay, toDay)}_$suffix.png"

    /** `2026-09-20_day.png` — one daily photo per tracked day. */
    fun dailyPhotoName(epochDay: Long): String = "${iso(epochDay)}_day.png"

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    fun minutes(ms: Long): String = (ms / 60_000.0).toLong().toString()
}
