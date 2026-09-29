package com.intent.screentime.data.repository

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.intent.Reasons
import com.intent.screentime.data.local.DefaultCategories
import com.intent.screentime.data.local.IntentDatabase
import com.intent.screentime.data.local.dao.UnsortedAppRow
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.CategoryEntity
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.DayNoteEntity
import com.intent.screentime.data.local.entity.DayReflection
import com.intent.screentime.data.local.entity.FocusSessionEntity
import com.intent.screentime.data.local.entity.IntentLogEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.TargetType
import com.intent.screentime.data.usage.HourlyBreakdown
import com.intent.screentime.data.usage.UsageIngestor
import kotlinx.coroutines.flow.Flow

/** A package's resolved category, as the UI needs it for chips and colouring. */
data class AppCategoryRef(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val colorHex: String,
)

/**
 * Read side of the app.
 *
 * Screens observe Flows over the pre-aggregated tables so a UI redraw never touches the
 * raw event log. Every query here is a single indexed read or a small join.
 */
class UsageRepository(
    private val database: IntentDatabase,
    private val ingestor: UsageIngestor,
) {

    fun observeDay(epochDay: Long): Flow<DailySummaryEntity?> =
        database.dailySummaryDao().observeDay(epochDay)

    fun observeDays(fromDay: Long, toDay: Long): Flow<List<DailySummaryEntity>> =
        database.dailySummaryDao().observeBetween(fromDay, toDay)

    fun observeAppUsage(epochDay: Long): Flow<List<DailyAppUsageEntity>> =
        database.dailyAppUsageDao().observeForDay(epochDay)

    fun observeTargets(): Flow<List<TargetEntity>> = database.targetDao().observeEnabled()

    /** A one-shot read of the same rows, for screens that fold targets into a chart. */
    suspend fun enabledTargets(): List<TargetEntity> = database.targetDao().enabled()

    fun observeStreak(limit: Int): Flow<List<StreakDayEntity>> =
        database.streakDayDao().observeRecent(limit)

    fun observeFocusSince(fromMs: Long): Flow<List<FocusSessionEntity>> =
        database.focusSessionDao().observeSince(fromMs)

    suspend fun categoryLookup(): Map<String, AppCategoryRef> =
        database.categoryDao().appCategoryRows().associate { row ->
            row.packageName to AppCategoryRef(
                id = row.categoryId,
                name = row.name,
                kind = row.kind,
                colorHex = row.colorHex,
            )
        }

    /** All categories, for editor pickers. */
    suspend fun allCategories() = database.categoryDao().all()

    suspend fun hourlyBuckets(epochDay: Long): List<HourlyBreakdown.Bucket> {
        val sessions = sessionsForDay(epochDay)
        val kinds = categoryLookup().mapValues { it.value.kind }
        return HourlyBreakdown.of(sessions, epochDay, kinds)
    }

    /**
     * One app's own day, hour by hour.
     *
     * Filtered in Kotlin rather than SQL: a single day is a handful of sessions, and the
     * bucketing already has to happen here because a session can straddle several hours.
     */
    suspend fun appHourlyBuckets(
        packageName: String,
        epochDay: Long,
    ): List<HourlyBreakdown.Bucket> = HourlyBreakdown.of(
        sessions = sessionsForDay(epochDay).filter { it.packageName == packageName },
        epochDay = epochDay,
    )

    /**
     * Day summaries over an arbitrary window, for averages that must not follow the
     * range the user is currently looking at.
     */
    suspend fun summariesBetween(fromDay: Long, toDay: Long): List<DailySummaryEntity> =
        database.dailySummaryDao().between(fromDay, toDay)

    suspend fun sessionsForDay(epochDay: Long) = database.appSessionDao().sessionsOverlapping(
        DayWindow.startOfDayMs(epochDay),
        DayWindow.endOfDayMs(epochDay),
    )

    /**
     * The same 24-hour shape as [hourlyBuckets], summed over a window.
     *
     * One read for the whole range: a window is at most ninety days of sessions, and the
     * bucketing already has to happen here because sessions straddle hours and days.
     */
    suspend fun hourlyBucketsForRange(fromDay: Long, toDay: Long): List<HourlyBreakdown.Bucket> {
        val sessions = sessionsBetween(
            DayWindow.startOfDayMs(fromDay),
            DayWindow.endOfDayMs(toDay),
        )
        val kinds = categoryLookup().mapValues { it.value.kind }
        return HourlyBreakdown.ofRange(sessions, fromDay, toDay, kinds)
    }

    /** The first day ever tracked, for bounding how far a window can step back. */
    suspend fun earliestTrackedDay(): Long? = database.dailySummaryDao().earliestDay()

    /** The first day an app was ever used, for the same bound on its own screen. */
    suspend fun earliestDayFor(packageName: String): Long? =
        database.dailyAppUsageDao().earliestDayFor(packageName)

    suspend fun recentSummaries(limit: Int): List<DailySummaryEntity> =
        database.dailySummaryDao().recent(limit)

    suspend fun daySummary(epochDay: Long): DailySummaryEntity? =
        database.dailySummaryDao().getDay(epochDay)

    /**
     * Totals per category over a date range, for the donut.
     *
     * Grouped in Kotlin rather than SQL because the package-to-category mapping is a
     * plain in-memory lookup we already have, and the range holds at most a few thousand
     * rows.
     */
    suspend fun categoryTotals(
        fromDay: Long,
        toDay: Long,
    ): List<Pair<AppCategoryRef, Long>> {
        val lookup = categoryLookup()
        val totals = HashMap<String, Long>()
        val refs = HashMap<String, AppCategoryRef>()

        for (row in database.dailyAppUsageDao().between(fromDay, toDay)) {
            val ref = lookup[row.packageName] ?: continue
            totals[ref.id] = (totals[ref.id] ?: 0L) + row.totalMs
            refs[ref.id] = ref
        }

        return totals.entries
            .mapNotNull { (id, total) -> refs[id]?.let { it to total } }
            .sortedByDescending { it.second }
    }

    // --- targets -----------------------------------------------------------

    suspend fun recentStreakRows(limit: Int): List<StreakDayEntity> =
        database.streakDayDao().recent(limit)

    /**
     * The day verdicts inside a window, for screens that judge a range rather than a day.
     *
     * Verdicts are only kept for the recent past — the nightly pass re-judges sixty days —
     * so a window older than that comes back empty rather than wrong.
     */
    suspend fun streakRowsBetween(fromDay: Long, toDay: Long): List<StreakDayEntity> =
        database.streakDayDao().between(fromDay, toDay)

    /**
     * Creates, moves or clears a target. Passing null minutes removes it.
     *
     * One entry point for all five target types rather than five near-identical setters,
     * because the UI treats them identically: a value or nothing.
     */
    suspend fun setTarget(type: TargetType, minutes: Int?, scopePackage: String? = null) {
        val dao = database.targetDao()
        val existing = dao.enabled().firstOrNull {
            it.type == type && it.scopePackage == scopePackage
        }

        when {
            minutes == null && existing != null -> dao.delete(existing)
            minutes == null -> Unit
            existing != null -> dao.upsert(existing.copy(valueMinutes = minutes, enabled = true))
            else -> dao.insert(
                TargetEntity(type = type, scopePackage = scopePackage, valueMinutes = minutes),
            )
        }
    }

    suspend fun setDailyCapMinutes(minutes: Int?) =
        setTarget(TargetType.DAILY_SCREEN_TIME_CAP, minutes)

    suspend fun setPerAppCap(packageName: String, minutes: Int?) =
        setTarget(TargetType.PER_APP_DAILY_CAP, minutes, scopePackage = packageName)

    /**
     * The bedtime window. A pair of times rather than a duration, which is why it does not
     * fit [setTarget]'s one-value shape.
     *
     * Both ends or neither: a window with one end is not a commitment, so a half-set value
     * clears the row rather than storing something the evaluator would have to guess at.
     */
    suspend fun setBedtime(startMinutesOfDay: Int?, endMinutesOfDay: Int?) {
        val dao = database.targetDao()
        val existing = dao.enabled().firstOrNull { it.type == TargetType.BEDTIME_WINDOW }

        if (startMinutesOfDay == null || endMinutesOfDay == null) {
            if (existing != null) dao.delete(existing)
            return
        }

        val row = TargetEntity(
            type = TargetType.BEDTIME_WINDOW,
            // Unused on this row type: a bedtime is a window, not an amount.
            valueMinutes = 0,
            startMinutesOfDay = startMinutesOfDay,
            endMinutesOfDay = endMinutesOfDay,
        )
        if (existing != null) dao.upsert(row.copy(id = existing.id)) else dao.insert(row)
    }

    // --- per-app history ---------------------------------------------------

    suspend fun appUsageBetween(fromDay: Long, toDay: Long): List<DailyAppUsageEntity> =
        database.dailyAppUsageDao().between(fromDay, toDay)

    fun observeAppUsageFor(
        packageName: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<DailyAppUsageEntity>> =
        database.dailyAppUsageDao().observeForPackageBetween(packageName, fromDay, toDay)

    // --- categories --------------------------------------------------------

    fun observeCategories(): Flow<List<CategoryEntity>> = database.categoryDao().observeAll()

    suspend fun categoryAppCount(id: String): Int =
        database.categoryDao().appCountForCategory(id)

    /**
     * Renaming or re-kinding a category re-scores recent history, for the same reason
     * re-categorising a single app does: yesterday's split must not silently contradict
     * today's definition of "productive".
     */
    suspend fun updateCategory(id: String, name: String, kind: CategoryKind, colorHex: String) {
        database.categoryDao().updateCategory(id, name, kind, colorHex)
        ingestor.reaggregateFrom(DayWindow.todayEpochDay() - DEFAULT_RESCORE_DAYS)
    }

    suspend fun addCategory(category: CategoryEntity) {
        database.categoryDao().upsertCategory(category)
    }

    /** Removing a category re-homes its apps to Uncategorised rather than orphaning them. */
    suspend fun deleteCategory(id: String) {
        database.categoryDao().reassignAppCategories(id, DefaultCategories.UNCATEGORIZED)
        database.categoryDao().deleteCategory(id)
        ingestor.reaggregateFrom(DayWindow.todayEpochDay() - DEFAULT_RESCORE_DAYS)
    }

    // --- intent prompt (Phase 7, opt-in) -----------------------------------

    /**
     * One row per prompt. [option] carries both the stable key the ledger groups by and
     * the chip text it displays, so a single vocabulary serves both.
     */
    suspend fun logIntent(packageName: String, option: Reasons.Option, timestampMs: Long) {
        database.intentLogDao().insert(
            IntentLogEntity(
                packageName = packageName,
                timestampMs = timestampMs,
                intentLabel = option.label,
                reasonKey = option.key,
                skipped = false,
            ),
        )
    }

    /**
     * A prompt the user let close unanswered.
     *
     * Recorded rather than discarded: "you answered 34 of 51" is a finding about the habit,
     * and it is only computable if the denominator is stored. The label is empty because
     * there was no answer to label.
     */
    suspend fun logSkippedIntent(packageName: String, timestampMs: Long) {
        database.intentLogDao().insert(
            IntentLogEntity(
                packageName = packageName,
                timestampMs = timestampMs,
                intentLabel = "",
                reasonKey = null,
                skipped = true,
            ),
        )
    }

    suspend fun intentLogsBetween(fromMs: Long, toMs: Long): List<IntentLogEntity> =
        database.intentLogDao().between(fromMs, toMs)

    suspend fun intentLogCount(): Long = database.intentLogDao().count()

    suspend fun intentAnsweredCount(): Long = database.intentLogDao().answeredCount()

    /** The sessions an intent's stated purpose can be judged against. */
    suspend fun sessionsBetween(fromMs: Long, toMs: Long): List<AppSessionEntity> =
        database.appSessionDao().sessionsOverlapping(fromMs, toMs)

    // --- day cards ---------------------------------------------------------

    suspend fun appUsageForDay(epochDay: Long): List<DailyAppUsageEntity> =
        database.dailyAppUsageDao().forDay(epochDay)

    fun observeDayNote(epochDay: Long): Flow<DayNoteEntity?> =
        database.dayNoteDao().observe(epochDay)

    suspend fun dayNote(epochDay: Long): DayNoteEntity? = database.dayNoteDao().get(epochDay)

    /** Notes + reflections for every day in a range, for period exports. */
    suspend fun dayNotesBetween(fromDay: Long, toDay: Long) =
        database.dayNoteDao().between(fromDay, toDay)

    /**
     * Both day-note setters read before they write. A blind upsert of a fresh entity would
     * null whichever of the two fields the caller was not setting.
     */
    suspend fun setDayNote(epochDay: Long, note: String?) {
        val existing = database.dayNoteDao().get(epochDay)
        database.dayNoteDao().upsert(
            DayNoteEntity(
                dayEpochDay = epochDay,
                note = note?.takeIf { it.isNotBlank() },
                reflection = existing?.reflection,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setDayReflection(epochDay: Long, reflection: DayReflection?) {
        val existing = database.dayNoteDao().get(epochDay)
        database.dayNoteDao().upsert(
            DayNoteEntity(
                dayEpochDay = epochDay,
                note = existing?.note,
                reflection = reflection?.key,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    // --- triage ------------------------------------------------------------

    /**
     * Every app still unsorted, ranked by usage over the window.
     *
     * Floored at [MIN_QUEUE_TOTAL_MS] because a queue that includes every system component
     * that ever accumulated a few seconds is not a queue, it is the same backlog the
     * feature was built to clear.
     */
    suspend fun unsortedQueue(
        fromDay: Long = DayWindow.todayEpochDay() - TRIAGE_LOOKBACK_DAYS,
        minTotalMs: Long = MIN_QUEUE_TOTAL_MS,
    ): List<UnsortedAppRow> = database.categoryDao().unsortedWithUsage(
        categoryId = DefaultCategories.UNCATEGORIZED,
        fromDay = fromDay,
        minTotalMs = minTotalMs,
    )

    /** Re-harvests from the OS. Called when a screen opens, so the day is never stale. */
    suspend fun refresh() {
        ingestor.ingest()
    }

    /** Rebuilds stored history. Used after a category change so past scores follow. */
    suspend fun recomputeFrom(epochDay: Long) {
        ingestor.recomputeFrom(epochDay)
    }

    /**
     * Records a deliberate category choice for an app and re-scores recent history.
     *
     * The recompute matters: changing an app from consumption to production should move
     * yesterday's numbers too, otherwise the score only ever reflects the future and the
     * user cannot trust the weeks they already have.
     */
    suspend fun setAppCategory(
        packageName: String,
        categoryId: String,
        rescoreDays: Long = DEFAULT_RESCORE_DAYS,
    ) {
        database.categoryDao().setUserCategory(packageName, categoryId)
        ingestor.reaggregateFrom(DayWindow.todayEpochDay() - rescoreDays)
    }

    private companion object {
        const val DEFAULT_RESCORE_DAYS = 30L

        /** How far back the triage queue looks to rank apps by how much they matter. */
        const val TRIAGE_LOOKBACK_DAYS = 30L

        /** Below a minute across the window, an app cannot move the split either way. */
        const val MIN_QUEUE_TOTAL_MS = 60_000L
    }
}
