package com.intent.screentime.focus

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.intent.screentime.IntentApp
import com.intent.screentime.notify.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps a focus session alive while the user is doing something else.
 *
 * **Deviation from the plan, deliberately:** the plan called for a `shortService`
 * foreground service, but Android 14 caps `shortService` at roughly three minutes —
 * which cannot host a 25, 45 or 90 minute session. `specialUse` has no such cap, and its
 * only real cost is a Play Store declaration that a sideloaded personal build never has
 * to make.
 *
 * The notification is a countdown (`setUsesChronometer` + `setChronometerCountDown`), so
 * it stays accurate without the service waking up once per second to update it. The only
 * wakeups are the single delay until the session ends.
 */
class FocusSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var completeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as IntentApp).container

        when (intent?.action) {
            ACTION_START -> {
                val plannedMs = intent.getLongExtra(EXTRA_PLANNED_MS, 0L)
                val label = intent.getStringExtra(EXTRA_LABEL)
                if (plannedMs <= 0L) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                scope.launch { begin(container, plannedMs, label) }
            }

            ACTION_CANCEL -> scope.launch {
                container.focusManager.cancel()
                stopSelf()
            }

            else -> {
                // Nothing to resume from: a restarted service with no action ends here.
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun begin(
        container: com.intent.screentime.core.di.AppContainer,
        plannedMs: Long,
        label: String?,
    ) {
        val session = container.focusManager.start(plannedMs, label)

        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FocusSessionService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        startForeground(
            NotificationHelper.ID_FOCUS,
            container.notifier.focusNotification(
                plannedEndMs = session.endMs,
                label = label,
                cancelIntent = cancelIntent,
            ),
        )

        completeJob?.cancel()
        completeJob = scope.launch {
            delay(session.remainingMs(System.currentTimeMillis()))
            container.focusManager.complete()
            stopSelf()
        }
    }

    override fun onDestroy() {
        completeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.intent.screentime.action.START_FOCUS"
        const val ACTION_CANCEL = "com.intent.screentime.action.CANCEL_FOCUS"
        private const val EXTRA_PLANNED_MS = "planned_ms"
        private const val EXTRA_LABEL = "label"

        fun start(context: Context, plannedMs: Long, label: String?) {
            val intent = Intent(context, FocusSessionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PLANNED_MS, plannedMs)
                .putExtra(EXTRA_LABEL, label)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, FocusSessionService::class.java).setAction(ACTION_CANCEL),
            )
        }
    }
}
