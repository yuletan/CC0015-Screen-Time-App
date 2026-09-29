package com.intent.screentime.data.goals

import com.intent.screentime.data.local.entity.StreakDayEntity

/**
 * How the recent nights went, as the reason to revisit the window.
 *
 * Only nights that have already happened are counted, and only the ones that were actually
 * judged: there is one row per tracked day, so a day the app never saw cannot be counted as
 * a kept night or as a broken one. Tonight is left out because it is still running — a
 * window nobody has slept through yet says nothing about whether the window is right.
 */
data class BedtimeNudge(
    val missedNights: Int,
    val judgedNights: Int,
) {
    /**
     * True once the misses are a pattern rather than one bad evening.
     *
     * Three in seven, not one: a window is a guess about the evenings you usually have, and
     * every guess is wrong sometimes. Two broken nights is a week; three is the window.
     */
    val repeated: Boolean get() = missedNights >= REPEATED_MISSES

    companion object {
        /** The nights a nudge is allowed to speak for. */
        const val WINDOW_NIGHTS = 7

        const val REPEATED_MISSES = 3

        fun from(rows: List<StreakDayEntity>, todayEpochDay: Long): BedtimeNudge {
            val judged = rows.filter {
                it.dayEpochDay >= todayEpochDay - WINDOW_NIGHTS && it.dayEpochDay < todayEpochDay
            }
            return BedtimeNudge(
                missedNights = judged.count { !it.metBedtime },
                judgedNights = judged.size,
            )
        }
    }
}
