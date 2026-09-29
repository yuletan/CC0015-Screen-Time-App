package com.intent.screentime.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType
import com.intent.screentime.data.repository.AppCategoryRef
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.data.stats.AppUsageInsight
import com.intent.screentime.data.stats.AppUsageInsights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppListRow(
    val packageName: String,
    val totalMs: Long,
    val sessionCount: Int,
    val category: AppCategoryRef?,
    /** The last seven days, oldest first — the shape behind the sparkline. */
    val trend: List<Long>,
    val insight: AppUsageInsight? = null,
)

data class AppsUiState(
    val loading: Boolean = true,
    val epochDay: Long = DayWindow.todayEpochDay(),
    val rows: List<AppListRow> = emptyList(),
    val totalMs: Long = 0L,
) {
    val maxMs: Long get() = rows.firstOrNull()?.totalMs ?: 0L
    val uncategorisedCount: Int
        get() = rows.count { it.category == null || it.category.id == "uncategorized" }
}

class AppsViewModel(
    private val repository: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val today: Long = DayWindow.todayEpochDay()

    /** Recomputed whenever data or an edit changes, so the list refreshes visibly. */
    private val dataVersion = MutableStateFlow(0)

    val state: StateFlow<AppsUiState> = combine(
        repository.observeAppUsage(today),
        repository.observeTargets(),
        dataVersion,
    ) { appUsage, targets, _ -> appUsage to targets }
        .map { (appUsage, targets) -> assemble(appUsage, targets) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    private suspend fun assemble(
        appUsage: List<DailyAppUsageEntity>,
        targets: List<TargetEntity>,
    ): AppsUiState {
        val categories = repository.categoryLookup()
        val trendByPackage = trendByPackage()

        val rows = appUsage.map { row ->
            val trend = trendByPackage[row.packageName].orEmpty()
            val previousDays = trend.dropLast(1).filter { it > 0L }
            val averageMs = if (previousDays.isEmpty()) {
                0L
            } else {
                previousDays.sum() / previousDays.size
            }
            val category = categories[row.packageName]
            val capMinutes = targets.firstOrNull {
                it.type == TargetType.PER_APP_DAILY_CAP && it.scopePackage == row.packageName
            }?.valueMinutes

            AppListRow(
                packageName = row.packageName,
                totalMs = row.totalMs,
                sessionCount = row.sessionCount,
                category = category,
                trend = trend,
                insight = AppUsageInsights.forApp(
                    todayMs = row.totalMs,
                    averageMs = averageMs,
                    capMinutes = capMinutes,
                    category = category,
                ),
            )
        }

        appInfo.preload(rows.take(PRELOAD_LIMIT).map { it.packageName })

        return AppsUiState(
            loading = false,
            epochDay = today,
            rows = rows,
            totalMs = rows.sumOf { it.totalMs },
        )
    }

    /**
     * One query for every app's week, regrouped here. Seven days at a time is small
     * enough that per-app queries would just be more round trips for the same rows.
     */
    private suspend fun trendByPackage(): Map<String, List<Long>> {
        val fromDay = today - (TREND_DAYS - 1)
        return repository.appUsageBetween(fromDay, today)
            .groupBy { it.packageName }
            .mapValues { (_, rows) ->
                val byDay = rows.associate { it.dayEpochDay to it.totalMs }
                (fromDay..today).map { byDay[it] ?: 0L }
            }
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    private companion object {
        const val PRELOAD_LIMIT = 40
        const val TREND_DAYS = 7L
    }
}
