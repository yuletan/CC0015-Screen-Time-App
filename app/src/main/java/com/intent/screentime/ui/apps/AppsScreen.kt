package com.intent.screentime.ui.apps

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.ui.components.AppIcon
import com.intent.screentime.ui.components.CategoryChip
import com.intent.screentime.ui.components.EmptyState
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.Sparkline
import com.intent.screentime.ui.components.categoryColor
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors
import com.intent.screentime.ui.theme.toComposeColor

@Composable
fun AppsScreen(
    state: AppsUiState,
    appInfo: AppInfoProvider,
    onOpenApp: (String) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Column(Modifier.padding(bottom = Spacing.xs)) {
                SectionEyebrow("Today")
                Text(
                    text = "Apps",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = if (state.rows.isEmpty()) {
                        "Nothing tracked yet today."
                    } else {
                        "${state.rows.size} apps · ${DurationFormat.compact(state.totalMs)} total" +
                            if (state.uncategorisedCount > 0) {
                                " · ${state.uncategorisedCount} need sorting"
                            } else {
                                ""
                            }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.rows.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Apps,
                    title = "No app usage yet",
                    body = "Once you use your phone, every app you open shows up here with " +
                        "the time it took and the category it belongs to.",
                )
            }
            return@LazyColumn
        }

        if (state.uncategorisedCount > 0) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "Tap an app to see its month, set a cap or sort its category. " +
                            "Producing and consuming are what the split on the Today screen " +
                            "is built from.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
            }
        }

        items(state.rows, key = { it.packageName }) { row ->
            AppRow(
                row = row,
                maxMs = state.maxMs,
                appInfo = appInfo,
                onClick = { onOpenApp(row.packageName) },
            )
        }
    }
}

@Composable
private fun AppRow(
    row: AppListRow,
    maxMs: Long,
    appInfo: AppInfoProvider,
    onClick: () -> Unit,
) {
    val fraction = if (maxMs > 0L) row.totalMs.toFloat() / maxMs.toFloat() else 0f
    val data = dataColors
    val barColor = categoryColor(row.category?.kind, data)

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AppIcon(packageName = row.packageName, provider = appInfo, size = 44.dp)

            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = appInfo.label(row.packageName),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    row.category?.let { category ->
                        CategoryChip(
                            name = category.name,
                            color = category.colorHex.toComposeColor(),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction.coerceIn(0.015f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(barColor),
                        )
                    }
                    Text(
                        text = DurationFormat.compact(row.totalMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // The week's shape, quiet enough to read as texture until it matters.
            if (row.trend.any { it > 0L }) {
                Sparkline(
                    values = row.trend,
                    modifier = Modifier.width(52.dp),
                    height = 26.dp,
                    color = barColor,
                )
            } else {
                Spacer(Modifier.width(52.dp))
            }
        }
    }
}

internal fun CategoryKind.label(): String = when (this) {
    CategoryKind.PRODUCTION -> "PRODUCING"
    CategoryKind.CONSUMPTION -> "CONSUMING"
    CategoryKind.UTILITY -> "UTILITY"
    CategoryKind.NEUTRAL -> "EITHER"
}
