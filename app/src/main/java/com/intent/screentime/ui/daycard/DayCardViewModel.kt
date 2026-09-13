package com.intent.screentime.ui.daycard

import androidx.lifecycle.ViewModel
import com.intent.screentime.data.local.entity.DayReflection
import com.intent.screentime.data.local.entity.IntentLogEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.ui.apps.AppInfoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DayAppRow(val packageName: String, val totalMs: Long, val sessionCount: Int)

data class DayCardUiState(
    val loading: Boolean = true,
    val epochDay: Long = 0L,
    val screenTimeMs: Long = 0L,
    val productionMs: Long = 0L,
    val consumptionMs: Long = 0L,
    val utilityMs: Long = 0L,
    val neutralMs: Long = 0L,
    val focusMs: Long = 0L,
    val unlockCount: Int = 0,
    val topApps: List<DayAppRow> = emptyList(),
    val appCount: Int = 0,
    val verdict: StreakDayEntity? = null,
    val intents: List<IntentLogEntity> = emptyList(),
    val note: String = "",
    val reflection: DayReflection? = null,
) {
    val hasData: Boolean get() = screenTimeMs > 0L

    val productionShare: Float
        get() {
            val accountable = productionMs + consumptionMs
            return if (accountable <= 0L) 0f else productionMs.toFloat() / accountable
        }
}

/**
 * Assembles one day's card.
 *
 * Everything here is a read of data that already exists — the day's summary, its app
 * breakdown, the verdict the nightly pass recorded, and the prompts answered that day.
 * The only thing this screen owns is the note and the reflection.
 */
class DayCardViewModel(
    epochDay: Long,
    private val repository: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(DayCardUiState(epochDay = epochDay))
    val state: StateFlow<DayCardUiState> = _state.asStateFlow()

    fun setNote(note: String) {
        _state.value = _state.value.copy(note = note)
    }

    fun setReflection(reflection: DayReflection?) {
        _state.value = _state.value.copy(reflection = reflection)
    }
}
