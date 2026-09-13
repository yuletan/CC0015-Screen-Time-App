package com.intent.screentime.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.intent.IntentStats
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.ui.apps.AppInfoProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.roundToLong

data class DailyPoint(
    val epochDay: Long,
    val screenTimeMs: Long,
    val productionMs: Long,
    val consumptionMs: Long,
)

/** A category's share of the range. Colours are resolved in the UI, not here. */
data class CategoryTotal(
    val name: String,
    val colorHex: String,
    val kind: CategoryKind,
    val totalMs: Long,
)

/** One app's share of the range, with its label already resolved for the column chart. */
data class AppTotal(
    val packageName: String,
    val name: String,
    val totalMs: Long,
)

/**
 * The reason ledger, shaped for the screen.
 *
 * [topPackageLabel] is resolved here — through [AppInfoProvider], a PackageManager call —
 * so the composable never has to. [topPackageSummaries] is that app's own breakdown, which
 * is the "why did *this* app keep pulling me in" answer the headline asks for.
 */
data class IntentInsight(
    val answered: Int,
    val skipped: Int,
    val overall: List<IntentStats.Summary>,
    val topPackageLabel: String?,
    val topPackageSummaries: List<IntentStats.Summary>,
    val hourCounts: List<Int>,
    val topReasonLabel: String?,
    val topReasonWindow: String?,
)

data class InsightsUiState(
    val loading: Boolean = true,
    val rangeDays: Int = 7,
    val points: List<DailyPoint> = emptyList(),
    val categories: List<CategoryTotal> = emptyList(),
    val topApps: List<AppTotal> = emptyList(),
    val averageMs: Long = 0L,
    val best: DailyPoint? = null,
    val worst: DailyPoint? = null,
    val weekdayAverages: List<Pair<String, Long>> = emptyList(),
    val weekdayAverageMs: Long = 0L,
    val weekendAverageMs: Long = 0L,
    /** Production share per day, as a whole percent, over days that have categorised time. */
    val productionRatios: List<Long> = emptyList(),
    /**
     * The reason ledger. Null only when no prompt was ever raised in the range — once a
     * single row exists it is shown, *including* a range where every prompt was skipped,
     * because then the answer rate is the whole story.
     */
    val intentInsight: IntentInsight? = null,
) {
    val hasData: Boolean get() = points.any { it.screenTimeMs > 0L }
    val totalMs: Long get() = points.sumOf { it.screenTimeMs }
    val hasRatioTrend: Boolean get() = productionRatios.size >= 2
}

/**
 * Trends over a chosen window.
 *
 * Switching range re-subscribes to a different slice of the summary table rather than
 * filtering in memory, so a 30-day view costs the same small query as a 7-day one.
 */
class InsightsViewModel(
    private val repository: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val rangeDays = MutableStateFlow(DEFAULT_RANGE_DAYS)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<InsightsUiState> = rangeDays
        .flatMapLatest { days ->
            val toDay = DayWindow.todayEpochDay()
            val fromDay = toDay - (days - 1)
            repository.observeDays(fromDay, toDay).map { summaries ->
                build(days, fromDay, toDay, summaries)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setRange(days: Int) {
        rangeDays.value = days
    }

    private suspend fun build(
        days: Int,
        fromDay: Long,
        toDay: Long,
        summaries: List<DailySummaryEntity>,
    ): InsightsUiState {
        val points = summaries.map {
            DailyPoint(
                epochDay = it.dayEpochDay,
                screenTimeMs = it.screenTimeMs,
                productionMs = it.productionMs,
                consumptionMs = it.consumptionMs,
            )
        }

        val withData = points.filter { it.screenTimeMs > 0L }

        val categories = repository.categoryTotals(fromDay, toDay).map { (ref, total) ->
            CategoryTotal(
                name = ref.name,
                colorHex = ref.colorHex,
                kind = ref.kind,
                totalMs = total,
            )
        }

        // One query for the whole range, grouped here: the package-to-label resolution is
        // a PackageManager call, so it is done once per app rather than once per day.
        val topApps = repository.appUsageBetween(fromDay, toDay)
            .groupBy { it.packageName }
            .map { (packageName, rows) ->
                AppTotal(
                    packageName = packageName,
                    name = appInfo.label(packageName),
                    totalMs = rows.sumOf { it.totalMs },
                )
            }
            .sortedByDescending { it.totalMs }
            .take(TOP_APPS)

        val weekdayTotals = LongArray(7)
        val weekdayCounts = IntArray(7)
        withData.forEach { point ->
            val index = LocalDate.ofEpochDay(point.epochDay).dayOfWeek.value - 1
            weekdayTotals[index] += point.screenTimeMs
            weekdayCounts[index] += 1
        }
        val weekdayAverages = (0 until 7).map { index ->
            WEEKDAY_LABELS[index] to if (weekdayCounts[index] > 0) {
                weekdayTotals[index] / weekdayCounts[index]
            } else {
                0L
            }
        }

        val weekdayDays = withData.filter { LocalDate.ofEpochDay(it.epochDay).dayOfWeek.value <= 5 }
        val weekendDays = withData.filter { LocalDate.ofEpochDay(it.epochDay).dayOfWeek.value >= 6 }

        val categorised = points.filter { it.productionMs + it.consumptionMs > 0L }
        val ratios = categorised.map { point ->
            val accountable = point.productionMs + point.consumptionMs
            (point.productionMs.toDouble() / accountable * 100.0).roundToLong()
        }

        // Only queried when the user has ever raised a prompt: the two reads are cheap but
        // pointless for someone who never turned Phase 7 on. A single row — even a lone
        // skip — is enough to render, because the answer rate is itself the finding.
        val logs = repository.intentLogsBetween(
            DayWindow.startOfDayMs(fromDay),
            System.currentTimeMillis(),
        )
        val intentInsight = if (logs.isEmpty()) {
            null
        } else {
            val ledger = IntentStats.ledger(
                logs = logs,
                sessions = repository.sessionsBetween(
                    logs.first().timestampMs,
                    System.currentTimeMillis(),
                ),
                zone = DayWindow.zone,
            )
            // The app with the most *answered* prompts, not the most rows: a package the
            // user kept ignoring is a story about the question, not about the app.
            val topPackage = logs
                .filterNot { it.skipped }
                .groupBy { it.packageName }
                .maxByOrNull { it.value.size }
                ?.key

            IntentInsight(
                answered = ledger.answered,
                skipped = ledger.skipped,
                overall = ledger.overall,
                topPackageLabel = topPackage?.let { appInfo.label(it) },
                topPackageSummaries = topPackage?.let { ledger.byPackage[it] }.orEmpty(),
                hourCounts = ledger.hourCounts,
                topReasonLabel = ledger.topReasonLabel,
                topReasonWindow = ledger.topReasonWindow,
            )
        }

        return InsightsUiState(
            loading = false,
            rangeDays = days,
            points = points,
            categories = categories,
            topApps = topApps,
            averageMs = if (withData.isEmpty()) 0L else withData.sumOf { it.screenTimeMs } / withData.size,
            best = withData.maxByOrNull { it.screenTimeMs },
            worst = withData.filter { it.screenTimeMs > 0L }.minByOrNull { it.screenTimeMs },
            weekdayAverages = weekdayAverages,
            weekdayAverageMs = weekdayDays.averageOrZero(),
            weekendAverageMs = weekendDays.averageOrZero(),
            productionRatios = ratios,
            intentInsight = intentInsight,
        )
    }

    private fun List<DailyPoint>.averageOrZero(): Long =
        if (isEmpty()) 0L else sumOf { it.screenTimeMs } / size

    companion object {
        const val DEFAULT_RANGE_DAYS = 7
        const val LONG_RANGE_DAYS = 30
        private const val TOP_APPS = 6
    }
}

internal val WEEKDAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
