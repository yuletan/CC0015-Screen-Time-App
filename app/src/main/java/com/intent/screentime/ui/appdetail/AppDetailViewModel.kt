package com.intent.screentime.ui.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.format.DayRangeFormat
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.core.format.HourFormat
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.CategoryEntity
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType
import com.intent.screentime.data.repository.AppCategoryRef
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.data.stats.AppUsageInsight
import com.intent.screentime.data.stats.AppUsageInsights
import com.intent.screentime.data.stats.LookbackWindow
import com.intent.screentime.data.stats.WeeklyRollup
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.ui.components.ChartRange
import com.intent.screentime.ui.components.ChartUnit
import com.intent.screentime.ui.components.TargetLine
import com.intent.screentime.ui.components.TrendPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AppDetailUiState(
    val loading: Boolean = true,
    val packageName: String = "",
    val range: ChartRange = ChartRange.WEEK,
    val unit: ChartUnit = ChartUnit.DAY,
    /** Which window this is: 0 is the one ending today, 1 the one before it, and so on. */
    val offset: Int = 0,
    /** The furthest back this app has usage, so the stepper can stop there. */
    val maxOffset: Int = 0,
    /** The window's own dates — "8–14 Sep" — because "3 weeks ago" makes the reader count. */
    val windowLabel: String = "",
    /** What the trend chart plots, at [unit]'s resolution. */
    val trend: List<TrendPoint> = emptyList(),
    val totalMs: Long = 0L,
    val averageMs: Long = 0L,
    val sessionCount: Int = 0,
    val todayMs: Long = 0L,
    val busiest: TrendPoint? = null,
    val capMinutes: Int? = null,
    /**
     * The cap drawn across the trend, on the daily chart only.
     *
     * A per-app cap is a daily figure the user set, so it is not drawn on a chart whose
     * points are week totals: the nearest honest weekly equivalent would be seven times a
     * number nobody chose, and labelling it "your cap" would be a small lie.
     */
    val trendTarget: TargetLine? = null,
    val category: AppCategoryRef? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val insight: AppUsageInsight? = null,
) {
    val canGoBack: Boolean get() = offset < maxOffset
    val canGoForward: Boolean get() = offset > 0
    val isPastWindow: Boolean get() = offset > 0
}

/**
 * One app, over a window the user chooses.
 *
 * The point of this screen is the trend, not the total: "four hours this month" is a
 * fact, but "half of it was last Tuesday" is a decision. Today is plotted hourly for the
 * same reason — an app used in three long sittings and an app opened sixty times share a
 * daily total and have nothing else in common. A cap and a category live here too,
 * because this is where the user is actually thinking about that app.
 */
class AppDetailViewModel(
    private val packageName: String,
    private val repository: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val today: Long = DayWindow.todayEpochDay()
    private val range = MutableStateFlow(ChartRange.WEEK)
    private val offset = MutableStateFlow(0)
    private val earliestDay = MutableStateFlow<Long?>(null)
    private val dataVersion = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            earliestDay.value = repository.earliestDayFor(packageName)
        }
    }

    /** One window to build: how long it is, how far back it sits, and how far back it can. */
    private data class WindowRequest(
        val chartRange: ChartRange,
        val offset: Int,
        val earliestDay: Long?,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AppDetailUiState> = combine(
        range,
        offset,
        earliestDay,
    ) { chartRange, windowOffset, earliest ->
        WindowRequest(chartRange, windowOffset, earliest)
    }
        .flatMapLatest { request ->
            val window = LookbackWindow.of(today, request.chartRange.days, request.offset)
            combine(
                repository.observeAppUsageFor(packageName, window.first, window.last),
                repository.observeTargets(),
                dataVersion,
            ) { rows, targets, _ -> Triple(rows, targets, window) }
                .map { (rows, targets, windowDays) ->
                    build(request, windowDays.first, windowDays.last, rows, targets)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState())

    fun setRange(range: ChartRange) {
        this.range.value = range
        // A longer window cannot sit as far back as a shorter one, so the offset is
        // re-clamped against the new length rather than left out of range.
        offset.value = offset.value.coerceIn(0, maxOffsetFor(range))
    }

    /** Steps the window one range towards older history, as far as this app goes. */
    fun showPrevious() {
        offset.value = (offset.value + 1).coerceAtMost(maxOffsetFor(range.value))
    }

    /** Steps the window one range back towards today. */
    fun showNext() {
        offset.value = (offset.value - 1).coerceAtLeast(0)
    }

    private fun maxOffsetFor(chartRange: ChartRange): Int =
        LookbackWindow.maxOffset(today, earliestDay.value, chartRange.days)

    fun setCap(minutes: Int?) {
        viewModelScope.launch {
            repository.setPerAppCap(packageName, minutes)
            dataVersion.value += 1
        }
    }

    fun setCategory(categoryId: String) {
        viewModelScope.launch {
            repository.setAppCategory(packageName, categoryId)
            dataVersion.value += 1
        }
    }

    private suspend fun build(
        request: WindowRequest,
        fromDay: Long,
        toDay: Long,
        rows: List<DailyAppUsageEntity>,
        targets: List<TargetEntity>,
    ): AppDetailUiState {
        appInfo.preload(listOf(packageName))

        val chartRange = request.chartRange
        val byDay = rows.associate { it.dayEpochDay to it.totalMs }
        val unit = chartRange.unit

        val trend = when (unit) {
            ChartUnit.HOUR -> repository.appHourlyBuckets(packageName, toDay).map { bucket ->
                TrendPoint(
                    label = HourFormat.short(bucket.hour),
                    value = bucket.totalMs,
                    epochDay = toDay,
                )
            }

            ChartUnit.DAY -> (fromDay..toDay).map { day ->
                TrendPoint(
                    label = DAY_LABEL.format(LocalDate.ofEpochDay(day)),
                    value = byDay[day] ?: 0L,
                    epochDay = day,
                )
            }

            // Days the app was not used are real zeroes here, unlike the device summary,
            // which only has a row for a day it actually tracked — so the range is filled
            // in rather than read back.
            ChartUnit.WEEK -> WeeklyRollup.ofTotals(
                (fromDay..toDay).map { day -> day to (byDay[day] ?: 0L) },
            ).map { week ->
                TrendPoint(
                    label = week.label,
                    value = week.screenTimeMs,
                    epochDay = week.startEpochDay,
                )
            }
        }

        val total = rows.sumOf { it.totalMs }
        val used = rows.count { it.totalMs > 0L }
        val previousRows = rows.filter { it.dayEpochDay < toDay && it.totalMs > 0L }
        val recentAverageMs = if (previousRows.isEmpty()) {
            0L
        } else {
            previousRows.sumOf { it.totalMs } / previousRows.size
        }

        val cap = targets
            .firstOrNull {
                it.type == TargetType.PER_APP_DAILY_CAP && it.scopePackage == packageName
            }
            ?.valueMinutes
        val category = repository.categoryLookup()[packageName]
        val insight = if (request.offset == 0) {
            AppUsageInsights.forApp(
                todayMs = byDay[toDay] ?: 0L,
                averageMs = recentAverageMs,
                capMinutes = cap,
                category = category,
            )
        } else {
            null
        }

        return AppDetailUiState(
            loading = false,
            packageName = packageName,
            range = chartRange,
            unit = unit,
            offset = request.offset,
            maxOffset = LookbackWindow.maxOffset(today, request.earliestDay, chartRange.days),
            windowLabel = DayRangeFormat.label(fromDay, toDay),
            trend = trend,
            totalMs = total,
            averageMs = if (used == 0) 0L else total / used,
            sessionCount = rows.sumOf { it.sessionCount },
            // The window's last day rather than always today: while browsing, "today"
            // would be a fact from outside the chart above it.
            todayMs = byDay[toDay] ?: 0L,
            busiest = trend.maxByOrNull { it.value }
                ?.takeIf { it.value > 0L && unit == ChartUnit.HOUR },
            capMinutes = cap,
            trendTarget = cap
                ?.takeIf { unit == ChartUnit.DAY }
                ?.let { minutes ->
                    val capMs = minutes * 60_000L
                    TargetLine(capMs, "cap ${DurationFormat.compact(capMs)}")
                },
            category = category,
            categories = repository.allCategories(),
            insight = insight,
        )
    }

    private companion object {
        val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}
