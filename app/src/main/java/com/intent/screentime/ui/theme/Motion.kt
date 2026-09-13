package com.intent.screentime.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Motion personality: decisive, settling, no bounce.
 *
 * A screen-time app is a measuring instrument, so it should feel like one — movement
 * that arrives quickly and comes to rest cleanly. Overshoot and elastic read as toy-like
 * and would undercut the data.
 */
object Motion {
    /** Sharp deceleration. Arrives fast, eases into place. */
    val expoOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Gentle acceleration for exits. */
    val exitEase: Easing = CubicBezierEasing(0.4f, 0f, 0.8f, 0.4f)

    const val ENTER_MS = 520
    const val EXIT_MS = 360
    const val RING_MS = 900

    /** Cascade offset. Deliberately uneven so the list does not march in lockstep. */
    fun staggerDelay(index: Int): Int = (index * 38).coerceAtMost(320)
}
