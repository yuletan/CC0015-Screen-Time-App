package com.intent.screentime.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.export.CsvExporter
import com.intent.screentime.data.local.entity.TargetType
import com.intent.screentime.data.prefs.UserPreferences
import com.intent.screentime.data.repository.UsageRepository
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
    private val onDigestTimeChanged: (Int) -> Unit,
) : ViewModel() {

    val capMinutes: StateFlow<Int?> = repository.observeTargets()
        .map { targets ->
            targets.firstOrNull { it.type == TargetType.DAILY_SCREEN_TIME_CAP }?.valueMinutes
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val digestMinutes: StateFlow<Int> = preferences.digestMinutesOfDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_DIGEST_MINUTES)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _exportUri = MutableStateFlow<Uri?>(null)
    val exportUri: StateFlow<Uri?> = _exportUri

    fun setCap(minutes: Int?) {
        viewModelScope.launch { repository.setDailyCapMinutes(minutes) }
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
    }
}
