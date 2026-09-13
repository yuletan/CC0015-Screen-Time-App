package com.intent.screentime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.ui.theme.DataColors
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

/**
 * A section eyebrow, not a heading. Small, tracked, uppercase, quiet — it labels a
 * region so the eye can skip it, while the content underneath does the talking.
 */
@Composable
fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * The app's one container primitive.
 *
 * Used deliberately and sparingly: a panel exists only where a group of content is
 * genuinely a unit. Panels are never nested, because a panel inside a panel is just
 * decoration with a border.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = color,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (title != null) {
                SectionEyebrow(title)
            }
            content()
        }
    }
}

/**
 * Change against yesterday. Sentiment is carried by an arrow and by words as well as
 * colour, so it survives both greyscale and colour-blindness.
 */
@Composable
fun DeltaPill(
    deltaMs: Long,
    modifier: Modifier = Modifier,
    lowerIsBetter: Boolean = true,
) {
    val data = dataColors
    val scheme = MaterialTheme.colorScheme

    val icon: ImageVector
    val tint: Color
    val label: String

    if (deltaMs == 0L) {
        icon = Icons.Filled.Remove
        tint = scheme.onSurfaceVariant
        label = "Same as yesterday"
    } else {
        val improved = if (lowerIsBetter) deltaMs < 0 else deltaMs > 0
        icon = if (deltaMs > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown
        tint = if (improved) data.positive else data.negative
        val direction = if (deltaMs > 0) "more" else "less"
        label = "${DurationFormat.compact(kotlin.math.abs(deltaMs))} $direction than yesterday"
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tint.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

/** A category swatch. Colour is paired with the category name, never used alone. */
@Composable
fun CategoryChip(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.16f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

/** One entry in a chart legend: swatch, name, value. */
@Composable
fun LegendItem(
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Empty states teach the space. Never a bare "no data".
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.size(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(Spacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(Spacing.sm))
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A big number with a caption. Used for secondary vitals, never for the hero. */
@Composable
fun StatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = tint,
            )
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The one place a category kind becomes a colour.
 *
 * Centralised so the timeline strip, the donut, the chips and the split bar can never
 * drift apart on what "consumption" looks like.
 */
fun categoryColor(kind: CategoryKind?, data: DataColors): Color = when (kind) {
    CategoryKind.PRODUCTION -> data.production
    CategoryKind.CONSUMPTION -> data.consumption
    CategoryKind.UTILITY -> data.utility
    CategoryKind.NEUTRAL -> data.neutral
    null -> data.neutral.copy(alpha = 0.35f)
}
