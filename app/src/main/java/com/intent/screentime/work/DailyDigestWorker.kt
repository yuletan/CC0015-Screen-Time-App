package com.intent.screentime.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.intent.screentime.IntentApp

/**
 * The daily digest notification, at the time the user chose.
 *
 * Scheduled as a 24-hour periodic job with an initial delay to the chosen time rather
 * than a one-shot that re-enqueues itself: a worker that reschedules its own unique name
 * races with its own cancellation, while a periodic job simply runs again tomorrow. The
 * only cost is that WorkManager may defer it slightly, which a reminder-style
 * notification can tolerate.
 */
class DailyDigestWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        (applicationContext as IntentApp).container.goalTracker.postDigest()
        Result.success()
    } catch (_: Throwable) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "daily-digest"
    }
}
