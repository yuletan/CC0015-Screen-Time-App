package com.intent.screentime.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.export.CsvExporter
import com.intent.screentime.data.export.PeriodExporter
import com.intent.screentime.data.goals.BedtimeNudge
import com.intent.screentime.data.goals.GoalTargets
import com.intent.screentime.data.goals.GoalTracker
import com.intent.screentime.data.local.entity.TargetType
import com.intent.screentime.data.prefs.UserPreferences
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.data.usage.BedtimeWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: UsageRepository,
    private val preferences: UserPreferences,
    private val csvExporter: CsvExporter,
    private val periodExporter: PeriodExporter? = null,
    private val goalTracker: GoalTracker,
    private val onDigestTimeChanged: (Int) -> Unit,
) : ViewModel() {

    val capMinutes: StateFlow<Int?> = repository.observeTargets()
        .map { targets ->
            targets.firstOrNull { it.type == TargetType.DAILY_SCREEN_TIME_CAP }?.valueMinutes
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The quiet window, so the row can state it and the dialog can edit it where it is. */
    val bedtime: StateFlow<BedtimeWindow?> = repository.observeTargets()
        .map { GoalTargets.from(it).bedtime }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The last week of nights, read from the judged rows rather than recomputed here, so
     * this row and the heatmap square are quoting the same verdict.
     */
    val bedtimeNudge: StateFlow<BedtimeNudge> = repository.observeStreak(RECENT_NIGHTS)
        .map { BedtimeNudge.from(it, DayWindow.todayEpochDay()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BedtimeNudge(missedNights = 0, judgedNights = 0),
        )

    val digestMinutes: StateFlow<Int> = preferences.digestMinutesOfDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_DIGEST_MINUTES)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _exportUri = MutableStateFlow<Uri?>(null)
    val exportUri: StateFlow<Uri?> = _exportUri

    private val _periodZip = MutableStateFlow<Pair<Uri, String>?>(null)
    /** A finished Day / Week / Month zip + its file name, consumed once. */
    val periodZip: StateFlow<Pair<Uri, String>?> = _periodZip

    private val _periodBusy = MutableStateFlow(false)
    val periodBusy: StateFlow<Boolean> = _periodBusy

    fun setCap(minutes: Int?) {
        viewModelScope.launch { repository.setDailyCapMinutes(minutes) }
    }

    /**
     * Writes the window and then re-judges the recent past against it.
     *
     * The re-judge is the point rather than a nicety: the heatmap, the score and this
     * row's own count all read the streak rows, and a window that only took effect after
     * tonight's pass would leave the screen contradicting the commitment just made.
     */
    fun setBedtime(startMinutesOfDay: Int?, endMinutesOfDay: Int?) {
        viewModelScope.launch {
            repository.setBedtime(startMinutesOfDay, endMinutesOfDay)
            val today = DayWindow.todayEpochDay()
            goalTracker.evaluateStreaks(fromDay = today - REJUDGE_DAYS, toDay = today)
        }
    }

    /**
     * Persisted first, then rescheduled: the worker reads the preference when it runs,
     * so the stored value must be the truth even if scheduling is delayed.
     */
    fun setDigestMinutes(minutes: Int) {
        viewModelScope.launch {
            preferences.setDigestMinutesOfDay(minutes)
            onDigestTimeChanged(minutes)
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            _exportUri.value = csvExporter.exportDailySummaries()
        }
    }

    fun clearExport() {
        _exportUri.value = null
    }

    /**
     * Downloads the last [days] ending today as one zip (`csv/` + `photos/`).
     * Day = 1, Week = 7, Month = 30 — a month is 4× weeks with its daily photos.
     */
    fun exportPeriod(days: Long) {
        val exporter = periodExporter ?: return
        if (_periodBusy.value) return
        viewModelScope.launch {
            _periodBusy.value = true
            try {
                val today = DayWindow.todayEpochDay()
                val uri = exporter.export(today - days + 1, today)
                if (uri != null) {
                    _periodZip.value = uri to (uri.lastPathSegment.orEmpty())
                }
            } finally {
                _periodBusy.value = false
            }
        }
    }

    fun clearPeriodZip() {
        _periodZip.value = null
    }

    fun refreshNow() = runBusy { repository.refresh() }

    /**
     * Rebuilds from a recent window rather than all history: the point of this action is
     * to repair numbers that look wrong, and sixty days is well beyond what the OS can
     * still be holding raw events for.
     */
    fun recomputeHistory() = runBusy {
        val earliest = DayWindow.todayEpochDay() - RECOMPUTE_DAYS
        repository.recomputeFrom(earliest)
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val RECOMPUTE_DAYS = 60L
        const val DEFAULT_DIGEST_MINUTES = 21 * 60

        /** Tonight plus the week the nudge speaks for. */
        const val RECENT_NIGHTS = BedtimeNudge.WINDOW_NIGHTS + 1

        /** Comfortably longer than the week the nudge reads. Kept in step with Goals. */
        const val REJUDGE_DAYS = 90L
    }
}
