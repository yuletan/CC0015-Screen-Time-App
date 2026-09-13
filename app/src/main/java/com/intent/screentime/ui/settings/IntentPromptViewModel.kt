package com.intent.screentime.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.data.apps.InstalledApp
import com.intent.screentime.data.apps.InstalledAppsProvider
import com.intent.screentime.data.prefs.UserPreferences
import com.intent.screentime.data.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How the prompt has gone so far: how many were answered out of how many were raised.
 *
 * Kept as one value because the screen reads it as a single sentence ("34 of 51"), and
 * because the shared navigation host already binds this under the name `promptCount` —
 * widening what it carries keeps that call site intact rather than editing a file this
 * work does not own.
 */
data class PromptCounts(val answered: Long, val total: Long)

/**
 * The intent prompt's own settings.
 *
 * Off by default and gated on the overlay permission, because both are honest signals:
 * this is the one feature in the app that watches what you do in real time, and it should
 * take two deliberate actions to switch on.
 */
class IntentPromptViewModel(
    private val preferences: UserPreferences,
    private val repository: UsageRepository,
    private val installedApps: InstalledAppsProvider,
    private val onEnabledChanged: (Boolean) -> Unit,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = preferences.intentPromptEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val watched: StateFlow<Set<String>> = preferences.intentWatchedPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps

    /** Answered prompts — the numerator of the answer rate. */
    private val _answered = MutableStateFlow(0L)
    val answered: StateFlow<Long> = _answered

    /** Prompts raised — the denominator, answered or not. */
    private val _total = MutableStateFlow(0L)
    val total: StateFlow<Long> = _total

    /** [answered] and [total] together, under the name the navigation host binds. */
    val promptCount: StateFlow<PromptCounts> = combine(answered, total, ::PromptCounts)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PromptCounts(answered = 0L, total = 0L),
        )

    init {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { installedApps.launcherApps() }
            // Both counts come from the same table, so the rate the screen shows can never
            // disagree with the rows behind it.
            _answered.value = repository.intentAnsweredCount()
            _total.value = repository.intentLogCount()
        }
    }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            preferences.setIntentPromptEnabled(value)
            onEnabledChanged(value)
        }
    }

    fun setWatched(packageName: String, watch: Boolean) {
        viewModelScope.launch {
            val current = preferences.intentWatchedPackages.first()
            preferences.setIntentWatchedPackages(
                if (watch) current + packageName else current - packageName,
            )
        }
    }
}
