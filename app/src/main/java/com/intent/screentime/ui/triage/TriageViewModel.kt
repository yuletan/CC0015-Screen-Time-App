package com.intent.screentime.ui.triage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.data.local.DefaultCategories
import com.intent.screentime.data.local.entity.CategoryEntity
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.ui.apps.AppInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One app waiting to be sorted, with the evidence that justifies sorting it. */
data class TriageItem(
    val packageName: String,
    val label: String,
    val totalMs: Long,
    val sessionCount: Int,
    val averageSessionMs: Long,
    val currentCategoryId: String,
)

/**
 * The last app the user sorted, kept only so the sheet can offer a single undo.
 *
 * [token] exists purely so the screen's snackbar effect re-runs even when the same app is
 * sorted twice in a row — without it a second, identical assignment would look exactly like
 * the first and the confirmation would never appear again.
 */
data class TriageSort(
    val packageName: String,
    val label: String,
    val kind: CategoryKind,
    val previousCategoryId: String,
    val index: Int,
    val token: Long,
)

data class TriageUiState(
    val loading: Boolean = true,
    val queue: List<TriageItem> = emptyList(),
    val index: Int = 0,
    /** True while a write is in flight, so the sheet can disable its inputs and avoid a race. */
    val busy: Boolean = false,
    val lastSort: TriageSort? = null,
) {
    val total: Int get() = queue.size
    val current: TriageItem? get() = queue.getOrNull(index)
    val sorted: Int get() = index.coerceIn(0, total)
    val finished: Boolean get() = !loading && index >= total
    val progress: Float get() = if (total <= 0) 0f else sorted.toFloat() / total.toFloat()
}

/**
 * Drives the triage queue.
 *
 * The queue is loaded once rather than observed as a Flow: an app that has just been
 * assigned has to leave the sheet, and a live query would yank rows out from under the user
 * mid-gesture. That means the ViewModel — not the database — owns which app is on screen,
 * and so it also owns keeping that pointer honest.
 */
class TriageViewModel(
    private val repository: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(TriageUiState())
    val state: StateFlow<TriageUiState> = _state.asStateFlow()

    /** The category each kind button lands on, resolved once from the live category table. */
    private var canonicalIds: Map<CategoryKind, String> = emptyMap()

    private var sortToken: Long = 0L

    init {
        viewModelScope.launch {
            val (items, canonical) = withContext(Dispatchers.IO) {
                // Anything without a launcher intent cannot be opened, so sorting it would
                // only ever add a row the user can never act on. Better out of the queue.
                val launchable = repository.unsortedQueue()
                    .filter { appInfo.isLaunchable(it.packageName) }

                // Warm the icon cache before the sheet composes, so the first card is not a
                // monogram that swaps to a real icon a frame later.
                appInfo.preload(launchable.map { it.packageName })

                val resolved = resolveCanonicalIds(repository.allCategories())

                val queue = launchable.map { row ->
                    val average = if (row.sessionCount > 0) {
                        row.totalMs / row.sessionCount
                    } else {
                        0L
                    }
                    TriageItem(
                        packageName = row.packageName,
                        label = appInfo.label(row.packageName),
                        totalMs = row.totalMs,
                        sessionCount = row.sessionCount,
                        averageSessionMs = average,
                        // Every app in this queue is here because its category is
                        // Uncategorised, so that is what undo restores it to.
                        currentCategoryId = DefaultCategories.UNCATEGORIZED,
                    )
                }

                queue to resolved
            }

            canonicalIds = canonical
            _state.value = TriageUiState(loading = false, queue = items)
        }
    }

    /**
     * Records a side for an app and moves the queue on.
     *
     * The index advances only *after* [UsageRepository.setAppCategory] returns. Advancing it
     * optimistically would let the sheet show the next app before the write had landed, and
     * a progress line that can be wrong is worse than one that is a moment slow. The `busy`
     * flag closes the window between the tap and the write, so a fast double-tap cannot
     * describe two different saves with one row.
     */
    fun assign(packageName: String, kind: CategoryKind) {
        val snapshot = _state.value
        if (snapshot.busy) return

        val position = snapshot.queue.indexOfFirst { it.packageName == packageName }
        if (position < 0) return
        val item = snapshot.queue[position]

        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            repository.setAppCategory(packageName, canonicalId(kind))
            sortToken += 1
            _state.value = _state.value.copy(
                busy = false,
                index = position + 1,
                lastSort = TriageSort(
                    packageName = packageName,
                    label = item.label,
                    kind = kind,
                    previousCategoryId = item.currentCategoryId,
                    index = position,
                    token = sortToken,
                ),
            )
        }
    }

    /**
     * Puts the last app back where it was, and rewinds the queue to it.
     *
     * Only one step deep, by design: the undo lives in a snackbar that is itself
     * short-lived, and a deeper stack would imply a history the sheet never shows.
     */
    fun undo() {
        val snapshot = _state.value
        if (snapshot.busy) return
        val last = snapshot.lastSort ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            repository.setAppCategory(last.packageName, last.previousCategoryId)
            _state.value = _state.value.copy(
                busy = false,
                index = last.index,
                lastSort = null,
            )
        }
    }

    /**
     * The three buttons choose a *kind*, not a category, so each kind needs exactly one
     * canonical home.
     *
     * Resolution follows the declared default order rather than the name-ordered list the
     * database returns, so the pick is stable and predictable: "Consuming" always means
     * Social, not whichever consumption category sorts first alphabetically (Games). That
     * is the conscious trade — a game lands on Social although "Games" would be more
     * precise — and it is harmless because the split only ever reads `kind`. The cost is
     * only that a chip may read "Social" for something plainly a game, which the footnote
     * admits and any app's own page can refine.
     */
    private fun resolveCanonicalIds(categories: List<CategoryEntity>): Map<CategoryKind, String> =
        CategoryKind.entries.associateWith { kind ->
            DefaultCategories.ALL
                .firstOrNull { it.kind == kind && categories.any { c -> c.id == it.id } }
                ?.id
                ?: categories.firstOrNull { it.kind == kind }?.id
                ?: DefaultCategories.UNCATEGORIZED
        }

    private fun canonicalId(kind: CategoryKind): String =
        canonicalIds[kind] ?: DefaultCategories.UNCATEGORIZED
}
