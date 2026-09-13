package com.intent.screentime.data.repository

import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.DefaultCategories
import com.intent.screentime.data.local.IntentDatabase
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.CategoryEntity
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
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

    suspend fun sessionsForDay(epochDay: Long) = database.appSessionDao().sessionsOverlapping(
        DayWindow.startOfDayMs(epochDay),
        DayWindow.endOfDayMs(epochDay),
    )

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

    suspend fun logIntent(packageName: String, label: String, timestampMs: Long) {
        database.intentLogDao().insert(
            IntentLogEntity(
                packageName = packageName,
                timestampMs = timestampMs,
                intentLabel = label,
            ),
        )
    }

    suspend fun intentLogsBetween(fromMs: Long, toMs: Long): List<IntentLogEntity> =
        database.intentLogDao().between(fromMs, toMs)

    suspend fun intentLogCount(): Long = database.intentLogDao().count()

    /** The sessions an intent's stated purpose can be judged against. */
    suspend fun sessionsBetween(fromMs: Long, toMs: Long): List<AppSessionEntity> =
        database.appSessionDao().sessionsOverlapping(fromMs, toMs)

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
    }
}
