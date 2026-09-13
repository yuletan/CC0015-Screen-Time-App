package com.intent.screentime.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.intent.screentime.IntentApp
import com.intent.screentime.core.permission.UsageAccess

/**
 * The nightly pass, run shortly after midnight.
 *
 * Its job is to make yesterday *final*: harvest whatever the OS still holds, re-aggregate
 * the day, judge it against the targets, and celebrate a milestone if one was reached.
 * Without it the Today screen would be right, but the history would never settle.
 */
class DailyRollupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!UsageAccess.isGranted(applicationContext)) return Result.success()

        return try {
            (applicationContext as IntentApp).container.goalTracker.runNightly()
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "daily-rollup"
    }
}
