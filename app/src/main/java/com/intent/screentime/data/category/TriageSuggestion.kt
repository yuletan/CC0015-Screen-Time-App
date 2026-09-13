package com.intent.screentime.data.category

import com.intent.screentime.data.local.entity.CategoryKind

/**
 * The one pattern worth naming in the triage queue.
 *
 * An app only reaches triage because Android returned `CATEGORY_UNDEFINED` for it, so we
 * genuinely do not know what it is. A guess is only useful if the user can trust it, and a
 * confident-sounding label on a two-session app would discredit every suggestion the app
 * ever makes. So the rule stays deliberately narrow: many sessions, each of them short, is
 * a checking habit — and that is something we can name without pretending to know more.
 */
object TriageSuggestion {

    /** Below this there is not enough signal to say anything at all. */
    const val MIN_SESSIONS = 5

    /** A session shorter than this, repeated, reads as a glance rather than a session. */
    const val SHORT_SESSION_MS = 3 * 60_000L

    /**
     * The suggested kind, or null when the evidence does not support a guess.
     *
     * A zero average is treated as "no evidence" rather than a very short session: it means
     * the app has no sessions at all, and an unsorted app with no usage is not a habit.
     */
    fun of(sessionCount: Int, averageSessionMs: Long): CategoryKind? =
        if (sessionCount >= MIN_SESSIONS && averageSessionMs in 1..SHORT_SESSION_MS) {
            CategoryKind.CONSUMPTION
        } else {
            null
        }
}
