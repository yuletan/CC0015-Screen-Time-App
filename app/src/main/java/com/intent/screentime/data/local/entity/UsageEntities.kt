package com.intent.screentime.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Raw usage events harvested from [android.app.usage.UsageStatsManager].
 *
 * These are the durable source of truth: Android only retains raw events for a few days,
 * so harvesting them into this table is what makes long-term history possible.
 *
 * The unique index makes re-ingestion idempotent, which lets the ingestor deliberately
 * re-query a small overlap window without producing duplicates.
 */
@Entity(
    tableName = "usage_event",
    indices = [
        Index(value = ["packageName", "eventType", "timestampMs"], unique = true),
        Index(value = ["timestampMs"]),
    ],
)
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val eventType: Int,
    val timestampMs: Long,
    val className: String? = null,
)

/** A foreground session derived by pairing ACTIVITY_RESUMED with ACTIVITY_PAUSED events. */
@Entity(
    tableName = "app_session",
    indices = [
        Index(value = ["dayEpochDay"]),
        Index(value = ["packageName", "dayEpochDay"]),
        Index(value = ["startMs"]),
    ],
)
data class AppSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    /** Epoch day on which the session *started*. Sessions spanning midnight are clipped during aggregation. */
    val dayEpochDay: Long,
)
