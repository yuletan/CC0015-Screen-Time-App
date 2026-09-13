package com.intent.screentime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The answer to the nightly question. Null on a day nobody answered. */
enum class DayReflection(val key: String, val label: String) {
    YES("yes", "Yes"),
    PARTLY("partly", "Partly"),
    NO("no", "No"),
    ;

    companion object {
        fun fromKey(key: String?): DayReflection? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The user's own words about a day: one note, and one answer to "was today the day you
 * wanted?".
 *
 * Deliberately a table of its own rather than columns on [StreakDayEntity]. That table is
 * rewritten wholesale by the nightly rollup's upsert, so anything typed into it would be
 * silently cleared the next time the day was judged. This one is only ever written by the
 * user.
 */
@Entity(tableName = "day_note")
data class DayNoteEntity(
    @PrimaryKey val dayEpochDay: Long,
    val note: String? = null,
    /** [DayReflection.key], stored as text so the migration needs no type converter. */
    val reflection: String? = null,
    val updatedAtMs: Long = 0L,
)
