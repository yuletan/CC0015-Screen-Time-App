package com.intent.screentime.ui.insights

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.format.DayRangeFormat
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.core.format.HourFormat
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.export.PeriodExporter
import com.intent.screentime.data.goals.GoalTargets
import com.intent.screentime.data.goals.ScoreWindow
import com.intent.screentime.data.intent.IntentStats
import com.intent.screentime.data.intent.Reasons
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.repository.AppCategoryRef
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.data.stats.DayTotals
import com.intent.screentime.data.stats.LookbackWindow
import com.intent.screentime.data.stats.UsageSplit
import com.intent.screentime.data.stats.WeekOverWeek
import com.intent.screentime.data.stats.WeekVerdict
import com.intent.screentime.data.stats.WeekdayAverages
import com.intent.screentime.data.stats.WeeklyRollup
import com.intent.screentime.data.usage.BedtimeWindow
import com.intent.screentime.data.usage.HourlyBreakdown
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

/** One app's share of the range, with its label already resolved for the ranking. */
data class AppTotal(
    val packageName: String,
    val name: String,
    val totalMs: Long,
    /**
     * What kind of time this app took, so the list can answer "is this producing or
     * consuming for me" without a second trip to the Apps screen.
     */
    val category: AppCategoryRef? = null,
)

/** One app that moved between the two weeks, labelled so a row can render it directly. */
data class AppMovementView(
    val packageName: String,
    val name: String,
    val currentMs: Long,
    val previousMs: Long,
    val deltaMs: Long,
)

/**
 * The week read back against the last one.
 *
 * [verdict] carries the ranked findings and [comparison] the numbers behind them, because
 * the panel shows both: the sentence says what moved, the rows let the reader check it.
 */
data class WeekOverWeekInsight(
    val verdict: List<WeekVerdict.Finding>,
    val comparison: WeekOverWeek.Result,
    val movers: List<AppMovementView>,
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

/**
 * The day's own vitals, only for a window that is a single day.
 *
 * Summed over a month they would be an average wearing a total's clothes — ninety unlocks
 * a day and one unlock a day read the same — so they are shown where they mean what they
 * say, which is the same reason the trend chart changes unit with the range.
 */
data class WindowVitals(
    val unlockCount: Int,
    val focusMs: Long,
    val sessionCount: Int,
)

data class InsightsUiState(
    val loading: Boolean = true,
    val range: ChartRange = ChartRange.WEEK,
    val unit: ChartUnit = ChartUnit.DAY,
    /** Which window this is: 0 is the one ending today, 1 the one before it, and so on. */
    val offset: Int = 0,
    /** The furthest back there is anything to see, so the stepper can stop there. */
    val maxOffset: Int = 0,
    val windowFrom: Long = 0L,
    val windowTo: Long = 0L,
    /** The window's own dates — "8–14 Sep" — because "3 weeks ago" makes the reader count. */
    val windowLabel: String = "",
    /** What the trend chart plots, at [unit]'s resolution. */
    val trend: List<TrendPoint> = emptyList(),
    val points: List<DailyPoint> = emptyList(),
    /**
     * The window's time by kind: Producing, Consuming, Utility, Neutral and Unsorted. The
     * same split the Today screen shows for a day, summed or averaged over the window.
     */
    val split: UsageSplit = UsageSplit(),
    /**
     * The window's Intent Score: its own number for a single day, the mean of the days'
     * numbers for a range. Null only when the window has no tracked day at all.
     */
    val score: ScoreWindow.Result? = null,
    val vitals: WindowVitals? = null,
    /** The daily cap the score's cap row is judged against, for its own explanation. */
    val dailyCapMinutes: Int? = null,
    val totalFocusMs: Long = 0L,
    /** The quiet window and how much of it was used, so the bedtime row can say so. */
    val bedtimeWindow: BedtimeWindow? = null,
    val bedtimeUsedMs: Long = 0L,
    val categories: List<CategoryTotal> = emptyList(),
    val topApps: List<AppTotal> = emptyList(),
    val averageMs: Long = 0L,
    val best: DailyPoint? = null,
    val worst: DailyPoint? = null,
    val activeHours: Int = 0,
    val busiest: TrendPoint? = null,
    /**
     * Week-over-week, empty-handed on a first week: there is nothing to compare against
     * yet, and a comparison against nothing is a claim rather than a finding.
     */
    val weekOverWeek: WeekOverWeekInsight? = null,
    /**
     * The goal drawn across the trend, when its period matches what the chart plots.
     *
     * A daily cap on an hourly chart describes nothing, and a weekly cap on a daily chart
     * would have to be divided by seven to be drawn at all — so each unit only accepts the
     * target that shares its period.
     */
    val trendTarget: TargetLine? = null,
    /** At [ChartUnit.WEEK]: what a week averaged, the heaviest, and how many there were. */
    val weeklyAverageMs: Long = 0L,
    val heaviestWeek: TrendPoint? = null,
    val weeksTracked: Int = 0,
    /**
     * Weekday averages always describe [weekdayWeeks] weeks, never the selected range: a
     * single week gives every weekday exactly one sample, which is a day, not a pattern.
     */
    val weekdayAverages: List<WeekdayAverages.Entry> = emptyList(),
    val weekdayWeeks: Int = 0,
    val weekdayAverageMs: Long = 0L,
    val weekendAverageMs: Long = 0L,
    /** Production share per day, as a whole percent, over days that have categorised time. */
    val ratioTrend: List<TrendPoint> = emptyList(),
    /**
     * The reason ledger. Null only when no prompt was ever raised in the range — once a
     * single row exists it is shown, *including* a range where every prompt was skipped,
     * because then the answer rate is the whole story.
     */
    val intentInsight: IntentInsight? = null,
    /**
     * At DAY and WEEK units: when in the day the window's time actually went. Empty for
     * Today, whose trend chart is already hour by hour.
     */
    val hourBuckets: List<HourlyBreakdown.Bucket> = emptyList(),
    /** The equally long window before this one; null when nothing was tracked in it. */
    val previousWindowMs: Long? = null,
) {
    val hasData: Boolean get() = trend.any { it.value > 0L }
    val totalMs: Long get() = points.sumOf { it.screenTimeMs }
    val hasRatioTrend: Boolean get() = ratioTrend.size >= 2

    /** True while the stepper is off the window that ends today. */
    val isPastWindow: Boolean get() = offset > 0

    val canGoBack: Boolean get() = offset < maxOffset
    val canGoForward: Boolean get() = offset > 0
}

/**
 * Trends over a chosen window — and, because the history is stored, over any window
 * before it.
 *
 * The range selector chooses how long the window is; the offset chooses which one, so
 * "last week" and "the week before last" are the same query a week apart. Switching
 * range re-subscribes to a different slice of the summary table rather than filtering in
 * memory, so a 30-day view costs the same small query as a 7-day one. Today is the
 * exception that earns its keep: it is plotted per hour, because "when do I use this" is
 * not a question a daily total can answer. The long ranges resolve to weeks for the same
 * kind of reason in the other direction — forty-two daily points is not a shape anyone
 * can read, and a week is the unit the targets are already written in.
 */
class InsightsViewModel(
    private val repository: UsageRepository,
    private val appInfo: AppInfoProvider,
    private val periodExporter: PeriodExporter? = null,
) : ViewModel() {

    private val range = MutableStateFlow(ChartRange.WEEK)
    private val offset = MutableStateFlow(0)
    private val earliestDay = MutableStateFlow<Long?>(null)

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting

    private val _exportUri = MutableStateFlow<Pair<Uri, String>?>(null)
    /** The finished zip + its file name, consumed once by the share sheet. */
    val exportUri: StateFlow<Pair<Uri, String>?> = _exportUri

    init {
        viewModelScope.launch {
            earliestDay.value = repository.earliestTrackedDay()
        }
    }

    /** One window to build: how long it is, how far back it sits, and how far back it can. */
    private data class WindowRequest(
        val chartRange: ChartRange,
        val offset: Int,
        val earliestDay: Long?,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<InsightsUiState> = combine(
        range,
        offset,
        earliestDay,
    ) { chartRange, windowOffset, earliest ->
        WindowRequest(chartRange, windowOffset, earliest)
    }
        .flatMapLatest { request ->
            val today = DayWindow.todayEpochDay()
            val window = LookbackWindow.of(today, request.chartRange.days, request.offset)
            repository.observeDays(window.first, window.last).map { summaries ->
                build(request, today, window, summaries)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setRange(range: ChartRange) {
        this.range.value = range
        // A 90-day window cannot sit three weeks back where a week could, so the offset
        // is re-clamped against the new length rather than left out of range.
        offset.value = offset.value.coerceIn(0, maxOffsetFor(range))
    }

    /** Steps the window one range towards older history, as far as the data goes. */
    fun showPrevious() {
        offset.value = (offset.value + 1).coerceAtMost(maxOffsetFor(range.value))
    }

    /** Steps the window one range back towards today. */
    fun showNext() {
        offset.value = (offset.value - 1).coerceAtLeast(0)
    }

    /**
     * Downloads the window currently on screen as one zip (`csv/` + `photos/`).
     *
     * [appCaptures] are the exact on-screen chart pixels collected by the screen —
     * the photo of what is seen in the app. The exporter files each one under its
     * date-named photo and only redraws panels that had nothing to capture (off-screen
     * or past windows).
     *
     * The window is whatever the range selector + stepper are showing — a day for
     * Today, 7 days for Week, 30 for Month — so Day / Week / Month downloads need
     * no separate picker. A month is 4× weeks: daily photos plus one chart set per
     * week, same two folders, longer duration.
     */
    fun exportCurrentWindow(appCaptures: Map<String, android.graphics.Bitmap> = emptyMap()) {
        val exporter = periodExporter ?: return
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            try {
                val window = state.value
                if (!window.hasData) return@launch
                val uri = exporter.export(window.windowFrom, window.windowTo, appCaptures)
                if (uri != null) {
                    val name = uri.lastPathSegment.orEmpty()
                    _exportUri.value = uri to name
                }
            } finally {
                _exporting.value = false
            }
        }
    }

    fun clearExport() {
        _exportUri.value = null
    }

    private fun maxOffsetFor(chartRange: ChartRange): Int =
        LookbackWindow.maxOffset(DayWindow.todayEpochDay(), earliestDay.value, chartRange.days)

    private suspend fun build(
        request: WindowRequest,
        today: Long,
        window: LongRange,
        summaries: List<DailySummaryEntity>,
    ): InsightsUiState {
        val chartRange = request.chartRange
        val fromDay = window.first
        val toDay = window.last
        val points = summaries.map {
            DailyPoint(
                epochDay = it.dayEpochDay,
                screenTimeMs = it.screenTimeMs,
                productionMs = it.productionMs,
                consumptionMs = it.consumptionMs,
            )
        }

        val unit = chartRange.unit
        val weeks = if (unit == ChartUnit.WEEK) {
            WeeklyRollup.of(summaries.map { it.toDayTotals() })
        } else {
            emptyList()
        }

        val trend = when (unit) {
            ChartUnit.HOUR -> repository.hourlyBuckets(toDay).map { bucket ->
                TrendPoint(
                    label = HourFormat.short(bucket.hour),
                    value = bucket.totalMs,
                    epochDay = toDay,
                )
            }

            ChartUnit.DAY -> points.map { point ->
                TrendPoint(
                    label = DAY_LABEL.format(LocalDate.ofEpochDay(point.epochDay)),
                    value = point.screenTimeMs,
                    epochDay = point.epochDay,
                )
            }

            // The week's total, anchored on its Monday, so tapping a point opens the card
            // for the first day of the week it summarises.
            ChartUnit.WEEK -> weeks.map { week ->
                TrendPoint(
                    label = week.label,
                    value = week.screenTimeMs,
                    epochDay = week.startEpochDay,
                )
            }
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
        val appRows = repository.appUsageBetween(fromDay, toDay)
        val lookup = repository.categoryLookup()
        val topApps = appRows
            .groupBy { it.packageName }
            .map { (packageName, rows) ->
                AppTotal(
                    packageName = packageName,
                    name = appInfo.label(packageName),
                    totalMs = rows.sumOf { it.totalMs },
                    category = lookup[packageName],
                )
            }
            .sortedByDescending { it.totalMs }
            .take(TOP_APPS)

        // The same rows the ranking came from, read once: the split of the window is what
        // the three above it are made of, and reading it twice would risk the two
        // disagreeing after a re-categorisation.
        val split = UsageSplit.from(appRows, lookup)

        // Real icons for the ranked rows, resolved off the main thread.
        appInfo.preload(topApps.map { it.packageName })

        // The comparison is defined against the running week — "this week vs last" — so it
        // belongs to the current window only. A fortnight anchored to a week in March
        // would be a comparison nobody asked for; the stepper tells that story instead.
        val weekOverWeek = if (request.offset == 0) weekOverWeek(today) else null

        val weekdayWindowStart = toDay - (WEEKDAY_WINDOW_DAYS - 1)
        val weekday = WeekdayAverages.of(
            repository.summariesBetween(weekdayWindowStart, toDay)
                .map { it.dayEpochDay to it.screenTimeMs },
        )

        val categorised = points.filter { it.productionMs + it.consumptionMs > 0L }
        val ratios = if (unit == ChartUnit.WEEK) {
            // Weekly, to match the chart above it: a 90-point ratio line would be a smear,
            // and the reader is comparing weeks by this point anyway.
            weeks.mapNotNull { week ->
                share(week.productionMs, week.consumptionMs)?.let { value ->
                    TrendPoint(label = week.label, value = value, epochDay = week.startEpochDay)
                }
            }
        } else {
            categorised.mapNotNull { point ->
                share(point.productionMs, point.consumptionMs)?.let { value ->
                    TrendPoint(
                        label = DAY_LABEL.format(LocalDate.ofEpochDay(point.epochDay)),
                        value = value,
                        epochDay = point.epochDay,
                    )
                }
            }
        }

        // Only queried when the user has ever raised a prompt: the two reads are cheap but
        // pointless for someone who never turned Phase 7 on. A single row — even a lone
        // skip — is enough to render, because the answer rate is itself the finding.
        // The window's own end, so a past range reports the prompts that were answered
        // inside it rather than everything since.
        val windowEndMs = minOf(DayWindow.endOfDayMs(toDay), System.currentTimeMillis())
        val logs = repository.intentLogsBetween(
            DayWindow.startOfDayMs(fromDay),
            windowEndMs,
        )
        val intentInsight = if (logs.isEmpty()) {
            null
        } else {
            val ledger = IntentStats.ledger(
                logs = logs,
                sessions = repository.sessionsBetween(
                    logs.first().timestampMs,
                    windowEndMs,
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

        val targets = repository.enabledTargets()
        val goalTargets = GoalTargets.from(targets)
        val dailyCapMs = goalTargets.dailyCapMinutes?.toLong()?.times(60_000L)
        val weeklyCapMs = goalTargets.weeklyCapMinutes?.toLong()?.times(60_000L)

        // The window's own score: each day judged by the same five weights the Today screen
        // uses, then averaged row by row, so a day reads here as it reads there.
        //
        // Sixty days of verdicts either side of the window, because the streak a day was on
        // is decided by the days before it, and the nightly pass keeps only that much.
        val streakRows = repository.streakRowsBetween(
            fromDay - STREAK_CONTEXT_DAYS,
            toDay,
        )
        val score = ScoreWindow.of(
            ScoreWindow.inputs(
                summaries = summaries,
                targets = goalTargets,
                streakRows = streakRows,
                todayEpochDay = today,
            ),
        )

        // When in the day the window's time went. On the longer ranges this is the panel
        // that answers "when am I actually on this thing"; a past day gets the same strip
        // the Today screen draws, from the same sessions, while today's own chart is already
        // hour by hour and the Today tab is where the live day belongs.
        val hourBuckets = when {
            unit != ChartUnit.HOUR -> repository.hourlyBucketsForRange(fromDay, toDay)
            request.offset > 0 -> repository.hourlyBuckets(toDay)
            else -> emptyList()
        }

        // A single day's own vitals. Only for a window that is one day, where a total is
        // the day rather than an average pretending to be one.
        val vitals = if (chartRange.days == 1L) {
            summaries.firstOrNull()?.let { summary ->
                WindowVitals(
                    unlockCount = summary.unlockCount,
                    focusMs = summary.focusMs,
                    sessionCount = appRows.sumOf { it.sessionCount },
                )
            }
        } else {
            null
        }

        // The window immediately before this one, for the "more/less than last week" line.
        val previousWindow = LookbackWindow.of(today, chartRange.days, request.offset + 1)
        val previousDays = repository.summariesBetween(previousWindow.first, previousWindow.last)
        val previousWindowMs = previousDays.takeIf { it.isNotEmpty() }?.sumOf { it.screenTimeMs }

        return InsightsUiState(
            loading = false,
            range = chartRange,
            unit = unit,
            offset = request.offset,
            maxOffset = LookbackWindow.maxOffset(today, request.earliestDay, chartRange.days),
            windowFrom = fromDay,
            windowTo = toDay,
            windowLabel = DayRangeFormat.label(fromDay, toDay),
            trend = trend,
            points = points,
            split = split,
            score = score,
            vitals = vitals,
            dailyCapMinutes = goalTargets.dailyCapMinutes,
            totalFocusMs = summaries.sumOf { it.focusMs },
            // The night's own numbers, only for the one-day window that can have a single
            // night to talk about. Stored rather than recomputed, so the score's bedtime row
            // and the day card cannot report two different figures for the same night.
            bedtimeWindow = goalTargets.bedtime,
            bedtimeUsedMs = if (chartRange.days == 1L) {
                streakRows.firstOrNull { it.dayEpochDay == toDay }?.bedtimeUsedMs ?: 0L
            } else {
                0L
            },
            categories = categories,
            topApps = topApps,
            averageMs = if (withData.isEmpty()) 0L else withData.sumOf { it.screenTimeMs } / withData.size,
            best = withData.maxByOrNull { it.screenTimeMs },
            worst = withData.filter { it.screenTimeMs > 0L }.minByOrNull { it.screenTimeMs },
            activeHours = if (unit == ChartUnit.HOUR) trend.count { it.value > 0L } else 0,
            busiest = trend.maxByOrNull { it.value }?.takeIf { it.value > 0L && unit == ChartUnit.HOUR },
            weekOverWeek = weekOverWeek,
            trendTarget = when (unit) {
                ChartUnit.HOUR -> null
                ChartUnit.DAY -> dailyCapMs?.let { capLine(it) }
                ChartUnit.WEEK -> weeklyCapMs?.let { capLine(it) }
            },
            weeklyAverageMs = if (weeks.isEmpty()) 0L else weeks.sumOf { it.screenTimeMs } / weeks.size,
            heaviestWeek = if (unit == ChartUnit.WEEK) trend.maxByOrNull { it.value } else null,
            weeksTracked = weeks.size,
            weekdayAverages = weekday.entries,
            weekdayWeeks = (WEEKDAY_WINDOW_DAYS / 7).toInt(),
            weekdayAverageMs = weekday.weekdayAverageMs,
            weekendAverageMs = weekday.weekendAverageMs,
            ratioTrend = ratios,
            intentInsight = intentInsight,
            hourBuckets = hourBuckets,
            previousWindowMs = previousWindowMs,
        )
    }

    /**
     * The two windows of the same length, and the movers between them.
     *
     * Reads its own narrow slices rather than reusing the range above: the comparison is
     * always a fortnight wide, whether the user is looking at a week or at ninety days.
     */
    private suspend fun weekOverWeek(toDay: Long): WeekOverWeekInsight? {
        val windows = WeekOverWeek.windowsFor(toDay)

        val current = daysIn(windows.current)
        val previous = daysIn(windows.previous)
        if (previous.isEmpty()) return null

        val result = WeekOverWeek.compare(
            current = current,
            previous = previous,
            previousWeek = daysIn(windows.previousFull),
            currentApps = appsIn(windows.current),
            previousApps = appsIn(windows.previous),
            currentMindlessOpens = mindlessOpens(windows.current),
            previousMindlessOpens = mindlessOpens(windows.previous),
        )

        val movers = result.movers.map { movement ->
            AppMovementView(
                packageName = movement.packageName,
                name = appInfo.label(movement.packageName),
                currentMs = movement.currentMs,
                previousMs = movement.previousMs,
                deltaMs = movement.deltaMs,
            )
        }
        appInfo.preload(movers.map { it.packageName })

        return WeekOverWeekInsight(
            verdict = WeekVerdict.rank(result),
            comparison = result,
            movers = movers,
        )
    }

    private suspend fun daysIn(range: LongRange): List<DayTotals> =
        repository.summariesBetween(range.first, range.last).map { it.toDayTotals() }

    private suspend fun appsIn(range: LongRange): List<Pair<String, Long>> =
        repository.appUsageBetween(range.first, range.last)
            .groupBy { it.packageName }
            .map { (packageName, rows) -> packageName to rows.sumOf { it.totalMs } }

    private suspend fun mindlessOpens(range: LongRange): Int =
        repository.intentLogsBetween(
            DayWindow.startOfDayMs(range.first),
            DayWindow.endOfDayMs(range.last),
        ).count { it.reasonKey in Reasons.mindlessKeys }

    /** Null when nothing accountable was recorded, rather than a share of zero. */
    private fun share(productionMs: Long, consumptionMs: Long): Long? {
        val accountable = productionMs + consumptionMs
        if (accountable == 0L) return null
        return (productionMs.toDouble() / accountable * 100.0).roundToLong()
    }

    private fun capLine(capMs: Long) = TargetLine(
        value = capMs,
        label = "cap ${DurationFormat.compact(capMs)}",
    )

    private fun DailySummaryEntity.toDayTotals() = DayTotals(
        epochDay = dayEpochDay,
        screenTimeMs = screenTimeMs,
        productionMs = productionMs,
        consumptionMs = consumptionMs,
        unlockCount = unlockCount,
        focusMs = focusMs,
    )

    companion object {
        private const val TOP_APPS = 6

        /** Eight weeks: enough Mondays for an average to mean something. */
        private const val WEEKDAY_WINDOW_DAYS = 56L

        /**
         * How far before a window to read day verdicts, so the streak a day was on can be
         * counted from days the window no longer contains. Kept in step with the window the
         * nightly pass re-judges.
         */
        private const val STREAK_CONTEXT_DAYS = 60L

        private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}
