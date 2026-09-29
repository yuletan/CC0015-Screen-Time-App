package com.intent.screentime.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.intent.screentime.data.local.entity.DayNoteEntity
import kotlinx.coroutines.flow.Flow

/** The user's own note and reflection for a day. Read-modify-write is the caller's job. */
@Dao
interface DayNoteDao {
    @Query("SELECT * FROM day_note WHERE dayEpochDay = :dayEpochDay")
    suspend fun get(dayEpochDay: Long): DayNoteEntity?

    @Query("SELECT * FROM day_note WHERE dayEpochDay = :dayEpochDay")
    fun observe(dayEpochDay: Long): Flow<DayNoteEntity?>

    @Query("SELECT * FROM day_note WHERE dayEpochDay BETWEEN :fromDay AND :toDay ORDER BY dayEpochDay ASC")
    suspend fun between(fromDay: Long, toDay: Long): List<DayNoteEntity>

    @Upsert
    suspend fun upsert(row: DayNoteEntity)
}
