package com.intent.screentime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.ui.theme.Motion
import com.intent.screentime.ui.theme.Spacing

data class SplitSegment(
    val label: String,
    val ms: Long,
    val color: Color,
)

/**
 * The production/consumption split as one bar plus its legend.
 *
 * A single proportional bar rather than a pie: the point being made is a *ratio*, and a
 * bar makes two quantities comparable at a glance in a way angles never do. Segments
 * smaller than a rounding sliver still appear in the legend so nothing silently vanishes.
 */
@Composable
fun SplitBar(
    segments: List<SplitSegment>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 14.dp,
) {
    val visible = segments.filter { it.ms > 0L }
    val total = visible.sumOf { it.ms }.toFloat()

    val reveal by animateFloatAsState(
        targetValue = if (total > 0f) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER_MS, easing = Motion.expoOut),
        label = "splitReveal",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape),
        ) {
            if (total <= 0f) return@Canvas
            val gap = 1.5.dp.toPx()
            var x = 0f
            visible.forEachIndexed { index, segment ->
                val isLast = index == visible.lastIndex
                val width = (segment.ms / total) * size.width * reveal
                val drawn = if (isLast) width else (width - gap).coerceAtLeast(0f)
                drawRoundRect(
                    color = segment.color,
                    topLeft = Offset(x, 0f),
                    size = Size(drawn, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
                x += width
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            visible.forEach { segment ->
                LegendItem(
                    color = segment.color,
                    label = segment.label,
                    value = DurationFormat.compact(segment.ms),
                )
            }
        }
    }
}
