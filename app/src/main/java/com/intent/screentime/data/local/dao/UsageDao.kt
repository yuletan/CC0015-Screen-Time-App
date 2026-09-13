package com.intent.screentime.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.UsageEventEntity

@Dao
interface UsageEventDao {
    /** IGNORE + the unique index makes overlapping re-ingestion idempotent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<UsageEventEntity>): List<Long>

    @Query("SELECT MAX(timestampMs) FROM usage_event")
    suspend fun latestTimestamp(): Long?

    @Query("SELECT * FROM usage_event WHERE timestampMs >= :startMs AND timestampMs < :endMs ORDER BY timestampMs ASC")
    suspend fun eventsBetween(startMs: Long, endMs: Long): List<UsageEventEntity>

    @Query("SELECT COUNT(*) FROM usage_event")
    suspend fun count(): Long

    @Query("DELETE FROM usage_event WHERE timestampMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long)
}

@Dao
interface AppSessionDao {
    @Insert
    suspend fun insertAll(sessions: List<AppSessionEntity>)

    /** Sessions are rebuilt for a rolling window, so clear that window first. */
    @Query("DELETE FROM app_session WHERE startMs >= :fromMs")
    suspend fun deleteFrom(fromMs: Long)

    @Query("SELECT * FROM app_session WHERE startMs >= :fromMs ORDER BY startMs ASC")
    suspend fun sessionsFrom(fromMs: Long): List<AppSessionEntity>

    /**
     * Every session overlapping a window, including ones that started before it.
     *
     * The overlap condition (rather than `startMs` alone) is what makes overnight
     * sessions count towards both of the days they touch.
     */
    @Query("SELECT * FROM app_session WHERE startMs < :endMs AND endMs > :startMs")
    suspend fun sessionsOverlapping(startMs: Long, endMs: Long): List<AppSessionEntity>
}
