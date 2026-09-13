package com.intent.screentime.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.intent.screentime.data.local.entity.FocusSessionEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.local.entity.TargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
    @Insert
    suspend fun insert(target: TargetEntity): Long

    @Upsert
    suspend fun upsert(target: TargetEntity)

    @Delete
    suspend fun delete(target: TargetEntity)

    @Query("SELECT * FROM target WHERE enabled = 1")
    suspend fun enabled(): List<TargetEntity>

    @Query("SELECT * FROM target WHERE enabled = 1")
    fun observeEnabled(): Flow<List<TargetEntity>>

    @Query("SELECT * FROM target ORDER BY id ASC")
    fun observeAll(): Flow<List<TargetEntity>>
}

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query("UPDATE focus_session SET endMs = :endMs, completed = :completed WHERE id = :id")
    suspend fun finish(id: Long, endMs: Long, completed: Boolean)

    @Query("SELECT * FROM focus_session WHERE startMs >= :fromMs ORDER BY startMs DESC")
    suspend fun since(fromMs: Long): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_session WHERE startMs >= :fromMs ORDER BY startMs DESC")
    fun observeSince(fromMs: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(endMs - startMs), 0) FROM focus_session WHERE startMs >= :fromMs AND endMs IS NOT NULL")
    suspend fun totalMsSince(fromMs: Long): Long

    /** Focus time that falls inside a window, clipping sessions that straddle its edges. */
    @Query(
        "SELECT COALESCE(SUM(MIN(endMs, :toMs) - MAX(startMs, :fromMs)), 0) FROM focus_session " +
            "WHERE endMs IS NOT NULL AND startMs < :toMs AND endMs > :fromMs",
    )
    suspend fun totalMsBetween(fromMs: Long, toMs: Long): Long
}

@Dao
interface StreakDayDao {
    @Upsert
    suspend fun upsert(row: StreakDayEntity)

    @Query("SELECT * FROM streak_day ORDER BY dayEpochDay DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<StreakDayEntity>

    @Query("SELECT * FROM streak_day WHERE dayEpochDay BETWEEN :fromDay AND :toDay ORDER BY dayEpochDay ASC")
    suspend fun between(fromDay: Long, toDay: Long): List<StreakDayEntity>

    @Query("SELECT * FROM streak_day WHERE dayEpochDay = :dayEpochDay")
    suspend fun getDay(dayEpochDay: Long): StreakDayEntity?

    @Query("SELECT * FROM streak_day ORDER BY dayEpochDay DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<StreakDayEntity>>
}
