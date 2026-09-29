package com.intent.screentime.ui.components

import androidx.compose.ui.graphics.Color
import com.intent.screentime.ui.theme.DataColors

/**
 * How much of a single hour the phone took, as three bands.
 *
 * A different question from [categoryColor], which says what *kind* of time it was: an
 * hour can be all consumption and still be nothing worth noticing, or all production and
 * still be an hour that got away. The strip answers the second question, so the bands are
 * cut on quantity rather than on category — and the read-out names the category in words,
 * because the fill can only carry one scale at a time.
 *
 * The thresholds live here rather than at a call site so the strip, the legend and the
 * read-out can never disagree about what "heavy" means.
 */
enum class HourBand {
    CALM,
    WATCHFUL,
    STRAINED;

    companion object {
        /** Half the hour gone. */
        const val WATCHFUL_MS = 30 * 60_000L

        /** Three quarters of the hour gone. */
        const val STRAINED_MS = 45 * 60_000L

        fun of(totalMs: Long): HourBand = when {
            totalMs >= STRAINED_MS -> STRAINED
            totalMs >= WATCHFUL_MS -> WATCHFUL
            else -> CALM
        }
    }
}

fun HourBand.color(data: DataColors): Color = when (this) {
    HourBand.CALM -> data.calm
    HourBand.WATCHFUL -> data.watchful
    HourBand.STRAINED -> data.strained
}

/** The legend entry and the spoken form. Colour is never the only carrier. */
fun HourBand.label(): String = when (this) {
    HourBand.CALM -> "Under 30m"
    HourBand.WATCHFUL -> "30–45m"
    HourBand.STRAINED -> "45m+"
}
