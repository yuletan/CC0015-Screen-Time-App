package com.intent.screentime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.ui.theme.Motion

/**
 * A screen-time trend as an area over a baseline.
 *
 * Deliberately axis-light: on a phone, gridline numbers cost more horizontal room than
 * they repay. The peak and the overall range are labelled instead, which is what a
 * glance actually needs, and every point stays inspectable via the caller's own labels.
 *
 * The area fill is a soft vertical gradient rather than a flat block, so the newest
 * stretch of the line carries the most weight.
 */
@Composable
fun TrendChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    startLabel: String? = null,
    endLabel: String? = null,
    peakLabel: String? = null,
) {
    val peak = values.maxOrNull() ?: 0L
    val scheme = MaterialTheme.colorScheme

    val reveal by animateFloatAsState(
        targetValue = if (values.isNotEmpty() && peak > 0L) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER_MS + 220, easing = Motion.expoOut),
        label = "trendReveal",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            if (values.size < 2 || peak <= 0L) return@Canvas

            val maxY = peak.toFloat()
            val stepX = size.width / (values.size - 1).toFloat()
            val topPadding = 10.dp.toPx()
            val usableHeight = size.height - topPadding

            fun pointX(index: Int) = index * stepX
            fun pointY(value: Long) = topPadding + usableHeight * (1f - value.toFloat() / maxY)

            // Three quiet gridlines give the eye a scale without any numbers.
            val gridColor = scheme.outlineVariant.copy(alpha = 0.5f)
            for (fraction in listOf(0f, 0.5f, 1f)) {
                val y = topPadding + usableHeight * fraction
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // Reveal by clipping in from the left, so the line draws itself across.
            clipRect(right = size.width * reveal) {
                val linePath = Path().apply {
                    moveTo(pointX(0), pointY(values[0]))
                    for (index in 1 until values.size) {
                        // Gentle horizontal midpoint smoothing, enough to read as a trend
                        // rather than a polyline, without inventing data.
                        val previousX = pointX(index - 1)
                        val previousY = pointY(values[index - 1])
                        val currentX = pointX(index)
                        val currentY = pointY(values[index])
                        val midX = (previousX + currentX) / 2f
                        cubicTo(midX, previousY, midX, currentY, currentX, currentY)
                    }
                }

                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(pointX(values.lastIndex), size.height)
                    lineTo(pointX(0), size.height)
                    close()
                }

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.34f),
                            lineColor.copy(alpha = 0.02f),
                        ),
                        startY = topPadding,
                        endY = size.height,
                    ),
                )

                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(
                        width = 2.6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )

                // Mark the most recent day, the one the user is standing on.
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(pointX(values.lastIndex), pointY(values.last())),
                )
                drawCircle(
                    color = scheme.surface,
                    radius = 2.dp.toPx(),
                    center = Offset(pointX(values.lastIndex), pointY(values.last())),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = startLabel.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (peakLabel != null) {
                Text(
                    text = peakLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                text = endLabel.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

/** Convenience for the label pair under a chart. */
internal fun rangeLabel(peakMs: Long): String =
    if (peakMs > 0) "peak ${DurationFormat.compact(peakMs)}" else ""
