package com.intent.screentime.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One answered intent prompt: what the user said they came to the app to do.
 *
 * Deliberately just a label and a timestamp. The interesting question is not what
 * someone typed, it is whether "check messages" reliably turns into eleven minutes —
 * and that is answered by joining this against the session that followed.
 */
@Entity(
    tableName = "intent_log",
    indices = [
        Index(value = ["timestampMs"]),
        Index(value = ["packageName"]),
    ],
)
data class IntentLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestampMs: Long,
    val intentLabel: String,
)
