package com.intent.screentime.data.goals

import kotlin.math.roundToInt

/**
 * The composite Intent Score (0–100).
 *
 * Four things are worth measuring about a day, and they are weighted by how much the
 * user can actually act on them:
 *
 *  - **production ratio (40%)** — of the time that is clearly one or the other, how much
 *    went into producing rather than consuming. Utility and unsorted time stays out of
 *    the ratio entirely rather than being miscounted.
 *  - **cap adherence (25%)** — full credit up to the cap, decaying linearly to zero at
 *    150% of it. A single bad hour should not zero the day; a doubling should.
 *  - **focus time (20%)** — one hour of deliberate focus earns full credit.
 *  - **streak (15%)** — seven consecutive days inside the cap earns full credit.
 *
 * When no cap is set the cap component contributes half credit, and when nothing is
 * categorised yet the ratio contributes half credit: the score should never punish a
 * user for data the app does not have.
 *
 * Pure and deterministic, so every one of those statements is unit tested.
 */
object IntentScore {

    const val MAX = 100

    private const val PRODUCTION_WEIGHT = 0.40f
    private const val CAP_WEIGHT = 0.25f
    private const val FOCUS_WEIGHT = 0.20f
    private const val STREAK_WEIGHT = 0.15f

    private const val NEUTRAL = 0.5f

    /** One hour of focus is full credit. */
    private const val FOCUS_FULL_CREDIT_MS = 60 * 60_000L

    /** Seven days inside the cap is full credit. */
    private const val STREAK_FULL_CREDIT_DAYS = 7f

    /** The cap component reaches zero at this multiple of the cap. */
    private const val CAP_DECAY_AT = 1.5f

    data class Parts(
        val production: Float,
        val cap: Float,
        val focus: Float,
        val streak: Float,
    )

    fun parts(
        productionMs: Long,
        consumptionMs: Long,
        screenTimeMs: Long,
        capMs: Long?,
        focusMs: Long,
        streakDays: Int,
    ): Parts = Parts(
        production = productionRatio(productionMs, consumptionMs),
        cap = capFactor(screenTimeMs, capMs),
        focus = (focusMs.toFloat() / FOCUS_FULL_CREDIT_MS).coerceIn(0f, 1f),
        streak = (streakDays / STREAK_FULL_CREDIT_DAYS).coerceIn(0f, 1f),
    )

    fun compute(
        productionMs: Long,
        consumptionMs: Long,
        screenTimeMs: Long,
        capMs: Long?,
        focusMs: Long,
        streakDays: Int,
    ): Int {
        val parts = parts(productionMs, consumptionMs, screenTimeMs, capMs, focusMs, streakDays)
        val weighted = PRODUCTION_WEIGHT * parts.production +
            CAP_WEIGHT * parts.cap +
            FOCUS_WEIGHT * parts.focus +
            STREAK_WEIGHT * parts.streak
        return (weighted * MAX).roundToInt().coerceIn(0, MAX)
    }

    /** Production share of categorised time; neutral when nothing is categorised yet. */
    fun productionRatio(productionMs: Long, consumptionMs: Long): Float {
        val accountable = productionMs + consumptionMs
        return if (accountable <= 0L) NEUTRAL else productionMs.toFloat() / accountable
    }

    private fun capFactor(screenTimeMs: Long, capMs: Long?): Float {
        if (capMs == null || capMs <= 0L) return NEUTRAL
        if (screenTimeMs <= capMs) return 1f

        val overageRoom = (CAP_DECAY_AT - 1f) * capMs
        val overage = (screenTimeMs - capMs).toFloat()
        return (1f - overage / overageRoom).coerceIn(0f, 1f)
    }
}
