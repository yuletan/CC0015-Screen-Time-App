package com.intent.screentime.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.goals.GoalTargets
import com.intent.screentime.data.goals.GoalTracker
import com.intent.screentime.data.goals.HeatmapBuilder
import com.intent.screentime.data.goals.StreakEvaluator
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.data.usage.BedtimeWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalsUiState(
    val loading: Boolean = true,
    val epochDay: Long = DayWindow.todayEpochDay(),
    val weekStartEpochDay: Long = DayWindow.todayEpochDay(),
    val weekScreenTimeMs: Long = 0L,
    val weekProductionMs: Long = 0L,
    val todayScreenTimeMs: Long = 0L,
    val todayProductionMs: Long = 0L,
    val todayFocusMs: Long = 0L,
    val targets: GoalTargets = GoalTargets(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val bestDayScore: Int = 0,
    val heatmap: List<List<HeatmapBuilder.Cell>> = emptyList(),
    /**
     * Last night's verdict, from the judged row rather than recomputed: it is the same
     * number the heatmap square and the day card are reading.
     *
     * Null when no window is set, or when the night has not been judged yet.
     */
    val lastNightMet: Boolean? = null,
    val lastNightUsedMs: Long = 0L,
) {
    val bedtime: BedtimeWindow? get() = targets.bedtime

    val weeklyCapMs: Long? get() = targets.weeklyCapMinutes?.takeIf { it > 0 }?.let { it * 60_000L }
    val weeklyGoalMs: Long? get() =
        targets.weeklyProductionGoalMinutes?.takeIf { it > 0 }?.let { it * 60_000L }

    val weeklyCapProgress: Float
        get() = weeklyCapMs?.let { if (it <= 0L) 0f else weekScreenTimeMs.toFloat() / it } ?: 0f

    val weeklyGoalProgress: Float
        get() = weeklyGoalMs?.let { if (it <= 0L) 0f else weekProductionMs.toFloat() / it } ?: 0f

    val overWeeklyCap: Boolean get() = weeklyCapMs?.let { weekScreenTimeMs > it } == true

    val hasWeeklyData: Boolean get() = weekScreenTimeMs > 0L
}

/**
 * The Goals board: what was committed to, and whether it is being honoured.
 *
 * Five commitments live here — a weekly cap, a weekly production goal, a daily cap, a
 * daily focus goal and a bedtime window — plus the streak that only exists because of
 * them. Everything is derived from two queries (this week's summaries and the streak
 * rows), so the screen stays cheap to open.
 */
class GoalsViewModel(
    private val repository: UsageRepository,
    private val goalTracker: GoalTracker,
) : ViewModel() {

    private val today: Long = DayWindow.todayEpochDay()
    private val weekStart: Long = today - (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)

    private val dataVersion = MutableStateFlow(0)

    val state: StateFlow<GoalsUiState> = combine(
        repository.observeDays(weekStart, today),
        repository.observeTargets(),
        dataVersion,
    ) { week, targets, _ -> week to targets }
        .map { (week, targets) -> assemble(week, targets) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    private suspend fun assemble(
        week: List<DailySummaryEntity>,
        targetRows: List<TargetEntity>,
    ): GoalsUiState {
        val streakRows = repository.recentStreakRows(STREAK_LOOKBACK)
        val todayRow = week.firstOrNull { it.dayEpochDay == today }
        val targets = GoalTargets.from(targetRows)
        val lastNight = streakRows.firstOrNull { it.dayEpochDay == today - 1 }

        return GoalsUiState(
            loading = false,
            epochDay = today,
            weekStartEpochDay = weekStart,
            weekScreenTimeMs = week.sumOf { it.screenTimeMs },
            weekProductionMs = week.sumOf { it.productionMs },
            todayScreenTimeMs = todayRow?.screenTimeMs ?: 0L,
            todayProductionMs = todayRow?.productionMs ?: 0L,
            todayFocusMs = todayRow?.focusMs ?: 0L,
            targets = targets,
            currentStreak = StreakEvaluator.currentStreak(streakRows, today),
            bestStreak = StreakEvaluator.bestStreak(streakRows),
            bestDayScore = streakRows.maxOfOrNull { it.score } ?: 0,
            heatmap = HeatmapBuilder.weeks(streakRows, today, HEATMAP_WEEKS),
            lastNightMet = lastNight?.metBedtime,
            lastNightUsedMs = lastNight?.bedtimeUsedMs ?: 0L,
        )
    }

    /**
     * Writes a target and then re-judges the recent past against it.
     *
     * The re-judge is the point rather than a nicety: a heatmap that keeps yesterday's
     * verdict until the next nightly pass is a heatmap that contradicts the goal the user
     * just set, and a bedtime would not appear on it at all until tomorrow.
     */
    fun setTarget(type: TargetType, minutes: Int?) {
        viewModelScope.launch {
            repository.setTarget(type, minutes)
            rejudge()
        }
    }

    fun setBedtime(startMinutesOfDay: Int?, endMinutesOfDay: Int?) {
        viewModelScope.launch {
            repository.setBedtime(startMinutesOfDay, endMinutesOfDay)
            rejudge()
        }
    }

    private suspend fun rejudge() {
        goalTracker.evaluateStreaks(fromDay = today - STREAK_WINDOW_DAYS, toDay = today)
        dataVersion.value += 1
    }

    private companion object {
        const val STREAK_LOOKBACK = 120
        const val HEATMAP_WEEKS = 12

        /** Comfortably longer than the twelve weeks the heatmap shows. */
        const val STREAK_WINDOW_DAYS = 90L
    }
}
