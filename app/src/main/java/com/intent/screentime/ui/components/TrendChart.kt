package com.intent.screentime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.ui.theme.Motion
import com.intent.screentime.ui.theme.dataColors

/**
 * One point on a trend: the short label a tick shows ("3 Sep", "9p"), its value, and —
 * when the point is a day — that day, so a screen can do something with the answer.
 */
data class TrendPoint(
    val label: String,
    val value: Long,
    val epochDay: Long? = null,
)

/**
 * A goal drawn across a trend, in the same units as the points.
 *
 * Whether one of these may be drawn is the caller's decision, and it is a question of
 * period rather than taste: a daily cap on an hour-by-hour chart describes nothing, and a
 * weekly cap on a daily chart would have to be divided by seven to be drawn at all — which
 * turns a number the user chose into a number nobody chose. Draw a target only where its
 * own period matches what the chart plots.
 */
data class TargetLine(val value: Long, val label: String)

/**
 * A trend over a baseline, with axes you can read and points you can touch.
 *
 * Dotted axes and three labelled levels exist so a glance can tell "double yesterday" from
 * "half of yesterday" before any interaction. Scrubbing then answers the exact question —
 * tap or drag to pin a point and the callout names it and its value, because a shape alone
 * says *that* something happened, never *when* or *how much*.
 *
 * The scale is rounded up to a clean maximum ([axisCeiling]) rather than stretched to the
 * peak, so the axis labels are round numbers and a quiet week does not look like a busy one.
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    valueFormat: (Long) -> String = DurationFormat::compact,
    axisCeiling: (Long) -> Long = ::niceCeilMs,
    axisLabel: (Long) -> String = ::axisTickLabel,
    onPointActivated: ((Int) -> Unit)? = null,
    target: TargetLine? = null,
    emptyMessage: String = "No usage in this range",
) {
    val scheme = MaterialTheme.colorScheme
    val data = dataColors
    val labelStyle = MaterialTheme.typography.labelSmall
    val valueStyle = MaterialTheme.typography.titleSmall
    val textMeasurer = rememberTextMeasurer()

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val pin = remember(points.size) { mutableStateOf<Int?>(null) }
    val activate = rememberUpdatedState(onPointActivated)
    val pinned = pin.value

    val peak = points.maxOfOrNull { it.value } ?: 0L
    // A target joins the scale only when there is data to scale against: on an empty chart
    // it would stretch the axis to the goal and leave the empty message floating in it.
    val drawnTarget = target?.takeIf { peak > 0L }
    val axisMax = remember(peak, drawnTarget) { axisCeiling(maxOf(peak, drawnTarget?.value ?: 0L)) }
    val yLabels = remember(axisMax) { axisTickLabels(axisMax, axisLabel) }
    val geometry = rememberChartGeometry(canvasSize, yLabels, labelStyle, textMeasurer)
    val xTicks = remember(points, geometry.plot) {
        axisTickIndices(points.size).map { index ->
            xOf(index, points.size, geometry.plot.left, geometry.plot.width) to points[index].label
        }
    }

    val reveal by animateFloatAsState(
        targetValue = if (peak > 0L) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER_MS + 220, easing = Motion.expoOut),
        label = "trendReveal",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { canvasSize = it }
            .chartScrub(points.size, geometry.plot, pin, activate)
            .semantics {
                contentDescription = if (peak > 0L) {
                    val targetText = drawnTarget?.let { ", target ${valueFormat(it.value)}" }.orEmpty()
                    "Trend across ${points.size} points, peak ${valueFormat(peak)}$targetText"
                } else {
                    emptyMessage
                }
                val pinnedPoint = pinned?.let(points::getOrNull)
                if (pinnedPoint != null) {
                    stateDescription = "${pinnedPoint.label}, ${valueFormat(pinnedPoint.value)}"
                }
            },
    ) {
        val plot = geometry.plot
        // Nothing is measurable until the canvas has been laid out once.
        if (plot.width <= 0f || plot.height <= 0f) return@Canvas
        drawChartAxes(
            geometry = geometry,
            xTicks = xTicks,
            gridColor = scheme.outlineVariant.copy(alpha = 0.6f),
            labelColor = scheme.onSurfaceVariant,
            labelStyle = labelStyle,
            textMeasurer = textMeasurer,
        )

        if (peak <= 0L) {
            drawChartEmptyMessage(
                plot = plot,
                message = emptyMessage,
                color = scheme.onSurfaceVariant,
                style = labelStyle,
                textMeasurer = textMeasurer,
            )
            return@Canvas
        }

        val maxValue = axisMax.toFloat()
        fun pointX(index: Int) = xOf(index, points.size, plot.left, plot.width)
        fun pointY(value: Long) = plot.bottom - plot.height * (value.toFloat() / maxValue)

        // Before the data and outside the reveal clip, so the goal is on the chart from the
        // first frame: a target that faded in with the line would read as part of it.
        if (drawnTarget != null) {
            drawChartTarget(
                plot = plot,
                y = pointY(drawnTarget.value),
                label = drawnTarget.label,
                color = data.target,
                backingColor = scheme.surfaceContainerLow,
                labelStyle = labelStyle,
                textMeasurer = textMeasurer,
            )
        }

        if (points.size == 1) {
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(pointX(0), pointY(points[0].value)))
        } else {
            // Reveal by clipping in from the left, so the line draws itself across.
            clipRect(right = plot.left + plot.width * reveal, top = plot.top) {
                val linePath = Path().apply {
                    moveTo(pointX(0), pointY(points[0].value))
                    for (index in 1 until points.size) {
                        // Gentle horizontal midpoint smoothing, enough to read as a trend
                        // rather than a polyline, without inventing data.
                        val previousX = pointX(index - 1)
                        val previousY = pointY(points[index - 1].value)
                        val currentX = pointX(index)
                        val currentY = pointY(points[index].value)
                        val midX = (previousX + currentX) / 2f
                        cubicTo(midX, previousY, midX, currentY, currentX, currentY)
                    }
                }

                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(pointX(points.lastIndex), plot.bottom)
                    lineTo(pointX(0), plot.bottom)
                    close()
                }

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.34f),
                            lineColor.copy(alpha = 0.02f),
                        ),
                        startY = plot.top,
                        endY = plot.bottom,
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

                // Mark the most recent point, the one the user is standing on.
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(pointX(points.lastIndex), pointY(points.last().value)),
                )
                drawCircle(
                    color = scheme.surface,
                    radius = 2.dp.toPx(),
                    center = Offset(pointX(points.lastIndex), pointY(points.last().value)),
                )
            }
        }

        if (pinned != null) {
            val point = points.getOrNull(pinned)
            if (point != null) {
                drawChartCallout(
                    plot = plot,
                    anchor = Offset(pointX(pinned), pointY(point.value)),
                    label = point.label,
                    value = valueFormat(point.value),
                    lineColor = lineColor,
                    bubbleColor = scheme.surfaceContainerHigh,
                    borderColor = scheme.outlineVariant,
                    labelColor = scheme.onSurfaceVariant,
                    valueColor = scheme.onSurface,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    textMeasurer = textMeasurer,
                )
            }
        }
    }
}
