package com.intent.screentime.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "intent_prefs")

/**
 * Small, non-relational app state.
 *
 * [ingestWatermarkMs] is the important one: it is the high-water mark of harvested
 * usage events, and it is what makes each harvest a cheap delta read instead of a
 * full re-scan of the event log.
 */
class UserPreferences(private val context: Context) {

    private object Keys {
        val INGEST_WATERMARK_MS = longPreferencesKey("ingest_watermark_ms")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DIGEST_MINUTES_OF_DAY = intPreferencesKey("digest_minutes_of_day")
        val LAST_DIGEST_EPOCH_DAY = longPreferencesKey("last_digest_epoch_day")
        val CATEGORIES_SEEDED = booleanPreferencesKey("categories_seeded")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CAP_ALERT_TOKENS = stringSetPreferencesKey("cap_alert_tokens")
        val LAST_MILESTONE_STREAK = intPreferencesKey("last_milestone_streak")
        val INTENT_PROMPT_ENABLED = booleanPreferencesKey("intent_prompt_enabled")
        val INTENT_WATCHED_PACKAGES = stringSetPreferencesKey("intent_watched_packages")
        val FOCUS_BACKFILL_DONE = booleanPreferencesKey("focus_backfill_done")
        val BEDTIME_BACKFILL_DONE = booleanPreferencesKey("bedtime_backfill_done")
        val CLASSIFIER_VERSION = intPreferencesKey("classifier_version")
    }

    val ingestWatermarkMs: Flow<Long> = context.dataStore.data.map { it[Keys.INGEST_WATERMARK_MS] ?: 0L }

    suspend fun setIngestWatermarkMs(value: Long) {
        context.dataStore.edit { it[Keys.INGEST_WATERMARK_MS] = value }
    }

    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = value }
    }

    /** Minutes past midnight for the daily digest notification. Default 21:00. */
    val digestMinutesOfDay: Flow<Int> =
        context.dataStore.data.map { it[Keys.DIGEST_MINUTES_OF_DAY] ?: (21 * 60) }

    suspend fun setDigestMinutesOfDay(value: Int) {
        context.dataStore.edit { it[Keys.DIGEST_MINUTES_OF_DAY] = value }
    }

    val lastDigestEpochDay: Flow<Long> =
        context.dataStore.data.map { it[Keys.LAST_DIGEST_EPOCH_DAY] ?: -1L }

    suspend fun setLastDigestEpochDay(value: Long) {
        context.dataStore.edit { it[Keys.LAST_DIGEST_EPOCH_DAY] = value }
    }

    /**
     * Material You is off by default: the authored palette is the app's identity, and a
     * wallpaper-derived scheme would dissolve it. Available for anyone who wants it.
     */
    val dynamicColor: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(value: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = value }
    }

    /**
     * Breach markers such as `daily:20345` or `app:20345:com.example`, so a cap that has
     * already been called out once today is never announced twice.
     */
    val capAlertTokens: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.CAP_ALERT_TOKENS] ?: emptySet() }

    suspend fun addCapAlertTokens(tokens: Set<String>, todayEpochDay: Long) {
        if (tokens.isEmpty()) return
        context.dataStore.edit { prefs ->
            // Old days are dropped here rather than on a schedule: the set stays small
            // and self-trimming without a pruning job.
            val kept = prefs[Keys.CAP_ALERT_TOKENS].orEmpty().filterTo(mutableSetOf()) { token ->
                token.endsWith(":$todayEpochDay") || token.contains(":$todayEpochDay:")
            }
            prefs[Keys.CAP_ALERT_TOKENS] = kept + tokens
        }
    }

    /** Highest streak milestone already celebrated, so it fires exactly once. */
    val lastMilestoneStreak: Flow<Int> =
        context.dataStore.data.map { it[Keys.LAST_MILESTONE_STREAK] ?: 0 }

    suspend fun setLastMilestoneStreak(value: Int) {
        context.dataStore.edit { it[Keys.LAST_MILESTONE_STREAK] = value }
    }

    /** Opt-in, off by default — the prompt is the last thing the app should start doing. */
    val intentPromptEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.INTENT_PROMPT_ENABLED] ?: false }

    suspend fun setIntentPromptEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.INTENT_PROMPT_ENABLED] = value }
    }

    /** The time-sink apps the user chose to be asked about. */
    val intentWatchedPackages: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.INTENT_WATCHED_PACKAGES] ?: emptySet() }

    suspend fun setIntentWatchedPackages(value: Set<String>) {
        context.dataStore.edit { it[Keys.INTENT_WATCHED_PACKAGES] = value }
    }

    /**
     * Whether stored history has been re-aggregated since focus gained stretch detection.
     *
     * One-time: days rolled up before that upgrade carry the timer-only figure, and a
     * day the user can see on two screens has to agree with itself.
     */
    val focusBackfillDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.FOCUS_BACKFILL_DONE] ?: false }

    suspend fun setFocusBackfillDone(value: Boolean) {
        context.dataStore.edit { it[Keys.FOCUS_BACKFILL_DONE] = value }
    }

    /**
     * Whether stored history has been judged since bedtime became a commitment.
     *
     * The migration's `DEFAULT 0` says every day before this version missed its bedtime,
     * which is not a verdict anyone ever reached. One pass over the recent past replaces
     * it with a real one.
     */
    val bedtimeBackfillDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BEDTIME_BACKFILL_DONE] ?: false }

    suspend fun setBedtimeBackfillDone(value: Boolean) {
        context.dataStore.edit { it[Keys.BEDTIME_BACKFILL_DONE] = value }
    }

    /**
     * The automatic classifier version stored app rows were last reconciled with.
     *
     * One-time, like the flags above, and for the same reason: a stored row is never
     * re-asked, so a classifier upgrade has to be pushed through explicitly, and the
     * stored version is what says whether that has happened yet.
     */
    val classifierVersion: Flow<Int> =
        context.dataStore.data.map { it[Keys.CLASSIFIER_VERSION] ?: 0 }

    suspend fun setClassifierVersion(value: Int) {
        context.dataStore.edit { it[Keys.CLASSIFIER_VERSION] = value }
    }

    suspend fun currentWatermark(): Long = ingestWatermarkMs.first()
}
