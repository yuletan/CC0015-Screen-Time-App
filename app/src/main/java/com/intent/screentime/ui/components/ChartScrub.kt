package com.intent.screentime.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Turns a chart into something you can interrogate: tap to pin a value, drag to scrub
 * across the range, hover with a pointer.
 *
 * Pinning is sticky rather than held, because the thing doing the pointing is a fingertip
 * covering the very data being read — the value has to survive the release to be useful.
 * A second tap either hands back to the caller ([activate]) or clears the pin.
 *
 * The pinned index lives in a [MutableState] passed by the caller rather than a captured
 * `Int?`, so the gesture handlers always read the live value instead of the one that
 * existed when the modifier was built.
 */
internal fun Modifier.chartScrub(
    count: Int,
    plot: Rect,
    pin: MutableState<Int?>,
    activate: State<((Int) -> Unit)?>? = null,
    slotBars: Boolean = false,
    toggleOff: Boolean = false,
): Modifier = this
    .pointerInput(count, plot, slotBars, toggleOff) {
        detectTapGestures { offset ->
            val index = scrubIndexAt(offset.x, count, plot, slotBars) ?: return@detectTapGestures
            if (index != pin.value) {
                pin.value = index
            } else if (toggleOff) {
                pin.value = null
            } else {
                activate?.value?.invoke(index)
            }
        }
    }
    .pointerInput(count, plot, slotBars) {
        detectDragGestures { change, _ ->
            scrubIndexAt(change.position.x, count, plot, slotBars)?.let { pin.value = it }
            change.consume()
        }
    }
    .pointerInput(count, plot, slotBars) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                val quiet = !change.pressed

                // Only a pointer that can genuinely hover. A fingertip cannot, and its
                // trailing move events would otherwise pin points nobody asked about.
                val hovering = event.type == PointerEventType.Move &&
                    quiet &&
                    change.type != PointerType.Touch

                when {
                    hovering -> scrubIndexAt(change.position.x, count, plot, slotBars)
                        ?.let { pin.value = it }

                    // A pointer that has left has stopped asking.
                    event.type == PointerEventType.Exit && quiet -> pin.value = null
                }
            }
        }
    }

private fun scrubIndexAt(x: Float, count: Int, plot: Rect, slotBars: Boolean): Int? =
    if (slotBars) {
        barIndexAtX(x, count, plot.left, plot.width)
    } else {
        indexAtX(x, count, plot.left, plot.width)
    }
