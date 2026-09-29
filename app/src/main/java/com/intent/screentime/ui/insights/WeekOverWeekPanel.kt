package com.intent.screentime.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.stats.WeekOverWeek
import com.intent.screentime.data.stats.WeekVerdict
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.components.AppIcon
import com.intent.screentime.ui.components.CapturablePanel
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors
import kotlin.math.abs

/** How a metric's numbers should be written, since the three do not share a unit. */
private enum class RowUnit { DURATION, PERCENT, COUNT }

private data class ComparisonRow(
    val label: String,
    val current: String,
    val previous: String,
    val direction: WeekOverWeek.Direction,
    val tone: WeekVerdict.Tone,
    val change: String,
)

/**
 * This week read against the same days of last week.
 *
 * The panel leads with the largest genuine movement rather than an average of the three,
 * because the three do not share a unit and any weighting would be an opinion. Then the
 * rows let the reader check the sentence, and the movers say *where* it happened — a
 * change with no attribution is a number, not a finding.
 *
 * The comparison is drawn over matched elapsed days, so on a Wednesday it reads Monday to
 * Wednesday against the same three days last week. The subtitle says so, and the footer
 * gives last week's finished total, because otherwise a part-finished week looks like a
 * collapse.
 */
@Composable
fun WeekOverWeekPanel(
    insight: WeekOverWeekInsight,
    appInfo: AppInfoProvider,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val comparison = insight.comparison
    val rows = comparisonRows(insight)
    val headline = insight.verdict.firstOrNull()?.sentence

    CapturablePanel(
        title = "This week vs last",
        fileName = "week-over-week",
        modifier = modifier,
        subtitle = if (comparison.isCurrentPartial) {
            "${comparison.currentDays} days against the same ${comparison.previousDays} last week"
        } else {
            "Seven days against the seven before"
        },
    ) {
        if (headline != null) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        rows.forEach { row -> ComparisonRowLine(row) }

        if (insight.movers.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "What moved",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            insight.movers.forEach { mover ->
                MoverRow(
                    mover = mover,
                    maxMs = insight.movers.maxOf { abs(it.deltaMs) },
                    appInfo = appInfo,
                    onClick = { onOpenApp(mover.packageName) },
                )
            }
        }

        if (comparison.isCurrentPartial && comparison.previousWeekDays > 0) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Last week finished at ${DurationFormat.compact(comparison.previousWeekTotalMs)}, " +
                    "over ${comparison.previousWeekDays} days.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The rows, in the order the verdict ranked them, with unlocks appended.
 *
 * Ranked order rather than a fixed one so the row that explains the headline is always
 * first — a reader who stops after one line has read the most important one.
 */
private fun comparisonRows(insight: WeekOverWeekInsight): List<ComparisonRow> {
    val comparison = insight.comparison

    val ranked = insight.verdict.mapNotNull { finding ->
        when (finding.metric) {
            WeekVerdict.Metric.SCREEN_TIME -> row(
                label = "Screen time",
                metric = comparison.screenTime,
                unit = RowUnit.DURATION,
                tone = finding.tone,
            )

            WeekVerdict.Metric.PRODUCTION_SHARE -> row(
                label = "Producing share",
                metric = comparison.productionShare,
                unit = RowUnit.PERCENT,
                tone = finding.tone,
            )

            WeekVerdict.Metric.MINDLESS_OPENS -> row(
                label = "Drift or boredom opens",
                metric = comparison.mindlessOpens,
                unit = RowUnit.COUNT,
                tone = finding.tone,
            )
        }
    }

    // Unlocks is not one of the ranked three — it is a proxy for compulsion rather than a
    // goal anybody sets — but it belongs beside them, because it is the number that moves
    // first when checking a phone becomes a reflex.
    val unlocks = if (comparison.unlocks.current > 0L || comparison.unlocks.previous > 0L) {
        listOf(
            row(
                label = "Unlocks",
                metric = comparison.unlocks,
                unit = RowUnit.COUNT,
                tone = toneFor(comparison.unlocks, lowerIsBetter = true),
            ),
        )
    } else {
        emptyList()
    }

    return ranked + unlocks
}

private fun row(
    label: String,
    metric: WeekOverWeek.Metric,
    unit: RowUnit,
    tone: WeekVerdict.Tone,
): ComparisonRow = ComparisonRow(
    label = label,
    current = when (unit) {
        RowUnit.DURATION -> DurationFormat.compact(metric.current)
        RowUnit.PERCENT -> "${metric.current}%"
        RowUnit.COUNT -> metric.current.toString()
    },
    previous = when (unit) {
        RowUnit.DURATION -> "was ${DurationFormat.compact(metric.previous)}"
        RowUnit.PERCENT -> "was ${metric.previous}%"
        RowUnit.COUNT -> "was ${metric.previous}"
    },
    direction = metric.direction,
    tone = tone,
    change = metric.percent?.let { "${abs(it)}%" } ?: when (unit) {
        RowUnit.DURATION -> DurationFormat.compact(abs(metric.delta))
        RowUnit.PERCENT -> "${abs(metric.delta)} pts"
        RowUnit.COUNT -> abs(metric.delta).toString()
    },
)

/** Falls back to the direction when no verdict exists — used for the unranked rows. */
private fun toneFor(metric: WeekOverWeek.Metric, lowerIsBetter: Boolean): WeekVerdict.Tone {
    if (metric.direction == WeekOverWeek.Direction.FLAT) return WeekVerdict.Tone.STALLED
    val improved = if (lowerIsBetter) {
        metric.direction == WeekOverWeek.Direction.DOWN
    } else {
        metric.direction == WeekOverWeek.Direction.UP
    }
    return if (improved) WeekVerdict.Tone.IMPROVED else WeekVerdict.Tone.SLIPPED
}

/**
 * One metric: what it is now, what it was, and which way it went.
 *
 * Direction is an arrow, sentiment is a colour, and both are in the words as well — the
 * same three signals DeltaPill uses, so a reader who cannot separate the colours is not
 * left with the change implied only by a hue.
 */
@Composable
private fun ComparisonRowLine(row: ComparisonRow) {
    val tint = toneColor(row.tone)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = row.current,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = row.previous,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(Spacing.sm))

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = when (row.direction) {
                    WeekOverWeek.Direction.UP -> Icons.Filled.TrendingUp
                    WeekOverWeek.Direction.DOWN -> Icons.Filled.TrendingDown
                    WeekOverWeek.Direction.FLAT -> Icons.Filled.Remove
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = row.change,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

/**
 * One app that moved: its size, the direction, and a bar for how much of the movement it
 * accounts for.
 *
 * The bar is scaled to the *largest movement*, not to the largest total, because the
 * question this list answers is which app explains the change — an app with three hours of
 * steady use every week has not explained anything.
 */
@Composable
private fun MoverRow(
    mover: AppMovementView,
    maxMs: Long,
    appInfo: AppInfoProvider,
    onClick: () -> Unit,
) {
    val rising = mover.deltaMs > 0
    // More time on an app is worse, and less of it is better: the sentiment is inverted
    // relative to the arrow, which is exactly why both are shown.
    val color = if (rising) dataColors.negative else dataColors.positive
    val fraction = if (maxMs > 0L) abs(mover.deltaMs).toFloat() / maxMs.toFloat() else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AppIcon(packageName = mover.packageName, provider = appInfo, size = 32.dp)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = mover.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = if (rising) {
                        "+${DurationFormat.compact(abs(mover.deltaMs))}"
                    } else {
                        "−${DurationFormat.compact(abs(mover.deltaMs))}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                )
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun toneColor(tone: WeekVerdict.Tone): Color {
    val data = dataColors
    return when (tone) {
        WeekVerdict.Tone.IMPROVED -> data.positive
        WeekVerdict.Tone.SLIPPED -> data.negative
        WeekVerdict.Tone.STALLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
