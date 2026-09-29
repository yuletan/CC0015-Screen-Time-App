package com.intent.screentime.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Where the data sits inside a chart canvas, once the axis gutters are reserved.
 *
 * The gutters are measured from the real label text rather than assumed, so a longer tick
 * ("1.5h") — or the same tick at a 1.3× font scale — widens the margin instead of
 * colliding with the plot.
 */
internal data class ChartGeometry(
    val plot: Rect,
    /** Y pixel and its label, top tick first. */
    val yTicks: List<Pair<Float, String>>,
)

@Composable
internal fun rememberChartGeometry(
    canvasSize: IntSize,
    yLabels: List<String>,
    labelStyle: TextStyle,
    textMeasurer: TextMeasurer,
): ChartGeometry {
    val density = LocalDensity.current
    val widest = remember(yLabels, labelStyle, density) {
        yLabels.maxOfOrNull { textMeasurer.measure(it, labelStyle).size.width } ?: 0
    }
    val labelHeight = remember(labelStyle, density) {
        textMeasurer.measure("0", labelStyle).size.height
    }

    return remember(canvasSize, yLabels, widest, labelHeight, density) {
        with(density) {
            val plot = Rect(
                left = widest.toFloat() + 8.dp.toPx(),
                top = 10.dp.toPx(),
                right = canvasSize.width.toFloat(),
                bottom = canvasSize.height.toFloat() - labelHeight - 8.dp.toPx(),
            )
            ChartGeometry(
                plot = plot,
                yTicks = yLabels.mapIndexed { index, label ->
                    val fraction = if (yLabels.size <= 1) {
                        0f
                    } else {
                        1f - index / (yLabels.size - 1).toFloat()
                    }
                    plot.bottom - plot.height * fraction to label
                },
            )
        }
    }
}

/**
 * The three levels every chart labels: the maximum, halfway, and zero.
 *
 * A single "0" when there is nothing to scale — three ticks stacked on the baseline would
 * be decoration pretending to be an axis.
 */
internal fun axisTickLabels(axisMax: Long, label: (Long) -> String): List<String> =
    if (axisMax <= 0L) {
        listOf(label(0L))
    } else {
        listOf(label(axisMax), label(axisMax / 2), label(0L))
    }

/**
 * The dotted frame every chart shares: both axis lines, the horizontal levels, and their
 * labels.
 *
 * Dotted rather than solid so the axes stay visibly *underneath* the data — they give a
 * value to read against without competing with the line or the bars.
 */
internal fun DrawScope.drawChartAxes(
    geometry: ChartGeometry,
    xTicks: List<Pair<Float, String>>,
    gridColor: Color,
    labelColor: Color,
    labelStyle: TextStyle,
    textMeasurer: TextMeasurer,
) {
    val plot = geometry.plot
    if (plot.width <= 0f || plot.height <= 0f) return

    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
    val hairline = 1.dp.toPx()
    val labelGap = 6.dp.toPx()

    drawLine(
        color = gridColor,
        start = Offset(plot.left, plot.top),
        end = Offset(plot.left, plot.bottom),
        strokeWidth = hairline,
        pathEffect = dash,
    )
    drawLine(
        color = gridColor,
        start = Offset(plot.left, plot.bottom),
        end = Offset(plot.right, plot.bottom),
        strokeWidth = hairline,
        pathEffect = dash,
    )

    geometry.yTicks.forEach { (y, label) ->
        if (y < plot.bottom - 1f) {
            drawLine(
                color = gridColor,
                start = Offset(plot.left, y),
                end = Offset(plot.right, y),
                strokeWidth = hairline,
                pathEffect = dash,
            )
        }
        val text = textMeasurer.measure(label, labelStyle)
        drawText(
            textLayoutResult = text,
            color = labelColor,
            topLeft = Offset(
                x = (plot.left - labelGap - text.size.width).coerceAtLeast(0f),
                y = (y - text.size.height / 2f)
                    .coerceIn(0f, (size.height - text.size.height).coerceAtLeast(0f)),
            ),
        )
    }

    // A label that would collide with the previous one is dropped, not overlapped: at 30
    // points, or at a large font scale, the alternative is an unreadable smear of dates.
    var lastRight = -Float.MAX_VALUE
    xTicks.forEach { (x, label) ->
        val text = textMeasurer.measure(label, labelStyle)
        val left = (x - text.size.width / 2f)
            .coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
        if (left <= lastRight + labelGap) return@forEach
        lastRight = left + text.size.width

        if (x > plot.left + 1f) {
            drawLine(
                color = gridColor,
                start = Offset(x, plot.top),
                end = Offset(x, plot.bottom),
                strokeWidth = hairline,
                pathEffect = dash,
            )
        }
        drawText(
            textLayoutResult = text,
            color = labelColor,
            topLeft = Offset(left, plot.bottom + labelGap),
        )
    }
}

/** Axis lines without any data behind them, plus the reason there is none. */
internal fun DrawScope.drawChartEmptyMessage(
    plot: Rect,
    message: String,
    color: Color,
    style: TextStyle,
    textMeasurer: TextMeasurer,
) {
    if (plot.width <= 0f || plot.height <= 0f) return
    val text = textMeasurer.measure(message, style)
    drawText(
        textLayoutResult = text,
        color = color,
        topLeft = Offset(
            x = plot.left + (plot.width - text.size.width) / 2f,
            y = plot.top + (plot.height - text.size.height) / 2f,
        ),
    )
}

/**
 * The line a target is drawn with: a dashed rule across the plot, and its name at the
 * right end.
 *
 * Annotations, not data. Dashed and drawn in ink rather than in one of the data hues, so
 * it reads as a note about the chart rather than as another series inside it — and so it
 * never implies that the goal itself is going well or badly.
 *
 * The label sits on a surface-coloured backing because the gutter is already occupied by
 * the y-axis ticks and the data line could be crossing the rule anywhere; the pill lifts
 * the text off whatever happens to be behind it.
 */
internal fun DrawScope.drawChartTarget(
    plot: Rect,
    y: Float,
    label: String,
    color: Color,
    backingColor: Color,
    labelStyle: TextStyle,
    textMeasurer: TextMeasurer,
) {
    if (plot.width <= 0f || plot.height <= 0f) return
    if (y < plot.top - 1f || y > plot.bottom + 1f) return

    drawLine(
        color = color,
        start = Offset(plot.left, y),
        end = Offset(plot.right, y),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )

    val text = textMeasurer.measure(label, labelStyle)
    val padH = 6.dp.toPx()
    val padV = 2.dp.toPx()
    val width = text.size.width + padH * 2
    val height = text.size.height + padV * 2

    // Right-aligned inside the plot, and flipped below the rule when sitting above it
    // would push the label off the top of the canvas.
    val left = (plot.right - width).coerceAtLeast(plot.left)
    val above = y - height - 3.dp.toPx()
    val top = if (above >= plot.top) above else y + 3.dp.toPx()

    drawRoundRect(
        color = backingColor,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
    drawText(
        textLayoutResult = text,
        color = color,
        topLeft = Offset(left + padH, top + padV),
    )
}

/**
 * The read-out for a touched point: a dotted drop-line, a ring on the point itself, and a
 * bubble naming it.
 *
 * The bubble is clamped inside the canvas and flips below the point when there is no room
 * above, so the answer to "how much was that?" is never half off-screen.
 */
internal fun DrawScope.drawChartCallout(
    plot: Rect,
    anchor: Offset,
    label: String,
    value: String,
    lineColor: Color,
    bubbleColor: Color,
    borderColor: Color,
    labelColor: Color,
    valueColor: Color,
    labelStyle: TextStyle,
    valueStyle: TextStyle,
    textMeasurer: TextMeasurer,
    /** A third, quieter line under the value: what the point was spent on. */
    note: String? = null,
) {
    drawLine(
        color = lineColor.copy(alpha = 0.55f),
        start = Offset(anchor.x, plot.top),
        end = Offset(anchor.x, plot.bottom),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())),
    )

    drawCircle(color = bubbleColor, radius = 6.dp.toPx(), center = anchor)
    drawCircle(
        color = lineColor,
        radius = 6.dp.toPx(),
        center = anchor,
        style = Stroke(width = 2.5.dp.toPx()),
    )

    val labelText = textMeasurer.measure(label, labelStyle)
    val valueText = textMeasurer.measure(value, valueStyle)
    val noteText = note?.let { textMeasurer.measure(it, labelStyle) }
    val padH = 10.dp.toPx()
    val padV = 7.dp.toPx()
    val gap = 2.dp.toPx()
    val widest = maxOf(
        labelText.size.width,
        valueText.size.width,
        noteText?.size?.width ?: 0,
    )
    val lines = labelText.size.height + valueText.size.height +
        (noteText?.size?.height ?: 0) + gap * (if (noteText == null) 1 else 2)
    val width = widest + padH * 2
    val height = lines + padV * 2

    val left = (anchor.x - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
    val above = anchor.y - height - 14.dp.toPx()
    val top = if (above >= 0f) {
        above
    } else {
        (anchor.y + 14.dp.toPx()).coerceAtMost((size.height - height).coerceAtLeast(0f))
    }

    val corner = CornerRadius(12.dp.toPx())
    drawRoundRect(
        color = bubbleColor,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )
    drawText(
        textLayoutResult = labelText,
        color = labelColor,
        topLeft = Offset(left + (width - labelText.size.width) / 2f, top + padV),
    )
    drawText(
        textLayoutResult = valueText,
        color = valueColor,
        topLeft = Offset(
            left + (width - valueText.size.width) / 2f,
            top + padV + labelText.size.height + gap,
        ),
    )
    if (noteText != null) {
        drawText(
            textLayoutResult = noteText,
            color = labelColor,
            topLeft = Offset(
                left + (width - noteText.size.width) / 2f,
                top + padV + labelText.size.height + valueText.size.height + gap * 2,
            ),
        )
    }
}
