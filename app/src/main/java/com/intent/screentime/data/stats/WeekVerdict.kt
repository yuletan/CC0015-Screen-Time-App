package com.intent.screentime.data.stats

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The week, read back as a ranked set of findings.
 *
 * A single headline number would have to average three things that do not share a unit,
 * and any weighting would be an opinion dressed as arithmetic. So instead the three are
 * each judged on their own terms and then ordered by how far they actually moved, which
 * answers the question a person asks first: *what changed this week?*
 *
 * Ranking is by relative movement — the same ratio for all three, so a share moving from
 * 20% to 30% competes fairly with screen time falling by half. That ratio is for ordering
 * only and is never shown; the sentences carry the plain figures, because "your production
 * share rose 50%" describes a ten-point move in a way nobody finds helpful.
 *
 * A move inside [STALL_BAND_PERCENT] is reported as stalled rather than as a direction.
 * Weighed daily, screen time wobbles by a few percent on nothing at all, and calling that
 * progress is how a tracker teaches its user to stop believing it.
 */
object WeekVerdict {

    enum class Metric { SCREEN_TIME, PRODUCTION_SHARE, MINDLESS_OPENS }

    enum class Tone { IMPROVED, STALLED, SLIPPED }

    data class Finding(
        val metric: Metric,
        val tone: Tone,
        /** For ordering only — see the class comment. */
        val magnitudePercent: Int,
        val sentence: String,
    )

    /** Movement smaller than this is noise, not a finding. */
    const val STALL_BAND_PERCENT = 5

    /** Where the scale tops out. A move from nothing is reported as large, not infinite. */
    private const val MAX_MAGNITUDE = 100

    /**
     * Ranked, biggest genuine move first. Empty when there is no previous week to compare
     * against — a first week has no verdict to give, only a baseline.
     */
    fun rank(result: WeekOverWeek.Result): List<Finding> {
        if (!result.hasPrevious) return emptyList()

        val candidates = buildList {
            add(finding(Metric.SCREEN_TIME, result.screenTime.current, result.screenTime.previous, lowerIsBetter = true, result = result))
            // Only when something was actually accountable in one of the windows: a share
            // of zero on both sides is not a ratio that held, it is an absence of data.
            if (result.productionShare.current > 0L || result.productionShare.previous > 0L) {
                add(finding(Metric.PRODUCTION_SHARE, result.productionShare.current, result.productionShare.previous, lowerIsBetter = false, result = result))
            }
            add(finding(Metric.MINDLESS_OPENS, result.mindlessOpens.current, result.mindlessOpens.previous, lowerIsBetter = true, result = result))
        }

        return candidates.sortedWith(
            compareByDescending<Finding> { it.magnitudePercent }.thenBy { it.metric.ordinal },
        )
    }

    private fun finding(
        metric: Metric,
        current: Long,
        previous: Long,
        lowerIsBetter: Boolean,
        result: WeekOverWeek.Result,
    ): Finding {
        val (magnitude, tone) = assess(current, previous, lowerIsBetter)
        return Finding(
            metric = metric,
            tone = tone,
            magnitudePercent = magnitude,
            sentence = sentenceFor(metric, tone, result),
        )
    }

    private fun assess(current: Long, previous: Long, lowerIsBetter: Boolean): Pair<Int, Tone> {
        if (current == 0L && previous == 0L) return 0 to Tone.STALLED

        val magnitude = if (previous == 0L) {
            MAX_MAGNITUDE
        } else {
            (abs(current - previous).toDouble() / previous * 100.0)
                .roundToLong()
                .toInt()
                .coerceAtMost(MAX_MAGNITUDE)
        }

        val improved = if (lowerIsBetter) current < previous else current > previous
        val tone = when {
            magnitude <= STALL_BAND_PERCENT -> Tone.STALLED
            improved -> Tone.IMPROVED
            else -> Tone.SLIPPED
        }
        return magnitude to tone
    }

    private fun sentenceFor(metric: Metric, tone: Tone, result: WeekOverWeek.Result): String =
        when (metric) {
            Metric.SCREEN_TIME -> when {
                tone == Tone.STALLED -> "Screen time is level with the same point last week."
                result.screenTime.percent == null -> "Screen time where last week recorded none."
                result.screenTime.percent!! < 0 ->
                    "Screen time down ${abs(result.screenTime.percent!!)}% against the same point last week."

                else -> "Screen time up ${result.screenTime.percent}% on the same point last week."
            }

            Metric.PRODUCTION_SHARE -> {
                val current = result.productionShare.current
                val previous = result.productionShare.previous
                when (tone) {
                    Tone.IMPROVED -> "More of your time went on producing things — $current% against $previous%."
                    Tone.SLIPPED -> "Less of your time went on producing things — $current% against $previous%."
                    Tone.STALLED -> "Your split between producing and consuming held at about $current%."
                }
            }

            Metric.MINDLESS_OPENS -> {
                val current = result.mindlessOpens.current
                val previous = result.mindlessOpens.previous
                when (tone) {
                    Tone.IMPROVED -> "Fewer opens that were drift or boredom — $current against $previous."
                    Tone.SLIPPED -> "More opens that were drift or boredom — $current against $previous."
                    Tone.STALLED -> "Opens out of drift or boredom held at about $current."
                }
            }
        }
}
