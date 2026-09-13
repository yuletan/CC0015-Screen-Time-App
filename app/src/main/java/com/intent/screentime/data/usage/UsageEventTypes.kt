package com.intent.screentime.data.usage

/**
 * Mirrors the `android.app.usage.UsageEvents.Event` constants that we care about.
 *
 * These are duplicated as plain Kotlin so that [SessionBuilder] and [DailyAggregator]
 * stay free of Android framework dependencies and can run as fast JVM unit tests.
 * [com.intent.screentime.data.usage.UsageStatsDataSource] asserts at runtime that these
 * values still match the framework, so the duplication cannot silently drift.
 */
object UsageEventTypes {
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val SCREEN_INTERACTIVE = 15
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 17
    const val KEYGUARD_HIDDEN = 18
    const val DEVICE_SHUTDOWN = 26
    const val DEVICE_STARTUP = 27

    /** Events that mean "whatever was in the foreground is no longer being looked at". */
    val SESSION_CLOSING: Set<Int> = setOf(SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN, KEYGUARD_SHOWN)
}
