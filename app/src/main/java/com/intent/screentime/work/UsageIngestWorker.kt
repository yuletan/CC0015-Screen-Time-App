package com.intent.screentime.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.intent.screentime.IntentApp
import com.intent.screentime.core.permission.UsageAccess

/**
 * Periodic harvest. Deliberately the *only* background mechanism in the app:
 *
 *  - no foreground service, because Android already records usage itself and we only
 *    need to copy the records down periodically
 *  - no `AlarmManager`, so no `BOOT_COMPLETED` receiver is required — WorkManager
 *    persists its schedule across reboots on its own
 *  - no wake locks
 *
 * That combination is why this app can track screen time without the persistent
 * notification and battery drain that comparable apps impose.
 */
class UsageIngestWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!UsageAccess.isGranted(applicationContext)) {
            // Nothing we can do until the user re-grants access. Succeed so we back off
            // to the normal cadence instead of hammering with retries.
            return Result.success()
        }

        return try {
            val container = (applicationContext as IntentApp).container
            val result = container.usageIngestor.ingest()
            if (!result.succeeded) return Result.retry()

            // Straight after the numbers move is the only moment a cap alert is worth
            // anything; it is de-duplicated per breach per day inside the tracker.
            container.goalTracker.checkCapAlerts()

            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "usage-ingest"
    }
}
