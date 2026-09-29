package com.intent.screentime.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.screentime.ui.theme.Spacing

/** Which resolution a chart is plotting. */
enum class ChartUnit { HOUR, DAY, WEEK }

/**
 * The window a chart is looking at.
 *
 * Today is a day, not a window of one day: it exists so an app or the device can be read
 * hour by hour, which is the only resolution at which "when do I use this" is answerable.
 *
 * The long ranges resolve to weeks rather than days. Forty-two daily points is not a
 * readable line and ninety is a smear, and a week is also the unit the weekly targets are
 * already written in — so the long view and the goal line agree on what a point is.
 *
 * Each range carries its own [unit] so the mapping lives in one place instead of being
 * re-derived, slightly differently, in every view model that offers the selector.
 */
enum class ChartRange(val days: Long, val pill: String, val unit: ChartUnit) {
    TODAY(1L, "Today", ChartUnit.HOUR),
    WEEK(7L, "Week", ChartUnit.DAY),
    MONTH(30L, "Month", ChartUnit.DAY),
    SIX_WEEKS(42L, "6 wks", ChartUnit.WEEK),
    QUARTER(90L, "90 days", ChartUnit.WEEK),
    ;

    /** How a screen describes the range in prose, for captions and empty states. */
    val caption: String
        get() = when (this) {
            TODAY -> "Today, hour by hour"
            WEEK -> "Last 7 days"
            MONTH -> "Last 30 days"
            SIX_WEEKS -> "Last 6 weeks, week by week"
            QUARTER -> "Last 90 days, week by week"
        }

    /** The range as a noun in a sentence: "the week before", "the six weeks before". */
    val noun: String
        get() = when (this) {
            TODAY -> "day"
            WEEK -> "week"
            MONTH -> "month"
            SIX_WEEKS -> "six weeks"
            QUARTER -> "90 days"
        }
}

@Composable
fun ChartRangeSelector(
    selected: ChartRange,
    onSelect: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ChartRange.entries.forEach { range ->
            val active = range == selected
            Surface(
                shape = CircleShape,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.selectable(
                    selected = active,
                    onClick = { onSelect(range) },
                ),
            ) {
                Text(
                    text = range.pill,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
