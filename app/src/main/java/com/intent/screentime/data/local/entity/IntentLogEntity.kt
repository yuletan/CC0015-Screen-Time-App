package com.intent.screentime.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One intent prompt: what the user said they came to the app to do, or that they said
 * nothing at all.
 *
 * A row exists for every prompt raised, not just the answered ones. The denominator is
 * itself a finding — "you answered 34 of 51" says something the answers alone cannot.
 * [skipped] distinguishes a deliberate dismissal from a chip the user chose.
 *
 * The interesting question is not what someone tapped, it is whether "checking something"
 * reliably turns into eleven minutes — and that is answered by joining this against the
 * session that followed.
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
    /** [com.intent.screentime.data.intent.Reasons] key; null on a skipped prompt. */
    val reasonKey: String? = null,
    /**
     * Declared explicitly so Room's expected schema matches what `ALTER TABLE ... DEFAULT 0`
     * produces in MIGRATION_2_3. Without it the identity check fails on an upgraded install.
     */
    @ColumnInfo(defaultValue = "0") val skipped: Boolean = false,
)
