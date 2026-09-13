package com.intent.screentime.focus

import androidx.room.withTransaction
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.IntentDatabase
import com.intent.screentime.data.local.entity.FocusSessionEntity
import com.intent.screentime.data.usage.UsageIngestor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The one place a focus session's lifetime is decided.
 *
 * The service starts, ends and cancels through here, and the UI observes [state] — that
 * way the timer on screen and the timer in the notification can never disagree, and a
 * finished session is always written down exactly once.
 *
 * Sessions run in this process, which is kept alive for the duration by the foreground
 * service, so a process-local flow is sufficient state; the database row is the durable
 * record.
 */
class FocusSessionManager(
    private val database: IntentDatabase,
    private val ingestor: UsageIngestor,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class FocusState(
        val sessionId: Long? = null,
        val startMs: Long = 0L,
        val plannedMs: Long = 0L,
        val label: String? = null,
    ) {
        val running: Boolean get() = sessionId != null

        val endMs: Long get() = startMs + plannedMs

        fun remainingMs(nowMs: Long): Long = (endMs - nowMs).coerceAtLeast(0L)

        fun elapsedMs(nowMs: Long): Long = (nowMs - startMs).coerceIn(0L, plannedMs)
    }

    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state

    suspend fun start(plannedMs: Long, label: String?): FocusState {
        val now = clock()
        val id = database.focusSessionDao().insert(
            FocusSessionEntity(startMs = now, plannedMs = plannedMs, label = label),
        )

        val started = FocusState(
            sessionId = id,
            startMs = now,
            plannedMs = plannedMs,
            label = label,
        )
        _state.value = started
        return started
    }

    /** Ran to the end: the minutes count, and today's aggregates immediately reflect it. */
    suspend fun complete() {
        val current = _state.value
        if (current.sessionId == null) return
        finish(current, completed = true, endMs = current.endMs)
    }

    /** Given up on early: recorded, but not counted. */
    suspend fun cancel() {
        val current = _state.value
        if (current.sessionId == null) return
        finish(current, completed = false, endMs = clock())
    }

    private suspend fun finish(state: FocusState, completed: Boolean, endMs: Long) {
        val id = state.sessionId ?: return
        val end = endMs.coerceAtLeast(state.startMs)

        database.withTransaction {
            database.focusSessionDao().finish(id, end, completed)
        }
        _state.value = FocusState()

        // Focus minutes feed the day's summary and the Intent Score, so today is
        // recomputed rather than left until the next harvest.
        ingestor.reaggregateFrom(DayWindow.todayEpochDay())
    }
}
