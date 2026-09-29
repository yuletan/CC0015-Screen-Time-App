package com.intent.screentime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.ui.theme.Spacing

/** Below a quarter of an hour there is nothing left worth rationing. */
private const val MIN_TARGET_MINUTES = 15

/** Twelve hours is more than anyone is awake for; past that a cap is not a cap. */
private const val MAX_TARGET_MINUTES = 12 * 60

/**
 * An exact amount of time, for the commitments where the round numbers are not the answer.
 *
 * A stepper rather than a clock face: a cap of 2h 45m is a length of time, not a moment, and
 * a dial that reads "2:45" invites the reading that it means a quarter to three. Two rows
 * of plus and minus keep the number honest and make the awkward values — the 3h 10m someone
 * actually meant — as easy to reach as 3h.
 *
 * Held as a single total rather than an hours field and a minutes field, which is what makes
 * the carry free: one more five-minute step past 3h 55m reads "4h" without any code that
 * knows it should.
 */
@Composable
fun DurationPickerDialog(
    title: String,
    description: String,
    currentMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
    initialMinutes: Int = 180,
    stepMinutes: Int = 5,
) {
    var total by remember {
        mutableStateOf((currentMinutes ?: initialMinutes).coerceIn(MIN_TARGET_MINUTES, MAX_TARGET_MINUTES))
    }

    fun nudge(delta: Int) {
        total = (total + delta).coerceIn(MIN_TARGET_MINUTES, MAX_TARGET_MINUTES)
    }

    val canShorten = total > MIN_TARGET_MINUTES
    val canLengthen = total < MAX_TARGET_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = DurationFormat.compact(total * 60_000L),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )

                StepperRow(
                    label = "Hours",
                    step = 60,
                    onStep = ::nudge,
                    canShorten = canShorten,
                    canLengthen = canLengthen,
                )
                StepperRow(
                    label = "Minutes",
                    step = stepMinutes,
                    onStep = ::nudge,
                    canShorten = canShorten,
                    canLengthen = canLengthen,
                )

                Text(
                    text = "From ${DurationFormat.compact(MIN_TARGET_MINUTES * 60_000L)} to " +
                        "${DurationFormat.compact(MAX_TARGET_MINUTES * 60_000L)}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(total) }) { Text("Set") }
        },
        dismissButton = {
            if (currentMinutes != null) {
                TextButton(onClick = { onConfirm(null) }) { Text("Remove") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun StepperRow(
    label: String,
    step: Int,
    onStep: (Int) -> Unit,
    canShorten: Boolean,
    canLengthen: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = Spacing.xs),
        )
        // Both ends are disabled at the bounds rather than silently clamped, so a tap that
        // does nothing is visibly a tap that cannot do anything.
        IconButton(onClick = { onStep(-step) }, enabled = canShorten) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = "Less $label",
                tint = if (canShorten) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            )
        }
        IconButton(onClick = { onStep(step) }, enabled = canLengthen) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "More $label",
                tint = if (canLengthen) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            )
        }
    }
}
