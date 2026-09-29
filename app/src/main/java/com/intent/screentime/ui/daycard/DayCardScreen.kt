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
import androidx.compose.material.icons.filled.Bolt
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
import com.intent.screentime.core.format.HourFormat
import com.intent.screentime.data.goals.IntentScore
import com.intent.screentime.data.intent.Reasons
import com.intent.screentime.data.local.entity.DayReflection
import com.intent.screentime.data.local.entity.IntentLogEntity
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.components.CapturablePanel
import com.intent.screentime.ui.components.EmptyState
import com.intent.screentime.ui.components.HourlyStrip
import com.intent.screentime.ui.components.IntentScorePanel
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.ProducingVsConsumingPanel
import com.intent.screentime.ui.components.RankedAppRow
import com.intent.screentime.ui.components.ScoreDetail
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.StatTile
import com.intent.screentime.ui.components.busiestHour
import com.intent.screentime.ui.components.pointsText
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
            item { ScorePanel(state) }
            item { SplitPanel(state) }
            item { TimelinePanel(state, appInfo) }
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
                        body = "CC0015 Intent has no usage recorded here — the day may predate your " +
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
 * The day's score, and the verdict that used to be its own panel.
 *
 * The ring and the five rows come from the shared panel, so this day reads the same here, on
 * the Today screen when it *was* today, and inside the Insights window it now falls in. What
 * stays on this card is the prose: whether the cap and the goal were met, and what the night
 * did — the parts that are a story about this day rather than arithmetic.
 *
 * A day over the cap is described, not condemned: the point of the card is to know what
 * happened, and a screen that shouts about yesterday teaches nothing except to avoid it.
 */
@Composable
private fun ScorePanel(state: DayCardUiState) {
    val score = state.score ?: return

    IntentScorePanel(
        score = score.score,
        verdict = when {
            score.score >= 70 -> "A day worth repeating."
            score.score >= 45 -> "A mixed day — the split was doing the work."
            else -> "Consumption had the run of it."
        },
        explanation = verdictSentence(state),
        contributions = score.contributions,
        details = score.contributions.map { contribution ->
            ScoreDetail(
                kind = contribution.kind,
                tone = contribution.tone,
                text = scoreDetailText(state, contribution.kind),
                pointsText = contribution.pointsText(),
            )
        },
        fileName = "day-intent-score",
        footnote = state.verdict?.let { verdict ->
            "${DurationFormat.compact(verdict.productionMs)} producing that day"
        },
    )
}

/**
 * What the day did against the commitments that were live at the time.
 *
 * Falls back to the numbers rather than to silence: a day is judged from its own data even
 * when the nightly pass has not written a row for it, so the card never has to say "never
 * judged" about a day it can perfectly well describe.
 */
private fun verdictSentence(state: DayCardUiState): String {
    val verdict = state.verdict

    val goal = when {
        verdict == null -> ""
        verdict.metCap && verdict.metGoal ->
            " Inside your cap, with the producing time you wanted."
        verdict.metCap -> " Inside your cap, though the producing goal went unmet."
        else -> " Screen time ran past your cap that day."
    }

    val bedtime = state.bedtimeWindow?.let { window ->
        val hours = "${DurationFormat.timeOfDay(window.startMinutesOfDay)}–" +
            DurationFormat.timeOfDay(window.endMinutesOfDay)
        val used = verdict?.bedtimeUsedMs ?: 0L
        when {
            verdict == null -> ""
            !verdict.metBedtime ->
                " ${DurationFormat.compact(used)} landed inside the $hours bedtime window."
            used <= 0L -> " The $hours bedtime window was clear."
            else -> " Only ${DurationFormat.compact(used)} landed inside the $hours window."
        }
    }.orEmpty()

    return "Computed from that day's own numbers, against the commitments that were live " +
        "then.$goal$bedtime"
}

/** The plain-language reason behind one row, from this day's own numbers. */
private fun scoreDetailText(
    state: DayCardUiState,
    kind: IntentScore.ContributionKind,
): String {
    val split = state.split
    val score = state.score

    return when (kind) {
        IntentScore.ContributionKind.PRODUCTION ->
            if (split.accountableMs <= 0L) {
                "Nothing was categorised as Producing or Consuming that day."
            } else {
                "${(split.productionShare * 100).toInt()}% of that day's categorised time " +
                    "was Producing."
            }

        IntentScore.ContributionKind.CAP -> {
            val capMinutes = state.capMinutes
            val capMs = capMinutes?.toLong()?.times(60_000L)
            when {
                capMs == null -> "No daily cap was set, so this part stays neutral."
                state.screenTimeMs > capMs ->
                    "${DurationFormat.compact(state.screenTimeMs - capMs)} over your " +
                        "${DurationFormat.compact(capMs)} cap."

                else -> "${DurationFormat.compact(capMs - state.screenTimeMs)} left before " +
                    "your cap."
            }
        }

        IntentScore.ContributionKind.BEDTIME -> {
            val window = state.bedtimeWindow
            val used = state.verdict?.bedtimeUsedMs ?: 0L
            when {
                window == null -> "No bedtime was set, so this part stays neutral."
                state.verdict == null ->
                    "The stored verdict for that day was not kept, so this part stays neutral."

                state.verdict.metBedtime -> "Bedtime held — ${DurationFormat.compact(used)} " +
                    "inside the ${DurationFormat.timeOfDay(window.startMinutesOfDay)}–" +
                    "${DurationFormat.timeOfDay(window.endMinutesOfDay)} window."

                else -> "${DurationFormat.compact(used)} inside your bedtime window. Under " +
                    "five minutes keeps the night."
            }
        }

        IntentScore.ContributionKind.FOCUS ->
            if (state.focusMs > 0L) {
                "${DurationFormat.compact(state.focusMs)} focused that day; one hour earns " +
                    "full credit."
            } else {
                "No focused time recorded that day."
            }

        IntentScore.ContributionKind.STREAK -> {
            val days = score?.longestStreak ?: 0
            if (days <= 0) {
                "No consecutive days inside your cap by then."
            } else {
                "$days consecutive days inside your cap by then."
            }
        }
    }
}

/**
 * That day against the split, in this card's own words.
 *
 * The rendering is the shared panel, so a past day, today and a whole window cannot answer
 * "did I spend this time or did it spend me" in three different shapes. The unsorted offer
 * stays off here: this card is about a day that is already over.
 */
@Composable
private fun SplitPanel(state: DayCardUiState) {
    ProducingVsConsumingPanel(
        split = state.split,
        fileName = "day-split",
        prose = when {
            state.split.accountableMs <= 0L ->
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
    )
}

/**
 * The shape of the day, hour by hour.
 *
 * The same strip Today draws, from the same sessions: a past day read as a total and a
 * past day read as a timeline are different days, and only one of them is true.
 */
@Composable
private fun TimelinePanel(state: DayCardUiState, appInfo: AppInfoProvider) {
    val peak = busiestHour(state.buckets)

    Panel(title = "The shape of that day") {
        HourlyStrip(buckets = state.buckets, labelOf = appInfo::label)

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
                    text = "Busiest stretch was ${HourFormat.short(peak.hour)} " +
                        "at ${DurationFormat.compact(peak.totalMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Where most of the day went, one row each, every row carrying the kind of time it took.
 *
 * The shared ranked row rather than a private list, so an app reads the same here as it does
 * on Today and in Insights — same icon, same chip, same bar, same tap target.
 */
@Composable
private fun TopAppsPanel(rows: List<DayAppRow>, appInfo: AppInfoProvider) {
    val data = dataColors
    val maxMs = rows.maxOfOrNull { it.totalMs } ?: 0L

    CapturablePanel(title = "Where most of it went", fileName = "day-top-apps") {
        rows.forEachIndexed { index, row ->
            RankedAppRow(
                packageName = row.packageName,
                name = appInfo.label(row.packageName),
                valueMs = row.totalMs,
                maxMs = maxMs,
                appInfo = appInfo,
                color = data.series[index % data.series.size],
                onClick = {},
                category = row.category,
            )
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

        if (state.autoFocusMs > 0L) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "${DurationFormat.compact(state.autoFocusMs)} of that day's focus was " +
                    "detected from long uninterrupted stretches in your work apps.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
