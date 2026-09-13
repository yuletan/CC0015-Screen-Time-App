package com.intent.screentime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intent.screentime.data.usage.HourlyBreakdown
import com.intent.screentime.ui.theme.Motion
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

/**
 * The shape of the day, hour by hour.
 *
 * Coloured by whichever category dominated each hour, which turns an abstract total into
 * a readable story: a solid gold block is a working morning, a scatter of magenta spikes
 * after 21:00 is the scroll that ate the evening. The two situations can share a total
 * and look nothing alike, which is the whole reason this strip exists.
 */
@Composable
fun HourlyStrip(
    buckets: List<HourlyBreakdown.Bucket>,
    modifier: Modifier = Modifier,
    height: Dp = 108.dp,
) {
    val data = dataColors
    val scheme = MaterialTheme.colorScheme
    val peak = buckets.maxOfOrNull { it.totalMs } ?: 0L

    val reveal by animateFloatAsState(
        targetValue = if (peak > 0L) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER_MS, easing = Motion.expoOut),
        label = "stripReveal",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (buckets.isEmpty()) return@Canvas

            val slot = size.width / buckets.size
            val barWidth = slot * 0.62f
            val minHeight = 3.dp.toPx()

            buckets.forEachIndexed { index, bucket ->
                val fraction = if (peak > 0L) bucket.totalMs.toFloat() / peak.toFloat() else 0f
                val barHeight = (size.height * fraction * reveal).coerceAtLeast(minHeight)
                val x = index * slot + (slot - barWidth) / 2f

                drawRoundRect(
                    color = if (bucket.totalMs == 0L) {
                        scheme.outlineVariant.copy(alpha = 0.45f)
                    } else {
                        categoryColor(bucket.dominantKind, data)
                    },
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(0, 6, 12, 18, 23).forEach { hour ->
                Text(
                    text = hourLabel(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun hourLabel(hour: Int): String = when (hour) {
    0 -> "12a"
    12 -> "12p"
    23 -> "11p"
    in 1..11 -> "${hour}a"
    else -> "${hour - 12}p"
}

/** Peak hour, used for the "busiest hour" callout. */
fun busiestHour(buckets: List<HourlyBreakdown.Bucket>): HourlyBreakdown.Bucket? =
    buckets.maxByOrNull { it.totalMs }?.takeIf { it.totalMs > 0L }
