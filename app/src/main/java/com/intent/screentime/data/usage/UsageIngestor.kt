package com.intent.screentime.data.usage

import android.util.Log
import androidx.room.withTransaction
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.category.CategoryClassifier
import com.intent.screentime.data.local.IntentDatabase
import com.intent.screentime.data.local.entity.AppCategoryEntity
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.UsageEventEntity
import com.intent.screentime.data.prefs.UserPreferences

data class IngestResult(
    val eventsInserted: Int,
    val sessionsBuilt: Int,
    val daysAggregated: Int,
    val skippedReason: String? = null,
) {
    val succeeded: Boolean get() = skippedReason == null
}

/**
 * Harvests usage events into the durable database and rebuilds the derived tables.
 *
 * The whole design rests on one fact: Android only keeps raw usage events for a few
 * days. Every run therefore copies events down permanently, and all long-range history
 * is served from our own tables afterwards.
 *
 * Cost per run is deliberately small. A steady-state run reads roughly the last
 * 15 minutes of events (delta from a [UserPreferences] watermark) and re-aggregates
 * one or two days, rather than re-scanning history.
 */
class UsageIngestor(
    private val dataSource: UsageStatsDataSource,
    private val database: IntentDatabase,
    private val preferences: UserPreferences,
    private val classifier: CategoryClassifier,
    private val excludedPackages: Set<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private class RebuildOutcome(val eventsInserted: Int, val sessions: List<AppSessionEntity>)

    suspend fun ingest(): IngestResult {
        val now = clock()
        val watermark = preferences.currentWatermark()

        // Steady state: resume from just before the watermark. First run: backfill as
        // far as the OS will let us.
        val queryStart = if (watermark <= 0L) {
            now - INITIAL_BACKFILL_MS
        } else {
            watermark - OVERLAP_MS
        }.coerceAtLeast(now - MAX_LOOKBACK_MS).coerceAtLeast(0L)

        Log.d(TAG, "ingest start: watermark=$watermark queryStart=$queryStart now=$now")

        val raw = dataSource.eventsBetween(queryStart, now)
        if (raw == null) {
            Log.w(TAG, "queryEvents returned null (device locked, or access revoked)")
            return IngestResult(
                eventsInserted = 0,
                sessionsBuilt = 0,
                daysAggregated = 0,
                skippedReason = "Usage events unavailable — device locked or usage access revoked",
            )
        }

        Log.d(
            TAG,
            "queryEvents returned ${raw.size} events; " +
                "types=${raw.groupingBy { it.eventType }.eachCount()}",
        )

        // Sessions are rebuilt over a window that is the *wider* of the ingest delta and a
        // few days of history, read back from our own durable event log.
        //
        // Taking the minimum satisfies two different requirements at once:
        //  - reaching back at least SESSION_REBUILD_WINDOW means a session that stays in
        //    the foreground across several harvests keeps its original RESUMED event,
        //    instead of being chopped into fragments
        //  - never starting later than the ingest delta means events pulled in by a
        //    long-gap or first-run backfill actually get turned into sessions and
        //    aggregates, rather than sitting in storage unused
        val rebuildFrom = sessionRebuildStart(queryStart, now)

        val outcome = database.withTransaction {
            val inserted = database.usageEventDao()
                .insertAll(raw.map { it.toEntity() })
                .count { it != -1L }

            val events = database.usageEventDao().eventsBetween(rebuildFrom, now)
            val sessions = SessionBuilder.build(
                events = events,
                windowStartMs = rebuildFrom,
                nowMs = now,
                excludedPackages = excludedPackages,
            )

            database.appSessionDao().deleteFrom(rebuildFrom)
            database.appSessionDao().insertAll(sessions)

            RebuildOutcome(inserted, sessions)
        }

        Log.d(
            TAG,
            "inserted=${outcome.eventsInserted} of ${raw.size} sessions=${outcome.sessions.size}",
        )

        val daysAggregated = aggregateDays(outcome.sessions, now)
        Log.d(TAG, "daysAggregated=$daysAggregated")

        // Advance only once the data is committed, and never past a window we received
        // nothing from. Advancing on an empty result would silently skip that span
        // forever — including the entire first-run backfill, which would leave the app
        // permanently with no history.
        if (raw.isNotEmpty()) {
            preferences.setIngestWatermarkMs(now)
        }

        return IngestResult(
            eventsInserted = outcome.eventsInserted,
            sessionsBuilt = outcome.sessions.size,
            daysAggregated = daysAggregated,
        )
    }

    /**
     * Rebuilds sessions and daily aggregates for everything from [fromDayEpoch] to now,
     * using events already stored locally rather than re-reading the OS.
     *
     * This is what makes history self-healing. It is needed to fill in days that were
     * backfilled before they could be aggregated, and — from Phase 3 onwards — to
     * recompute past production/consumption splits after the user re-categorises an app.
     */
    suspend fun recomputeFrom(fromDayEpoch: Long, now: Long = clock()): Int {
        val rebuildFrom = DayWindow.startOfDayMs(fromDayEpoch)

        val sessions = database.withTransaction {
            val events = database.usageEventDao().eventsBetween(rebuildFrom, now)
            val built = SessionBuilder.build(
                events = events,
                windowStartMs = rebuildFrom,
                nowMs = now,
                excludedPackages = excludedPackages,
            )
            database.appSessionDao().deleteFrom(rebuildFrom)
            database.appSessionDao().insertAll(built)
            built
        }

        // Clear stale aggregates across the whole range first, so a day that now has no
        // sessions does not keep an out-of-date row behind.
        val toDay = DayWindow.epochDayOf(now)
        database.dailyAppUsageDao().deleteRange(fromDayEpoch, toDay)
        database.dailySummaryDao().deleteRange(fromDayEpoch, toDay)

        return aggregateDays(sessions, now)
    }

    /**
     * Re-runs only the aggregation for [fromDayEpoch] onwards, reusing the sessions
     * already stored.
     *
     * This is the cheap path, and the one used after a category change: the sessions
     * themselves are still valid, only the production/consumption split has moved, so
     * rebuilding them would be wasted work.
     */
    suspend fun reaggregateFrom(fromDayEpoch: Long, now: Long = clock()): Int {
        val toDay = DayWindow.epochDayOf(now)
        database.dailyAppUsageDao().deleteRange(fromDayEpoch, toDay)
        database.dailySummaryDao().deleteRange(fromDayEpoch, toDay)

        val sessions = database.appSessionDao().sessionsOverlapping(
            DayWindow.startOfDayMs(fromDayEpoch),
            now,
        )
        return aggregateDays(sessions, now)
    }

    /**
     * Re-asks the classifier about every row it — not the user — filled in, and re-scores
     * recent history when anything moved.
     *
     * [resolveCategories] never re-examines a stored row, which is what keeps harvests
     * cheap: an app is classified once, ever. That also means an improved mapping would
     * otherwise only reach apps the user had never opened, so the app calls this once per
     * classifier version bump. Rows the user set are left exactly as they are.
     *
     * Returns how many packages changed category.
     */
    suspend fun reclassifyAutoCategories(): Int {
        val auto = database.categoryDao().allAppCategories().filterNot { it.isUserOverride }
        val moved = auto.mapNotNull { row ->
            val fresh = classifier.categoryIdFor(row.packageName)
            if (fresh == row.categoryId) null else row.copy(categoryId = fresh)
        }
        if (moved.isEmpty()) return 0

        database.categoryDao().upsertAppCategories(moved)
        reaggregateFrom(DayWindow.todayEpochDay() - RECLASSIFY_RESCORE_DAYS)
        return moved.size
    }

    /**
     * Recomputes the daily aggregate rows for every day touched by [sessions].
     *
     * The per-app totals are rebuilt from **all** sessions overlapping each day, read
     * back from the database, rather than from [sessions] alone. This matters a great
     * deal: an incremental harvest only rebuilds the last few minutes of sessions, so
     * aggregating from just those would overwrite the whole day's totals with a few
     * minutes of data.
     */
    private suspend fun aggregateDays(sessions: List<AppSessionEntity>, now: Long): Int {
        val days = (sessions.map { it.dayEpochDay } + DayWindow.epochDayOf(now)).toSortedSet()
        if (days.isEmpty()) return 0

        // Pull the complete session set for every affected day up front. Doing this once
        // means classification sees every app involved, and each day is aggregated from
        // its full data rather than from whatever this run happened to rebuild.
        val sessionsByDay = LinkedHashMap<Long, List<AppSessionEntity>>()
        for (day in days) {
            sessionsByDay[day] = database.appSessionDao().sessionsOverlapping(
                DayWindow.startOfDayMs(day),
                DayWindow.endOfDayMs(day),
            )
        }

        val kindByPackage = resolveCategories(sessionsByDay.values.flatten())

        for ((day, overlapping) in sessionsByDay) {
            val dayStart = DayWindow.startOfDayMs(day)
            val dayEnd = DayWindow.endOfDayMs(day)

            val appUsage = DailyAggregator.appUsageForDay(
                sessions = overlapping,
                epochDay = day,
                excludedPackages = excludedPackages,
            )
            val unlockCount = dataSource.unlockCount(dayStart, dayEnd)

            // Focus is what the timer claimed *and* what the screen shows: a long
            // uninterrupted stretch in a work app counts even with no session running.
            // The two can cover the same minutes, so the day's figure is their union and
            // only the detector's extra is attributed to it.
            val manualFocus = FocusStretches.manual(
                sessions = database.focusSessionDao().overlapping(dayStart, dayEnd),
                epochDay = day,
            )
            val detectedFocus = FocusStretches.detected(
                sessions = overlapping,
                epochDay = day,
                kindByPackage = kindByPackage,
                excludedPackages = excludedPackages,
            )
            val focusMs = FocusStretches.unionMs(manualFocus + detectedFocus)
            val autoFocusMs = focusMs - FocusStretches.unionMs(manualFocus)

            val summary = DailyAggregator.summarize(
                epochDay = day,
                appUsage = appUsage,
                kindByPackage = kindByPackage,
                unlockCount = unlockCount,
                focusMs = focusMs,
                autoFocusMs = autoFocusMs,
            )

            Log.d(
                TAG,
                "aggregate day=$day from ${overlapping.size} sessions -> " +
                    "screen=${summary.screenTimeMs / 60000}m top=${summary.topPackage}",
            )

            database.withTransaction {
                database.dailyAppUsageDao().deleteDay(day)
                database.dailyAppUsageDao().upsertAll(appUsage)
                database.dailySummaryDao().upsert(summary)
            }
        }

        return days.size
    }

    /**
     * Resolves every package seen in [sessions] to a [CategoryKind], auto-classifying
     * anything new. Packages the user has explicitly categorised are never overwritten.
     */
    private suspend fun resolveCategories(
        sessions: List<AppSessionEntity>,
    ): Map<String, CategoryKind> {
        val kindById = database.categoryDao().all().associate { it.id to it.kind }

        val existing = database.categoryDao().allAppCategories().associateBy { it.packageName }
        val unknown = sessions
            .map { it.packageName }
            .distinct()
            .filterNot { it in existing }

        if (unknown.isNotEmpty()) {
            database.categoryDao().upsertAppCategories(
                unknown.map { packageName ->
                    AppCategoryEntity(
                        packageName = packageName,
                        categoryId = classifier.categoryIdFor(packageName),
                        isUserOverride = false,
                    )
                },
            )
        }

        val packageToCategoryId = if (unknown.isEmpty()) {
            existing.mapValues { (_, row) -> row.categoryId }
        } else {
            database.categoryDao().allAppCategories().associate { it.packageName to it.categoryId }
        }

        return packageToCategoryId.mapValues { (_, categoryId) ->
            kindById[categoryId] ?: CategoryKind.NEUTRAL
        }
    }

    private fun UsageStatsDataSource.RawEvent.toEntity() = UsageEventEntity(
        packageName = packageName,
        eventType = eventType,
        timestampMs = timestampMs,
        className = className,
    )

    companion object {
        private const val TAG = "IntentIngest"
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        /** Deliberate overlap so a boundary event is never lost between runs. */
        private const val OVERLAP_MS = 10 * MINUTE

        /** First-run backfill. The OS keeps far less than this, so it self-limits. */
        private const val INITIAL_BACKFILL_MS = 7 * DAY

        /** Absolute ceiling on how far back we ever ask the OS for events. */
        private const val MAX_LOOKBACK_MS = 7 * DAY

        /** How far back sessions are rebuilt from our own event log each run. */
        private const val SESSION_REBUILD_WINDOW_MS = 3 * DAY

        /** How far back a re-classification re-scores, matching a manual re-sort's window. */
        private const val RECLASSIFY_RESCORE_DAYS = 30L

        /**
         * Earliest timestamp sessions must be rebuilt from.
         *
         * Deliberately the **minimum** of the ingest delta and the rebuild window.
         * Using the maximum here was a real bug: on a first run the backfill reaches
         * back 7 days while the rebuild window is only 3, so the oldest days were
         * ingested into storage but never turned into sessions or aggregates.
         */
        internal fun sessionRebuildStart(queryStartMs: Long, nowMs: Long): Long =
            minOf(queryStartMs, nowMs - SESSION_REBUILD_WINDOW_MS).coerceAtLeast(0L)
    }
}
