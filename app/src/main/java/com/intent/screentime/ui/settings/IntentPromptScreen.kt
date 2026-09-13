package com.intent.screentime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.screentime.data.apps.InstalledApp
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

/**
 * The opt-in intent prompt: what it costs, what it does, and which apps it watches.
 *
 * The screen states the battery cost plainly rather than burying it. The user is being
 * asked to trade a small amount of battery for a moment of reflection, and that trade
 * only makes sense if it is visible.
 */
@Composable
fun IntentPromptScreen(
    enabled: Boolean,
    watched: Set<String>,
    apps: List<InstalledApp>,
    promptCount: PromptCounts,
    overlayGranted: Boolean,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onGrantOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val visibleApps = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.gutter,
            end = Spacing.gutter,
            top = Spacing.sm,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SectionEyebrow("Settings")
            }
            Text(
                text = "Intent prompt",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        item {
            Panel(title = "What it does") {
                Text(
                    text = "When you open an app you flagged, Intent shows a short card " +
                        "first: what are you here for? Answer in one tap, or just ignore " +
                        "it — it closes itself after 15 seconds. Either way the prompt " +
                        "itself is recorded: your reason if you gave one, or a quiet note " +
                        "that you let it close.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Your answers are joined to the session that follows, so " +
                        "\"checking something\" that turns into 20 minutes becomes a number " +
                        "on the Insights screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "The honest trade: detecting a launch without an Accessibility " +
                        "Service means checking the usage log every couple of seconds while " +
                        "this is switched on. That costs battery — which is why it is off " +
                        "by default and why nothing is watched until you pick apps below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (promptCount.total > 0) {
                        "${promptCount.answered} of ${promptCount.total} prompts " +
                            "answered so far."
                    } else {
                        "No prompts raised yet."
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            Panel(title = "Switch") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Ask before flagged apps",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (overlayGranted) {
                                "Overlay permission granted."
                            } else {
                                "Needs the 'display over other apps' permission first."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.padding(horizontal = Spacing.xs))
                    Switch(
                        checked = enabled && overlayGranted,
                        enabled = overlayGranted,
                        onCheckedChange = onToggle,
                    )
                }

                if (!overlayGranted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Without the overlay permission Android cannot draw " +
                                "the pause card.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedButton(onClick = onGrantOverlay) {
                        Text("Grant overlay access")
                    }
                }
            }
        }

        if (enabled && overlayGranted) {
            item {
                Panel(title = "Watched apps") {
                    Text(
                        text = "Pick the apps that pull you in without asking. One or two " +
                            "is plenty to start; flagging everything turns the card into " +
                            "noise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${watched.size} watched",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(visibleApps, key = { it.packageName }) { app ->
                WatchRow(
                    app = app,
                    watched = app.packageName in watched,
                    onToggle = { onToggleApp(app.packageName, it) },
                )
            }
        }
    }
}

@Composable
private fun WatchRow(
    app: InstalledApp,
    watched: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!watched) },
        shape = MaterialTheme.shapes.medium,
        color = if (watched) {
            dataColors.production.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = watched, onCheckedChange = onToggle)
        }
    }
}
