package com.intent.screentime.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.intent.screentime.core.format.DayRangeFormat
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.intent.IntentStats
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.data.stats.DayTotals
import com.intent.screentime.data.stats.UsageSplit
import com.intent.screentime.data.stats.WeeklyRollup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Downloads a day, week or month as one zip: `csv/` + `photos/` + `insights.md`.
 *
 * - A **day** carries its hourly shape, its split and its top apps, named
 *   `2026-09-20_day_….png`.
 * - A **week** carries the 7 daily photos plus the weekly trend / split /
 *   top-apps / hours, named `2026-09-14_to_2026-09-20_week_….png`.
 * - A **month** is 4× weeks: ~30 daily photos, one chart set per Mon–Sun week,
 *   plus the month-level charts — same two folders, longer duration.
 *
 * `weekly.csv` is rolled up from the same daily rows `daily.csv` lists, so the
 * weekly numbers always include their daily data.
 *
 * Null when there is nothing to export yet.
 */
class PeriodExporter(
    private val context: Context,
    private val repository: UsageRepository,
    private val labelOf: (String) -> String = { it },
) {

    /**
     * [appCaptures] are the exact on-screen chart pixels keyed by panel fileName
     * (e.g. "insights-trend-week", "week-over-week"). Each one is filed under its
     * date-named photo instead of a redraw; anything missing falls back to Canvas.
     */
    suspend fun export(
        fromDay: Long,
        toDay: Long,
        appCaptures: Map<String, Bitmap> = emptyMap(),
    ): Uri? = withContext(Dispatchers.IO) {
        val from = minOf(fromDay, toDay)
        val to = maxOf(fromDay, toDay)
        // Guardrail: a zip of unbounded history would OOM on bitmaps. 92 days covers
        // the longest Insights view (90 days) with room for its partial weeks.
        val clampedFrom = maxOf(from, to - MAX_DAYS + 1)
        runCatching { exportUnchecked(clampedFrom, to, appCaptures) }.getOrNull()
    }

    private suspend fun exportUnchecked(
        fromDay: Long,
        toDay: Long,
        appCaptures: Map<String, Bitmap>,
    ): Uri? {
        val summaries = repository.summariesBetween(fromDay, toDay)
        if (summaries.isEmpty()) return null

        val appUsage = repository.appUsageBetween(fromDay, toDay)
        val lookup = repository.categoryLookup()
        val split = UsageSplit.from(appUsage, lookup)

        val startMs = DayWindow.startOfDayMs(fromDay)
        val endMs = minOf(DayWindow.endOfDayMs(toDay), System.currentTimeMillis())
        val logs = repository.intentLogsBetween(startMs, endMs)
        val ledger = if (logs.isEmpty()) {
            null
        } else {
            IntentStats.ledger(
                logs = logs,
                sessions = repository.sessionsBetween(logs.first().timestampMs, endMs),
                zone = DayWindow.zone,
            )
        }
        val notes = repository.dayNotesBetween(fromDay, toDay)

        val byDay = summaries.associateBy { it.dayEpochDay }
        val dayTotals = summaries.map {
            DayTotals(
                epochDay = it.dayEpochDay,
                screenTimeMs = it.screenTimeMs,
                productionMs = it.productionMs,
                consumptionMs = it.consumptionMs,
                unlockCount = it.unlockCount,
                focusMs = it.focusMs,
            )
        }
        val weeks = WeeklyRollup.of(dayTotals)

        // Previous equal-length window, for the "more/less than before" line.
        val days = toDay - fromDay + 1
        val prev = repository.summariesBetween(fromDay - days, fromDay - 1)
        val prevTotal = prev.takeIf { it.isNotEmpty() }?.sumOf { it.screenTimeMs }

        // --- csv/ ----------------------------------------------------------
        val sessionsByDay = appUsage.groupBy { it.dayEpochDay }
        val logsByDay = logs.groupBy { DayWindow.epochDayOf(it.timestampMs) }
        val notesByDay = notes.associateBy { it.dayEpochDay }
        val dayRows = summaries.map { summary ->
            PeriodCsvs.DayRow(
                summary = summary,
                sessionCount = sessionsByDay[summary.dayEpochDay]?.sumOf { it.sessionCount } ?: 0,
                prompts = logsByDay[summary.dayEpochDay]?.size ?: 0,
                promptsAnswered = logsByDay[summary.dayEpochDay]?.count { !it.skipped } ?: 0,
                note = notesByDay[summary.dayEpochDay],
            )
        }
        val dailyCsv = PeriodCsvs.daily(dayRows)
        val weeklyCsv = PeriodCsvs.weekly(weeks, summaries)

        val grouped = appUsage.groupBy { it.packageName }
        val appRows = grouped.map { (pkg, rows) ->
            PeriodCsvs.AppRow(
                packageName = pkg,
                label = labelOf(pkg),
                totalMs = rows.sumOf { it.totalMs },
                sessionCount = rows.sumOf { it.sessionCount },
            )
        }.sortedByDescending { it.totalMs }
        val appsCsv = PeriodCsvs.apps(appRows)
        val intentsCsv = PeriodCsvs.intents(logs, labelOf, DayWindow.zone)

        val insights = PeriodInsightsText.build(
            PeriodInsightsText.Input(
                fromDay = fromDay,
                toDay = toDay,
                summaries = summaries,
                split = split,
                weeks = weeks,
                topApps = appRows,
                ledger = ledger,
                notes = notes,
                previousTotalMs = prevTotal,
            ),
        )

        // --- photos/ -------------------------------------------------------
        val windowLabel = DayRangeFormat.label(fromDay, toDay)
        val stem = PeriodExport.rangeStem(fromDay, toDay)
        val photos = ArrayList<Pair<String, Bitmap>>()

        val rangeBucketsEarly = repository.hourlyBucketsForRange(fromDay, toDay)
        val periodPoints = when {
            // A single day has no daily series — its trend is its hourly shape.
            days == 1L -> rangeBucketsEarly.map {
                PeriodChartRenderer.Point(
                    label = "%02d:00".format(it.hour),
                    valueMs = it.totalMs,
                )
            }
            days <= 31L -> {
                summaries.sortedBy { it.dayEpochDay }.map {
                    PeriodChartRenderer.Point(
                        label = DAY_LABEL.format(LocalDate.ofEpochDay(it.dayEpochDay)),
                        valueMs = it.screenTimeMs,
                    )
                }
            }
            else -> {
                weeks.sortedBy { it.startEpochDay }.map {
                    PeriodChartRenderer.Point(label = it.label, valueMs = it.screenTimeMs)
                }
            }
        }
        // The photo of what is seen in the app wins wherever one was captured;
        // Canvas only fills panels that had nothing to screenshot (past windows,
        // Settings exports, off-screen panels).
        fun captured(vararg prefixes: String): Bitmap? =
            appCaptures.entries.firstOrNull { (key, _) ->
                prefixes.any { key.startsWith(it) }
            }?.value

        captured("insights-trend")?.let { photos += "${stem}_trend.png" to it }
            ?: run {
                photos += "${stem}_trend.png" to PeriodChartRenderer.trend(
                    points = periodPoints,
                    title = if (days == 1L) "Screen time per hour" else "Screen time per day",
                    subtitle = windowLabel,
                    caption = if (days == 1L) {
                        "Hourly, $windowLabel."
                    } else {
                        "Daily totals, $windowLabel."
                    },
                )
            }
        captured("insights-split")?.let { photos += "${stem}_split.png" to it }
            ?: run {
                photos += "${stem}_split.png" to PeriodChartRenderer.split(
                    productionMs = split.productionMs,
                    consumptionMs = split.consumptionMs,
                    utilityMs = split.utilityMs,
                    neutralMs = split.neutralMs,
                    unsortedMs = split.unsortedMs,
                    title = "Producing vs consuming",
                    subtitle = windowLabel,
                )
            }
        captured("insights-top-apps")?.let { photos += "${stem}_top-apps.png" to it }
            ?: run {
                photos += "${stem}_top-apps.png" to PeriodChartRenderer.topApps(
                    bars = appRows.take(8).mapIndexed { i, app ->
                        PeriodChartRenderer.Bar(
                            label = app.label,
                            valueMs = app.totalMs,
                            color = PeriodChartRenderer.SERIES[i % PeriodChartRenderer.SERIES.size],
                        )
                    },
                    title = "Biggest apps",
                    subtitle = windowLabel,
                )
            }
        captured("insights-hours")?.let { photos += "${stem}_hours.png" to it }
            ?: run {
                photos += "${stem}_hours.png" to PeriodChartRenderer.hours(
                    buckets = rangeBucketsEarly,
                    title = "When you're on your phone",
                    subtitle = "Hours of the day across $windowLabel",
                )
            }
        // Extra as-seen panels the Canvas set has no equivalent for.
        captured("insights-score")?.let { photos += "${stem}_score.png" to it }
        captured("insights-weekday")?.let { photos += "${stem}_weekday.png" to it }
        captured("insights-categories")?.let { photos += "${stem}_categories.png" to it }
        captured("insights-production-ratio")?.let {
            photos += "${stem}_production-ratio.png" to it
        }
        captured("week-over-week")?.let { photos += "${stem}_week-over-week.png" to it }

        // Weekly chart photos: one trend per Mon–Sun week when the period spans weeks.
        // This is what makes a month "4× weeks" — the month zip carries each week's
        // own chart alongside the month-level ones above.
        if (weeks.size > 1) {
            for (week in weeks.sortedBy { it.startEpochDay }) {
                val wFrom = week.startEpochDay
                val wTo = week.startEpochDay + 6
                val wStem = "${PeriodExport.iso(wFrom)}_to_${PeriodExport.iso(wTo)}_week"
                val wDays = (wFrom..wTo).mapNotNull { byDay[it] }.sortedBy { it.dayEpochDay }
                if (wDays.size < 2) continue
                photos += "${wStem}_trend.png" to PeriodChartRenderer.trend(
                    points = wDays.map {
                        PeriodChartRenderer.Point(
                            label = DAY_LABEL.format(LocalDate.ofEpochDay(it.dayEpochDay)),
                            valueMs = it.screenTimeMs,
                        )
                    },
                    title = "Screen time per day",
                    subtitle = DayRangeFormat.label(wFrom, wTo),
                    caption = "Daily totals, ${DayRangeFormat.label(wFrom, wTo)}.",
                    width = 1400,
                    height = 900,
                )
            }
        }

        // Daily photos: one hourly chart per tracked day.
        for (summary in summaries.sortedBy { it.dayEpochDay }) {
            if (summary.screenTimeMs <= 0L) continue
            val buckets = repository.hourlyBuckets(summary.dayEpochDay)
            val date = LocalDate.ofEpochDay(summary.dayEpochDay)
            photos += PeriodExport.dailyPhotoName(summary.dayEpochDay) to
                PeriodChartRenderer.hours(
                    buckets = buckets,
                    title = date.format(FULL_LABEL),
                    subtitle = "Hourly shape · ${compact(summary.screenTimeMs)} total",
                    caption = "One bar per hour, ${date.format(FULL_LABEL)}.",
                    width = PeriodChartRenderer.DAY_W,
                    height = PeriodChartRenderer.DAY_H,
                )
        }

        // --- zip -----------------------------------------------------------
        val dir = File(context.cacheDir, EXPORT_DIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, PeriodExport.zipName(fromDay, toDay))
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("insights.md"))
            zip.write(insights.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            writeText(zip, "csv/daily.csv", dailyCsv)
            writeText(zip, "csv/weekly.csv", weeklyCsv)
            writeText(zip, "csv/apps.csv", appsCsv)
            writeText(zip, "csv/intents.csv", intentsCsv)

            for ((name, bitmap) in photos) {
                try {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    val bytes = ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.toByteArray()
                    }
                    zip.write(bytes)
                    zip.closeEntry()
                } finally {
                    bitmap.recycle()
                }
            }
        }

        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
    }

    private fun writeText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun compact(ms: Long): String {
        val m = (ms / 60_000.0).toLong()
        return when {
            m < 60L -> "${m}m"
            m % 60L == 0L -> "${m / 60}h"
            else -> "${m / 60}h ${m % 60}m"
        }
    }

    companion object {
        const val EXPORT_DIR = "exports"
        const val MAX_DAYS = 92L

        fun shareIntent(uri: Uri, fileName: String): Intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "CC0015 Intent export ($fileName)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        fun chooserFor(uri: Uri, fileName: String, title: String): Intent =
            Intent.createChooser(shareIntent(uri, fileName), title)

        private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
        private val FULL_LABEL: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE d MMMM")
    }
}
