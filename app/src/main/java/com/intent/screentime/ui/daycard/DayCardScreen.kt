package com.intent.screentime.ui.daycard

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.intent.Reasons
import com.intent.screentime.data.local.entity.DayReflection
import com.intent.screentime.data.local.entity.IntentLogEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.components.AppIcon
import com.intent.screentime.ui.components.EmptyState
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.SplitBar
import com.intent.screentime.ui.components.SplitSegment
import com.intent.screentime.ui.components.StatTile
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One past day, opened from its square on the heatmap.
 *
 * The verdict, the split, the apps, the prompts answered and one note of the user's own —
 * which is what turns a heatmap of squares into something closer to a diary.
 */
@Composable
fun DayCardScreen(
    state: DayCardUiState,
    appInfo: AppInfoProvider,
    onBack: () -> Unit,
    onSetNote: (String) -> Unit,
    onSetReflection: (DayReflection?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whether anything was actually typed, so the field is not written back on a card the
    // user only opened to read. The commit fires on the way out as well as on blur, because
    // leaving the screen takes the field with it and focus loss is not guaranteed to reach us.
    var edited by remember(state.epochDay) { mutableStateOf(false) }
    val latestNote by rememberUpdatedState(state.note)

    DisposableEffect(state.epochDay) {
        onDispose { if (edited) onSetNote(latestNote) }
    }

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
        item { DayHeader(state.epochDay, onBack) }

        if (state.loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            return@LazyColumn
        }

        // The question only makes sense about a day there is something to say about, so a
        // blank day asks nothing — but an answer already given is still shown quietly.
        if (state.hasData || state.reflection != null) {
            item { ReflectionPanel(state, onSetReflection) }
        }

        if (state.hasData) {
            item { VerdictPanel(state.verdict) }
            item { SplitPanel(state) }
            if (state.topApps.isNotEmpty()) {
                item { TopAppsPanel(state.topApps, appInfo) }
            }
            item { VitalsRow(state) }
            if (state.intents.isNotEmpty()) {
                item { PromptsPanel(state.intents, appInfo) }
            }
        }

        item {
            Panel(title = "A note of your own") {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = {
                        edited = true
                        onSetNote(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (!focus.isFocused && edited) onSetNote(state.note)
                        },
                    placeholder = { Text("Anything worth remembering about this day…") },
                    minLines = 3,
                )
                Text(
                    text = "Saved as you type. Only you ever read it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.hasData) {
            item {
                Panel {
                    EmptyState(
                        icon = Icons.Filled.EventBusy,
                        title = "Nothing was tracked that day",
                        body = "Intent has no usage recorded here — the day may predate your " +
                            "first install, or the phone stayed off. There is nothing to " +
                            "judge, and this card says so rather than showing you zeroes.",
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(epochDay: Long, onBack: () -> Unit) {
    val date = remember(epochDay) { LocalDate.ofEpochDay(epochDay) }
    val isToday = date == LocalDate.now()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            SectionEyebrow(if (isToday) "Today" else "A day in your history")
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The nightly question, answered on the card rather than only in a notification.
 *
 * While it is unanswered it sits at the top and is the first thing the eye lands on. Once
 * answered it collapses to a single line, so a card the user has already filed away never
 * keeps asking.
 */
@Composable
private fun ReflectionPanel(state: DayCardUiState, onSetReflection: (DayReflection?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val answered = state.reflection
    var reasking by remember(state.epochDay) { mutableStateOf(false) }

    if (answered != null && !reasking) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "You called this day ${answered.label.lowercase()}.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { reasking = true }) {
                Text("Change", style = MaterialTheme.typography.labelLarge)
            }
        }
        return
    }

    Panel(title = "The nightly question") {
        Text(
            text = "Was today the day you wanted?",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )
        Text(
            text = "The same question the evening digest asks. Your answer is your own; it " +
                "never moves a score.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DayReflection.entries.forEach { option ->
                ReflectionChip(
                    label = option.label,
                    selected = answered == option,
                    onClick = {
                        onSetReflection(option)
                        reasking = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ReflectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (selected) scheme.primary else scheme.surfaceContainerHigh,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
        )
    }
}

/**
 * The verdict, said plainly.
 *
 * A day over the cap is described, not condemned: the point of the card is to know what
 * happened, and a screen that shouts about yesterday teaches nothing except to avoid it.
 */
@Composable
private fun VerdictPanel(verdict: StreakDayEntity?) {
    val scheme = MaterialTheme.colorScheme
    val data = dataColors

    Panel(title = "The verdict") {
        if (verdict == null) {
            Text(
                text = "This day was never judged.",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
            Text(
                text = "The nightly pass writes a verdict once a day is over. Days before " +
                    "tracking began are left alone.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            return@Panel
        }

        Text(
            text = when {
                verdict.metCap && verdict.metGoal ->
                    "Inside your cap, with the producing time you wanted."
                verdict.metCap ->
                    "Inside your cap. The producing goal went unmet that day."
                else ->
                    "Screen time ran past your cap that day."
            },
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
        )
        Text(
            text = when {
                verdict.score >= 70 -> "A day worth repeating."
                verdict.score >= 45 -> "A mixed day — the split was doing the work."
                else -> "Consumption had the run of it."
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = "Scored ${verdict.score} of 100 · " +
                "${DurationFormat.compact(verdict.productionMs)} producing",
            style = MaterialTheme.typography.labelSmall,
            color = data.production,
        )
    }
}

@Composable
private fun SplitPanel(state: DayCardUiState) {
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
                    "Not enough categorised time that day. Sort your apps and the past " +
                        "starts meaning something too."

                state.productionShare >= 0.25f ->
                    "${(state.productionShare * 100).toInt()}% of that day's categorised time " +
                        "went into producing rather than consuming."

                else ->
                    "${(state.productionShare * 100).toInt()}% of that day's categorised time " +
                        "went into producing. Utility and unsorted time is deliberately left " +
                        "out of this ratio."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopAppsPanel(rows: List<DayAppRow>, appInfo: AppInfoProvider) {
    Panel(title = "Where most of it went") {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AppIcon(packageName = row.packageName, provider = appInfo, size = 36.dp)
                Text(
                    text = appInfo.label(row.packageName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = DurationFormat.compact(row.totalMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VitalsRow(state: DayCardUiState) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatTile(
                value = if (state.focusMs > 0) DurationFormat.compact(state.focusMs) else "0m",
                caption = "focused",
                icon = Icons.Filled.TouchApp,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            StatTile(
                value = state.unlockCount.toString(),
                caption = "unlocks",
                icon = Icons.Filled.Lock,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            StatTile(
                value = state.promptsAnswered.toString(),
                caption = "prompts answered",
                icon = Icons.Filled.Chat,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The prompts raised that day and what the user said they were for.
 *
 * A prompt let close is shown as exactly that. It is a real answer — "I had no reason to
 * give" — and rendering it as a blank line would quietly turn it into a missing one.
 */
@Composable
private fun PromptsPanel(intents: List<IntentLogEntity>, appInfo: AppInfoProvider) {
    val scheme = MaterialTheme.colorScheme

    Panel(title = "What you came for") {
        intents.forEach { log ->
            val reason = when {
                log.skipped -> "let it close"
                else -> Reasons.labelFor(log.reasonKey)
                    ?: log.intentLabel.ifBlank { "answered without a reason" }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (log.skipped) scheme.onSurfaceVariant else scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = appInfo.label(log.packageName),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = Instant.ofEpochMilli(log.timestampMs)
                        .atZone(ZoneId.systemDefault())
                        .format(PROMPT_TIME),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val PROMPT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
