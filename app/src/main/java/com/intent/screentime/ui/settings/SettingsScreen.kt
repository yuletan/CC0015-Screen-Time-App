package com.intent.screentime.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.goals.BedtimeNudge
import com.intent.screentime.data.usage.BedtimeWindow
import com.intent.screentime.ui.components.BedtimeDialog
import com.intent.screentime.ui.components.Panel
import com.intent.screentime.ui.components.SectionEyebrow
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

@Composable
fun SettingsScreen(
    dynamicColor: Boolean,
    capMinutes: Int?,
    bedtime: BedtimeWindow?,
    bedtimeNudge: BedtimeNudge,
    busy: Boolean,
    digestMinutes: Int,
    exportUri: Uri?,
    periodZip: Pair<Uri, String>? = null,
    periodBusy: Boolean = false,
    onBack: () -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSetCap: () -> Unit,
    onSetBedtime: (Int?, Int?) -> Unit,
    onSetDigestTime: (Int) -> Unit,
    onExportCsv: () -> Unit,
    onExportConsumed: () -> Unit,
    onExportPeriod: (Long) -> Unit = {},
    onPeriodConsumed: () -> Unit = {},
    onOpenCategories: () -> Unit,
    onOpenIntentPrompt: () -> Unit,
    onRefreshNow: () -> Unit,
    onRecomputeHistory: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDigestDialog by remember { mutableStateOf(false) }
    var showBedtimeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // The export result is handed to whatever the user picks, then forgotten: nothing
    // stays in the cache that the app still needs to know about.
    LaunchedEffect(exportUri) {
        val uri = exportUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CC0015 Intent export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export CC0015 Intent data"))
        onExportConsumed()
    }

    // A finished Day / Week / Month zip: csv/ + photos/ + insights.md, shared once.
    LaunchedEffect(periodZip) {
        val (uri, name) = periodZip ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CC0015 Intent export ($name)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Download period ($name)"))
        onPeriodConsumed()
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
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    SectionEyebrow("Preferences")
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        item {
            Panel(title = "Targets") {
                SettingsAction(
                    title = "Daily screen time cap",
                    detail = capMinutes?.let { "Currently ${DurationFormat.compact(it * 60_000L)}" }
                        ?: "No cap set",
                    actionLabel = if (capMinutes == null) "Set" else "Change",
                    onAction = onSetCap,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsAction(
                    title = "Bedtime",
                    detail = bedtimeDetail(bedtime, bedtimeNudge),
                    detailTint = if (bedtime != null && bedtimeNudge.repeated) {
                        dataColors.watchful
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    actionLabel = if (bedtime == null) "Set" else "Change",
                    onAction = { showBedtimeDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsAction(
                    title = "Categories",
                    detail = "Rename a category, change which side of the split it counts " +
                        "on, or add your own.",
                    actionLabel = "Open",
                    onAction = onOpenCategories,
                )
            }
        }

        item {
            Panel(title = "Notifications") {
                SettingsAction(
                    title = "Daily digest",
                    detail = "One short summary of the day at ${DurationFormat.timeOfDay(digestMinutes)}.",
                    actionLabel = "Change",
                    onAction = { showDigestDialog = true },
                )
            }
        }

        item {
            Panel(title = "Intent prompt") {
                SettingsAction(
                    title = "Ask before flagged apps",
                    detail = "Off by default. A two-second pause before the apps you choose, " +
                        "so you can say why you opened them.",
                    actionLabel = "Open",
                    onAction = onOpenIntentPrompt,
                )
            }
        }

        item {
            Panel(title = "Appearance") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Use wallpaper colours",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Material You. Off by default so the app keeps its own " +
                                "palette. Category colours never change either way.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.padding(horizontal = Spacing.xs))
                    Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                }
            }
        }

        item {
            Panel(title = "Data") {
                SettingsAction(
                    title = "Sync from device now",
                    detail = "Copy the latest usage events across immediately instead of " +
                        "waiting for the 15 minute background pass.",
                    actionLabel = if (busy) "Working…" else "Sync",
                    enabled = !busy,
                    onAction = onRefreshNow,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsAction(
                    title = "Rebuild history",
                    detail = "Recomputes sessions and daily totals from the events already " +
                        "stored. Use this if past numbers ever look wrong.",
                    actionLabel = if (busy) "Working…" else "Rebuild",
                    enabled = !busy,
                    onAction = onRecomputeHistory,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsAction(
                    title = "Export data",
                    detail = "Writes the last 90 days of daily totals to a CSV and opens " +
                        "the share sheet. Nothing leaves the phone unless you send it.",
                    actionLabel = "Export",
                    onAction = onExportCsv,
                )
            }
        }

        item {
            Panel(title = "Download period (ZIP)") {
                Text(
                    text = "One file with two folders: csv/ (daily + weekly numbers, " +
                        "apps, prompts) and photos/ (chart images named by date). " +
                        "A week carries its 7 daily photos plus weekly charts; " +
                        "a month is 4× weeks with daily photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                SettingsAction(
                    title = "Today — 1 day",
                    detail = "Daily CSV row, hourly photo, insights.",
                    actionLabel = if (periodBusy) "Working…" else "Download",
                    enabled = !periodBusy,
                    onAction = { onExportPeriod(1L) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsAction(
                    title = "This week — 7 days",
                    detail = "Daily photos + weekly charts and weekly CSV.",
                    actionLabel = if (periodBusy) "Working…" else "Download",
                    enabled = !periodBusy,
                    onAction = { onExportPeriod(7L) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsAction(
                    title = "This month — 30 days",
                    detail = "Daily photos, one chart set per week, monthly charts.",
                    actionLabel = if (periodBusy) "Working…" else "Download",
                    enabled = !periodBusy,
                    onAction = { onExportPeriod(30L) },
                )
            }
        }

        item {
            Panel(title = "Privacy") {
                Text(
                    text = "Everything Intent knows is on this phone. There is no account, " +
                        "no sync and no network permission in the app at all, so your usage " +
                        "data cannot leave the device even by accident.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                SettingsAction(
                    title = "Tracking diagnostics",
                    detail = "Check whether the background harvest is running and what it " +
                        "has collected.",
                    actionLabel = "Open",
                    onAction = onOpenDiagnostics,
                )
            }
        }
    }

    if (showDigestDialog) {
        DigestTimeDialog(
            currentMinutes = digestMinutes,
            onDismiss = { showDigestDialog = false },
            onConfirm = {
                onSetDigestTime(it)
                showDigestDialog = false
            },
        )
    }

    if (showBedtimeDialog) {
        BedtimeDialog(
            current = bedtime,
            onDismiss = { showBedtimeDialog = false },
            onConfirm = { start, end ->
                onSetBedtime(start, end)
                showBedtimeDialog = false
            },
        )
    }
}

/**
 * The bedtime row's second line.
 *
 * Three states rather than two: what is missing, what is set, and what is set but keeps
 * being broken. The third is the one worth saying out loud — a window that is missed every
 * week is a window that was guessed wrong, and the row is where the guess can be changed.
 */
private fun bedtimeDetail(window: BedtimeWindow?, nudge: BedtimeNudge): String {
    if (window == null) {
        return "No quiet window set. A bedtime colours each night on the Goals heatmap, " +
            "held or broken."
    }

    val hours = "${DurationFormat.timeOfDay(window.startMinutesOfDay)} – " +
        DurationFormat.timeOfDay(window.endMinutesOfDay)

    return when {
        nudge.repeated -> "Missed ${nudge.missedNights} of the last " +
            "${nudge.judgedNights} nights inside $hours. Worth moving to an hour that " +
            "fits your evenings."

        nudge.judgedNights == 0 -> "$hours. Nothing inside it has been judged yet."

        nudge.missedNights == 0 -> "$hours. Held on every night judged so far."

        else -> "$hours. Broken on ${nudge.missedNights} of the " +
            "${nudge.judgedNights} nights judged so far."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DigestTimeDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = currentMinutes / 60,
        initialMinute = currentMinutes % 60,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily digest") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "When should the day's summary arrive? Pick a time you are " +
                        "usually winding down.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SettingsAction(
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
    /** Only the rows with something to flag change this; the rest stay quiet. */
    detailTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = detailTint,
            )
        }
        TextButton(onClick = onAction, enabled = enabled) {
            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}
