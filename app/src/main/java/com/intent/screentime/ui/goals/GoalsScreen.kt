package com.intent.screentime.ui.goals

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.local.entity.TargetType
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.ProgressRing
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.StreakHeatmap
import com.intent.screentime.ui.components.StreakHeatmapLegend
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

/**
 * The Goals board.
 *
 * Ordered by what actually motivates: the streak first, because it is the thing the
 * user is protecting, then the weekly commitments whose progress is worth watching, and
 * only then the two daily values that are steady state.
 */
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    onSetTarget: (TargetType, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<TargetType?>(null) }

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
                SectionEyebrow("Momentum")
                Text(
                    text = "Goals",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        item { StreakPanel(state) }

        item {
            WeeklyGoalPanel(
                title = "Weekly screen time cap",
                caption = "Your week against the ceiling you set",
                usedMs = state.weekScreenTimeMs,
                targetMs = state.weeklyCapMs,
                progress = state.weeklyCapProgress,
                over = state.overWeeklyCap,
                emptyHint = "No weekly cap yet. Set one and this ring starts counting down " +
                    "the week for you.",
                onEdit = { editing = TargetType.WEEKLY_SCREEN_TIME_CAP },
            )
        }

        item {
            WeeklyGoalPanel(
                title = "Weekly production goal",
                caption = "Producing rather than consuming",
                usedMs = state.weekProductionMs,
                targetMs = state.weeklyGoalMs,
                progress = state.weeklyGoalProgress,
                over = false,
                emptyHint = "No production goal yet. Set the number of hours you want to " +
                    "spend producing each week.",
                onEdit = { editing = TargetType.WEEKLY_PRODUCTION_GOAL },
            )
        }

        item {
            Panel(title = "Daily commitments") {
                TargetRow(
                    title = "Daily screen time cap",
                    value = state.targets.dailyCapMinutes?.let {
                        DurationFormat.compact(it * 60_000L)
                    },
                    onChange = { editing = TargetType.DAILY_SCREEN_TIME_CAP },
                )
                TargetRow(
                    title = "Daily focus goal",
                    value = state.targets.dailyFocusGoalMinutes?.let {
                        "${DurationFormat.compact(it * 60_000L)} · " +
                            "${DurationFormat.compact(state.todayFocusMs)} today"
                    },
                    onChange = { editing = TargetType.DAILY_FOCUS_GOAL },
                )
            }
        }
    }

    val editingType = editing
    if (editingType != null) {
        MinutesDialog(
            config = configFor(editingType),
            currentMinutes = currentMinutesFor(state, editingType),
            onDismiss = { editing = null },
            onConfirm = { minutes ->
                onSetTarget(editingType, minutes)
                editing = null
            },
        )
    }
}

@Composable
private fun StreakPanel(state: GoalsUiState) {
    val data = dataColors

    Panel(title = "Streak") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = if (state.currentStreak > 0) data.consumption else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(34.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (state.currentStreak) {
                        0 -> "No streak yet"
                        1 -> "1 day inside the cap"
                        else -> "${state.currentStreak} days inside the cap"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.bestStreak > 0) {
                        "Best run ${state.bestStreak} days · best day score ${state.bestDayScore}"
                    } else {
                        "Days land here once the nightly pass has judged them."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))

        StreakHeatmap(weeks = state.heatmap)

        Spacer(Modifier.height(Spacing.xs))

        StreakHeatmapLegend()
    }
}

@Composable
private fun WeeklyGoalPanel(
    title: String,
    caption: String,
    usedMs: Long,
    targetMs: Long?,
    progress: Float,
    over: Boolean,
    emptyHint: String,
    onEdit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Panel(title = title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ProgressRing(
                progress = if (targetMs == null) 0f else progress,
                color = if (over) scheme.error else scheme.primary,
                diameter = 104.dp,
                strokeWidth = 11.dp,
            ) {
                Text(
                    text = if (targetMs != null) {
                        "${(progress.coerceIn(0f, 9.99f) * 100).toInt()}%"
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (targetMs == null) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "${DurationFormat.compact(usedMs)} of " +
                            DurationFormat.compact(targetMs),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = when {
                            over -> "${DurationFormat.compact(usedMs - targetMs)} over this week"
                            else -> "${DurationFormat.compact(targetMs - usedMs)} left this week"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) scheme.error else scheme.onSurfaceVariant,
                    )
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        TextButton(onClick = onEdit) {
            Text(
                text = if (targetMs == null) "Set a target" else "Change",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun TargetRow(title: String, value: String?, onChange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value ?: "Not set",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onChange) {
            Text(
                text = if (value == null) "Set" else "Change",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private data class DialogConfig(
    val title: String,
    val description: String,
    val presetsMinutes: List<Int>,
)

private fun configFor(type: TargetType): DialogConfig = when (type) {
    TargetType.WEEKLY_SCREEN_TIME_CAP -> DialogConfig(
        title = "Weekly screen time cap",
        description = "The whole week, not a day. Intent judges each evening against a " +
            "seventh of this, so the streak still means something daily.",
        presetsMinutes = listOf(14, 21, 28, 35, 42, 49).map { it * 60 },
    )

    TargetType.WEEKLY_PRODUCTION_GOAL -> DialogConfig(
        title = "Weekly production goal",
        description = "Hours of producing rather than consuming, each week. Aim for a " +
            "number you can hit in a normal week, not a heroic one.",
        presetsMinutes = listOf(5, 7, 10, 14, 20).map { it * 60 },
    )

    TargetType.DAILY_FOCUS_GOAL -> DialogConfig(
        title = "Daily focus goal",
        description = "Completed focus sessions count towards this.",
        presetsMinutes = listOf(25, 45, 60, 90, 120),
    )

    TargetType.DAILY_SCREEN_TIME_CAP -> DialogConfig(
        title = "Daily screen time cap",
        description = "The ring on the Today screen measures the day against this.",
        presetsMinutes = listOf(120, 180, 240, 300, 360, 480),
    )

    TargetType.PER_APP_DAILY_CAP -> DialogConfig(
        title = "App cap",
        description = "Set from an app's own screen.",
        presetsMinutes = listOf(15, 30, 45, 60, 90),
    )
}

private fun currentMinutesFor(state: GoalsUiState, type: TargetType): Int? = when (type) {
    TargetType.WEEKLY_SCREEN_TIME_CAP -> state.targets.weeklyCapMinutes
    TargetType.WEEKLY_PRODUCTION_GOAL -> state.targets.weeklyProductionGoalMinutes
    TargetType.DAILY_FOCUS_GOAL -> state.targets.dailyFocusGoalMinutes
    TargetType.DAILY_SCREEN_TIME_CAP -> state.targets.dailyCapMinutes
    TargetType.PER_APP_DAILY_CAP -> null
}

/**
 * Presets rather than a slider, for the same reason as the cap dialog: a commitment is a
 * decision, and picking from round numbers is faster than dragging to a figure nobody
 * meant. Null clears the target.
 */
@Composable
private fun MinutesDialog(
    config: DialogConfig,
    currentMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(config.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = config.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    config.presetsMinutes.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            row.forEach { minutes ->
                                val selected = currentMinutes == minutes
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier.clickable { onConfirm(minutes) },
                                ) {
                                    Text(
                                        text = DurationFormat.compact(minutes * 60_000L),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (currentMinutes != null) {
                TextButton(onClick = { onConfirm(null) }) { Text("Remove") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
