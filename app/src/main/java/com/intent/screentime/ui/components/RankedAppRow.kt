package com.intent.screentime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.repository.AppCategoryRef
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

/**
 * One ranked app: icon, its full name, what kind of time it took, its time, and a bar to
 * compare it against the others.
 *
 * A row rather than a column because an app name is a word, not a number — "Google Play
 * Services for AR" does not fit a sixth of a phone's width, and an ellipsised name is an
 * app you cannot identify. The row gives the name the whole width it needs and puts the
 * value where the eye looks for it, at the end of the line.
 *
 * The category chip is the answer to "is this app producing or consuming for me", and it
 * sits under the name so a list of apps can be read as a list of kinds of time.
 */
@Composable
fun RankedAppRow(
    packageName: String,
    name: String,
    valueMs: Long,
    maxMs: Long,
    appInfo: AppInfoProvider,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null renders the row as unsorted, the same as an app with no category at all. */
    category: AppCategoryRef? = null,
) {
    val fraction = if (maxMs > 0L) (valueMs.toFloat() / maxMs.toFloat()) else 0f
    val shape = RoundedCornerShape(3.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AppIcon(packageName = packageName, provider = appInfo, size = 40.dp)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    // Last resort only: a name long enough to still need this is one
                    // nothing short of a second line could show in full.
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = DurationFormat.compact(valueMs),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.xs))

            CategoryChip(
                name = category?.name ?: "Unsorted",
                kind = category?.kind,
                categoryId = category?.id,
                color = if (category != null) {
                    categoryColor(category.kind, dataColors)
                } else {
                    dataColors.neutral.copy(alpha = 0.65f)
                },
            )

            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (valueMs > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0.015f, 1f))
                            .height(6.dp)
                            .clip(shape)
                            .background(color),
                    )
                }
            }
        }
    }
}
