package com.intent.screentime.data.goals

import kotlin.math.roundToInt

/**
 * The composite Intent Score (0–100).
 *
 * The score is the sum of five point scores, and it is shown that way: out of 100, 30 come
 * from focus time, 20 from producing rather than consuming, 20 from staying under the cap,
 * 20 from keeping the night's quiet window and 10 from the streak. "24 of 30" on the screen
 * plus the other four rows gives the ring exactly — there is no hidden weighting left to
 * explain. The rows are listed heaviest first, so where a row sits is where it matters.
 *
 *  - **focus time (30 pts)** — one hour of focus earns full credit. Deliberately the
 *    heaviest row: a long uninterrupted stretch in a producing app is the clearest
 *    evidence the phone can see of the behaviour the app exists to encourage, and the
 *    score should reward it louder than anything else.
 *  - **producing (20 pts)** — of the time that is clearly one or the other, how much went
 *    into producing rather than consuming. Utility and unsorted time stays out of the ratio
 *    entirely rather than being miscounted.
 *  - **cap adherence (20 pts)** — full credit up to the cap, decaying linearly to zero at
 *    150% of it. A single bad hour should not zero the day; a doubling should.
 *  - **bedtime (20 pts)** — a kept quiet window earns all of it; a night that was broken
 *    earns none. Not using the phone at an hour you chose yourself is the commitment the
 *    rest of the app is trying to make visible, and a score that ignored it would be
 *    measuring enthusiasm for the app rather than the habit.
 *  - **streak (10 pts)** — seven consecutive days inside the cap earns full credit.
 *
 * Three of these can be **neutral**, and all three for the same reason: the score should
 * never punish a user for data or commitments the app does not have. No cap set, nothing
 * categorised yet, or a bedtime that has not opened tonight each contribute half credit
 * rather than zero.
 *
 * Pure and deterministic, so every one of those statements is unit tested.
 */
object IntentScore {

    const val MAX = 100

    /** What each row of the breakdown is worth. The one place the weights live. */
    const val FOCUS_MAX = 30
    const val PRODUCTION_MAX = 20
    const val CAP_MAX = 20
    const val BEDTIME_MAX = 20
    const val STREAK_MAX = 10

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
        val bedtime: Float,
        val focus: Float,
        val streak: Float,
    )

    /** The five rows as the user reads them: points earned, each out of its own maximum. */
    data class Points(
        val production: Int,
        val cap: Int,
        val bedtime: Int,
        val focus: Int,
        val streak: Int,
    ) {
        val total: Int get() = production + cap + bedtime + focus + streak
    }

    enum class ContributionKind {
        PRODUCTION,
        CAP,
        BEDTIME,
        FOCUS,
        STREAK,
    }

    enum class ContributionTone {
        POSITIVE,
        NEGATIVE,
        NEUTRAL,
    }

    data class Contribution(
        val kind: ContributionKind,
        val earned: Int,
        val available: Int,
        val tone: ContributionTone,
        val fraction: Float,
    )

    fun contributions(
        productionMs: Long,
        consumptionMs: Long,
        screenTimeMs: Long,
        capMs: Long?,
        focusMs: Long,
        streakDays: Int,
        bedtimeHeld: Boolean? = null,
    ): List<Contribution> {
        val parts = parts(
            productionMs = productionMs,
            consumptionMs = consumptionMs,
            screenTimeMs = screenTimeMs,
            capMs = capMs,
            focusMs = focusMs,
            streakDays = streakDays,
            bedtimeHeld = bedtimeHeld,
        )
        val points = points(
            productionMs = productionMs,
            consumptionMs = consumptionMs,
            screenTimeMs = screenTimeMs,
            capMs = capMs,
            focusMs = focusMs,
            streakDays = streakDays,
            bedtimeHeld = bedtimeHeld,
        )
        val productionTone = when {
            productionMs + consumptionMs <= 0L -> ContributionTone.NEUTRAL
            productionMs > consumptionMs -> ContributionTone.POSITIVE
            productionMs < consumptionMs -> ContributionTone.NEGATIVE
            else -> ContributionTone.NEUTRAL
        }
        val capTone = when {
            capMs == null -> ContributionTone.NEUTRAL
            screenTimeMs <= capMs -> ContributionTone.POSITIVE
            else -> ContributionTone.NEGATIVE
        }
        val bedtimeTone = when (bedtimeHeld) {
            null -> ContributionTone.NEUTRAL
            true -> ContributionTone.POSITIVE
            false -> ContributionTone.NEGATIVE
        }

        return listOf(
            Contribution(
                kind = ContributionKind.FOCUS,
                earned = points.focus,
                available = FOCUS_MAX,
                tone = if (focusMs > 0L) {
                    ContributionTone.POSITIVE
                } else {
                    ContributionTone.NEGATIVE
                },
                fraction = parts.focus,
            ),
            Contribution(
                kind = ContributionKind.PRODUCTION,
                earned = points.production,
                available = PRODUCTION_MAX,
                tone = productionTone,
                fraction = parts.production,
            ),
            Contribution(
                kind = ContributionKind.CAP,
                earned = points.cap,
                available = CAP_MAX,
                tone = capTone,
                fraction = parts.cap,
            ),
            Contribution(
                kind = ContributionKind.BEDTIME,
                earned = points.bedtime,
                available = BEDTIME_MAX,
                tone = bedtimeTone,
                fraction = parts.bedtime,
            ),
            Contribution(
                kind = ContributionKind.STREAK,
                earned = points.streak,
                available = STREAK_MAX,
                tone = if (streakDays > 0) {
                    ContributionTone.POSITIVE
                } else {
                    ContributionTone.NEGATIVE
                },
                fraction = parts.streak,
            ),
        )
    }

    fun parts(
        productionMs: Long,
        consumptionMs: Long,
        screenTimeMs: Long,
        capMs: Long?,
        focusMs: Long,
        streakDays: Int,
        bedtimeHeld: Boolean? = null,
    ): Parts = Parts(
        production = productionRatio(productionMs, consumptionMs),
        cap = capFactor(screenTimeMs, capMs),
        bedtime = bedtimeFactor(bedtimeHeld),
        focus = (focusMs.toFloat() / FOCUS_FULL_CREDIT_MS).coerceIn(0f, 1f),
        streak = (streakDays / STREAK_FULL_CREDIT_DAYS).coerceIn(0f, 1f),
    )

    fun points(
        productionMs: Long,
        consumptionMs: Long,
        screenTimeMs: Long,
        capMs: Long?,
        focusMs: Long,
        streakDays: Int,
        bedtimeHeld: Boolean? = null,
    ): Points {
        val parts = parts(
            productionMs = productionMs,
            consumptionMs = consumptionMs,
            screenTimeMs = screenTimeMs,
            capMs = capMs,
            focusMs = focusMs,
            streakDays = streakDays,
            bedtimeHeld = bedtimeHeld,
        )
        return Points(
            production = (parts.production * PRODUCTION_MAX).roundToInt(),
            cap = (parts.cap * CAP_MAX).roundToInt(),
            bedtime = (parts.bedtime * BEDTIME_MAX).roundToInt(),
            focus = (parts.focus * FOCUS_MAX).roundToInt(),
            streak = (parts.streak * STREAK_MAX).roundToInt(),
        )
    }

    /**
     * The total, as the sum of the rounded rows rather than a second rounding of the
     * unrounded sum. The breakdown on screen must add up to the number above it.
     */
    fun compute(
        productionMs: Long,
        consumptionMs: Long,
        screenTimeMs: Long,
        capMs: Long?,
        focusMs: Long,
        streakDays: Int,
        bedtimeHeld: Boolean? = null,
    ): Int = points(
        productionMs = productionMs,
        consumptionMs = consumptionMs,
        screenTimeMs = screenTimeMs,
        capMs = capMs,
        focusMs = focusMs,
        streakDays = streakDays,
        bedtimeHeld = bedtimeHeld,
    ).total.coerceIn(0, MAX)

    /** Production share of categorised time; neutral when nothing is categorised yet. */
    fun productionRatio(productionMs: Long, consumptionMs: Long): Float {
        val accountable = productionMs + consumptionMs
        return if (accountable <= 0L) NEUTRAL else productionMs.toFloat() / accountable
    }

    /**
     * A night is kept or it is not, so there is no partial credit to argue about.
     *
     * Null means there is nothing to judge — no window set, or tonight has not opened yet.
     * Half credit rather than zero, for the same reason the cap behaves that way.
     */
    private fun bedtimeFactor(held: Boolean?): Float = when (held) {
        null -> NEUTRAL
        true -> 1f
        false -> 0f
    }

    private fun capFactor(screenTimeMs: Long, capMs: Long?): Float {
        if (capMs == null || capMs <= 0L) return NEUTRAL
        if (screenTimeMs <= capMs) return 1f

        val overageRoom = (CAP_DECAY_AT - 1f) * capMs
        val overage = (screenTimeMs - capMs).toFloat()
        return (1f - overage / overageRoom).coerceIn(0f, 1f)
    }
}
