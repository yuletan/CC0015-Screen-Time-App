package com.intent.screentime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.intent.screentime.data.export.ImageExporter
import com.intent.screentime.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * A panel whose body can be exported as an image.
 *
 * Captured with a graphics layer rather than by reading the window back, so the picture is
 * exactly this panel's contents — no status bar, no tab bar, no other panel that happened
 * to be scrolled into view. The share button lives in the panel header, *outside* the
 * captured region, which is the whole reason the capture is clean: a button inside the
 * picture would be the one thing a reader did not want in it.
 *
 * [capture] is false when there is nothing worth exporting yet, so the affordance is
 * hidden rather than offering a share of an empty state.
 */
@Composable
fun CapturablePanel(
    title: String,
    fileName: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    capture: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val registry = LocalChartCaptureRegistry.current

    // Registers the exact on-screen pixels for batch period export. The key is the
    // panel's own fileName (e.g. "insights-trend-week"), which the exporter maps to
    // a date-named photo. Panels with capture=false never register.
    DisposableEffect(registry, fileName, capture) {
        if (registry != null && capture) {
            registry.register(fileName) { layer.toImageBitmap().asAndroidBitmap() }
            onDispose { registry.unregister(fileName) }
        } else {
            onDispose { }
        }
    }

    Panel(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        actions = if (capture) {
            {
                IconButton(
                    onClick = {
                        scope.launch {
                            // Suspend on purpose: the layer is rasterised off the frame
                            // that is already drawn.
                            val bitmap = layer.toImageBitmap().asAndroidBitmap()
                            val uri = ImageExporter(context).exportPng(bitmap, fileName) ?: return@launch
                            context.startActivity(
                                ImageExporter.chooserFor(
                                    uri = uri,
                                    subject = "CC0015 Intent chart",
                                    title = "Share this chart",
                                ),
                            )
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share this chart as an image",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            null
        },
    ) {
        Box(
            modifier = Modifier.drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) {
            // Re-establishes the column the panel's own content block expects, since the
            // capture box sits between the two.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                content()
            }
        }
    }
}
