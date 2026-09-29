package com.intent.screentime.data.goals

import com.intent.screentime.data.local.entity.StreakDayEntity
import java.time.LocalDate

/**
 * Lays the streak rows out as a calendar grid: one column per week, one cell per day.
 *
 * Pure and separate from the drawing code, because the fiddly part — aligning the grid
 * so every column starts on a Monday and today lands in the last column — is exactly the
 * part that is easy to get wrong and easy to test.
 */
object HeatmapBuilder {

    data class Cell(
        val epochDay: Long,
        val metCap: Boolean,
        val metGoal: Boolean,
        val metBedtime: Boolean,
        val hasData: Boolean,
        val isToday: Boolean,
    )

    /**
     * [weeks] columns ending with the week that contains [todayEpochDay].
     * Days before the first recorded day simply come back as `hasData = false`.
     */
    fun weeks(
        rows: List<StreakDayEntity>,
        todayEpochDay: Long,
        weeks: Int = DEFAULT_WEEKS,
    ): List<List<Cell>> {
        val byDay = rows.associateBy { it.dayEpochDay }
        val todayWeekdayIndex = LocalDate.ofEpochDay(todayEpochDay).dayOfWeek.value - 1

        val firstDay = todayEpochDay - todayWeekdayIndex - (weeks - 1) * DAYS_PER_WEEK

        return (0 until weeks).map { week ->
            (0 until DAYS_PER_WEEK).map { weekday ->
                val epochDay = firstDay + week * DAYS_PER_WEEK + weekday
                val row = byDay[epochDay]
                Cell(
                    epochDay = epochDay,
                    metCap = row?.metCap ?: false,
                    metGoal = row?.metGoal ?: false,
                    metBedtime = row?.metBedtime ?: false,
                    hasData = row != null,
                    isToday = epochDay == todayEpochDay,
                )
            }
        }
    }

    const val DAYS_PER_WEEK = 7
    const val DEFAULT_WEEKS = 12
}
