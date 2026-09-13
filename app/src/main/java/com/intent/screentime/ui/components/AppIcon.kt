package com.intent.screentime.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.theme.dataColors

/**
 * An app's real launcher icon, falling back to a monogram while it loads or if the
 * package can no longer be resolved (uninstalled, work profile, hidden).
 *
 * The fallback is a real design, not a gap: it carries the app's initial on a tinted
 * ground, so the list never reflows or shows holes.
 */
@Composable
fun AppIcon(
    packageName: String,
    provider: AppInfoProvider,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    cornerRadius: Dp = 13.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val bitmap = provider.icon(packageName)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape),
        )
        return
    }

    val palette = dataColors.series
    val tint = palette[provider.monogramColorIndex(packageName) % palette.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = provider.label(packageName).take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = tint,
        )
    }
}
