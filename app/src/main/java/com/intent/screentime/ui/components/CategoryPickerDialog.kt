package com.intent.screentime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.intent.screentime.data.local.entity.CategoryEntity
import com.intent.screentime.ui.components.displayLabel
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.toComposeColor

/**
 * "Which side of the split does this app belong on?"
 *
 * Shared between the Apps list and an app's own screen so the choice looks and behaves
 * identically wherever it is made.
 */
@Composable
fun CategoryPickerDialog(
    title: String,
    categories: List<CategoryEntity>,
    currentCategoryId: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Which side of the split does this app belong on?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                categories.forEach { category ->
                    val selected = category.id == currentCategoryId
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) {
                            category.colorHex.toComposeColor().copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(category.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = Spacing.sm,
                                vertical = 10.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(category.colorHex.toComposeColor()),
                            )
                            Text(
                                text = if (category.id == "uncategorized") "Unsorted" else category.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (category.id == "uncategorized") {
                                    "Not sorted"
                                } else {
                                    category.kind.displayLabel()
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
