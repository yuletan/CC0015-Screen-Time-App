package com.intent.screentime.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.intent.screentime.data.local.entity.IntentLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntentLogDao {
    @Insert
    suspend fun insert(row: IntentLogEntity): Long

    @Query("SELECT * FROM intent_log WHERE timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC")
    suspend fun between(fromMs: Long, toMs: Long): List<IntentLogEntity>

    @Query("SELECT * FROM intent_log ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<IntentLogEntity>>

    @Query("SELECT COUNT(*) FROM intent_log")
    suspend fun count(): Long

    /** Answered prompts only: the numerator of the answer rate. */
    @Query("SELECT COUNT(*) FROM intent_log WHERE skipped = 0")
    suspend fun answeredCount(): Long
}
