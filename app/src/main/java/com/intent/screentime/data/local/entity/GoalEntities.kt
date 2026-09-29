package com.intent.screentime.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Caps are upper bounds; goals are lower bounds the user wants to hit. */
enum class TargetType {
    DAILY_SCREEN_TIME_CAP,
    WEEKLY_SCREEN_TIME_CAP,
    PER_APP_DAILY_CAP,
    WEEKLY_PRODUCTION_GOAL,
    DAILY_FOCUS_GOAL,

    /**
     * A quiet window, not an amount. This is the one target whose value is a pair of times
     * rather than a duration, which is why it is the only one that reads
     * [TargetEntity.startMinutesOfDay] and [TargetEntity.endMinutesOfDay].
     */
    BEDTIME_WINDOW,
}

@Entity(tableName = "target")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TargetType,
    /** Set only for PER_APP_DAILY_CAP. */
    val scopePackage: String? = null,
    /** Unused on BEDTIME_WINDOW rows, which are written with 0. */
    val valueMinutes: Int,
    /** Minutes past midnight. Set only for BEDTIME_WINDOW. */
    val startMinutesOfDay: Int? = null,
    val endMinutesOfDay: Int? = null,
    val enabled: Boolean = true,
)

@Entity(tableName = "focus_session", indices = [Index(value = ["startMs"])])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long? = null,
    val plannedMs: Long,
    val completed: Boolean = false,
    val label: String? = null,
)

@Entity(tableName = "streak_day")
data class StreakDayEntity(
    @PrimaryKey val dayEpochDay: Long,
    val metCap: Boolean,
    val metGoal: Boolean,
    val score: Int,
    val productionMs: Long,
    /**
     * Declared explicitly so Room's expected schema matches the `DEFAULT 0` that
     * MIGRATION_4_5 writes. Without it the identity check fails on an upgraded install,
     * for the same reason `IntentLogEntity.skipped` carries one.
     */
    @ColumnInfo(defaultValue = "0") val metBedtime: Boolean = false,
    /**
     * Screen time that fell inside that night's window, as judged. Stored rather than
     * recomputed so the day card and the goals board cannot report two different numbers
     * for the same night.
     */
    @ColumnInfo(defaultValue = "0") val bedtimeUsedMs: Long = 0,
)
