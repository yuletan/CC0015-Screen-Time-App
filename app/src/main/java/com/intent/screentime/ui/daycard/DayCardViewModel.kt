package com.intent.screentime.ui.daycard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.DayNoteEntity
import com.intent.screentime.data.local.entity.DayReflection
import com.intent.screentime.data.goals.GoalTargets
import com.intent.screentime.data.goals.ScoreWindow
import com.intent.screentime.data.local.entity.IntentLogEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.repository.AppCategoryRef
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.data.stats.UsageSplit
import com.intent.screentime.data.usage.BedtimeWindow
import com.intent.screentime.data.usage.HourlyBreakdown
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

/** One app's share of the day, with the kind of time it took resolved for the row. */
data class DayAppRow(
    val packageName: String,
    val totalMs: Long,
    val sessionCount: Int,
    val category: AppCategoryRef? = null,
)

data class DayCardUiState(
    val loading: Boolean = true,
    val epochDay: Long = 0L,
    val screenTimeMs: Long = 0L,
    /** The day's time by kind: Producing, Consuming, Utility, Neutral and Unsorted. */
    val split: UsageSplit = UsageSplit(),
    /**
     * The day's Intent Score, computed from the day's own numbers by the same code the
     * Today screen and the Insights window use, so all three agree on what a day scored.
     */
    val score: ScoreWindow.Result? = null,
    /**
     * The cap a single day was judged against — a daily cap, or a seventh of a weekly one.
     * Carried so the score's cap row can say what it was measured against.
     */
    val capMinutes: Int? = null,
    val focusMs: Long = 0L,
    /** How much of [focusMs] the stretch detector found rather than the timer. */
    val autoFocusMs: Long = 0L,
    val unlockCount: Int = 0,
    val topApps: List<DayAppRow> = emptyList(),
    val appCount: Int = 0,
    val buckets: List<HourlyBreakdown.Bucket> = emptyList(),
    val verdict: StreakDayEntity? = null,
    val intents: List<IntentLogEntity> = emptyList(),
    val note: String = "",
    val reflection: DayReflection? = null,
    /**
     * The bedtime window as it stands today, or null if none is set. Carried so the card
     * can describe the night without pretending every historical day had one.
     */
    val bedtimeWindow: BedtimeWindow? = null,
) {
    val hasData: Boolean get() = screenTimeMs > 0L

    val productionShare: Float get() = split.productionShare

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

        val split = UsageSplit.from(appUsage, categories)

        val topApps = appUsage.take(TOP_APP_LIMIT).map { row ->
            DayAppRow(
                packageName = row.packageName,
                totalMs = row.totalMs,
                sessionCount = row.sessionCount,
                category = categories[row.packageName],
            )
        }
        appInfo.preload(topApps.map { it.packageName })

        // One read of the day verdicts either side of this day: the row for the day itself,
        // and the rows the streak it was on was counted from.
        val targets = GoalTargets.from(repository.enabledTargets())
        val streakRows = repository.streakRowsBetween(epochDay - STREAK_LOOKBACK, epochDay)

        return DayCardUiState(
            loading = false,
            epochDay = epochDay,
            screenTimeMs = summary?.screenTimeMs ?: 0L,
            split = split,
            score = ScoreWindow.of(
                ScoreWindow.inputs(
                    summaries = listOfNotNull(summary),
                    targets = targets,
                    streakRows = streakRows,
                    todayEpochDay = DayWindow.todayEpochDay(),
                ),
            ),
            capMinutes = targets.dailyCapMinutes
                ?: targets.weeklyCapMinutes?.let { it / 7 },
            focusMs = summary?.focusMs ?: 0L,
            autoFocusMs = summary?.autoFocusMs ?: 0L,
            unlockCount = summary?.unlockCount ?: 0,
            topApps = topApps,
            appCount = appUsage.size,
            buckets = repository.hourlyBuckets(epochDay),
            verdict = streakRows.firstOrNull { it.dayEpochDay == epochDay },
            intents = repository.intentLogsBetween(
                DayWindow.startOfDayMs(epochDay),
                DayWindow.endOfDayMs(epochDay),
            ),
            note = note?.note.orEmpty(),
            reflection = DayReflection.fromKey(note?.reflection),
            bedtimeWindow = targets.bedtime,
        )
    }

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
        /** The card shows the same five apps the Today screen does. */
        const val TOP_APP_LIMIT = 5

        const val NOTE_DEBOUNCE_MS = 400L

        /** Comfortably longer than the twelve weeks the heatmap can point at. */
        const val STREAK_LOOKBACK = 120L
    }
}
