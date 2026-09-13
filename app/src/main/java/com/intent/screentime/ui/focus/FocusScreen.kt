package com.intent.screentime.ui.focus

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.local.entity.FocusSessionEntity
import com.intent.screentime.ui.components.EmptyState
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.StatTile
import com.intent.screentime.ui.components.TimeRing
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PRESETS_MINUTES = listOf(25, 45, 90)
private val GOAL_PRESETS_MINUTES = listOf(30, 60, 90, 120)

/**
 * The focus timer.
 *
 * One instrument at a time: while a session runs the presets disappear and the ring is
 * the whole screen, because a timer surrounded by things to tap is a timer that invites
 * you to leave it.
 */
@Composable
fun FocusScreen(
    state: FocusUiState,
    onStart: (Long) -> Unit,
    onCancel: () -> Unit,
    onSetGoal: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustom by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }

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
                SectionEyebrow("Sessions")
                Text(
                    text = "Focus",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        item {
            if (state.running) {
                RunningPanel(state = state, onCancel = onCancel)
            } else {
                IdlePanel(
                    onStart = onStart,
                    onCustom = { showCustom = true },
                )
            }
        }

        item {
            TodayPanel(
                state = state,
                onSetGoal = { showGoal = true },
            )
        }

        item {
            SectionEyebrow("Recent sessions")
        }

        if (state.history.isEmpty()) {
            item {
                Panel {
                    EmptyState(
                        icon = Icons.Filled.Timer,
                        title = "No sessions yet",
                        body = "A focus session is a stretch of time you claim for one " +
                            "thing. Completed minutes count towards your day and your goal.",
                    )
                }
            }
        } else {
            items(state.history, key = { it.id }) { session ->
                HistoryRow(session)
            }
        }
    }

    if (showCustom) {
        CustomMinutesDialog(
            onDismiss = { showCustom = false },
            onConfirm = { minutes ->
                onStart(minutes * 60_000L)
                showCustom = false
            },
        )
    }

    if (showGoal) {
        GoalDialog(
            currentMinutes = state.goalMinutes,
            onDismiss = { showGoal = false },
            onConfirm = {
                onSetGoal(it)
                showGoal = false
            },
        )
    }
}

@Composable
private fun IdlePanel(onStart: (Long) -> Unit, onCustom: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    Panel(title = "Start a session") {
        Text(
            text = "Pick a length. The timer runs in the notification while you get on " +
                "with the thing you actually sat down to do.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PRESETS_MINUTES.forEach { minutes ->
                Surface(
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh,
                    modifier = Modifier.clickable { onStart(minutes * 60_000L) },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "$minutes",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = "min",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedButton(onClick = onCustom) {
                Text("Custom", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun RunningPanel(state: FocusUiState, onCancel: () -> Unit) {
    val data = dataColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TimeRing(
            usedMs = state.elapsedMs,
            targetMs = state.plannedMs,
            diameter = 248.dp,
            strokeWidth = 22.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = DurationFormat.clock(state.remainingMs),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = state.label ?: "Focus session",
            style = MaterialTheme.typography.titleMedium,
            color = data.production,
        )

        Spacer(Modifier.height(Spacing.sm))

        OutlinedButton(onClick = onCancel) {
            Text("End early", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TodayPanel(state: FocusUiState, onSetGoal: () -> Unit) {
    val data = dataColors
    val scheme = MaterialTheme.colorScheme

    Panel(title = "Today") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatTile(
                value = DurationFormat.compact(state.todayFocusMs),
                caption = "focused",
                tint = data.production,
            )
            StatTile(
                value = state.goalMinutes?.let { DurationFormat.compact(it * 60_000L) } ?: "—",
                caption = if (state.metGoal) "goal met" else "daily goal",
                tint = if (state.metGoal) data.production else scheme.onSurface,
            )
            StatTile(
                value = state.streakDays.toString(),
                caption = "day streak",
                tint = if (state.streakDays > 0) data.consumption else scheme.onSurface,
                icon = Icons.Filled.LocalFireDepartment,
            )
        }

        Spacer(Modifier.height(Spacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    state.goalMinutes == null ->
                        "Set a daily focus goal and completed sessions count towards it."
                    state.metGoal -> "Goal met for today."
                    else -> "${DurationFormat.compact(state.goalMinutes * 60_000L - state.todayFocusMs)} " +
                        "left to hit today's goal."
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSetGoal) {
                Text(
                    text = if (state.goalMinutes == null) "Set goal" else "Change",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(session: FocusSessionEntity) {
    val data = dataColors
    val end = session.endMs ?: return
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(session.startMs).atZone(zone)
    val isToday = date.toLocalDate() == LocalDate.now(zone)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (session.completed) data.production else data.neutral,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isToday) {
                        "Today at ${date.format(TIME_FORMAT)}"
                    } else {
                        date.format(DATE_FORMAT)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (session.completed) {
                        "Completed · ${DurationFormat.compact(session.plannedMs)}"
                    } else {
                        "Ended early · ${DurationFormat.compact((end - session.startMs).coerceAtLeast(0L))}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (session.completed) {
                Text(
                    text = DurationFormat.compact(session.plannedMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = data.production,
                )
            }
        }
    }
}

@Composable
private fun CustomMinutesDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf("30") }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in 5..180

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "Between 5 and 180 minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { minutes?.let(onConfirm) },
                enabled = valid,
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun GoalDialog(
    currentMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily focus goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "Completed sessions count towards this, and so does the score.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    GOAL_PRESETS_MINUTES.forEach { minutes ->
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
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
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

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
