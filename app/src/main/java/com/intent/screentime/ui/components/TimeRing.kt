package com.intent.screentime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intent.screentime.ui.theme.Motion
import com.intent.screentime.ui.theme.dataColors

/**
 * The hero instrument: a day's screen time measured against its cap.
 *
 * Three states, all designed:
 *  - **no cap set** — a dashed guide track, so the ring reads as "unconfigured" rather
 *    than "empty" or, worse, "100% used"
 *  - **within cap** — a brand-gradient arc sweeping clockwise from twelve o'clock
 *  - **over cap** — the arc turns to the error colour and the caller surfaces the
 *    overage, because exceeding a self-set limit is the one thing worth interrupting for
 *
 * A sweep rather than a full disc: the eye reads an arc's remaining gap as "room left",
 * which is the whole point of the screen.
 */
@Composable
fun TimeRing(
    usedMs: Long,
    targetMs: Long?,
    modifier: Modifier = Modifier,
    diameter: Dp = 232.dp,
    strokeWidth: Dp = 22.dp,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val data = dataColors

    val hasTarget = targetMs != null && targetMs > 0L
    val over = hasTarget && usedMs > targetMs

    val rawProgress = if (hasTarget) usedMs.toFloat() / targetMs.toFloat() else 0f
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = Motion.RING_MS, easing = Motion.expoOut),
        label = "ringProgress",
    )

    val arcBrush = if (over) {
        SolidColor(scheme.error)
    } else {
        Brush.sweepGradient(listOf(scheme.primary, data.consumption, scheme.primary))
    }

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = scheme.surfaceContainerHighest,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = if (hasTarget) {
                        null
                    } else {
                        PathEffect.dashPathEffect(floatArrayOf(stroke * 0.35f, stroke * 0.9f))
                    },
                ),
            )

            if (progress > 0f) {
                drawArc(
                    brush = arcBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        content()
    }
}
