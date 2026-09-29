package com.intent.screentime.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * Collects "the photo of what is seen in the app" for a period download.
 *
 * Each [CapturablePanel] registers a small lambda that rasterises its own graphics
 * layer — the exact pixels on screen, not a redraw. The export button then runs every
 * registered lambda and hands the bitmaps to the exporter, which files them under
 * date-named photo names. Panels that were never composed (scrolled far off, or a
 * past window with no UI) simply have no entry, and the exporter falls back to its
 * Canvas redraw for those.
 */
class ChartCaptureRegistry {
    private val captures = LinkedHashMap<String, suspend () -> Bitmap?>()

    fun register(key: String, capture: suspend () -> Bitmap?) {
        captures[key] = capture
    }

    fun unregister(key: String) {
        captures.remove(key)
    }

    /** Runs every registered capture, skipping failures so one panel never sinks the zip. */
    suspend fun captureAll(): Map<String, Bitmap> {
        val out = LinkedHashMap<String, Bitmap>()
        for ((key, capture) in captures.toMap()) {
            val bitmap = runCatching { capture() }.getOrNull() ?: continue
            // A layer that was composed but never drawn hands back a 0-size image.
            if (bitmap.width <= 0 || bitmap.height <= 0) continue
            out[key] = bitmap
        }
        return out
    }
}

val LocalChartCaptureRegistry = compositionLocalOf<ChartCaptureRegistry?> { null }

@Composable
fun rememberChartCaptureRegistry(): ChartCaptureRegistry =
    remember { ChartCaptureRegistry() }
