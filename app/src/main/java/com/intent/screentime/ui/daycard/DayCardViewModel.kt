package com.intent.screentime.ui.daycard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.DayNoteEntity
import com.intent.screentime.data.local.entity.DayReflection
import com.intent.screentime.data.local.entity.IntentLogEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.ui.apps.AppInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** Prompts the user actually answered, as opposed to ones they let close. */
    val promptsAnswered: Int get() = intents.count { !it.skipped }
}

/**
 * Assembles one day's card.
 *
 * Everything here is a read of data that already exists — the day's summary, its app
 * breakdown, the verdict the nightly pass recorded, and the prompts answered that day.
 * The only thing this screen owns is the note and the reflection.
 */
class DayCardViewModel(
    private val epochDay: Long,
    private val repository: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    /**
     * Bumped whenever a write lands, because two of the reads below — the streak row and
     * the intent log — are not observed tables. The combined summary and app usage flows
     * refresh themselves; these need a nudge, exactly as [com.intent.screentime.ui.apps.AppsViewModel]
     * nudges its own recomputation after an edit.
     */
    private val dataVersion = MutableStateFlow(0)

    /**
     * The note as typed, held apart from the assembled card.
     *
     * Null means nobody has touched it, so the stored note shows. Once it is non-null the
     * typed text wins over the database: assembling the card re-reads the day, and if the
     * field were owned by that pass it would snap back to the last committed value while
     * the user was still typing. Keeping the draft in its own flow also means a keystroke
     * never re-runs the day's queries.
     */
    private val typedNote = MutableStateFlow<String?>(null)

    /**
     * The same idea for the reflection, with one wrinkle: null is a real answer ("no"),
     * so a wrapper is needed to tell "cleared" apart from "never touched".
     */
    private val chosenReflection = MutableStateFlow<ReflectionChoice?>(null)

    /** Serialises the two read-modify-writes over the single day_note row. */
    private val writeLock = Mutex()

    /**
     * A scope for the user's own words that outlives a visit to the card.
     *
     * Leaving the screen clears the ViewModel and cancels [viewModelScope], which would drop
     * a note typed in the last second before the back press. This scope is referenced from
     * here and nowhere else, so it becomes garbage along with the ViewModel once its few
     * short jobs have drained — a durability device, not a leak.
     */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var noteJob: Job? = null

    private val card: Flow<DayCardUiState> = combine(
        repository.observeDay(epochDay),
        repository.observeAppUsage(epochDay),
        repository.observeDayNote(epochDay),
        dataVersion,
    ) { summary, appUsage, note, _ -> Triple(summary, appUsage, note) }
        .map { (summary, appUsage, note) -> assemble(summary, appUsage, note) }

    val state: StateFlow<DayCardUiState> = combine(
        card,
        typedNote,
        chosenReflection,
    ) { assembled, note, reflection ->
        assembled.copy(
            note = note ?: assembled.note,
            reflection = if (reflection != null) reflection.value else assembled.reflection,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DayCardUiState(epochDay = epochDay),
    )

    private suspend fun assemble(
        summary: DailySummaryEntity?,
        appUsage: List<DailyAppUsageEntity>,
        note: DayNoteEntity?,
    ): DayCardUiState {
        val categories = repository.categoryLookup()

        // The same fold the Today board uses, so a day's four-way split never disagrees
        // with itself depending on which screen is asking.
        var production = 0L
        var consumption = 0L
        var utility = 0L
        var neutral = 0L
        for (row in appUsage) {
            when (categories[row.packageName]?.kind) {
                CategoryKind.PRODUCTION -> production += row.totalMs
                CategoryKind.CONSUMPTION -> consumption += row.totalMs
                CategoryKind.UTILITY -> utility += row.totalMs
                CategoryKind.NEUTRAL, null -> neutral += row.totalMs
            }
        }

        val topApps = appUsage.take(TOP_APP_LIMIT).map { row ->
            DayAppRow(
                packageName = row.packageName,
                totalMs = row.totalMs,
                sessionCount = row.sessionCount,
            )
        }
        appInfo.preload(topApps.map { it.packageName })

        return DayCardUiState(
            loading = false,
            epochDay = epochDay,
            screenTimeMs = summary?.screenTimeMs ?: 0L,
            productionMs = production,
            consumptionMs = consumption,
            utilityMs = utility,
            neutralMs = neutral,
            focusMs = summary?.focusMs ?: 0L,
            unlockCount = summary?.unlockCount ?: 0,
            topApps = topApps,
            appCount = appUsage.size,
            verdict = verdictFor(),
            intents = repository.intentLogsBetween(
                DayWindow.startOfDayMs(epochDay),
                DayWindow.endOfDayMs(epochDay),
            ),
            note = note?.note.orEmpty(),
            reflection = DayReflection.fromKey(note?.reflection),
        )
    }

    /**
     * The verdict the nightly pass recorded for this day.
     *
     * Read out of the recent rows rather than a dedicated by-day query: the heatmap only
     * ever opens the last twelve weeks, so a lookback of [STREAK_LOOKBACK] always contains
     * the day, and it keeps this screen on the repository's existing read surface.
     */
    private suspend fun verdictFor(): StreakDayEntity? =
        repository.recentStreakRows(STREAK_LOOKBACK).firstOrNull { it.dayEpochDay == epochDay }

    /**
     * Records what the user is typing, immediately and then lazily.
     *
     * The text lands in [typedNote] on every keystroke, so the field never lags. Persisting
     * waits a moment: one database round trip per letter would be pointless work. The wait
     * is skipped when the incoming text is unchanged, which is exactly what happens when
     * the field loses focus and re-sends its own value — and because the screen can only
     * reach this one entry point, that repetition is the commit signal, read for free
     * rather than plumbed through a second callback the nav host does not pass.
     */
    fun setNote(note: String) {
        val unchanged = note == currentNote()
        typedNote.value = note
        noteJob?.cancel()
        noteJob = writeScope.launch {
            if (!unchanged) delay(NOTE_DEBOUNCE_MS)
            persistNote(note)
        }
    }

    fun setReflection(reflection: DayReflection?) {
        chosenReflection.value = ReflectionChoice(reflection)
        writeScope.launch {
            persistReflection(reflection)
        }
    }

    private fun currentNote(): String = typedNote.value ?: state.value.note

    private suspend fun persistNote(note: String) {
        writeLock.withLock { repository.setDayNote(epochDay, note) }
        dataVersion.value += 1
    }

    private suspend fun persistReflection(reflection: DayReflection?) {
        writeLock.withLock { repository.setDayReflection(epochDay, reflection) }
        dataVersion.value += 1
    }

    /** A reflection the user just chose, which may not have reached the database yet. */
    private class ReflectionChoice(val value: DayReflection?)

    private companion object {
        const val TOP_APP_LIMIT = 3
        const val NOTE_DEBOUNCE_MS = 400L

        /** Comfortably longer than the twelve weeks the heatmap can point at. */
        const val STREAK_LOOKBACK = 120
    }
}
