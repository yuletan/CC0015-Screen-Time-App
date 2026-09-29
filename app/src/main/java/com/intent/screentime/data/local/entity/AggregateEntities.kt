package com.intent.screentime.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-app, per-day rollup. The UI reads this rather than scanning raw events. */
@Entity(
    tableName = "daily_app_usage",
    primaryKeys = ["dayEpochDay", "packageName"],
    indices = [Index(value = ["dayEpochDay"])],
)
data class DailyAppUsageEntity(
    val dayEpochDay: Long,
    val packageName: String,
    val totalMs: Long,
    val sessionCount: Int,
    val firstUseMs: Long?,
    val lastUseMs: Long?,
)

/** One row per day summarising the whole device. */
@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val dayEpochDay: Long,
    val screenTimeMs: Long,
    val unlockCount: Int,
    val productionMs: Long,
    val consumptionMs: Long,
    /**
     * Focus time: the union of completed timer sessions and detected stretches in work
     * apps, so the timer and the detector covering the same minutes count once.
     */
    val focusMs: Long,
    /**
     * The part of [focusMs] the stretch detector added beyond the timer. Reported
     * separately so the figure can say where it came from instead of appearing from
     * nowhere on a day the user never started a session.
     */
    val autoFocusMs: Long = 0,
    val topPackage: String?,
)
