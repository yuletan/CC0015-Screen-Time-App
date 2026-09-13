package com.intent.screentime.data.local.entity

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
}

@Entity(tableName = "target")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TargetType,
    /** Set only for PER_APP_DAILY_CAP. */
    val scopePackage: String? = null,
    val valueMinutes: Int,
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
)
