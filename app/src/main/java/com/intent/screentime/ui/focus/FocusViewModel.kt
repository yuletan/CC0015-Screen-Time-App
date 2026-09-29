package com.intent.screentime.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.focus.FocusStats
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.FocusSessionEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.focus.FocusSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FocusUiState(
    val running: Boolean = false,
    val plannedMs: Long = 0L,
    val remainingMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val label: String? = null,
    val todayFocusMs: Long = 0L,
    /** How much of [todayFocusMs] the stretch detector found rather than the timer. */
    val autoFocusMs: Long = 0L,
    val goalMinutes: Int? = null,
    val streakDays: Int = 0,
    val history: List<FocusSessionEntity> = emptyList(),
) {
    val progress: Float get() = if (plannedMs <= 0L) 0f else elapsedMs.toFloat() / plannedMs
    val goalProgress: Float
        get() = goalMinutes?.takeIf { it > 0 }?.let {
            todayFocusMs.toFloat() / (it * 60_000L)
        } ?: 0f
    val metGoal: Boolean get() = goalMinutes?.let { todayFocusMs >= it * 60_000L } == true
}

/**
 * The focus timer's view of the world.
 *
 * The countdown ticks once a second *only while a session is running* — the rest of the
 * time this screen is completely still, which is the point: a timer app that wakes up to
 * redraw an idle screen is spending battery to tell the user nothing.
 */
class FocusViewModel(
    private val repository: UsageRepository,
    private val manager: FocusSessionManager,
    private val onStart: (Long, String?) -> Unit,
    private val onCancel: () -> Unit,
) : ViewModel() {

    private val today: Long = DayWindow.todayEpochDay()

    private data class Inputs(
        val focus: FocusSessionManager.FocusState,
        val history: List<FocusSessionEntity>,
        val targets: List<TargetEntity>,
        val summary: DailySummaryEntity?,
    )

    private val inputs: Flow<Inputs> = combine(
        manager.state,
        repository.observeFocusSince(DayWindow.startOfDayMs(today) - HISTORY_WINDOW_MS),
        repository.observeTargets(),
        repository.observeDay(today),
    ) { focus, history, targets, summary -> Inputs(focus, history, targets, summary) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<FocusUiState> = inputs
        .flatMapLatest { input ->
            if (input.focus.running) {
                ticker().map { now -> build(input, now) }
            } else {
                flowOf(build(input, System.currentTimeMillis()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    private fun ticker(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    private fun build(input: Inputs, nowMs: Long): FocusUiState {
        val goalMinutes = input.targets
            .firstOrNull { it.type == TargetType.DAILY_FOCUS_GOAL }
            ?.valueMinutes

        return FocusUiState(
            running = input.focus.running,
            plannedMs = input.focus.plannedMs,
            remainingMs = input.focus.remainingMs(nowMs),
            elapsedMs = input.focus.elapsedMs(nowMs),
            label = input.focus.label,
            // The day's own figure once it exists: it is the union of the timer and the
            // stretch detector, and it is what the Today board and the score read.
            todayFocusMs = input.summary?.focusMs
                ?: FocusStats.completedMsOn(input.history, today),
            autoFocusMs = input.summary?.autoFocusMs ?: 0L,
            goalMinutes = goalMinutes,
            streakDays = FocusStats.streak(input.history, today),
            history = input.history
                .filter { it.endMs != null }
                .sortedByDescending { it.startMs }
                .take(HISTORY_LIMIT),
        )
    }

    fun start(plannedMs: Long, label: String? = null) {
        onStart(plannedMs, label)
    }

    fun cancel() {
        onCancel()
    }

    fun setGoal(minutes: Int?) {
        viewModelScope.launch { repository.setTarget(TargetType.DAILY_FOCUS_GOAL, minutes) }
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val HISTORY_LIMIT = 30
        const val HISTORY_WINDOW_MS = 30L * 24 * 60 * 60 * 1_000
    }
}
