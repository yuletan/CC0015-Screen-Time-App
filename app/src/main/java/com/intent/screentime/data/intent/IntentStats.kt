package com.intent.screentime.data.intent

import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.IntentLogEntity
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * What the intent log adds up to.
 *
 * The headline number is not how often someone opened Instagram — the app already knows
 * that — it is **how long they stayed after saying why**. "You said 'check messages' 34
 * times today, averaging 11 minutes each" is the observation that changes behaviour,
 * and it only exists because the stated intent and the following session are joined
 * together here.
 *
 * Everything here is pure: it takes already-loaded lists and touches no DAO, so the same
 * arithmetic serves the Insights screen and the tests.
 */
object IntentStats {

    data class Summary(
        val label: String,
        val count: Int,
        val averageFollowUpMs: Long,
    )

    /**
     * One range's worth of the reason ledger.
     *
     * [answered] and [skipped] sit beside the breakdown rather than being derived from it,
     * because the denominator is itself a finding: "you answered 34 of 51" says something
     * the answered rows alone cannot. A skipped row has no label, so it is deliberately
     * absent from [overall] and [byPackage].
     */
    data class Ledger(
        val answered: Int,
        val skipped: Int,
        val overall: List<Summary>,
        val byPackage: Map<String, List<Summary>>,
        /** Answered prompts per local hour, 24 entries. A [List] rather than an
         *  [IntArray] so two equal ledgers stay `equals`-equal. */
        val hourCounts: List<Int>,
        val topReasonLabel: String?,
        /** e.g. "22:00–01:00", or null when there is too little to claim a cluster. */
        val topReasonWindow: String?,
    )

    /**
     * The fewest occurrences that can support a cluster claim.
     *
     * Below this a "peak" is just where two opens happened to land, and the app would
     * rather say nothing than dress noise up as a pattern.
     */
    const val MIN_CLUSTER_SAMPLES = 5

    fun summarize(
        logs: List<IntentLogEntity>,
        sessions: List<AppSessionEntity>,
        maxFollowUpMs: Long = DEFAULT_MAX_FOLLOW_UP_MS,
    ): List<Summary> {
        // Skipped rows carry an empty label, so folding them in here would add a blank
        // "reason" row whose count is really just a count of unanswered prompts. The
        // denominator belongs to [ledger], not to a reason breakdown.
        val answered = logs.filterNot { it.skipped }

        val byPackage = sessions.groupBy { it.packageName }
        val followUps = HashMap<String, MutableList<Long>>()

        for (log in answered) {
            val session = byPackage[log.packageName]
                ?.filter { it.startMs >= log.timestampMs }
                ?.minByOrNull { it.startMs }
                ?: continue

            val duration = (session.endMs - session.startMs).coerceIn(0L, maxFollowUpMs)
            if (duration <= 0L) continue
            followUps.getOrPut(log.intentLabel) { mutableListOf() }.add(duration)
        }

        return answered
            .groupingBy { it.intentLabel }
            .eachCount()
            .map { (label, count) ->
                val durations = followUps[label].orEmpty()
                Summary(
                    label = label,
                    count = count,
                    averageFollowUpMs = if (durations.isEmpty()) {
                        0L
                    } else {
                        durations.sum() / durations.size
                    },
                )
            }
            .sortedByDescending { it.count }
    }

    /**
     * The whole ledger for one range: the answer rate, the reasons, and — when the
     * evidence supports it — a clock window the most common reason clusters in.
     */
    fun ledger(
        logs: List<IntentLogEntity>,
        sessions: List<AppSessionEntity>,
        zone: ZoneId,
    ): Ledger {
        val answered = logs.filterNot { it.skipped }
        val overall = summarize(answered, sessions)

        val hourCounts = MutableList(HOURS) { 0 }
        answered.forEach { hourCounts[hourOf(it.timestampMs, zone)] += 1 }

        val topReason = overall.firstOrNull()
        // The window is a claim about the *top reason*, so it waits until that reason —
        // not merely the range — clears MIN_CLUSTER_SAMPLES.
        val window = topReason
            ?.takeIf { it.count >= MIN_CLUSTER_SAMPLES }
            ?.let { densestWindow(answered.filter { log -> log.intentLabel == it.label }, zone) }

        return Ledger(
            answered = answered.size,
            skipped = logs.size - answered.size,
            overall = overall,
            byPackage = answered
                .groupBy { it.packageName }
                .mapValues { (_, rows) -> summarize(rows, sessions) },
            hourCounts = hourCounts,
            topReasonLabel = topReason?.label,
            topReasonWindow = window,
        )
    }

    /**
     * The densest 3-hour window, scanned across all 24 wrap-around start hours.
     *
     * The wrap is the whole point: a late-evening habit opens at 22:00 and runs past
     * midnight, so a window is allowed to cross 24:00 instead of being cut off at the day
     * boundary — the naive scan would report 22:00–23:00 and miss half the cluster.
     */
    private fun densestWindow(logs: List<IntentLogEntity>, zone: ZoneId): String? {
        if (logs.isEmpty()) return null

        val counts = MutableList(HOURS) { 0 }
        logs.forEach { counts[hourOf(it.timestampMs, zone)] += 1 }

        var bestStart = 0
        var bestSum = -1
        for (start in 0 until HOURS) {
            val sum = (0 until WINDOW_HOURS).sumOf { counts[(start + it) % HOURS] }
            if (sum > bestSum) {
                bestSum = sum
                bestStart = start
            }
        }

        val end = (bestStart + WINDOW_HOURS) % HOURS
        val startLabel = String.format(Locale.ROOT, "%02d:00", bestStart)
        val endLabel = String.format(Locale.ROOT, "%02d:00", end)
        return "$startLabel–$endLabel"
    }

    private fun hourOf(timestampMs: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(timestampMs).atZone(zone).hour

    /** A session that starts within this window of the prompt is treated as its outcome. */
    private const val DEFAULT_MAX_FOLLOW_UP_MS = 60L * 60_000

    private const val HOURS = 24
    private const val WINDOW_HOURS = 3
}
