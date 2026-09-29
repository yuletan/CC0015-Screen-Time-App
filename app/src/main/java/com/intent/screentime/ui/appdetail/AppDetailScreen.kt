package com.intent.screentime.ui.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.stats.AppUsageInsight
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.components.AppIcon
import com.intent.screentime.ui.components.CapturablePanel
import com.intent.screentime.ui.components.CategoryChip
import com.intent.screentime.ui.components.CategoryPickerDialog
import com.intent.screentime.ui.components.ChartRange
import com.intent.screentime.ui.components.ChartRangeSelector
import com.intent.screentime.ui.components.ChartUnit
import com.intent.screentime.ui.components.displayLabel
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.SetCapDialog
import com.intent.screentime.ui.components.StatTile
import com.intent.screentime.ui.components.TrendChart
import com.intent.screentime.ui.components.WindowStepper
import com.intent.screentime.ui.components.categoryColor
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

@Composable
fun AppDetailScreen(
    state: AppDetailUiState,
    appInfo: AppInfoProvider,
    onBack: () -> Unit,
    onSelectRange: (ChartRange) -> Unit,
    onPreviousWindow: () -> Unit,
    onNextWindow: () -> Unit,
    onSetCap: (Int?) -> Unit,
    onSetCategory: (String) -> Unit,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCapDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(Spacing.xs))
                AppIcon(packageName = state.packageName, provider = appInfo, size = 44.dp)
                Spacer(Modifier.size(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = appInfo.label(state.packageName),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // The window's own dates once it exists: which seven days the
                        // chart is showing is the first thing the reader needs.
                        text = state.windowLabel.ifEmpty { state.range.caption },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.category != null) {
                        val category = state.category
                        Spacer(Modifier.height(Spacing.xs))
                        CategoryChip(
                            name = category.name,
                            color = categoryColor(category.kind, dataColors),
                            kind = category.kind,
                            categoryId = category.id,
                        )
                    } else {
                        Spacer(Modifier.height(Spacing.xs))
                        CategoryChip(
                            name = "Unsorted",
                            color = dataColors.neutral.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }

        item {
            ChartRangeSelector(selected = state.range, onSelect = onSelectRange)
        }

        item {
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

        // Nothing below is known until the first read lands; zeros and an empty chart
        // would read as "this app was not used", which is a different statement.
        if (state.loading) return@LazyColumn

        item {
            val hourly = state.unit == ChartUnit.HOUR
            val weekly = state.unit == ChartUnit.WEEK

            CapturablePanel(
                title = "Trend",
                fileName = "appdetail-trend-${state.range.name.lowercase()}",
                subtitle = when {
                    hourly && state.isPastWindow ->
                        "${state.windowLabel}. Tap or drag across the chart to read any hour."
                    hourly -> "Tap or drag across the chart to read any hour."
                    weekly -> "Tap or drag across the chart to read any week."
                    else -> "Tap or drag across the chart to read any day."
                },
            ) {
                TrendChart(
                    points = state.trend,
                    lineColor = dataColors.consumption,
                    onPointActivated = { index ->
                        state.trend.getOrNull(index)?.epochDay?.let(onOpenDay)
                    },
                    target = state.trendTarget,
                    emptyMessage = if (hourly) {
                        if (state.isPastWindow) "Nothing recorded that day" else "Nothing recorded yet today"
                    } else {
                        "Not used in this range"
                    },
                )

                if (state.trendTarget != null) {
                    Text(
                        text = "Dashed line: the daily cap you set for this app.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(Spacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (hourly) {
                        StatTile(
                            value = DurationFormat.compact(state.totalMs),
                            caption = if (state.isPastWindow) "used that day" else "used today",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = state.busiest?.let { DurationFormat.compact(it.value) } ?: "—",
                            caption = state.busiest
                                ?.let { "busiest hour · ${it.label}" }
                                ?: "busiest hour",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = state.sessionCount.toString(),
                            caption = "sessions",
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        StatTile(
                            value = DurationFormat.compact(state.todayMs),
                            caption = if (state.isPastWindow) "on the last day" else "today",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = DurationFormat.compact(state.averageMs),
                            caption = "per active day",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = DurationFormat.compact(state.totalMs),
                            caption = "in ${state.range.days} days",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        state.insight?.let { insight ->
            item {
                AppInsightPanel(
                    insight = insight,
                    onAction = when (insight.action) {
                        AppUsageInsight.Action.SET_CAP,
                        AppUsageInsight.Action.CHANGE_CAP -> ({ showCapDialog = true })
                        AppUsageInsight.Action.CHOOSE_CATEGORY -> ({ showCategoryDialog = true })
                        null -> null
                    },
                )
            }
        }

        item {
            Panel(title = "This app in your day") {
                RowLine(
                    title = "Daily cap",
                    value = state.capMinutes?.let { DurationFormat.compact(it * 60_000L) }
                        ?: "Not set",
                    actionLabel = if (state.capMinutes == null) "Set" else "Change",
                    onAction = { showCapDialog = true },
                )
                RowLine(
                    title = "Category",
                    value = state.category?.let {
                        if (it.id == "uncategorized") {
                            "Unsorted"
                        } else {
                            "${it.name} · ${it.kind.displayLabel()}"
                        }
                    } ?: "Unsorted",
                    actionLabel = "Change",
                    onAction = { showCategoryDialog = true },
                )
                RowLine(
                    title = "Sessions",
                    value = state.sessionCount.toString(),
                    actionLabel = null,
                    onAction = {},
                )
                Text(
                    text = "Caps are measured per day, and a breach is announced once, " +
                        "on the day it happens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showCapDialog) {
        SetCapDialog(
            currentMinutes = state.capMinutes,
            onDismiss = { showCapDialog = false },
            onConfirm = {
                onSetCap(it)
                showCapDialog = false
            },
        )
    }

    if (showCategoryDialog) {
        CategoryPickerDialog(
            title = appInfo.label(state.packageName),
            categories = state.categories,
            currentCategoryId = state.category?.id,
            onDismiss = { showCategoryDialog = false },
            onPick = {
                onSetCategory(it)
                showCategoryDialog = false
            },
        )
    }
}

@Composable
private fun AppInsightPanel(
    insight: AppUsageInsight,
    onAction: (() -> Unit)?,
) {
    Panel(title = insight.title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = "Usage insight",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = insight.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(
                            text = when (insight.action) {
                                AppUsageInsight.Action.SET_CAP -> "Set a cap"
                                AppUsageInsight.Action.CHANGE_CAP -> "Change cap"
                                AppUsageInsight.Action.CHOOSE_CATEGORY -> "Choose a category"
                                null -> "Open"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

/** A setting's name, its current value, and the way to change it. */
@Composable
private fun RowLine(
    title: String,
    value: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionEyebrow(text = title, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = Spacing.sm),
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
