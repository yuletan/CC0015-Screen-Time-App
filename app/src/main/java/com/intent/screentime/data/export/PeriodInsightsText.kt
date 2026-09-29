package com.intent.screentime.data.export

import com.intent.screentime.data.intent.IntentStats
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.DayNoteEntity
import com.intent.screentime.data.local.entity.DayReflection
import com.intent.screentime.data.stats.UsageSplit
import com.intent.screentime.data.stats.WeeklyRollup
import java.time.LocalDate

/**
 * The human-readable half of a period download: `insights.md` at the zip root.
 *
 * Machine numbers live in `csv/`; this file says what they mean — totals, the
 * production share, the apps that moved the period, the reason ledger, and the
 * user's own notes. Written so it can be quoted straight into the CC0015 report:
 * every claim names the figure behind it.
 */
object PeriodInsightsText {

    data class Input(
        val fromDay: Long,
        val toDay: Long,
        val summaries: List<DailySummaryEntity>,
        val split: UsageSplit,
        val weeks: List<WeeklyRollup.Week>,
        val topApps: List<PeriodCsvs.AppRow>,
        val ledger: IntentStats.Ledger?,
        val notes: List<DayNoteEntity>,
        val previousTotalMs: Long? = null,
    )

    fun build(input: Input): String = buildString {
        val from = LocalDate.ofEpochDay(input.fromDay)
        val to = LocalDate.ofEpochDay(input.toDay)
        val days = input.toDay - input.fromDay + 1
        val kind = PeriodExport.kindFor(input.fromDay, input.toDay)?.label
            ?: "$days-day"

        appendLine("# Screen time insights — $from to $to ($kind)")
        appendLine()
        appendLine("Exported by CC0015 Intent. Numbers below match `csv/` row for row.")
        appendLine()

        val tracked = input.summaries.filter { it.screenTimeMs > 0L }
        val total = tracked.sumOf { it.screenTimeMs }
        appendLine("## Headline")
        appendLine()
        if (tracked.isEmpty()) {
            appendLine("No usage was recorded in this period.")
            appendLine()
            return@buildString
        }
        val avg = total / tracked.size
        appendLine("- Days in period: $days (${tracked.size} with usage)")
        appendLine("- Total screen time: ${compact(total)}")
        appendLine("- Daily average (tracked days): ${compact(avg)}")
        val heaviest = tracked.maxBy { it.screenTimeMs }
        val lightest = tracked.minBy { it.screenTimeMs }
        appendLine(
            "- Heaviest day: ${LocalDate.ofEpochDay(heaviest.dayEpochDay)} " +
                "at ${compact(heaviest.screenTimeMs)}",
        )
        appendLine(
            "- Lightest day: ${LocalDate.ofEpochDay(lightest.dayEpochDay)} " +
                "at ${compact(lightest.screenTimeMs)}",
        )
        input.previousTotalMs?.let { prev ->
            if (prev > 0L) {
                val delta = total - prev
                val pct = (delta * 100.0 / prev).toLong()
                val direction = when {
                    delta > 0L -> "more"
                    delta < 0L -> "less"
                    else -> "about the same"
                }
                appendLine(
                    "- Against the previous equal-length period: " +
                        "${compact(kotlin.math.abs(delta))} $direction " +
                        "($pct%).",
                )
            }
        }
        appendLine()

        appendLine("## What the time was made of")
        appendLine()
        val split = input.split
        if (split.accountableMs > 0L) {
            val share = (split.productionShare * 100).toInt()
            appendLine(
                "- Producing ${compact(split.productionMs)} vs " +
                    "consuming ${compact(split.consumptionMs)} " +
                    "($share% producing of categorised time).",
            )
        } else {
            appendLine("- Not enough categorised time to split producing vs consuming.")
        }
        if (split.utilityMs > 0L) appendLine("- Utility: ${compact(split.utilityMs)}.")
        if (split.neutralMs > 0L) appendLine("- Neutral: ${compact(split.neutralMs)}.")
        if (split.unsortedMs > 0L) {
            appendLine(
                "- Unsorted: ${compact(split.unsortedMs)} across " +
                    "${split.unsortedCount} app-days — sort these and the split sharpens.",
            )
        }
        val focus = input.summaries.sumOf { it.focusMs }
        if (focus > 0L) appendLine("- Focused: ${compact(focus)}.")
        appendLine()

        if (input.weeks.size > 1) {
            appendLine("## Week by week")
            appendLine()
            for (week in input.weeks.sortedBy { it.startEpochDay }) {
                val start = LocalDate.ofEpochDay(week.startEpochDay)
                val end = LocalDate.ofEpochDay(week.startEpochDay + 6)
                val partial = if (week.isPartial) " (partial, ${week.days}d)" else ""
                appendLine(
                    "- $start to $end: ${compact(week.screenTimeMs)}$partial",
                )
            }
            appendLine()
        }

        if (input.topApps.isNotEmpty()) {
            appendLine("## Biggest apps in this period")
            appendLine()
            input.topApps.take(8).forEachIndexed { i, app ->
                appendLine(
                    "${i + 1}. ${app.label} — ${compact(app.totalMs)} " +
                        "(${app.sessionCount} sessions)",
                )
            }
            appendLine()
        }

        val ledger = input.ledger
        if (ledger != null) {
            appendLine("## Why you opened things")
            appendLine()
            val totalPrompts = ledger.answered + ledger.skipped
            if (ledger.skipped > 0) {
                appendLine(
                    "- Answered ${ledger.answered} of $totalPrompts prompts " +
                        "(${ledger.skipped} let close).",
                )
            } else {
                appendLine("- Answered all $totalPrompts prompts.")
            }
            ledger.overall.take(8).forEach { row ->
                val follow = if (row.averageFollowUpMs > 0L) {
                    ", avg ${compact(row.averageFollowUpMs)} after"
                } else {
                    ""
                }
                appendLine("- ${row.label}: ${row.count}×$follow")
            }
            if (ledger.topReasonLabel != null && ledger.topReasonWindow != null) {
                appendLine(
                    "- '${ledger.topReasonLabel}' opens cluster ${ledger.topReasonWindow}.",
                )
            }
            appendLine()
        }

        val withWords = input.notes.filter {
            !it.note.isNullOrBlank() || it.reflection != null
        }
        if (withWords.isNotEmpty()) {
            appendLine("## Your notes")
            appendLine()
            for (note in withWords.sortedBy { it.dayEpochDay }) {
                val date = LocalDate.ofEpochDay(note.dayEpochDay)
                val reflection = DayReflection.fromKey(note.reflection)?.label
                val prefix = if (reflection != null) " ($reflection)" else ""
                appendLine("### $date$prefix")
                appendLine()
                note.note?.takeIf { it.isNotBlank() }?.let { appendLine(it.trim()) }
                appendLine()
            }
        }

        appendLine("---")
        appendLine("Files: `csv/daily.csv` (per day), `csv/weekly.csv` (per week), " +
            "`csv/apps.csv`, `csv/intents.csv`, `photos/` (charts named by date).")
    }

    private fun compact(ms: Long): String {
        val m = (ms / 60_000.0).toLong()
        return when {
            m < 60L -> "${m}m"
            m % 60L == 0L -> "${m / 60}h"
            else -> "${m / 60}h ${m % 60}m"
        }
    }
}
