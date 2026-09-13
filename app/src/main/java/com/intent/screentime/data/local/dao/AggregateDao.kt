package com.intent.screentime.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyAppUsageDao {
    @Upsert
    suspend fun upsertAll(rows: List<DailyAppUsageEntity>)

    @Query("DELETE FROM daily_app_usage WHERE dayEpochDay = :dayEpochDay")
    suspend fun deleteDay(dayEpochDay: Long)

    @Query("DELETE FROM daily_app_usage WHERE dayEpochDay BETWEEN :fromDay AND :toDay")
    suspend fun deleteRange(fromDay: Long, toDay: Long)

    @Query("SELECT * FROM daily_app_usage WHERE dayEpochDay = :dayEpochDay ORDER BY totalMs DESC")
    suspend fun forDay(dayEpochDay: Long): List<DailyAppUsageEntity>

    @Query("SELECT * FROM daily_app_usage WHERE dayEpochDay = :dayEpochDay ORDER BY totalMs DESC")
    fun observeForDay(dayEpochDay: Long): Flow<List<DailyAppUsageEntity>>

    @Query("SELECT * FROM daily_app_usage WHERE dayEpochDay BETWEEN :fromDay AND :toDay")
    suspend fun between(fromDay: Long, toDay: Long): List<DailyAppUsageEntity>

    @Query(
        "SELECT * FROM daily_app_usage WHERE packageName = :packageName " +
            "AND dayEpochDay BETWEEN :fromDay AND :toDay ORDER BY dayEpochDay ASC",
    )
    suspend fun forPackageBetween(
        packageName: String,
        fromDay: Long,
        toDay: Long,
    ): List<DailyAppUsageEntity>

    @Query(
        "SELECT * FROM daily_app_usage WHERE packageName = :packageName " +
            "AND dayEpochDay BETWEEN :fromDay AND :toDay ORDER BY dayEpochDay ASC",
    )
    fun observeForPackageBetween(
        packageName: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<DailyAppUsageEntity>>

    @Query("SELECT COALESCE(SUM(totalMs), 0) FROM daily_app_usage WHERE dayEpochDay = :dayEpochDay")
    suspend fun totalForDay(dayEpochDay: Long): Long
}

@Dao
interface DailySummaryDao {
    @Upsert
    suspend fun upsert(row: DailySummaryEntity)

    @Query("DELETE FROM daily_summary WHERE dayEpochDay BETWEEN :fromDay AND :toDay")
    suspend fun deleteRange(fromDay: Long, toDay: Long)

    @Query("SELECT * FROM daily_summary WHERE dayEpochDay = :dayEpochDay")
    suspend fun getDay(dayEpochDay: Long): DailySummaryEntity?

    @Query("SELECT * FROM daily_summary WHERE dayEpochDay = :dayEpochDay")
    fun observeDay(dayEpochDay: Long): Flow<DailySummaryEntity?>

    @Query("SELECT * FROM daily_summary WHERE dayEpochDay BETWEEN :fromDay AND :toDay ORDER BY dayEpochDay ASC")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summary WHERE dayEpochDay BETWEEN :fromDay AND :toDay ORDER BY dayEpochDay ASC")
    suspend fun between(fromDay: Long, toDay: Long): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_summary ORDER BY dayEpochDay DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DailySummaryEntity>
}
