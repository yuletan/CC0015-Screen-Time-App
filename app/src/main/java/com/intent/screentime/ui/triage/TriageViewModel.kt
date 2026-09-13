package com.intent.screentime.ui.triage

import androidx.lifecycle.ViewModel
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.ui.apps.AppInfoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One app waiting to be sorted, with the evidence that justifies sorting it. */
data class TriageItem(
    val packageName: String,
    val label: String,
    val totalMs: Long,
    val sessionCount: Int,
    val averageSessionMs: Long,
    val suggestion: CategoryKind?,
    val currentCategoryId: String,
)

data class TriageUiState(
    val loading: Boolean = true,
    val queue: List<TriageItem> = emptyList(),
    val index: Int = 0,
) {
    val total: Int get() = queue.size
    val current: TriageItem? get() = queue.getOrNull(index)
    val sorted: Int get() = index.coerceIn(0, total)
    val finished: Boolean get() = !loading && index >= total
}

/**
 * Drives the triage queue.
 *
 * The queue is loaded once rather than observed: an app that has just been assigned has to
 * leave the sheet, and a live query would yank rows out from under the user mid-gesture.
 */
class TriageViewModel(
    private val repository: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(TriageUiState())
    val state: StateFlow<TriageUiState> = _state.asStateFlow()

    fun assign(packageName: String, kind: CategoryKind) {
        _state.value = _state.value
    }

    fun undo() {
        _state.value = _state.value
    }
}
