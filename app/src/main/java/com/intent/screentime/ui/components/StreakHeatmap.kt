package com.intent.screentime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intent.screentime.data.goals.HeatmapBuilder
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A calendar of the last twelve weeks, one square per day.
 *
 * Three states worth distinguishing, and they are distinguished by more than hue:
 * a day that honoured both cap and goal, a day that honoured the cap but missed the
 * production goal, and a day that went over. Empty squares are days before tracking
 * began, drawn as absence rather than failure.
 *
 * Passing [onCellClick] turns the squares into doors: a day that has data, or today,
 * opens its card, and every cell carries a spoken description either way so the grid
 * means the same thing to a screen reader as it does to the eye.
 */
@Composable
fun StreakHeatmap(
    weeks: List<List<HeatmapBuilder.Cell>>,
    modifier: Modifier = Modifier,
    onCellClick: ((Long) -> Unit)? = null,
) {
    val data = dataColors
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(4.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        weeks.forEach { week ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                week.forEach { cell ->
                    val filled = cell.hasData
                    val fill = when {
                        // Absence, not failure — but it still has to be visible. A fill one
                        // step off the panel colour disappears entirely, which leaves a
                        // twelve-week grid reading as a blank card and hides the fact that
                        // the days are tappable at all.
                        !filled -> Color.Transparent
                        cell.metCap && cell.metGoal -> data.production
                        cell.metCap -> scheme.primary
                        else -> scheme.surfaceContainerHighest
                    }

                    // A twelve-week grid cannot reach the 48dp touch target on a phone,
                    // so the squares are made as tall as the layout will bear and no more.
                    val interactive = onCellClick != null && (cell.hasData || cell.isToday)

                    val cellModifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(shape)
                        .background(fill)
                        .then(
                            when {
                                cell.isToday -> Modifier.border(1.5.dp, scheme.onSurface, shape)
                                !filled -> Modifier.border(1.dp, scheme.outlineVariant, shape)
                                else -> Modifier
                            },
                        )
                        .then(
                            if (interactive) {
                                Modifier.clickable { onCellClick?.invoke(cell.epochDay) }
                            } else {
                                Modifier
                            },
                        )
                        .semantics { contentDescription = describe(cell) }

                    Box(cellModifier)
                }
            }
        }
    }
}

/**
 * What a square means, in words. Colour already carries this for a sighted reader; the
 * description is what carries it for everyone else, and it is deliberately worded the
 * same way the legend is.
 */
private fun describe(cell: HeatmapBuilder.Cell): String {
    val date = LocalDate.ofEpochDay(cell.epochDay).format(DAY_LABEL)
    val outcome = when {
        !cell.hasData -> "no data"
        cell.metCap && cell.metGoal -> "inside the cap and the goal"
        cell.metCap -> "inside the cap"
        else -> "over the cap"
    }
    return if (cell.isToday) "$date, today, $outcome" else "$date, $outcome"
}

private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

/** The legend under the heatmap. Colour is always paired with a word. */
@Composable
fun StreakHeatmapLegend(modifier: Modifier = Modifier) {
    val data = dataColors
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(3.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendEntry(color = data.production, label = "Cap + goal", shape = shape)
        LegendEntry(color = scheme.primary, label = "Cap only", shape = shape)
        LegendEntry(color = scheme.surfaceContainerHighest, label = "Over cap", shape = shape)
    }
}

@Composable
private fun LegendEntry(color: Color, label: String, shape: RoundedCornerShape) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(10.dp).clip(shape).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
