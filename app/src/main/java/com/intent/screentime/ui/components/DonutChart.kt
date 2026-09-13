package com.intent.screentime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intent.screentime.ui.theme.Motion

data class DonutSlice(
    val label: String,
    val value: Long,
    val color: Color,
)

/**
 * Category share as a donut.
 *
 * A hole rather than a solid pie so the total can live in the middle, which is the
 * number people look for first. Slices are separated by a small gap in the surface
 * colour, so adjacent hues never rely on contrast alone to stay distinguishable.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    strokeWidth: Dp = 26.dp,
    content: @Composable () -> Unit = {},
) {
    val visible = slices.filter { it.value > 0L }
    val total = visible.sumOf { it.value }.toFloat()

    val sweep by animateFloatAsState(
        targetValue = if (total > 0f) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.RING_MS, easing = Motion.expoOut),
        label = "donutSweep",
    )

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            if (total <= 0f) return@Canvas

            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            val gapDegrees = if (visible.size > 1) 2.4f else 0f

            var startAngle = -90f
            for (slice in visible) {
                val fraction = slice.value / total
                val sweepAngle = (fraction * 360f * sweep) - gapDegrees
                if (sweepAngle > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
                startAngle += fraction * 360f * sweep
            }
        }

        content()
    }
}
