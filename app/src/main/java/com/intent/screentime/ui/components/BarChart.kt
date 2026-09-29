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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

data class ChartBar(
    val label: String,
    val value: Long,
    val color: Color,
)

/**
 * A bar per category, on the same dotted axes as the trend.
 *
 * Bars rather than a line because these are comparisons, not a sequence: nothing is
 * "between" Wednesday and Thursday. Tapping one pins it and names its value, which is what
 * turns "Wednesdays are worse" into "Wednesdays are worse by 1h 40m" — and the unselected
 * bars step back so the answer is unambiguous.
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
    valueFormat: (Long) -> String = DurationFormat::compact,
    axisCeiling: (Long) -> Long = ::niceCeilMs,
    axisLabel: (Long) -> String = ::axisTickLabel,
    emptyMessage: String = "No usage in this range",
) {
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall
    val valueStyle = MaterialTheme.typography.titleSmall
    val textMeasurer = rememberTextMeasurer()

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val pin = remember(bars.size) { mutableStateOf<Int?>(null) }
    val pinned = pin.value

    val peak = bars.maxOfOrNull { it.value } ?: 0L
    val axisMax = remember(peak) { axisCeiling(peak) }
    val yLabels = remember(axisMax) { axisTickLabels(axisMax, axisLabel) }
    val geometry = rememberChartGeometry(canvasSize, yLabels, labelStyle, textMeasurer)
    val xTicks = remember(bars, geometry.plot) {
        bars.indices.map { index ->
            barCenterX(index, bars.size, geometry.plot.left, geometry.plot.width) to bars[index].label
        }
    }

    val reveal by animateFloatAsState(
        targetValue = if (peak > 0L) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER_MS, easing = Motion.expoOut),
        label = "barReveal",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { canvasSize = it }
            .chartScrub(bars.size, geometry.plot, pin, slotBars = true, toggleOff = true)
            .semantics {
                contentDescription = if (peak > 0L) {
                    "Bar chart, ${bars.size} bars, highest ${valueFormat(peak)}"
                } else {
                    emptyMessage
                }
                val pinnedBar = pinned?.let(bars::getOrNull)
                if (pinnedBar != null) {
                    stateDescription = "${pinnedBar.label}, ${valueFormat(pinnedBar.value)}"
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
        val slot = plot.width / bars.size
        val barWidth = (slot * 0.52f).coerceAtMost(34.dp.toPx())
        val minHeight = 3.dp.toPx()

        bars.forEachIndexed { index, bar ->
            val fraction = (bar.value.toFloat() / maxValue).coerceIn(0f, 1f)
            val barHeight = (plot.height * fraction * reveal).coerceAtLeast(minHeight)
            val center = barCenterX(index, bars.size, plot.left, plot.width)
            val color = when {
                bar.value == 0L -> scheme.outlineVariant.copy(alpha = 0.5f)
                pinned == null || pinned == index -> bar.color
                else -> bar.color.copy(alpha = 0.4f)
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(center - barWidth / 2f, plot.bottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }

        if (pinned != null) {
            val bar = bars.getOrNull(pinned)
            if (bar != null) {
                val fraction = (bar.value.toFloat() / maxValue).coerceIn(0f, 1f)
                drawChartCallout(
                    plot = plot,
                    anchor = Offset(
                        x = barCenterX(pinned, bars.size, plot.left, plot.width),
                        y = plot.bottom - plot.height * fraction,
                    ),
                    label = bar.label,
                    value = valueFormat(bar.value),
                    lineColor = bar.color,
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
