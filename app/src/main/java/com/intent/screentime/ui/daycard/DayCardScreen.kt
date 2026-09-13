package com.intent.screentime.ui.daycard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.intent.screentime.data.local.entity.DayReflection
import com.intent.screentime.ui.apps.AppInfoProvider

/**
 * One past day, opened from its square on the heatmap.
 *
 * The verdict, the split, the apps, the prompts answered and one note of the user's own —
 * which is what turns a heatmap of squares into something closer to a diary.
 */
@Composable
fun DayCardScreen(
    state: DayCardUiState,
    appInfo: AppInfoProvider,
    onBack: () -> Unit,
    onSetNote: (String) -> Unit,
    onSetReflection: (DayReflection?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Day",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}
