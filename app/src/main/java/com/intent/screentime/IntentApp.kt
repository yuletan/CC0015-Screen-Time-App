package com.intent.screentime

import android.app.Application
import android.provider.Settings
import android.util.Log
import com.intent.screentime.core.di.AppContainer
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.category.AppCategoryMapping
import com.intent.screentime.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IntentApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Only used to read preferences once at startup; nothing long-running lives here. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannels()

        appScope.launch {
            // Safe to call on every start: the harvest and rollup use KEEP and never
            // duplicate, while the digest uses UPDATE so a changed time takes effect.
            WorkScheduler.scheduleAll(this@IntentApp, container.preferences)

            backfillClassifierIfNeeded()
            backfillFocusIfNeeded()
            backfillBedtimeIfNeeded()

            // The intent prompt survives a restart only if the user left it on *and*
            // still holds the overlay permission — losing either should leave the app
            // as quiet as it was before the feature existed.
            val promptEnabled = container.preferences.intentPromptEnabled.first()
            if (promptEnabled && Settings.canDrawOverlays(this@IntentApp)) {
                container.startIntentWatch()
            }
        }
    }

    /**
     * Re-classifies stored apps once after the automatic classifier changes.
     *
     * A package is never re-asked once it has a row, so a mapping upgrade needs an
     * explicit push: rows the user did not set are refreshed, and recent history is
     * re-scored so a day spent writing in Docs stops reading as unsorted time. Runs before
     * the focus backfill, which depends on those categories to detect stretches.
     */
    private suspend fun backfillClassifierIfNeeded() {
        val stored = container.preferences.classifierVersion.first()
        if (stored >= AppCategoryMapping.CLASSIFIER_VERSION) return

        val applied = runCatching {
            container.usageIngestor.reclassifyAutoCategories()
        }.onFailure {
            Log.w(TAG, "Category re-classification failed; will retry on the next launch", it)
        }.isSuccess

        if (applied) {
            container.preferences.setClassifierVersion(AppCategoryMapping.CLASSIFIER_VERSION)
        }
    }

    /**
     * Rebuilds stored history once after focus gained stretch detection.
     *
     * Days rolled up before that upgrade hold the timer-only figure, so without this the
     * day cards and the trends would keep showing a focus number the app no longer
     * produces. The sessions are already stored, so this is arithmetic, not a re-harvest.
     */
    private suspend fun backfillFocusIfNeeded() {
        if (container.preferences.focusBackfillDone.first()) return

        val rebuilt = runCatching {
            container.database.dailySummaryDao().earliestDay()?.let { earliest ->
                container.usageIngestor.reaggregateFrom(earliest)
            }
        }.onFailure {
            Log.w(TAG, "Focus history rebuild failed; will retry on the next launch", it)
        }.isSuccess

        if (rebuilt) container.preferences.setFocusBackfillDone(true)
    }

    /**
     * Re-judges recent history once after bedtime joined the commitments.
     *
     * The migration that added `metBedtime` writes it as false for every existing row,
     * which reads as a night someone broke when in fact nobody ever judged it. The
     * sessions are already stored, so one pass replaces that default with a real verdict.
     */
    private suspend fun backfillBedtimeIfNeeded() {
        if (container.preferences.bedtimeBackfillDone.first()) return

        val today = DayWindow.todayEpochDay()
        val judged = runCatching {
            container.goalTracker.evaluateStreaks(
                fromDay = today - STREAK_WINDOW_DAYS,
                toDay = today,
            )
        }.onFailure {
            Log.w(TAG, "Bedtime history rebuild failed; will retry on the next launch", it)
        }.isSuccess

        if (judged) container.preferences.setBedtimeBackfillDone(true)
    }

    private companion object {
        const val TAG = "IntentApp"

        /** Kept in step with GoalTracker.STREAK_WINDOW_DAYS. */
        const val STREAK_WINDOW_DAYS = 60L
    }
}
