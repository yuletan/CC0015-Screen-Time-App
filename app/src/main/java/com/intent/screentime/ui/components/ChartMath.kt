package com.intent.screentime.ui.components

import com.intent.screentime.core.format.DurationFormat

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 3_600_000L

/**
 * Axis maxima a duration chart may land on.
 *
 * Every one halves into another readable tick, so "0 / 1.5h / 3h" never turns into
 * "0 / 0.8h / 1.5h". A chart that quotes 3h should mean 3h.
 */
private val DURATION_STEPS = longArrayOf(
    15 * MINUTE_MS,
    30 * MINUTE_MS,
    HOUR_MS,
    HOUR_MS + 30 * MINUTE_MS,
    2 * HOUR_MS,
    3 * HOUR_MS,
    4 * HOUR_MS,
    6 * HOUR_MS,
    8 * HOUR_MS,
    12 * HOUR_MS,
    24 * HOUR_MS,
)

private val PERCENT_STEPS = longArrayOf(10, 20, 25, 50, 75, 100)

/** Rounds a duration peak up to a clean axis maximum. */
fun niceCeilMs(peakMs: Long): Long = niceCeil(peakMs, DURATION_STEPS, HOUR_MS)

/** The same, for a percentage axis. */
fun niceCeilPercent(peak: Long): Long = niceCeil(peak, PERCENT_STEPS, 10)

private fun niceCeil(peak: Long, steps: LongArray, fallbackStep: Long): Long {
    if (peak <= 0L) return 0L
    steps.firstOrNull { it >= peak }?.let { return it }
    return ((peak + fallbackStep - 1) / fallbackStep) * fallbackStep
}

/** "45m", "3h", "1.5h" — short enough for an axis gutter. */
fun axisTickLabel(ms: Long): String = when {
    ms <= 0L -> "0"
    ms < HOUR_MS -> "${ms / MINUTE_MS}m"
    ms % HOUR_MS == 0L -> "${ms / HOUR_MS}h"
    else -> DurationFormat.hoursDecimal(ms)
}

/**
 * The data indices that get a tick, spread evenly and always including both ends.
 *
 * A stride rather than a fixed count, so 24 hourly points land on 0 / 6 / 12 / 18 / 23 and
 * 30 daily points on 0 / 8 / 16 / 24 / 29 instead of a ragged division.
 */
fun axisTickIndices(count: Int, maxTicks: Int = 5): List<Int> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(0)

    val ticks = maxTicks.coerceAtLeast(2)
    val stride = ((count - 1) + (ticks - 2)) / (ticks - 1)
    return buildList {
        var index = 0
        while (index < count - 1) {
            add(index)
            index += stride
        }
        add(count - 1)
    }
}

/** X pixel of a data index across a plot area. */
fun xOf(index: Int, count: Int, left: Float, width: Float): Float {
    if (count <= 1) return left
    return left + width * index / (count - 1).toFloat()
}

/** The data index nearest an x pixel — what a finger is actually pointing at. */
fun indexAtX(x: Float, count: Int, left: Float, width: Float): Int? {
    if (count <= 0 || width <= 0f) return null
    if (count == 1) return 0
    val fraction = ((x - left) / width).coerceIn(0f, 1f)
    return Math.round(fraction * (count - 1)).coerceIn(0, count - 1)
}

/** Centre of a bar's slot. Bars share the width evenly instead of sitting on the ends. */
fun barCenterX(index: Int, count: Int, left: Float, width: Float): Float {
    if (count <= 0) return left
    return left + width * (index + 0.5f) / count
}

/** The bar index nearest an x pixel. Every pixel inside the plot belongs to one bar. */
fun barIndexAtX(x: Float, count: Int, left: Float, width: Float): Int? {
    if (count <= 0 || width <= 0f) return null
    val fraction = ((x - left) / width).coerceIn(0f, 0.9999f)
    return (fraction * count).toInt().coerceIn(0, count - 1)
}
