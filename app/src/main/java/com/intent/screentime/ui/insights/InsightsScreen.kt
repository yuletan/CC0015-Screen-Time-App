package com.intent.screentime.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import android.graphics.Bitmap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.core.format.HourFormat
import com.intent.screentime.data.goals.IntentScore
import com.intent.screentime.data.goals.ScoreWindow
import com.intent.screentime.data.intent.IntentStats
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.components.BarChart
import com.intent.screentime.ui.components.CapturablePanel
import com.intent.screentime.ui.components.ChartBar
import com.intent.screentime.ui.components.ChartRange
import com.intent.screentime.ui.components.ChartRangeSelector
import com.intent.screentime.ui.components.ChartUnit
import com.intent.screentime.ui.components.LocalChartCaptureRegistry
import com.intent.screentime.ui.components.rememberChartCaptureRegistry
import com.intent.screentime.ui.components.DonutChart
import com.intent.screentime.ui.components.DonutSlice
import com.intent.screentime.ui.components.EmptyState
import com.intent.screentime.ui.components.HourlyStrip
import com.intent.screentime.ui.components.IntentScorePanel
import com.intent.screentime.ui.components.LegendItem
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.ProducingVsConsumingPanel
import com.intent.screentime.ui.components.RankedAppRow
import com.intent.screentime.ui.components.ScoreDetail
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.StatTile
import com.intent.screentime.ui.components.TrendChart
import com.intent.screentime.ui.components.WindowStepper
import com.intent.screentime.ui.components.busiestHour
import com.intent.screentime.ui.components.niceCeilPercent
import com.intent.screentime.ui.components.pointsText
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors
import com.intent.screentime.ui.theme.toComposeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    appInfo: AppInfoProvider,
    onSelectRange: (ChartRange) -> Unit,
    onPreviousWindow: () -> Unit,
    onNextWindow: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenDay: (Long) -> Unit,
    onOpenTriage: () -> Unit,
    modifier: Modifier = Modifier,
    exporting: Boolean = false,
    onExport: (Map<String, Bitmap>) -> Unit = {},
) {
    // Collects the exact on-screen chart pixels for the zip. Panels register
    // themselves via CapturablePanel; anything never composed falls back to a redraw.
    val captureRegistry = rememberChartCaptureRegistry()
    val exportScope = rememberCoroutineScope()

    CompositionLocalProvider(LocalChartCaptureRegistry provides captureRegistry) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.gutter,
            end = Spacing.gutter,
            top = Spacing.sm,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column {
                SectionEyebrow("Trends")
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.sm))
                ChartRangeSelector(selected = state.range, onSelect = onSelectRange)

                // Which window, now that the history can be stepped through. Held back
                // until the first build lands so the label is never an empty box.
                if (state.windowLabel.isNotEmpty()) {
                    WindowStepper(
                        label = state.windowLabel,
                        canGoBack = state.canGoBack,
                        canGoForward = state.canGoForward,
                        onPrevious = onPreviousWindow,
                        onNext = onNextWindow,
                        backDescription = "Previous ${state.range.noun}",
                        forwardDescription = "Next ${state.range.noun}",
                    )
                }

                // One zip for the window on screen: csv/ + photos/ + insights.md.
                // Photos are the charts exactly as seen here, named by date (a day
                // carries its date, a week/month their range).
                if (!state.loading && state.hasData) {
                    Spacer(Modifier.height(Spacing.xs))
                    FilledTonalButton(
                        onClick = {
                            exportScope.launch {
                                onExport(captureRegistry.captureAll())
                            }
                        },
                        enabled = !exporting,
                    ) {
                        if (exporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (exporting) {
                                "Packing…"
                            } else {
                                "Download this ${state.range.noun} (ZIP)"
                            },
                        )
                    }
                    Text(
                        text = "One file: daily + weekly CSVs, chart photos by date, insights.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // The first read is still in flight. Both the empty state and an axis-only chart
        // would be claims about the day we cannot make yet.
        if (state.loading) return@LazyColumn

        if (!state.hasData) {
            item {
                Panel {
                    EmptyState(
                        icon = Icons.Filled.Insights,
                        title = when {
                            state.isPastWindow -> "Nothing tracked in this window"
                            state.unit == ChartUnit.HOUR -> "Nothing yet today"
                            else -> "Not enough history yet"
                        },
                        body = when {
                            state.isPastWindow ->
                                "No usage was recorded between ${state.windowLabel}. Step " +
                                    "forward again and the chart picks up where your history does."
                            state.unit == ChartUnit.HOUR ->
                                "Nothing recorded so far today. This fills in as the day goes on."
                            else ->
                                "CC0015 Intent needs a couple of days of usage before trends " +
                                    "mean anything. Come back tomorrow and this fills in."
                        },
                    )
                }
            }
            return@LazyColumn
        }

        item { TrendPanel(state, onOpenDay) }

        // The window read the way the Today screen reads a day: what the time was made of,
        // what it scored, and what shape it took. Everything below this point answers "how
        // does that compare" — these three answer "what was it", which is the question a
        // reader stepping back through their history is actually asking.
        item { SplitPanel(state, onOpenTriage) }
        state.score?.let { score ->
            item { ScorePanel(state, score) }
        }
        if (state.vitals != null) {
            item { VitalsPanel(state.vitals) }
        }

        // When in the day the window's time went: the strip for a past day, from the same
        // sessions the Today screen draws, and the range sum for the longer windows. A
        // Today window that is still running has its own hourly chart above.
        if (state.hourBuckets.any { it.totalMs > 0L }) {
            item { HoursPanel(state, appInfo) }
        }
        // Sits under the trend rather than above it: it always describes this week against
        // last, whatever range the selector is on, and putting a fixed window above a
        // switchable one would read as though it belonged to the range.
        state.weekOverWeek?.let { insight ->
            item { WeekOverWeekPanel(insight, appInfo, onOpenApp) }
        }
        item { WeekdayPanel(state) }
        if (state.topApps.isNotEmpty()) {
            item { TopAppsPanel(state, appInfo, onOpenApp) }
        }
        item { CategoryPanel(state) }
        if (state.hasRatioTrend) {
            item { ProductionRatioPanel(state, onOpenDay) }
        }
        val insight = state.intentInsight
        if (insight != null) {
            item { IntentPanel(insight) }
            // A second panel only earns its place when the top app has more than one
            // reason to tell apart; a one-row panel would just repeat the headline.
            if (insight.topPackageLabel != null && insight.topPackageSummaries.size > 1) {
                item { TopPackagePanel(insight.topPackageLabel, insight.topPackageSummaries) }
            }
        }
    }
    }
}

/**
 * The range's shape. Daily for a week or a month, hourly for today, weekly for the long
 * ranges — "when do I use this" is not a question a daily total can answer, and a
 * forty-two point line is not a shape anyone can read.
 */
@Composable
private fun TrendPanel(state: InsightsUiState, onOpenDay: (Long) -> Unit) {
    val hourly = state.unit == ChartUnit.HOUR
    val weekly = state.unit == ChartUnit.WEEK

    CapturablePanel(
        title = when {
            hourly -> "Screen time per hour"
            weekly -> "Screen time per week"
            else -> "Screen time per day"
        },
        fileName = "insights-trend-${state.range.name.lowercase()}",
        subtitle = when {
            hourly && state.isPastWindow ->
                "${state.windowLabel}. Tap or drag across the chart to read any hour."
            hourly -> "Today. Tap or drag across the chart to read any hour."
            weekly -> "Tap or drag across the chart to read any week."
            else -> "Tap or drag across the chart to read any day."
        },
        capture = state.hasData,
    ) {
        TrendChart(
            points = state.trend,
            lineColor = MaterialTheme.colorScheme.primary,
            onPointActivated = { index -> state.trend.getOrNull(index)?.epochDay?.let(onOpenDay) },
            target = state.trendTarget,
            emptyMessage = if (hourly) "Nothing recorded yet today" else "No usage in this range",
        )

        // Spelled out rather than left to the reader, because these charts leave the app
        // as images and a caption is the only legend an image has.
        if (state.trendTarget != null) {
            Text(
                text = "Dashed line: your " + if (weekly) "weekly cap." else "daily cap.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            when {
                hourly -> {
                    StatTile(
                        value = DurationFormat.compact(state.totalMs),
                        caption = "total today",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = state.busiest?.let { DurationFormat.compact(it.value) } ?: "—",
                        caption = state.busiest?.let { "busiest hour · ${it.label}" } ?: "busiest hour",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = state.activeHours.toString(),
                        caption = "hours with use",
                        modifier = Modifier.weight(1f),
                    )
                }

                weekly -> {
                    StatTile(
                        value = DurationFormat.compact(state.weeklyAverageMs),
                        caption = "weekly average",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = state.heaviestWeek?.let { DurationFormat.compact(it.value) } ?: "—",
                        caption = "heaviest week",
                        modifier = Modifier.weight(1f),
                        tint = dataColors.consumption,
                    )
                    StatTile(
                        value = state.weeksTracked.toString(),
                        caption = if (state.weeksTracked == 1) "week tracked" else "weeks tracked",
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    StatTile(
                        value = DurationFormat.compact(state.averageMs),
                        caption = "daily average",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = state.best?.let { DurationFormat.compact(it.screenTimeMs) } ?: "—",
                        caption = "heaviest day",
                        modifier = Modifier.weight(1f),
                        tint = dataColors.consumption,
                    )
                    StatTile(
                        value = state.worst?.let { DurationFormat.compact(it.screenTimeMs) } ?: "—",
                        caption = "lightest day",
                        modifier = Modifier.weight(1f),
                        tint = dataColors.production,
                    )
                }
            }
        }

        // Same-length windows either side of this one, so the comparison is honest
        // whatever the range is: thirty days are held against the thirty before them.
        state.previousWindowMs?.let { previous ->
            val deltaMs = state.totalMs - previous
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = when {
                    deltaMs > 0L -> "${DurationFormat.compact(deltaMs)} more than the " +
                        "${state.range.noun} before."
                    deltaMs < 0L -> "${DurationFormat.compact(-deltaMs)} less than the " +
                        "${state.range.noun} before."
                    else -> "About the same as the ${state.range.noun} before."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What the window's time was made of.
 *
 * The one question every range can be asked: a month that reads heavy because of three long
 * evenings and one spread evenly are the same total, and only the split separates them.
 */
@Composable
private fun SplitPanel(state: InsightsUiState, onOpenTriage: () -> Unit) {
    val split = state.split
    val singleDay = state.range.days == 1L
    val share = split.productionShare
    // Guarded rather than assumed: a day can have sessions before its summary row lands.
    val days = state.points.size.coerceAtLeast(1)

    ProducingVsConsumingPanel(
        split = split,
        fileName = "insights-split-${state.range.name.lowercase()}",
        subtitle = if (singleDay) state.windowLabel else "Across ${state.windowLabel}",
        onOpenTriage = onOpenTriage,
        prose = when {
            split.accountableMs <= 0L ->
                "Not enough categorised time in this window. Sort your apps on the Apps " +
                    "screen and this split starts meaning something."

            else -> "${(share * 100).toInt()}% of " +
                (if (singleDay) "that day's" else "this window's") +
                " categorised time went into producing rather than consuming." +
                if (singleDay) {
                    ""
                } else {
                    " That is a daily average of " +
                        DurationFormat.compact(split.productionMs / days) +
                        " producing."
                }
        },
    )
}

/**
 * The window's Intent Score.
 *
 * A single day is its own score, exactly as the Today screen gives it. A range is the mean
 * of its days' scores, row by row, so the five rows still add up to the ring — with the best
 * and toughest day named underneath, because an average hides both.
 */
@Composable
private fun ScorePanel(state: InsightsUiState, score: ScoreWindow.Result) {
    val singleDay = score.isSingleDay

    IntentScorePanel(
        score = score.score,
        verdict = if (singleDay) {
            when {
                score.score >= 70 -> "A day worth repeating."
                score.score >= 45 -> "A mixed day — the split was doing the work."
                else -> "Consumption had the run of it."
            }
        } else {
            "Your average day scored ${score.score} of 100."
        },
        explanation = if (singleDay) {
            "The same five rows the Today screen shows for a day, on ${state.windowLabel}."
        } else {
            "Averaged across the ${score.days} " +
                (if (score.days == 1) "day" else "days") +
                " in ${state.windowLabel} that have usage. Each row is the mean of that " +
                "day's own points, so the five still add up to the ring."
        },
        contributions = score.contributions,
        details = score.contributions.map { contribution ->
            ScoreDetail(
                kind = contribution.kind,
                tone = contribution.tone,
                text = scoreDetailText(state, score, contribution.kind),
                pointsText = contribution.pointsText(),
            )
        },
        fileName = "insights-score-${state.range.name.lowercase()}",
        footnote = scoreSpread(score),
    )
}

/**
 * Which days carried the window and which dragged, since one average hides both.
 *
 * Suppressed on a single day, where the best and the worst day are the same day.
 */
private fun scoreSpread(score: ScoreWindow.Result): String? {
    val best = score.best ?: return null
    val worst = score.worst ?: return null
    if (score.isSingleDay || best.score == worst.score) return null

    return "Best ${best.score} on ${dayLabel(best.epochDay)} · " +
        "toughest ${worst.score} on ${dayLabel(worst.epochDay)}"
}

/**
 * The plain-language reason behind one row, from the window's own numbers rather than from
 * an average's fraction — the reader can check every figure here against the panels around it.
 */
private fun scoreDetailText(
    state: InsightsUiState,
    score: ScoreWindow.Result,
    kind: IntentScore.ContributionKind,
): String {
    val split = state.split
    val singleDay = score.isSingleDay
    val days = score.days.coerceAtLeast(1)

    return when (kind) {
        IntentScore.ContributionKind.PRODUCTION ->
            if (split.accountableMs <= 0L) {
                "No Producing or Consuming time was categorised in this window."
            } else {
                "${(split.productionShare * 100).toInt()}% of the " +
                    "${DurationFormat.compact(split.accountableMs)} of Producing and " +
                    "Consuming time was Producing."
            }

        IntentScore.ContributionKind.CAP -> {
            val capMinutes = state.dailyCapMinutes
            when {
                capMinutes == null -> "No daily cap is set, so this part stays neutral."
                singleDay -> {
                    val capMs = capMinutes * 60_000L
                    if (state.totalMs > capMs) {
                        "${DurationFormat.compact(state.totalMs - capMs)} over your " +
                            "${DurationFormat.compact(capMs)} cap."
                    } else {
                        "${DurationFormat.compact(capMs - state.totalMs)} left before your cap."
                    }
                }

                else -> {
                    // Judged per day, the way the app judges a day — a weekly cap is a
                    // seventh of itself each day — so it is the average day that has to fit.
                    val capMs = capMinutes * 60_000L
                    val averageMs = state.totalMs / days
                    if (averageMs > capMs) {
                        "${DurationFormat.compact(averageMs - capMs)} over a " +
                            "${DurationFormat.compact(capMs)} daily cap on the average day."
                    } else {
                        "${DurationFormat.compact(capMs - averageMs)} left before your cap " +
                            "on the average day."
                    }
                }
            }
        }

        IntentScore.ContributionKind.BEDTIME -> bedtimeDetail(state, score)

        IntentScore.ContributionKind.FOCUS ->
            if (state.totalFocusMs > 0L) {
                val average = DurationFormat.compact(state.totalFocusMs / days)
                if (singleDay) {
                    "${DurationFormat.compact(state.totalFocusMs)} focused; one hour earns " +
                        "full credit on a day."
                } else {
                    "${DurationFormat.compact(state.totalFocusMs)} focused in this window — " +
                        "$average a day, against the hour that earns full credit."
                }
            } else {
                "No focused time recorded in this window."
            }

        IntentScore.ContributionKind.STREAK ->
            if (score.longestStreak <= 0) {
                "No consecutive days inside your cap in this window."
            } else if (singleDay) {
                "${score.longestStreak} consecutive days inside your cap by then."
            } else {
                "The longest run inside your cap reached ${score.longestStreak} " +
                    if (score.longestStreak == 1) "day." else "days."
            }
    }
}

/**
 * The bedtime row, which for a window is about nights rather than about one last night.
 *
 * Counted from the stored verdicts, so a window older than the verdicts says so instead of
 * claiming a night nobody judged was kept.
 */
private fun bedtimeDetail(state: InsightsUiState, score: ScoreWindow.Result): String {
    val window = state.bedtimeWindow
        ?: return "No bedtime is set, so this part stays neutral."

    val hours = "${DurationFormat.timeOfDay(window.startMinutesOfDay)}–" +
        DurationFormat.timeOfDay(window.endMinutesOfDay)

    if (score.isSingleDay) {
        val held = score.contributions
            .singleOrNull { it.kind == IntentScore.ContributionKind.BEDTIME }
            ?.tone
        return when (held) {
            IntentScore.ContributionTone.POSITIVE ->
                "Bedtime held — ${DurationFormat.compact(state.bedtimeUsedMs)} inside the " +
                    "$hours window."

            IntentScore.ContributionTone.NEGATIVE ->
                "${DurationFormat.compact(state.bedtimeUsedMs)} inside the $hours bedtime window."

            else ->
                "That night was not judged, so this part stays neutral."
        }
    }

    return when {
        score.nightsJudged == 0 ->
            "No stored bedtime verdict for these nights — the app keeps them for the last " +
                "sixty days."

        score.nightsHeld == score.nightsJudged ->
            "All ${score.nightsJudged} judged nights held the $hours window."

        else -> "${score.nightsHeld} of ${score.nightsJudged} judged nights held the " +
            "$hours window."
    }
}

private fun dayLabel(epochDay: Long): String = DAY_LABEL.format(LocalDate.ofEpochDay(epochDay))

/**
 * The day's own vitals — unlocks, focus and sessions — for a window that is one day.
 *
 * The same three the Today screen leads with, so a past day can be read the same way as the
 * one you are living in.
 */
@Composable
private fun VitalsPanel(vitals: WindowVitals) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatTile(
                value = vitals.unlockCount.toString(),
                caption = "unlocks",
                icon = Icons.Filled.Lock,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            StatTile(
                value = if (vitals.focusMs > 0) DurationFormat.compact(vitals.focusMs) else "0m",
                caption = "focused",
                icon = Icons.Filled.TouchApp,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            StatTile(
                value = vitals.sessionCount.toString(),
                caption = "sessions",
                icon = Icons.Filled.Schedule,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * When in the day the window's time went, summed across its days.
 *
 * A total says how much; this says when. A month that reads heavy because of three long
 * evenings looks nothing like one spread across every lunchtime, and every number above
 * would read the same either way.
 */
@Composable
private fun HoursPanel(state: InsightsUiState, appInfo: AppInfoProvider) {
    val peak = busiestHour(state.hourBuckets)
    val singleDay = state.range.days == 1L

    CapturablePanel(
        title = if (singleDay) "The shape of that day" else "When you're on your phone",
        fileName = "insights-hours-${state.range.name.lowercase()}",
        subtitle = if (singleDay) state.windowLabel else "Hours of the day across ${state.windowLabel}",
    ) {
        HourlyStrip(buckets = state.hourBuckets, labelOf = appInfo::label)

        if (peak != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (singleDay) {
                        "Busiest stretch was ${HourFormat.short(peak.hour)} at " +
                            DurationFormat.compact(peak.totalMs)
                    } else {
                        "Busiest hour: ${HourFormat.short(peak.hour)} · " +
                            "${DurationFormat.compact(peak.totalMs)} in total"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Mon–Sun, always over the same eight weeks rather than over the selected range.
 *
 * A weekday average drawn from one week is a single day in a costume, and it would swing
 * every time the user switched to "Today" — so this panel holds its own window and says so.
 */
@Composable
private fun WeekdayPanel(state: InsightsUiState) {
    val weekdayColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outlineVariant

    CapturablePanel(
        title = "Average by weekday",
        fileName = "insights-weekday",
        subtitle = "Last ${state.weekdayWeeks} weeks, tap a day for its average",
    ) {
        BarChart(
            bars = state.weekdayAverages.map { entry ->
                ChartBar(
                    label = entry.label,
                    value = entry.averageMs,
                    color = if (entry.sampleDays > 0) weekdayColor else emptyColor,
                )
            },
            valueFormat = { "avg ${DurationFormat.compact(it)}" },
            emptyMessage = "No weekday history yet",
        )

        Spacer(Modifier.height(Spacing.xs))

        WeekdayVsWeekend(state)
    }
}

@Composable
private fun WeekdayVsWeekend(state: InsightsUiState) {
    val hasBoth = state.weekdayAverageMs > 0L && state.weekendAverageMs > 0L
    val deltaMs = state.weekendAverageMs - state.weekdayAverageMs

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatTile(
            value = DurationFormat.compact(state.weekdayAverageMs),
            caption = "weekday average",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = if (hasBoth) {
                DurationFormat.compact(abs(deltaMs))
            } else {
                "—"
            },
            caption = if (hasBoth) {
                if (deltaMs > 0) "more on weekends" else "more on weekdays"
            } else {
                "not enough days"
            },
            modifier = Modifier.weight(1f),
            tint = if (hasBoth) {
                if (deltaMs > 0) dataColors.consumption else dataColors.production
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        StatTile(
            value = DurationFormat.compact(state.weekendAverageMs),
            caption = "weekend average",
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The range's biggest apps, one row each, each carrying the kind of time it took.
 *
 * Rows rather than columns: an app name is a word, and a sixth of a phone's width turns
 * "Instagram" into "In…" — a chart that cannot name what it is measuring is a puzzle, not
 * a finding. Every row opens that app's own history.
 */
@Composable
private fun TopAppsPanel(
    state: InsightsUiState,
    appInfo: AppInfoProvider,
    onOpenApp: (String) -> Unit,
) {
    val data = dataColors
    val maxMs = state.topApps.maxOfOrNull { it.totalMs } ?: 0L

    CapturablePanel(title = "Biggest apps in this range", fileName = "insights-top-apps") {
        state.topApps.forEachIndexed { index, app ->
            RankedAppRow(
                packageName = app.packageName,
                name = app.name,
                valueMs = app.totalMs,
                maxMs = maxMs,
                appInfo = appInfo,
                color = data.series[index % data.series.size],
                onClick = { onOpenApp(app.packageName) },
                category = app.category,
            )
        }
    }
}

@Composable
private fun CategoryPanel(state: InsightsUiState) {
    val data = dataColors
    val slices = state.categories.mapIndexed { index, total ->
        DonutSlice(
            label = total.name,
            value = total.totalMs,
            color = total.colorHex.toComposeColor(
                fallback = data.series[index % data.series.size],
            ),
        )
    }
    val total = slices.sumOf { it.value }

    CapturablePanel(title = "Where it goes", fileName = "insights-categories") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DonutChart(
                slices = slices,
                diameter = 124.dp,
                strokeWidth = 22.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = DurationFormat.compact(total),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                slices.take(MAX_LEGEND_ROWS).forEach { slice ->
                    LegendItem(
                        color = slice.color,
                        label = slice.label,
                        value = DurationFormat.compact(slice.value),
                    )
                }
                if (slices.size > MAX_LEGEND_ROWS) {
                    Text(
                        text = "+${slices.size - MAX_LEGEND_ROWS} more categories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The production ratio over time, as a percentage rather than a duration.
 *
 * TrendChart is reused deliberately: the shape is what matters, and a percentage line has
 * the same job as a time line — showing whether the habit is moving. Its axis is scaled in
 * points, not minutes, so the numbers above it mean what they say.
 */
@Composable
private fun ProductionRatioPanel(state: InsightsUiState, onOpenDay: (Long) -> Unit) {
    val latest = state.ratioTrend.lastOrNull()?.value ?: 0L
    val earliest = state.ratioTrend.firstOrNull()?.value ?: 0L
    val trend = latest - earliest

    CapturablePanel(title = "Production ratio", fileName = "insights-production-ratio") {
        TrendChart(
            points = state.ratioTrend,
            lineColor = dataColors.production,
            valueFormat = { "$it%" },
            axisCeiling = ::niceCeilPercent,
            axisLabel = { value: Long -> "$value%" },
            onPointActivated = { index -> state.ratioTrend.getOrNull(index)?.epochDay?.let(onOpenDay) },
            emptyMessage = "Not enough categorised time yet",
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = if (trend > 0) {
                "Up $trend points since the range began. Producing is gaining ground on " +
                    "consuming."
            } else if (trend < 0) {
                "Down ${abs(trend)} points since the range began. Worth a look at which " +
                    "app is taking the difference."
            } else {
                "Flat across the range — the split between producing and consuming has " +
                    "held steady."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The Phase 7 payoff: why things were opened, how often the question was answered, and
 * when.
 *
 * Shown whenever a single prompt exists in the range — *including* a range in which every
 * prompt was let close. Hiding the all-skipped case would hide the one thing this panel
 * is most honest about: how often the question went unanswered.
 */
@Composable
private fun IntentPanel(insight: IntentInsight) {
    val total = insight.answered + insight.skipped

    Panel(title = "Why you opened things") {
        if (insight.overall.isNotEmpty()) {
            ReasonRows(insight.overall)

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = "Follow-up is how long you stayed after answering. The gap between " +
                    "the reason and the reality is the whole point of asking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = if (insight.skipped > 0) {
                "You answered ${insight.answered} of $total prompts. The other " +
                    "${insight.skipped} you let close."
            } else {
                "You answered all $total prompts."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        HourClockStrip(insight.hourCounts)

        if (insight.topReasonLabel != null && insight.topReasonWindow != null) {
            Text(
                text = "Your '${insight.topReasonLabel}' opens cluster " +
                    "${insight.topReasonWindow}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One app's own reasons, when the range has more than one to separate. */
@Composable
private fun TopPackagePanel(label: String, summaries: List<IntentStats.Summary>) {
    Panel(title = "Why you opened $label") {
        ReasonRows(summaries)
    }
}

/**
 * The per-reason rows, shared between the overall panel and the per-app one so the two can
 * never render the same number two different ways.
 */
@Composable
private fun ReasonRows(summaries: List<IntentStats.Summary>) {
    val data = dataColors
    summaries.forEachIndexed { index, summary ->
        ReasonRow(
            summary = summary,
            color = data.series[index % data.series.size],
        )
    }
}

/** Colour dot, reason, count, and what the stated reason actually turned into. */
@Composable
private fun ReasonRow(summary: IntentStats.Summary, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = summary.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.sm),
        )
        Text(
            text = "${summary.count}×",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (summary.averageFollowUpMs > 0L) {
                " · avg ${DurationFormat.compact(summary.averageFollowUpMs)}"
            } else {
                ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How many prompts landed in each hour of the day.
 *
 * Deliberately not the shared HourlyStrip: that one colours each bar by the category kind
 * that dominated the hour, which says nothing about a reason, so its palette would be
 * meaningless here. Every bar here carries the same accent and only the height matters —
 * the shape of *when* things get opened, not what for.
 */
@Composable
private fun HourClockStrip(hourCounts: List<Int>, modifier: Modifier = Modifier) {
    val peak = hourCounts.maxOrNull() ?: 0

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hourCounts.forEach { count ->
                val fraction = if (peak > 0) count.toFloat() / peak.toFloat() else 0f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((44 * fraction).dp.coerceAtLeast(3.dp))
                        .clip(CircleShape)
                        .background(
                            if (count > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(0, 6, 12, 18, 23).forEach { hour ->
                Text(
                    text = HourFormat.short(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MAX_LEGEND_ROWS = 6

/** Names the days the score's footnote points at, without the year the reader knows. */
private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
