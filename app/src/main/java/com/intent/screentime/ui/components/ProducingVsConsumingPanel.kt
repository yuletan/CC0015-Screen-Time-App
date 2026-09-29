package com.intent.screentime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.stats.UsageSplit
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

/**
 * Producing against consuming, as one bar and one sentence.
 *
 * The same panel serves today, a past day and a whole window, so the answer to "did I spend
 * this time or did it spend me" cannot look different depending on which screen asked. Only
 * the sentence changes: a day card talks about that day, a window talks about the range.
 *
 * The standing note about what the bar includes stays in one place for the same reason —
 * the ratio deliberately excludes Utility, Neutral and Unsorted time, and every surface has
 * to say so or the percentages look invented.
 */
@Composable
fun ProducingVsConsumingPanel(
    split: UsageSplit,
    prose: String,
    fileName: String,
    title: String = "Producing vs consuming",
    subtitle: String? = null,
    /** Null hides the unsorted row: a screen with nowhere to send the reader stays quiet. */
    onOpenTriage: (() -> Unit)? = null,
    capture: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val data = dataColors

    val segments = listOf(
        SplitSegment("Producing", split.productionMs, data.production),
        SplitSegment("Consuming", split.consumptionMs, data.consumption),
        SplitSegment("Utility", split.utilityMs, data.utility),
        SplitSegment("Neutral", split.neutralMs, data.neutral),
        SplitSegment("Unsorted", split.unsortedMs, data.neutral.copy(alpha = 0.65f)),
    )

    CapturablePanel(
        title = title,
        subtitle = subtitle,
        fileName = fileName,
        capture = capture,
        modifier = modifier,
    ) {
        SplitBar(segments = segments)

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = "The bar shows all tracked time. The Producing percentage below compares only " +
                "Producing with Consuming; Utility, Neutral, and Unsorted stay out of that ratio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = prose,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (onOpenTriage != null && split.unsortedCount > 0) {
            Spacer(Modifier.height(Spacing.xs))
            UnsortedRow(
                count = split.unsortedCount,
                ms = split.unsortedMs,
                onClick = onOpenTriage,
            )
        }
    }
}

/**
 * The unsorted pile, offered as a way to clear it.
 *
 * The split can read a misleading low simply because nothing is categorised, so this says
 * how much time that is and offers the fix in place, rather than describing the problem and
 * sending the reader elsewhere.
 */
@Composable
private fun UnsortedRow(count: Int, ms: Long, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${DurationFormat.compact(ms)} unsorted across " +
                    (if (count == 1) "1 app" else "$count apps") + " — sort",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
