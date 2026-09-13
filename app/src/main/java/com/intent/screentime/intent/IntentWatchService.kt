package com.intent.screentime.intent

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.intent.screentime.IntentApp
import com.intent.screentime.core.di.AppContainer
import com.intent.screentime.data.usage.UsageEventTypes
import com.intent.screentime.notify.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Watches for the apps the user flagged, and asks why before they land.
 *
 * **The honest trade:** detecting a launch without an Accessibility Service — which this
 * app refuses to require — means polling usage events. That costs a wakeup every couple
 * of seconds, so this service only exists while the feature is switched on (off by
 * default), only runs for apps the user explicitly picked, and announces itself with a
 * silent notification. When it is off, the app is exactly as cheap as it was before.
 */
class IntentWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastPromptAt = HashMap<String, Long>()
    private var pollJob: Job? = null
    private lateinit var overlay: IntentOverlay

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = IntentOverlay(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as IntentApp).container

        startForeground(NotificationHelper.ID_WATCH, container.notifier.watchNotification())

        if (pollJob == null) {
            pollJob = scope.launch { poll(container) }
        }

        return START_NOT_STICKY
    }

    private suspend fun poll(container: AppContainer) {
        var lastPollMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            delay(POLL_MS)
            val now = System.currentTimeMillis()

            if (!container.preferences.intentPromptEnabled.first()) {
                stopSelf()
                return
            }

            val watched = container.preferences.intentWatchedPackages.first()
            if (watched.isEmpty()) {
                lastPollMs = now
                continue
            }

            val events = container.dataSource.eventsBetween(lastPollMs, now)
            lastPollMs = now
            events ?: continue

            for (event in events) {
                if (event.eventType != UsageEventTypes.ACTIVITY_RESUMED) continue
                if (event.packageName !in watched) continue

                val last = lastPromptAt[event.packageName] ?: 0L
                if (now - last < PROMPT_COOLDOWN_MS) continue

                lastPromptAt[event.packageName] = now
                askWhy(container, event.packageName)
                // One prompt at a time: back-to-back overlays are harassment.
                break
            }
        }
    }

    private suspend fun askWhy(container: AppContainer, packageName: String) {
        val label = container.classifier.labelOf(packageName)
        // Stamped once, when the prompt is raised, so a skip is recorded at the moment the
        // question was asked rather than the moment it timed out. That keeps it in the
        // hour it belongs to.
        val askedAt = System.currentTimeMillis()

        withContext(Dispatchers.Main) {
            overlay.show(
                appLabel = label,
                onAnswer = { option ->
                    scope.launch {
                        container.usageRepository.logIntent(packageName, option, askedAt)
                    }
                },
                onTimeoutAnswer = {
                    // Deliberately reversed from the original "nothing is recorded": an
                    // unanswered prompt is now written down too. The denominator is a
                    // finding in its own right — "you answered 34 of 51" is only
                    // computable if the 17 you ignored are kept — and skipping the
                    // question without the row would quietly inflate the answer rate.
                    scope.launch {
                        container.usageRepository.logSkippedIntent(packageName, askedAt)
                    }
                },
            )
        }
    }

    override fun onDestroy() {
        overlay.hide()
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val POLL_MS = 2_000L
        private const val PROMPT_COOLDOWN_MS = 3 * 60_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, IntentWatchService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IntentWatchService::class.java))
        }
    }
}
