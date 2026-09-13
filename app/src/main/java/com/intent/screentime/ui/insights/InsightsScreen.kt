package com.intent.screentime.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.ui.components.BarColumn
import com.intent.screentime.ui.components.ColumnChart
import com.intent.screentime.ui.components.DonutChart
import com.intent.screentime.ui.components.DonutSlice
import com.intent.screentime.ui.components.EmptyState
import com.intent.screentime.ui.components.LegendItem
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.StatTile
import com.intent.screentime.ui.components.TrendChart
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors
import com.intent.screentime.ui.theme.toComposeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onSelectRange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                RangeSelector(selected = state.rangeDays, onSelect = onSelectRange)
            }
        }

        if (!state.hasData) {
            item {
                Panel {
                    EmptyState(
                        icon = Icons.Filled.Insights,
                        title = "Not enough history yet",
                        body = "Intent needs a couple of days of usage before trends mean " +
                            "anything. Come back tomorrow and this fills in.",
                    )
                }
            }
            return@LazyColumn
        }

        item { TrendPanel(state) }
        item { WeekdayPanel(state) }
        if (state.topApps.isNotEmpty()) {
            item { TopAppsPanel(state) }
        }
        item { CategoryPanel(state) }
        if (state.hasRatioTrend) {
            item { ProductionRatioPanel(state) }
        }
        if (state.intents.isNotEmpty()) {
            item { IntentPanel(state) }
        }
    }
}

@Composable
private fun RangeSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(
            InsightsViewModel.DEFAULT_RANGE_DAYS to "7 days",
            InsightsViewModel.LONG_RANGE_DAYS to "30 days",
        ).forEach { (days, label) ->
            val active = selected == days
            Surface(
                shape = CircleShape,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.clickable { onSelect(days) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TrendPanel(state: InsightsUiState) {
    val first = state.points.firstOrNull()?.epochDay
    val last = state.points.lastOrNull()?.epochDay
    val formatter = DateTimeFormatter.ofPattern("d MMM")

    Panel(title = "Screen time per day") {
        TrendChart(
            values = state.points.map { it.screenTimeMs },
            lineColor = MaterialTheme.colorScheme.primary,
            startLabel = first?.let { LocalDate.ofEpochDay(it).format(formatter) },
            endLabel = last?.let { LocalDate.ofEpochDay(it).format(formatter) },
            peakLabel = state.points.maxOfOrNull { it.screenTimeMs }
                ?.takeIf { it > 0 }
                ?.let { "peak ${DurationFormat.compact(it)}" },
        )

        Spacer(Modifier.height(Spacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatTile(
                value = DurationFormat.compact(state.averageMs),
                caption = "daily average",
            )
            StatTile(
                value = state.best?.let { DurationFormat.compact(it.screenTimeMs) } ?: "—",
                caption = "heaviest day",
                tint = dataColors.consumption,
            )
            StatTile(
                value = state.worst?.let { DurationFormat.compact(it.screenTimeMs) } ?: "—",
                caption = "lightest day",
                tint = dataColors.production,
            )
        }
    }
}

@Composable
private fun WeekdayPanel(state: InsightsUiState) {
    val peak = state.weekdayAverages.maxOfOrNull { it.second } ?: 0L

    Panel(title = "Average by weekday") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            state.weekdayAverages.forEach { (label, value) ->
                val fraction = if (peak > 0L) value.toFloat() / peak.toFloat() else 0f
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height((76 * fraction).dp.coerceAtLeast(4.dp))
                            .clip(CircleShape)
                            .background(
                                if (value > 0L) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

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
            tint = if (hasBoth) {
                if (deltaMs > 0) dataColors.consumption else dataColors.production
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        StatTile(
            value = DurationFormat.compact(state.weekendAverageMs),
            caption = "weekend average",
        )
    }
}

@Composable
private fun TopAppsPanel(state: InsightsUiState) {
    val data = dataColors

    Panel(title = "Biggest apps in this range") {
        ColumnChart(
            columns = state.topApps.mapIndexed { index, app ->
                BarColumn(
                    label = app.name,
                    value = app.totalMs,
                    color = data.series[index % data.series.size],
                )
            },
        )
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

    Panel(title = "Where it goes") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            DonutChart(
                slices = slices,
                diameter = 148.dp,
                strokeWidth = 24.dp,
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
 * TrendChart is reused deliberately: the shape is what matters, and a percentage line
 * has the same job as a time line — showing whether the habit is moving.
 */
@Composable
private fun ProductionRatioPanel(state: InsightsUiState) {
    val peak = state.productionRatios.maxOrNull() ?: 0L
    val latest = state.productionRatios.lastOrNull() ?: 0L
    val earliest = state.productionRatios.firstOrNull() ?: 0L
    val trend = latest - earliest

    Panel(title = "Production ratio") {
        TrendChart(
            values = state.productionRatios,
            lineColor = dataColors.production,
            startLabel = "first day",
            endLabel = "latest",
            peakLabel = "peak $peak%",
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
 * The Phase 7 payoff: the stated reason, and the session that followed it.
 *
 * Nothing here is shown until the intent prompt has been switched on and answered at
 * least once, which keeps the screen honest for everyone who never opted in.
 */
@Composable
private fun IntentPanel(state: InsightsUiState) {
    val data = dataColors

    Panel(title = "What you came for") {
        state.intents.forEachIndexed { index, summary ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(data.series[index % data.series.size]),
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

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = "Follow-up is how long you stayed after answering. The gap between the " +
                "reason and the reality is the whole point of asking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MAX_LEGEND_ROWS = 6