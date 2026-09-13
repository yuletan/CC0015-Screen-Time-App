package com.intent.screentime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.ui.theme.Motion
import com.intent.screentime.ui.theme.Spacing

data class BarColumn(
    val label: String,
    val value: Long,
    val color: Color,
)

/**
 * A ranked column chart, one column per app.
 *
 * Columns rather than another line chart because these values are *per app*, not over
 * time: the comparison is between neighbours, and bars make ranking readable at a
 * glance. Each column carries its own time underneath, so the chart needs no axis.
 */
@Composable
fun ColumnChart(
    columns: List<BarColumn>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
) {
    val peak = columns.maxOfOrNull { it.value } ?: 0L

    val reveal by animateFloatAsState(
        targetValue = if (peak > 0L) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER_MS, easing = Motion.expoOut),
        label = "columnReveal",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        columns.forEach { column ->
            val fraction = if (peak > 0L) column.value.toFloat() / peak.toFloat() else 0f

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = DurationFormat.compact(column.value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height((height * fraction * reveal).coerceAtLeast(4.dp))
                        .clip(CircleShape)
                        .background(column.color),
                )

                Text(
                    text = column.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
