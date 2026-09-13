package com.intent.screentime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A seven-day shape for one app, drawn small enough to sit inside a list row.
 *
 * It is deliberately not the [TrendChart]: a sparkline answers "rising or falling?" and
 * nothing else. No axes, no labels, no fill — the row's own number carries the magnitude.
 */
@Composable
fun Sparkline(
    values: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val peak = values.maxOrNull() ?: 0L
    val idleColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas

        if (peak <= 0L) {
            // A transparent app is a flat line, not an empty gap.
            drawLine(
                color = idleColor,
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val stepX = size.width / (values.size - 1).toFloat()
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value.toFloat() / peak.toFloat()) * (size.height - 2f) - 1f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
