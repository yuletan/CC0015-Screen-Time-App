package com.intent.screentime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.screentime.ui.theme.Spacing

private val CAP_PRESETS_MINUTES = listOf(120, 180, 240, 300, 360, 480)

/**
 * Picks the daily screen-time cap.
 *
 * Presets rather than a free slider on purpose: a cap is a decision about a habit, and
 * choosing from round options is faster than dragging a number to 3h47m that nobody means.
 * What the presets must not be is the *only* way through — someone whose day is shaped
 * around 2h 30m should be able to say so without rounding to a figure they did not choose —
 * so *Custom…* hands over to [DurationPickerDialog]. Passing null clears the cap entirely.
 */
@Composable
fun SetCapDialog(
    currentMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    var custom by remember { mutableStateOf(false) }

    if (custom) {
        DurationPickerDialog(
            title = "Daily screen time cap",
            description = "The ring measures today against this. Pick the point where you " +
                "would want to be told you have had enough.",
            currentMinutes = currentMinutes,
            initialMinutes = currentMinutes ?: 180,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily screen time cap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "The ring measures today against this. Pick the point where you " +
                        "would want to be told you have had enough.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    CAP_PRESETS_MINUTES.chunked(3).forEach { row ->
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
                                        text = "${minutes / 60}h",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.padding(
                                            horizontal = 18.dp,
                                            vertical = 8.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { custom = true }) {
                    Text("Custom…", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            if (currentMinutes != null) {
                TextButton(onClick = { onConfirm(null) }) { Text("Remove cap") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
