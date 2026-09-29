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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
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
import com.intent.screentime.ui.components.categoryColor
import com.intent.screentime.ui.components.AppIcon
import com.intent.screentime.ui.components.CategoryChip
import com.intent.screentime.ui.components.EmptyState
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.Sparkline
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

private const val MAX_NOTICED_APPS = 3

@Composable
fun AppsScreen(
    state: AppsUiState,
    appInfo: AppInfoProvider,
    onOpenApp: (String) -> Unit,
    onSortCategories: () -> Unit,
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
                // An invitation, not an explanation. The old copy told the user to go and
                // sort somewhere else; this one is the somewhere else.
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSortCategories),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Sort your apps",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = (if (state.uncategorisedCount == 1) {
                                    "1 app"
                                } else {
                                    "${state.uncategorisedCount} apps"
                                }) + " still have no side. Producing and consuming are what " +
                                    "the split on Today is built from — give each one a side " +
                                    "and it starts meaning something.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        val noticed = state.rows.filter { it.insight != null }.take(MAX_NOTICED_APPS)
        if (noticed.isNotEmpty()) {
            item {
                WorthNoticingPanel(
                    rows = noticed,
                    appInfo = appInfo,
                    onOpenApp = onOpenApp,
                )
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
private fun WorthNoticingPanel(
    rows: List<AppListRow>,
    appInfo: AppInfoProvider,
    onOpenApp: (String) -> Unit,
) {
    Panel(
        title = "Worth noticing",
        subtitle = "A few patterns that may be useful to look at, not rules to follow.",
    ) {
        rows.forEach { row ->
            val insight = row.insight ?: return@forEach
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenApp(row.packageName) }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AppIcon(packageName = row.packageName, provider = appInfo, size = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${appInfo.label(row.packageName)} · ${insight.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = insight.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open ${appInfo.label(row.packageName)} details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
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
                Text(
                    text = appInfo.label(row.packageName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (row.category != null) {
                    val category = row.category
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
