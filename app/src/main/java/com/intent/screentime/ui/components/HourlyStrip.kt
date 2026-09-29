package com.intent.screentime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import com.intent.screentime.core.format.HourFormat
import com.intent.screentime.data.usage.HourlyBreakdown
import com.intent.screentime.ui.theme.Motion
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

/**
 * The shape of the day, hour by hour.
 *
 * Each bar is coloured by **how much of that hour** the phone took — calm under half an
 * hour, amber to three quarters, red past that — which turns an abstract total into a
 * readable story: a scatter of red spikes after 21:00 is a different day from the same
 * total spread thinly, and only the strip shows the difference.
 *
 * That is a change of question. The bar used to be coloured by the *kind* of time, and it
 * cannot answer both at once: an hour can be all consumption and still be nothing worth
 * noticing. So the category moved into the read-out instead, where it is said in words —
 * which is also the only form that reaches a reader who cannot separate the three hues.
 *
 * Tap or drag any hour to pin it and read its exact minutes, and the app that took them.
 * The pin is sticky rather than held, because the fingertip is covering the very bar being
 * read.
 */
@Composable
fun HourlyStrip(
    buckets: List<HourlyBreakdown.Bucket>,
    modifier: Modifier = Modifier,
    /**
     * Taller than a plain bar chart would need: the read-out bubble is drawn inside the
     * canvas, so the strip has to have room for it.
     */
    height: Dp = 132.dp,
    /** Resolves a package name to something a person reads. Null leaves the app unnamed. */
    labelOf: ((String) -> String)? = null,
) {
    val data = dataColors
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall
    val valueStyle = MaterialTheme.typography.titleSmall
    val textMeasurer = rememberTextMeasurer()
    val peak = buckets.maxOfOrNull { it.totalMs } ?: 0L

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val pin = remember(buckets.size) { mutableStateOf<Int?>(null) }
    val pinned = pin.value

    // This strip has no axes or gutters, so the whole canvas is the plot and a finger's x
    // position maps straight onto the slot it is covering.
    val plot = remember(canvasSize) {
        Rect(0f, 0f, canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }

    val reveal by animateFloatAsState(
        targetValue = if (peak > 0L) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER_MS, easing = Motion.expoOut),
        label = "stripReveal",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { canvasSize = it }
                .chartScrub(buckets.size, plot, pin, slotBars = true, toggleOff = true)
                .semantics {
                    contentDescription = if (peak > 0L) {
                        "Shape of the day, hour by hour, busiest " +
                            DurationFormat.compact(peak)
                    } else {
                        "Shape of the day, hour by hour, with no usage recorded"
                    }
                    pinned?.let(buckets::getOrNull)?.let { bucket ->
                        stateDescription = describeHour(bucket, labelOf)
                    }
                },
        ) {
            if (buckets.isEmpty()) return@Canvas

            val slot = size.width / buckets.size
            val barWidth = slot * 0.62f
            val minHeight = 3.dp.toPx()

            buckets.forEachIndexed { index, bucket ->
                val fraction = if (peak > 0L) bucket.totalMs.toFloat() / peak.toFloat() else 0f
                val barHeight = (size.height * fraction * reveal).coerceAtLeast(minHeight)
                val x = index * slot + (slot - barWidth) / 2f
                val base = barColor(bucket, scheme.outlineVariant, data)

                drawRoundRect(
                    color = if (pinned == null || pinned == index) {
                        base
                    } else {
                        base.copy(alpha = 0.4f)
                    },
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }

            val bucket = pinned?.let(buckets::getOrNull)
            if (bucket != null) {
                val fraction = if (peak > 0L) bucket.totalMs.toFloat() / peak.toFloat() else 0f
                val barHeight = (size.height * fraction * reveal).coerceAtLeast(minHeight)

                drawChartCallout(
                    plot = plot,
                    anchor = Offset(
                        x = barCenterX(pinned, buckets.size, 0f, size.width),
                        y = size.height - barHeight,
                    ),
                    label = HourFormat.short(bucket.hour),
                    value = if (bucket.totalMs == 0L) {
                        "none"
                    } else {
                        DurationFormat.compact(bucket.totalMs)
                    },
                    lineColor = barColor(bucket, scheme.outline, data),
                    bubbleColor = scheme.surfaceContainerHigh,
                    borderColor = scheme.outlineVariant,
                    labelColor = scheme.onSurfaceVariant,
                    valueColor = scheme.onSurface,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    textMeasurer = textMeasurer,
                    note = hourNote(bucket, labelOf),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(0, 6, 12, 18, 23).forEach { hour ->
                Text(
                    text = HourFormat.short(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        HourlyStripLegend()
    }
}

/** The legend under the strip. Colour is always paired with a word. */
@Composable
fun HourlyStripLegend(modifier: Modifier = Modifier) {
    val data = dataColors
    val shape = RoundedCornerShape(3.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HourBand.entries.forEach { band ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(10.dp).clip(shape).background(band.color(data)))
                Text(
                    text = band.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** An empty hour is absence, not a calm one, so it keeps the faint outline colour. */
private fun barColor(
    bucket: HourlyBreakdown.Bucket,
    empty: Color,
    data: com.intent.screentime.ui.theme.DataColors,
): Color = if (bucket.totalMs == 0L) {
    empty.copy(alpha = 0.45f)
} else {
    HourBand.of(bucket.totalMs).color(data)
}

/**
 * What that hour was spent on: the app with the most of it, and what kind of time that was.
 * Null when the hour is empty or the app cannot be named.
 */
internal fun hourNote(
    bucket: HourlyBreakdown.Bucket,
    labelOf: ((String) -> String)?,
): String? {
    val packageName = bucket.dominantPackage ?: return null
    val app = labelOf?.invoke(packageName) ?: return null
    return listOfNotNull(app, bucket.dominantKind?.displayLabel()).joinToString(" · ")
}

internal fun describeHour(
    bucket: HourlyBreakdown.Bucket,
    labelOf: ((String) -> String)?,
): String {
    val hour = HourFormat.short(bucket.hour)
    if (bucket.totalMs == 0L) return "$hour, no screen time"

    val used = DurationFormat.compact(bucket.totalMs)
    val note = hourNote(bucket, labelOf) ?: return "$hour, $used"
    return "$hour, $used, $note"
}

/** Peak hour, used for the "busiest hour" callout. */
fun busiestHour(buckets: List<HourlyBreakdown.Bucket>): HourlyBreakdown.Bucket? =
    buckets.maxByOrNull { it.totalMs }?.takeIf { it.totalMs > 0L }
