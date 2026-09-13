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
    val focusMs: Long,
    val topPackage: String?,
)
