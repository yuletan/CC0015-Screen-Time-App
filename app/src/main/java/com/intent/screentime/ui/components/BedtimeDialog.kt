package com.intent.screentime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.usage.BedtimeWindow
import com.intent.screentime.ui.theme.Spacing

/** Round windows first, the same way every other commitment offers its likely answers. */
private val BEDTIME_PRESETS = listOf(
    22 * 60 to 6 * 60,
    23 * 60 to 7 * 60,
    23 * 60 + 30 to 6 * 60 + 30,
    0 to 7 * 60,
)

private const val DEFAULT_BEDTIME_START = 23 * 60
private const val DEFAULT_BEDTIME_END = 7 * 60

private enum class BedtimeStep { START, END }

/**
 * The bedtime window.
 *
 * One `TimePicker` reused rather than two stacked: two clock faces side by side do not fit
 * a phone dialog, and a pair of dials on one screen is harder to read than one at a time
 * with the result shown above it.
 *
 * Shared between the Goals board and Settings, because the window is one commitment with
 * one editor: a second copy of these rules is a second place for them to drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedtimeDialog(
    current: BedtimeWindow?,
    onDismiss: () -> Unit,
    onConfirm: (Int?, Int?) -> Unit,
) {
    var start by remember { mutableStateOf(current?.startMinutesOfDay ?: DEFAULT_BEDTIME_START) }
    var end by remember { mutableStateOf(current?.endMinutesOfDay ?: DEFAULT_BEDTIME_END) }
    var step by remember { mutableStateOf(BedtimeStep.START) }

    // Keyed on the step so the dial is rebuilt at the other end of the window rather than
    // keeping the wheel position it was left at.
    val picking = if (step == BedtimeStep.START) start else end
    val pickerState = key(step) {
        rememberTimePickerState(
            initialHour = picking / 60,
            initialMinute = picking % 60,
            is24Hour = true,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == BedtimeStep.START) "Bedtime" else "Wake up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = if (step == BedtimeStep.START) {
                        "The hour you would rather be off the phone. A night is judged with " +
                            "the evening it starts on."
                    } else {
                        "When the window ends. Up to five minutes inside it still keeps the " +
                            "night — enough to set an alarm, not enough to scroll."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    BEDTIME_PRESETS.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            row.forEach { (presetStart, presetEnd) ->
                                val selected = start == presetStart && end == presetEnd
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier.clickable {
                                        onConfirm(presetStart, presetEnd)
                                    },
                                ) {
                                    Text(
                                        text = "${DurationFormat.timeOfDay(presetStart)}–" +
                                            DurationFormat.timeOfDay(presetEnd),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 8.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "${DurationFormat.timeOfDay(start)} – " +
                        DurationFormat.timeOfDay(end),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = pickerState.hour * 60 + pickerState.minute
                    if (step == BedtimeStep.START) {
                        start = picked
                        step = BedtimeStep.END
                    } else {
                        onConfirm(start, picked)
                    }
                },
            ) {
                Text(if (step == BedtimeStep.START) "Next" else "Set")
            }
        },
        dismissButton = {
            when {
                step == BedtimeStep.END ->
                    TextButton(onClick = { step = BedtimeStep.START }) { Text("Back") }

                current != null ->
                    TextButton(onClick = { onConfirm(null, null) }) { Text("Remove") }

                else ->
                    TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
