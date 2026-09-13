package com.intent.screentime.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.intent.screentime.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Registers the app's background work.
 *
 * [ExistingPeriodicWorkPolicy.KEEP] is important for the harvest: re-registering on
 * every app start must never stack duplicate jobs or reset the schedule. The digest uses
 * UPDATE instead, because changing the chosen time *should* move it.
 */
object WorkScheduler {

    /** WorkManager's floor for periodic work; also all we need for good daily totals. */
    private const val INGEST_INTERVAL_MINUTES = 15L

    /** Just after midnight: late enough that the previous day is closed, early enough to be invisible. */
    private const val ROLLUP_MINUTES_OF_DAY = 15

    suspend fun scheduleAll(context: Context, preferences: UserPreferences) {
        scheduleIngest(context)
        scheduleRollup(context)
        scheduleDigest(context, preferences.digestMinutesOfDay.first())
    }

    private fun scheduleIngest(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageIngestWorker>(
            INGEST_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UsageIngestWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleRollup(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyRollupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(
                nextOccurrenceDelay(ROLLUP_MINUTES_OF_DAY, System.currentTimeMillis()),
                TimeUnit.MILLISECONDS,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyRollupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Re-invoked whenever the digest time changes, so UPDATE is the right policy here. */
    fun scheduleDigest(context: Context, minutesOfDay: Int) {
        val request = PeriodicWorkRequestBuilder<DailyDigestWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(
                nextOccurrenceDelay(minutesOfDay, System.currentTimeMillis()),
                TimeUnit.MILLISECONDS,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyDigestWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Milliseconds until the next occurrence of [minutesOfDay] past local midnight.
     *
     * Pure so the two cases that are easy to get wrong — the time is still ahead today,
     * and the time has already passed — are unit tested rather than discovered by a
     * notification arriving at the wrong hour.
     */
    internal fun nextOccurrenceDelay(
        minutesOfDay: Int,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val minutes = minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val startOfToday = now.toLocalDate().atStartOfDay(zone)
        val target = startOfToday.plusMinutes(minutes.toLong())

        val next = if (target.isAfter(now)) target else target.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
