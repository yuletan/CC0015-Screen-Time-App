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
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.components.AppIcon
import com.intent.screentime.ui.components.CategoryChip
import com.intent.screentime.ui.components.CategoryPickerDialog
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.SetCapDialog
import com.intent.screentime.ui.components.StatTile
import com.intent.screentime.ui.components.TrendChart
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors
import com.intent.screentime.ui.theme.toComposeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AppDetailScreen(
    state: AppDetailUiState,
    appInfo: AppInfoProvider,
    onBack: () -> Unit,
    onSetCap: (Int?) -> Unit,
    onSetCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCapDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM") }

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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Last 30 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.category?.let { category ->
                    CategoryChip(
                        name = category.name,
                        color = category.colorHex.toComposeColor(),
                    )
                }
            }
        }

        item {
            Panel(title = "Trend") {
                val peak = state.peakMs
                TrendChart(
                    values = state.days.map { it.second },
                    lineColor = dataColors.consumption,
                    startLabel = state.days.firstOrNull()?.first
                        ?.let { LocalDate.ofEpochDay(it).format(formatter) },
                    endLabel = state.days.lastOrNull()?.first
                        ?.let { LocalDate.ofEpochDay(it).format(formatter) },
                    peakLabel = if (peak > 0) "peak ${DurationFormat.compact(peak)}" else null,
                )

                Spacer(Modifier.height(Spacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatTile(
                        value = DurationFormat.compact(state.todayMs),
                        caption = "today",
                    )
                    StatTile(
                        value = DurationFormat.compact(state.averageMs),
                        caption = "per active day",
                    )
                    StatTile(
                        value = DurationFormat.compact(state.totalMs),
                        caption = "in 30 days",
                    )
                }
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
                    value = state.category?.name ?: "Uncategorised",
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
