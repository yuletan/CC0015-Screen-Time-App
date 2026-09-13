package com.intent.screentime.core.di

import android.content.Context
import android.content.Intent
import com.intent.screentime.data.category.CategoryClassifier
import com.intent.screentime.data.export.CsvExporter
import com.intent.screentime.data.goals.GoalTracker
import com.intent.screentime.data.local.IntentDatabase
import com.intent.screentime.data.prefs.UserPreferences
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.data.usage.UsageIngestor
import com.intent.screentime.data.usage.UsageStatsDataSource
import com.intent.screentime.focus.FocusSessionManager
import com.intent.screentime.focus.FocusSessionService
import com.intent.screentime.intent.IntentWatchService
import com.intent.screentime.notify.NotificationHelper
import com.intent.screentime.ui.apps.AppInfoProvider
import com.intent.screentime.work.WorkScheduler

/**
 * Manual dependency graph.
 *
 * Hilt is deliberately not used: at this app size it would add an annotation processor,
 * build time and APK size for no real benefit, and it is the most likely source of
 * friction with AGP 9's built-in Kotlin. Every dependency here is a `by lazy` singleton,
 * which is all the container this app needs.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: IntentDatabase by lazy { IntentDatabase.build(appContext) }

    val preferences: UserPreferences by lazy { UserPreferences(appContext) }

    val dataSource: UsageStatsDataSource by lazy { UsageStatsDataSource(appContext) }

    val classifier: CategoryClassifier by lazy { CategoryClassifier(appContext) }

    val usageIngestor: UsageIngestor by lazy {
        UsageIngestor(
            dataSource = dataSource,
            database = database,
            preferences = preferences,
            classifier = classifier,
            excludedPackages = excludedPackages,
        )
    }

    val usageRepository: UsageRepository by lazy { UsageRepository(database, usageIngestor) }

    val notifier: NotificationHelper by lazy { NotificationHelper(appContext) }

    val csvExporter: CsvExporter by lazy { CsvExporter(appContext, database) }

    /**
     * Moves the digest to a new time of day. Kept here rather than in a ViewModel so the
     * screen never needs a Context just to talk to WorkManager.
     */
    fun rescheduleDigest(minutesOfDay: Int) {
        WorkScheduler.scheduleDigest(appContext, minutesOfDay)
    }

    val focusManager: FocusSessionManager by lazy {
        FocusSessionManager(database = database, ingestor = usageIngestor)
    }

    /** Focus sessions are started and cancelled through the service, never in the UI. */
    fun startFocusSession(plannedMs: Long, label: String?) {
        FocusSessionService.start(appContext, plannedMs, label)
    }

    fun cancelFocusSession() {
        FocusSessionService.cancel(appContext)
    }

    /** Only ever called while the user has the intent prompt switched on. */
    fun startIntentWatch() {
        IntentWatchService.start(appContext)
    }

    fun stopIntentWatch() {
        IntentWatchService.stop(appContext)
    }

    /**
     * The background brain: rollups, cap alerts and digests. Built here rather than in a
     * worker so app code and workers share exactly one implementation.
     */
    val goalTracker: GoalTracker by lazy {
        GoalTracker(
            database = database,
            ingestor = usageIngestor,
            preferences = preferences,
            notifier = notifier,
            labelOf = classifier::labelOf,
        )
    }

    val appInfoProvider: AppInfoProvider by lazy {
        AppInfoProvider(
            context = appContext,
            iconSizePx = (48 * appContext.resources.displayMetrics.density).toInt(),
        )
    }

    /**
     * Packages that must never count towards screen time:
     * our own app, the system UI, and whichever launcher is installed.
     *
     * The launcher is resolved dynamically rather than hardcoded, because it differs
     * across devices and OEMs.
     */
    val excludedPackages: Set<String> by lazy {
        buildSet {
            add(appContext.packageName)
            add("com.android.systemui")

            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            appContext.packageManager
                .queryIntentActivities(homeIntent, 0)
                .mapTo(this) { it.activityInfo.packageName }
        }
    }
}
