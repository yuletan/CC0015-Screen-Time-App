package com.intent.screentime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.ui.apps.label
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.toComposeColor

/**
 * Editing the vocabulary, not just the assignments.
 *
 * The seed categories are a starting point, and "Learning" meaning something different
 * to this user than to the next one is expected — so they can be renamed, re-kinded, or
 * supplemented. Kind is the field with real consequences: a category that counts as
 * producing shifts every chart, which is why it is spelled out in words rather than
 * implied by colour.
 */
@Composable
fun CategoryEditorScreen(
    state: CategoryEditorUiState,
    onBack: () -> Unit,
    onSave: (id: String, name: String, kind: CategoryKind, colorHex: String) -> Unit,
    onAdd: (name: String, kind: CategoryKind, colorHex: String) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<CategoryRow?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.gutter,
            end = Spacing.gutter,
            top = Spacing.sm,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f)) {
                    SectionEyebrow("Settings")
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { adding = true }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "New category",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item {
            Text(
                text = "A category's kind decides which side of the production/consumption " +
                    "split its apps count on. Changing one re-scores your recent history, " +
                    "so yesterday's numbers stay consistent with today's definition.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(state.rows, key = { it.category.id }) { row ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { editing = row },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(row.category.colorHex.toComposeColor()),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = row.category.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${row.category.kind.label()} · " +
                                if (row.appCount == 1) "1 app" else "${row.appCount} apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (row.category.isDefault) {
                        Text(
                            text = "DEFAULT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    editing?.let { row ->
        CategoryDialog(
            title = "Edit category",
            initialName = row.category.name,
            initialKind = row.category.kind,
            initialColor = row.category.colorHex,
            deleteLabel = if (row.category.isDefault) null else "Delete category",
            onDismiss = { editing = null },
            onConfirm = { name, kind, color ->
                onSave(row.category.id, name, kind, color)
                editing = null
            },
            onDelete = if (row.category.isDefault) {
                null
            } else {
                {
                    onDelete(row.category.id)
                    editing = null
                }
            },
        )
    }

    if (adding) {
        CategoryDialog(
            title = "New category",
            initialName = "",
            initialKind = CategoryKind.PRODUCTION,
            initialColor = CategoryEditorViewModel.PALETTE.first(),
            deleteLabel = null,
            onDismiss = { adding = false },
            onConfirm = { name, kind, color ->
                onAdd(name, kind, color)
                adding = false
            },
            onDelete = null,
        )
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    initialName: String,
    initialKind: CategoryKind,
    initialColor: String,
    deleteLabel: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, CategoryKind, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(initialKind) }
    var color by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionEyebrow("Counts as")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    CategoryKind.entries.forEach { option ->
                        val selected = option == kind
                        Surface(
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.clickable { kind = option },
                        ) {
                            Text(
                                text = option.label(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 6.dp,
                                ),
                            )
                        }
                    }
                }

                SectionEyebrow("Colour")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CategoryEditorViewModel.PALETTE.forEach { hex ->
                        val selected = hex == color
                        Box(
                            Modifier
                                .size(if (selected) 30.dp else 26.dp)
                                .clip(CircleShape)
                                .background(hex.toComposeColor())
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { color = hex },
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Category colours are picked from a fixed palette so the charts " +
                        "stay readable in both themes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), kind, color) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (onDelete != null && deleteLabel != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = deleteLabel,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
