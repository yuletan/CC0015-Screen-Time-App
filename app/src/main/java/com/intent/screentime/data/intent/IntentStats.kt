package com.intent.screentime.data.intent

import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.IntentLogEntity

/**
 * What the intent log adds up to.
 *
 * The headline number is not how often someone opened Instagram — the app already knows
 * that — it is **how long they stayed after saying why**. "You said 'check messages' 34
 * times today, averaging 11 minutes each" is the observation that changes behaviour,
 * and it only exists because the stated intent and the following session are joined
 * together here.
 */
object IntentStats {

    data class Summary(
        val label: String,
        val count: Int,
        val averageFollowUpMs: Long,
    )

    fun summarize(
        logs: List<IntentLogEntity>,
        sessions: List<AppSessionEntity>,
        maxFollowUpMs: Long = DEFAULT_MAX_FOLLOW_UP_MS,
    ): List<Summary> {
        val byPackage = sessions.groupBy { it.packageName }
        val followUps = HashMap<String, MutableList<Long>>()

        for (log in logs) {
            val session = byPackage[log.packageName]
                ?.filter { it.startMs >= log.timestampMs }
                ?.minByOrNull { it.startMs }
                ?: continue

            val duration = (session.endMs - session.startMs).coerceIn(0L, maxFollowUpMs)
            if (duration <= 0L) continue
            followUps.getOrPut(log.intentLabel) { mutableListOf() }.add(duration)
        }

        return logs
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

    /** A session that starts within this window of the prompt is treated as its outcome. */
    private const val DEFAULT_MAX_FOLLOW_UP_MS = 60L * 60_000
}
