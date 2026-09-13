package com.intent.screentime.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.goals.IntentScore
import com.intent.screentime.data.goals.StreakEvaluator
import com.intent.screentime.data.local.DefaultCategories
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.repository.AppCategoryRef
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.data.usage.HourlyBreakdown
import com.intent.screentime.ui.apps.AppInfoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUsageRow(
    val packageName: String,
    val totalMs: Long,
    val sessionCount: Int,
    val category: AppCategoryRef?,
)

data class TodayUiState(
    val loading: Boolean = true,
    val epochDay: Long = DayWindow.todayEpochDay(),
    val screenTimeMs: Long = 0L,
    val productionMs: Long = 0L,
    val consumptionMs: Long = 0L,
    val utilityMs: Long = 0L,
    val neutralMs: Long = 0L,
    /** Today's app time that no category has claimed yet — the backlog triage clears. */
    val unsortedCount: Int = 0,
    val unsortedMs: Long = 0L,
    val capMinutes: Int? = null,
    val deltaVsYesterdayMs: Long = 0L,
    val hasYesterday: Boolean = false,
    val topApps: List<AppUsageRow> = emptyList(),
    val buckets: List<HourlyBreakdown.Bucket> = emptyList(),
    val unlockCount: Int = 0,
    val focusMs: Long = 0L,
    val topAppMs: Long = 0L,
    val streakDays: Int = 0,
    val intentScore: Int = 0,
    val scoreParts: IntentScore.Parts? = null,
) {
    val hasData: Boolean get() = screenTimeMs > 0L

    /** Null means no cap has been set, which renders the ring in its unconfigured state. */
    val capMs: Long? get() = capMinutes?.takeIf { it > 0 }?.let { it * 60_000L }

    val overCap: Boolean get() = capMs?.let { screenTimeMs > it } == true

    val overageMs: Long get() = capMs?.let { (screenTimeMs - it).coerceAtLeast(0L) } ?: 0L

    /** Share of screen time that was spent producing rather than consuming. */
    val productionShare: Float
        get() {
            val accountable = productionMs + consumptionMs
            return if (accountable <= 0L) 0f else productionMs.toFloat() / accountable
        }
}

/**
 * Assembles the Today board.
 *
 * The hourly buckets need a suspend read, so state is assembled rather than purely
 * combined: whenever the summary, the per-app rows or the targets change, the day's
 * timeline is recomputed from the sessions already stored.
 */
class TodayViewModel(
    private val repository: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val today: Long = DayWindow.todayEpochDay()

    private var lastRefreshAt: Long = 0L

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    val state: StateFlow<TodayUiState> = combine(
        repository.observeDay(today),
        repository.observeAppUsage(today),
        repository.observeTargets(),
    ) { summary, appUsage, targets ->
        Triple(summary, appUsage, targets.firstOrNull { it.valueMinutes > 0 })
    }
        .map { (summary, appUsage, capTarget) -> assemble(summary, appUsage, capTarget) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private suspend fun assemble(
        summary: DailySummaryEntity?,
        appUsage: List<DailyAppUsageEntity>,
        capTarget: TargetEntity?,
    ): TodayUiState {
        val categories = repository.categoryLookup()
        val buckets = repository.hourlyBuckets(today)
        val yesterday = repository.daySummary(today - 1)
        val screenTime = summary?.screenTimeMs ?: 0L
        val focusMs = summary?.focusMs ?: 0L
        val capMs = capTarget?.valueMinutes?.takeIf { it > 0 }?.let { it * 60_000L }

        val rows = appUsage.take(TOP_APP_LIMIT).map { row ->
            AppUsageRow(
                packageName = row.packageName,
                totalMs = row.totalMs,
                sessionCount = row.sessionCount,
                category = categories[row.packageName],
            )
        }

        // Warm the icon cache off the main thread before these rows compose.
        appInfo.preload(rows.map { it.packageName })

        var production = 0L
        var consumption = 0L
        var utility = 0L
        var neutral = 0L
        var unsortedCount = 0
        var unsortedMs = 0L
        for (row in appUsage) {
            val ref = categories[row.packageName]
            // A missing row or the placeholder category are the same thing to the user:
            // this app has no side yet, so its time counts as unsorted rather than neutral.
            if (ref == null || ref.id == DefaultCategories.UNCATEGORIZED) {
                unsortedCount += 1
                unsortedMs += row.totalMs
            }
            when (ref?.kind) {
                CategoryKind.PRODUCTION -> production += row.totalMs
                CategoryKind.CONSUMPTION -> consumption += row.totalMs
                CategoryKind.UTILITY -> utility += row.totalMs
                CategoryKind.NEUTRAL, null -> neutral += row.totalMs
            }
        }

        // The streak the score reports is the one the user is standing on, so today's
        // still-running day is read from the judged rows behind it rather than guessed.
        val streakDays = StreakEvaluator.currentStreak(repository.recentStreakRows(STREAK_LOOKBACK), today)

        return TodayUiState(
            loading = false,
            epochDay = today,
            screenTimeMs = screenTime,
            productionMs = production,
            consumptionMs = consumption,
            utilityMs = utility,
            neutralMs = neutral,
            unsortedCount = unsortedCount,
            unsortedMs = unsortedMs,
            capMinutes = capTarget?.valueMinutes,
            deltaVsYesterdayMs = if (yesterday != null) screenTime - yesterday.screenTimeMs else 0L,
            hasYesterday = yesterday != null,
            topApps = rows,
            buckets = buckets,
            unlockCount = summary?.unlockCount ?: 0,
            focusMs = focusMs,
            topAppMs = appUsage.firstOrNull()?.totalMs ?: 0L,
            streakDays = streakDays,
            intentScore = IntentScore.compute(
                productionMs = production,
                consumptionMs = consumption,
                screenTimeMs = screenTime,
                capMs = capMs,
                focusMs = focusMs,
                streakDays = streakDays,
            ),
            scoreParts = IntentScore.parts(
                productionMs = production,
                consumptionMs = consumption,
                screenTimeMs = screenTime,
                capMs = capMs,
                focusMs = focusMs,
                streakDays = streakDays,
            ),
        )
    }

    fun refresh() {
        if (_refreshing.value) return
        lastRefreshAt = System.currentTimeMillis()
        viewModelScope.launch {
            _refreshing.value = true
            try {
                repository.refresh()
            } finally {
                _refreshing.value = false
            }
        }
    }

    /**
     * Harvests on screen open, but not on every tab switch: a harvest reads a delta, yet
     * hammering it on each navigation would still be pointless work and battery.
     */
    fun refreshIfStale(maxAgeMs: Long = STALE_AFTER_MS) {
        if (System.currentTimeMillis() - lastRefreshAt < maxAgeMs) return
        refresh()
    }

    fun setCap(minutes: Int?) {
        viewModelScope.launch { repository.setDailyCapMinutes(minutes) }
    }

    private companion object {
        const val TOP_APP_LIMIT = 5
        const val STALE_AFTER_MS = 2 * 60_000L
        const val STREAK_LOOKBACK = 60
    }
}
