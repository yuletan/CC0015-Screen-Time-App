package com.intent.screentime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import com.intent.screentime.data.goals.HeatmapBuilder
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

/**
 * A calendar of the last twelve weeks, one square per day.
 *
 * Three states worth distinguishing, and they are distinguished by more than hue:
 * a day that honoured both cap and goal, a day that honoured the cap but missed the
 * production goal, and a day that went over. Empty squares are days before tracking
 * began, drawn as absence rather than failure.
 */
@Composable
fun StreakHeatmap(
    weeks: List<List<HeatmapBuilder.Cell>>,
    modifier: Modifier = Modifier,
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { cell ->
                    val fill = when {
                        !cell.hasData -> scheme.surfaceContainerLow
                        cell.metCap && cell.metGoal -> data.production
                        cell.metCap -> scheme.primary
                        else -> scheme.surfaceContainerHighest
                    }

                    val cellModifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(shape)
                        .background(fill)
                        .then(
                            if (cell.isToday) {
                                Modifier.border(1.5.dp, scheme.onSurface, shape)
                            } else {
                                Modifier
                            },
                        )

                    Box(cellModifier)
                }
            }
        }
    }
}

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
