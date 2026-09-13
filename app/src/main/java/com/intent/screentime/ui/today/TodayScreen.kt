package com.intent.screentime.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.components.AppIcon
import com.intent.screentime.ui.components.CategoryChip
import com.intent.screentime.ui.components.DeltaPill
import com.intent.screentime.ui.components.EmptyState
import com.intent.screentime.ui.components.HourlyStrip
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.ProgressRing
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.SplitBar
import com.intent.screentime.ui.components.SplitSegment
import com.intent.screentime.ui.components.StatTile
import com.intent.screentime.ui.components.TimeRing
import com.intent.screentime.ui.components.busiestHour
import com.intent.screentime.ui.components.hourLabel
import com.intent.screentime.ui.theme.Motion
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    state: TodayUiState,
    refreshing: Boolean,
    appInfo: AppInfoProvider,
    onRefresh: () -> Unit,
    onSetCap: (Int?) -> Unit,
    onOpenApps: () -> Unit,
    onOpenTriage: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCapDialog by remember { mutableStateOf(false) }

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
            DayHeader(
                epochDay = state.epochDay,
                refreshing = refreshing,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings,
            )
        }

        item {
            EnterAnimation(index = 0) {
                HeroPanel(state = state, onSetCapClick = { showCapDialog = true })
            }
        }

        if (!state.hasData) {
            item {
                EnterAnimation(index = 1) {
                    Panel {
                        EmptyState(
                            icon = Icons.Filled.Schedule,
                            title = "Nothing tracked yet today",
                            body = "Use your phone normally. Intent copies your usage down " +
                                "every 15 minutes, so this fills in as the day goes on.",
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item {
            EnterAnimation(index = 1) {
                SplitPanel(state = state, onOpenTriage = onOpenTriage)
            }
        }

        item {
            EnterAnimation(index = 2) {
                ScorePanel(state = state)
            }
        }

        item {
            EnterAnimation(index = 3) {
                TimelinePanel(state = state)
            }
        }

        item {
            EnterAnimation(index = 4) {
                VitalsRow(state = state)
            }
        }

        item {
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionEyebrow("Where the time went")
                TextButton(onClick = onOpenApps) {
                    Text("All apps", style = MaterialTheme.typography.labelLarge)
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        itemsIndexed(state.topApps, key = { _, row -> row.packageName }) { index, row ->
            EnterAnimation(index = index + 4) {
                AppUsageRowItem(
                    row = row,
                    maxMs = state.topAppMs,
                    appInfo = appInfo,
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
}

/**
 * Staggered entrance. Each row arrives slightly after the one above it, so the board
 * assembles rather than blinking into existence.
 */
@Composable
private fun EnterAnimation(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(Motion.staggerDelay(index).toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER_MS, easing = Motion.expoOut),
        label = "enter$index",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 28f
        },
    ) {
        content()
    }
}

@Composable
private fun DayHeader(
    epochDay: Long,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val today = remember(epochDay) { LocalDate.ofEpochDay(epochDay) }
    val isToday = today == LocalDate.now()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionEyebrow(if (isToday) "Today" else "Most recent day")
            Text(
                text = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh from device",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroPanel(state: TodayUiState, onSetCapClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val data = dataColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TimeRing(
            usedMs = state.screenTimeMs,
            targetMs = state.capMs,
            diameter = 236.dp,
            strokeWidth = 22.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = DurationFormat.compact(state.screenTimeMs),
                    style = MaterialTheme.typography.displayLarge,
                    color = scheme.onSurface,
                )
                Text(
                    text = when {
                        state.capMs != null -> "of ${DurationFormat.compact(state.capMs!!)} cap"
                        else -> "no daily cap"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        when {
            state.overCap -> StatusPill(
                icon = Icons.Filled.ErrorOutline,
                text = "${DurationFormat.compact(state.overageMs)} over your cap",
                tint = scheme.error,
            )

            state.capMs != null -> StatusPill(
                icon = Icons.Filled.TrackChanges,
                text = "${DurationFormat.compact(state.capMs!! - state.screenTimeMs)} left today",
                tint = data.positive,
            )

            else -> OutlinedButton(onClick = onSetCapClick) {
                Icon(
                    Icons.Filled.TrackChanges,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(Spacing.sm))
                Text("Set a daily cap")
            }
        }

        if (state.hasYesterday) {
            Spacer(Modifier.height(Spacing.sm))
            DeltaPill(deltaMs = state.deltaVsYesterdayMs)
        }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, text: String, tint: androidx.compose.ui.graphics.Color) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, color = tint)
        }
    }
}

@Composable
private fun SplitPanel(state: TodayUiState, onOpenTriage: () -> Unit) {
    val data = dataColors

    val segments = listOf(
        SplitSegment("Producing", state.productionMs, data.production),
        SplitSegment("Consuming", state.consumptionMs, data.consumption),
        SplitSegment("Utility", state.utilityMs, data.utility),
        SplitSegment("Unsorted", state.neutralMs, data.neutral),
    )

    Panel(title = "Producing vs consuming") {
        SplitBar(segments = segments)

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = when {
                state.productionMs == 0L && state.consumptionMs == 0L ->
                    "Not enough categorised time yet. Sort your apps on the Apps screen " +
                        "and this split starts meaning something."

                state.productionShare >= 0.25f ->
                    "${(state.productionShare * 100).toInt()}% of your categorised time went " +
                        "into producing rather than consuming."

                else ->
                    "${(state.productionShare * 100).toInt()}% of your categorised time went " +
                        "into producing. Utility and unsorted time is deliberately left out " +
                        "of this ratio."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.unsortedCount > 0) {
            Spacer(Modifier.height(Spacing.xs))
            UnsortedRow(
                count = state.unsortedCount,
                ms = state.unsortedMs,
                onClick = onOpenTriage,
            )
        }
    }
}

/**
 * Today's unsorted pile, offered as a way to clear it.
 *
 * The split can read a misleading low simply because nothing is categorised, so this says
 * how much time that is and offers the fix in place, rather than describing the problem and
 * sending the user elsewhere.
 */
@Composable
private fun UnsortedRow(count: Int, ms: Long, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${DurationFormat.compact(ms)} unsorted across " +
                    (if (count == 1) "1 app" else "$count apps") + " — sort",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The composite score, and the sentence that explains it.
 *
 * A single number is only motivating if the reader knows what moved it, so the ring is
 * always accompanied by the breakdown: the split, the cap, focus time, the streak.
 */
@Composable
private fun ScorePanel(state: TodayUiState) {
    val scheme = MaterialTheme.colorScheme
    val data = dataColors
    val parts = state.scoreParts

    Panel(title = "Intent Score") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ProgressRing(
                progress = state.intentScore / 100f,
                diameter = 96.dp,
                strokeWidth = 11.dp,
                color = when {
                    state.intentScore >= 70 -> data.production
                    state.intentScore >= 45 -> scheme.primary
                    else -> scheme.error
                },
            ) {
                Text(
                    text = state.intentScore.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onSurface,
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = when {
                        state.intentScore >= 70 -> "A day worth repeating."
                        state.intentScore >= 45 -> "A mixed day — the split is doing the work."
                        else -> "Consumption had the run of today."
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text = scoreExplanation(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                if (parts != null) {
                    Text(
                        text = "Producing ${percent(parts.production)} · " +
                            "cap ${percent(parts.cap)} · " +
                            "focus ${percent(parts.focus)} · " +
                            "streak ${percent(parts.streak)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun percent(fraction: Float): String = "${(fraction * 100).toInt()}%"

private fun scoreExplanation(state: TodayUiState): String {
    val streak = if (state.streakDays > 0) {
        " A ${state.streakDays}-day streak is carrying ${(state.scoreParts?.streak ?: 0f) * 15f} " +
            "points of it."
    } else {
        ""
    }
    return when {
        state.capMs == null && state.productionMs == 0L && state.consumptionMs == 0L ->
            "Sort your apps on the Apps screen and set a cap; both feed straight into " +
                "this number."
        state.overCap ->
            "Being over your cap is costing points, and producing time is what wins them " +
                "back.$streak"
        else ->
            "Weighted from producing versus consuming, staying under your cap, focus " +
                "time and your streak.$streak"
    }
}

@Composable
private fun TimelinePanel(state: TodayUiState) {
    val peak = busiestHour(state.buckets)

    Panel(title = "The shape of your day") {
        HourlyStrip(buckets = state.buckets)

        if (peak != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Busiest stretch was ${hourLabel(peak.hour)} " +
                        "at ${DurationFormat.compact(peak.totalMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VitalsRow(state: TodayUiState) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatTile(
                value = state.unlockCount.toString(),
                caption = "unlocks",
                icon = Icons.Filled.Lock,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            StatTile(
                value = if (state.focusMs > 0) DurationFormat.compact(state.focusMs) else "0m",
                caption = "focused",
                icon = Icons.Filled.TouchApp,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            StatTile(
                value = state.topApps.sumOf { it.sessionCount }.toString(),
                caption = "sessions",
                icon = Icons.Filled.Schedule,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AppUsageRowItem(row: AppUsageRow, maxMs: Long, appInfo: AppInfoProvider) {
    val fragment = if (maxMs > 0L) (row.totalMs.toFloat() / maxMs.toFloat()) else 0f
    val barColor = dataColors.series[
        appInfo.monogramColorIndex(row.packageName) % dataColors.series.size,
    ]

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
                            color = category.colorHex.let {
                                androidx.compose.ui.graphics.Color(
                                    android.graphics.Color.parseColor(it),
                                )
                            },
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
                                .fillMaxWidth(fragment.coerceIn(0.02f, 1f))
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
        }
    }
}

@Composable
private fun SetCapDialog(
    currentMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    com.intent.screentime.ui.components.SetCapDialog(
        currentMinutes = currentMinutes,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
