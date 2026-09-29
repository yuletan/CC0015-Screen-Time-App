package com.intent.screentime.data.stats

import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.local.DefaultCategories
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.repository.AppCategoryRef

/** A calm, optional next step when one app's usage is worth noticing. */
data class AppUsageInsight(
    val title: String,
    val body: String,
    val action: Action?,
) {
    enum class Action {
        SET_CAP,
        CHANGE_CAP,
        CHOOSE_CATEGORY,
    }
}

object AppUsageInsights {
    const val MIN_NOTICEABLE_MS = 30 * 60_000L
    const val HIGH_USAGE_MS = 2 * 60 * 60_000L
    const val ABOVE_USUAL_MULTIPLIER = 1.5

    fun forApp(
        todayMs: Long,
        averageMs: Long,
        capMinutes: Int?,
        category: AppCategoryRef?,
    ): AppUsageInsight? {
        if (todayMs < MIN_NOTICEABLE_MS) return null

        val capMs = capMinutes?.takeIf { it > 0 }?.let { it * 60_000L }
        if (capMs != null && todayMs > capMs) {
            val action = AppUsageInsight.Action.CHANGE_CAP
            return AppUsageInsight(
                title = "This app is over its cap",
                body = "${DurationFormat.compact(todayMs)} here today, " +
                    "${DurationFormat.compact(todayMs - capMs)} over your " +
                    "${DurationFormat.compact(capMs)} cap.",
                action = action,
            )
        }

        if (category == null || category.id == DefaultCategories.UNCATEGORIZED) {
            return AppUsageInsight(
                title = "Sort this app before judging its time",
                body = "${DurationFormat.compact(todayMs)} here today. Choose whether it is " +
                    "Producing, Consuming, Utility, or Neutral so the rest of the app can " +
                    "interpret it honestly.",
                action = AppUsageInsight.Action.CHOOSE_CATEGORY,
            )
        }

        val aboveUsual = averageMs > 0L &&
            todayMs.toDouble() >= averageMs * ABOVE_USUAL_MULTIPLIER
        if (aboveUsual) {
            val action = if (capMs == null) {
                AppUsageInsight.Action.SET_CAP
            } else {
                AppUsageInsight.Action.CHANGE_CAP
            }
            return AppUsageInsight(
                title = "Today is higher than usual here",
                body = "${DurationFormat.compact(todayMs)} today versus an average of " +
                    "${DurationFormat.compact(averageMs)} on active days. " +
                    categoryMessage(category.kind),
                action = action,
            )
        }

        if (todayMs < HIGH_USAGE_MS) return null

        return when (category.kind) {
            CategoryKind.CONSUMPTION -> AppUsageInsight(
                title = "Worth a pause",
                body = "${DurationFormat.compact(todayMs)} here today. " +
                    "A short break or a personal cap could make the boundary easier to notice.",
                action = if (capMs == null) {
                    AppUsageInsight.Action.SET_CAP
                } else {
                    AppUsageInsight.Action.CHANGE_CAP
                },
            )

            CategoryKind.PRODUCTION -> AppUsageInsight(
                title = "A long producing stretch",
                body = "${DurationFormat.compact(todayMs)} here today. If this was planned, " +
                    "it is useful context for how the rest of the day felt.",
                action = null,
            )

            CategoryKind.UTILITY, CategoryKind.NEUTRAL -> AppUsageInsight(
                title = "A lot of time in this app",
                body = "${DurationFormat.compact(todayMs)} here today. Check whether that " +
                    "matches what you meant to do.",
                action = null,
            )
        }
    }

    private fun categoryMessage(kind: CategoryKind): String = when (kind) {
        CategoryKind.CONSUMPTION -> "If that was unplanned, a pause or cap may help."
        CategoryKind.PRODUCTION -> "That may be a planned stretch of work or learning."
        CategoryKind.UTILITY -> "Check whether that utility time was expected."
        CategoryKind.NEUTRAL -> "It is kept separate from the Producing/Consuming ratio."
    }
}
