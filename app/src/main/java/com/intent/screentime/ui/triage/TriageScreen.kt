package com.intent.screentime.ui.triage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.components.AppIcon
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.components.categoryColor
import com.intent.screentime.ui.theme.Motion
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors
import kotlin.math.abs
import kotlinx.coroutines.launch

/** How far the card must travel before a swipe is treated as a decision. */
private val SWIPE_THRESHOLD = 96.dp

/** How far the card may be dragged before it stops following the finger. */
private val MAX_DRAG = 168.dp

/** The sheet leaves a slice of the screen showing, so it reads as a sheet, not a page. */
private const val SHEET_HEIGHT_FRACTION = 0.84f

private const val SCRIM_ALPHA = 0.5f

/**
 * The sheet-styled route that clears the unsorted pile.
 *
 * A route rather than a [androidx.compose.material3.ModalBottomSheet] on purpose: the 21:00
 * digest deep-links straight into it, and undo needs a snackbar host of its own. The scrim
 * is tappable so the sheet never traps the user; the bottom bar is still there besides.
 */
@Composable
fun TriageScreen(
    state: TriageUiState,
    appInfo: AppInfoProvider,
    onAssign: (String, CategoryKind) -> Unit,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val lastSort = state.lastSort

    // One confirmation per sorted app, keyed by the event's token so sorting the same app
    // twice still announces itself. Undo is the snackbar's own action, which is the whole
    // reason this sheet carries a SnackbarHost rather than borrowing the app-wide one.
    LaunchedEffect(lastSort?.token) {
        val sort = lastSort ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Sorted ${sort.label} as ${sort.kind.actionName()}",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndo()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone,
            ),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(SHEET_HEIGHT_FRACTION)
                // Swallow taps on the sheet's own body so they cannot fall through to the
                // scrim and dismiss the flow mid-sort. Buttons and the drag handle still win
                // their own gestures because they sit deeper in the tree.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                SheetHandle()

                when {
                    state.loading -> LoadingBody(Modifier.fillMaxWidth().weight(1f))

                    state.finished -> FinishedBody(
                        total = state.total,
                        onDone = onDone,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )

                    else -> QueueBody(
                        state = state,
                        appInfo = appInfo,
                        onAssign = onAssign,
                        onDone = onDone,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Spacing.md),
        )
    }
}

/** The grab handle. Decoration, but the shape that says "this can be dragged away". */
@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun LoadingBody(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun QueueBody(
    state: TriageUiState,
    appInfo: AppInfoProvider,
    onAssign: (String, CategoryKind) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.current ?: return
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val thresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val maxDragPx = with(LocalDensity.current) { MAX_DRAG.toPx() }

    val offset = remember { Animatable(0f) }
    var thresholdCrossed by remember { mutableStateOf(false) }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            val next = (offset.value + delta).coerceIn(-maxDragPx, maxDragPx)
            offset.snapTo(next)

            // One buzz on the way out, none on the way back: crossing the line is the only
            // moment the gesture needs to say something the eyes might have missed.
            val crossed = abs(next) >= thresholdPx
            if (crossed && !thresholdCrossed) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            thresholdCrossed = crossed
        }
    }

    Column(modifier = modifier.padding(horizontal = Spacing.gutter)) {
        Header(state = state, onDone = onDone)

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            // The inner box takes its size from the card; the target matches it exactly.
            Box(Modifier.fillMaxWidth()) {
                SwipeTarget(
                    offsetX = offset.value,
                    thresholdPx = thresholdPx,
                    modifier = Modifier.matchParentSize(),
                )

                Panel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = offset.value }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            enabled = !state.busy,
                            state = dragState,
                            onDragStopped = {
                                val value = offset.value
                                val kind = when {
                                    value >= thresholdPx -> CategoryKind.PRODUCTION
                                    value <= -thresholdPx -> CategoryKind.CONSUMPTION
                                    // Utility is button-only: three options do not fit two
                                    // swipe directions, so a short swipe just springs back.
                                    else -> null
                                }
                                thresholdCrossed = false
                                if (kind != null) onAssign(item.packageName, kind)
                                offset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = Motion.EXIT_MS,
                                        easing = Motion.exitEase,
                                    ),
                                )
                            },
                        ),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    AppCard(item = item, appInfo = appInfo)
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            KindButton(
                kind = CategoryKind.PRODUCTION,
                hint = "swipe →",
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { onAssign(item.packageName, CategoryKind.PRODUCTION) }

            KindButton(
                kind = CategoryKind.CONSUMPTION,
                hint = "← swipe",
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { onAssign(item.packageName, CategoryKind.CONSUMPTION) }

            KindButton(
                kind = CategoryKind.UTILITY,
                hint = null,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { onAssign(item.packageName, CategoryKind.UTILITY) }
        }

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = "Choosing a side is all the split needs. Swipe right for Producing or " +
                "left for Consuming, or tap a button — these pick a kind, not a specific " +
                "category, so you can refine any app later from its own page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun Header(state: TriageUiState, onDone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionEyebrow("Sorting")
            Text(
                text = "${state.sorted} of ${state.total}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TextButton(onClick = onDone) { Text("Done") }
    }

    Spacer(Modifier.height(Spacing.xs))

    val animated by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = Motion.ENTER_MS, easing = Motion.expoOut),
        label = "triageProgress",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * The destination the card is being dragged towards.
 *
 * It appears only once the drag has started, tinted in the kind's colour and labelled with
 * its name, so the gesture states what it will do before the user lets go.
 */
@Composable
private fun SwipeTarget(
    offsetX: Float,
    thresholdPx: Float,
    modifier: Modifier = Modifier,
) {
    if (offsetX == 0f) return

    val data = dataColors
    val toProducing = offsetX > 0f
    val kind = if (toProducing) CategoryKind.PRODUCTION else CategoryKind.CONSUMPTION
    val tint = categoryColor(kind, data)
    val progress = (abs(offsetX) / thresholdPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(tint.copy(alpha = 0.16f * progress)),
        contentAlignment = if (toProducing) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Text(
            text = kind.actionName(),
            style = MaterialTheme.typography.titleMedium,
            color = tint.copy(alpha = 0.55f + 0.45f * progress),
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}

@Composable
private fun AppCard(item: TriageItem, appInfo: AppInfoProvider) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            packageName = item.packageName,
            provider = appInfo,
            size = 72.dp,
            cornerRadius = 22.dp,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = item.label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = evidenceLine(item),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The evidence, stated as facts: how long, how often, and how long each time. */
private fun evidenceLine(item: TriageItem): String {
    val sessions = if (item.sessionCount == 1) "1 session" else "${item.sessionCount} sessions"
    val base = "${DurationFormat.compact(item.totalMs)} · $sessions"
    return if (item.sessionCount > 0) {
        "$base · avg ${DurationFormat.compact(item.averageSessionMs)}"
    } else {
        base
    }
}

@Composable
private fun KindButton(
    kind: CategoryKind,
    hint: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = categoryColor(kind, dataColors)

    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = tint.copy(alpha = if (enabled) 0.16f else 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.sm + Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = kind.actionName(),
                style = MaterialTheme.typography.titleSmall,
                color = tint.copy(alpha = if (enabled) 1f else 0.5f),
                maxLines = 1,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The end of the queue. A statement of fact, not a reward — there is no confetti in this app.
 */
@Composable
private fun FinishedBody(total: Int, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (total == 0) "Nothing waiting" else "That's the pile cleared",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = if (total == 0) {
                "Every app is already sorted. New apps will land here the first time you " +
                    "open them, so it stays short."
            } else {
                "$total apps sorted. The producing and consuming split on Today now " +
                    "reflects all of them, and you can still change any app from its own page."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        Button(onClick = onDone) { Text("Done") }
    }
}

/** The button labels, in sentence case, shared by the buttons, the targets and the snackbar. */
private fun CategoryKind.actionName(): String = when (this) {
    CategoryKind.PRODUCTION -> "Producing"
    CategoryKind.CONSUMPTION -> "Consuming"
    CategoryKind.UTILITY -> "Utility"
    CategoryKind.NEUTRAL -> "Either"
}
