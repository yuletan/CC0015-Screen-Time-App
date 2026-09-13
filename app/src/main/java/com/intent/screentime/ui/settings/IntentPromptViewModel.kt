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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _promptCount = MutableStateFlow(0L)
    val promptCount: StateFlow<Long> = _promptCount

    init {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { installedApps.launcherApps() }
            _promptCount.value = repository.intentLogCount()
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
