package com.intent.screentime.ui.triage

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.ui.apps.AppInfoProvider

/**
 * The sheet-styled route that clears the unsorted pile.
 *
 * A route rather than a [androidx.compose.material3.ModalBottomSheet] on purpose: the 21:00
 * digest deep-links straight into it, and undo needs a snackbar host of its own.
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
    Text(
        text = "Triage",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}
