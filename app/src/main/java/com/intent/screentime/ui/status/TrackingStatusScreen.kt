package com.intent.screentime.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.IntentDatabase
import com.intent.screentime.data.usage.IngestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Diagnostics surface used to verify the harvest pipeline on a real device.
 *
 * It stays in the app afterwards as a Settings > Diagnostics entry, because "is the
 * background job actually running?" is the single most useful thing to be able to
 * check when screen-time numbers look wrong.
 */
@Composable
fun TrackingStatusScreen(
    database: IntentDatabase,
    onHarvestNow: suspend () -> IngestResult,
    onRecomputeHistory: suspend () -> Int,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<IngestResult?>(null) }
    var recomputedDays by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }

    suspend fun refresh() {
        snapshot = withContext(Dispatchers.IO) {
            val today = DayWindow.todayEpochDay()
            Snapshot(
                events = database.usageEventDao().count(),
                sessions = database.appSessionDao().sessionsFrom(0L).size,
                watermark = database.usageEventDao().latestTimestamp() ?: 0L,
                todayScreenTimeMs = database.dailySummaryDao().getDay(today)?.screenTimeMs ?: 0L,
                todayTopPackage = database.dailySummaryDao().getDay(today)?.topPackage,
                trackedDays = database.dailySummaryDao().recent(60).size,
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Tracking status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Verifies that events are being harvested and rolled up correctly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatRow("Raw events stored", snapshot?.events?.toString() ?: "—")
                StatRow("Sessions built", snapshot?.sessions?.toString() ?: "—")
                StatRow("Days tracked", snapshot?.trackedDays?.toString() ?: "—")
                StatRow(
                    "Latest event",
                    snapshot?.watermark?.takeIf { it > 0 }
                        ?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) }
                        ?: "—",
                )
                StatRow("Today's screen time", snapshot?.todayScreenTimeMs?.let(DurationFormat::compact) ?: "—")
                StatRow("Today's top app", snapshot?.todayTopPackage ?: "—")
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        status = onHarvestNow()
                    } catch (t: Throwable) {
                        error = t.message ?: t::class.java.simpleName
                    } finally {
                        busy = false
                        refresh()
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Harvest now")
            }
        }

        Spacer(Modifier.height(12.dp))

        status?.let { result ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Last harvest", style = MaterialTheme.typography.titleSmall)
                    if (result.succeeded) {
                        Text(
                            "${result.eventsInserted} new events · ${result.sessionsBuilt} sessions · " +
                                "${result.daysAggregated} days aggregated",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            result.skippedReason ?: "Skipped",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))

        androidx.compose.material3.OutlinedButton(
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        recomputedDays = onRecomputeHistory()
                    } catch (t: Throwable) {
                        error = t.message ?: t::class.java.simpleName
                    } finally {
                        busy = false
                        refresh()
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Recompute history")
        }

        recomputedDays?.let { days ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Rebuilt sessions and aggregates across $days days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        androidx.compose.material3.OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("App settings")
        }

        Spacer(Modifier.height(24.dp))
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }
}

private data class Snapshot(
    val events: Long,
    val sessions: Int,
    val watermark: Long,
    val todayScreenTimeMs: Long,
    val todayTopPackage: String?,
    val trackedDays: Int,
)

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
