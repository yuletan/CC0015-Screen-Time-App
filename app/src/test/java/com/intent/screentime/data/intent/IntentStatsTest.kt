package com.intent.screentime.data.intent

import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.IntentLogEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentStatsTest {

    private fun log(packageName: String, label: String, timestampMs: Long) =
        IntentLogEntity(packageName = packageName, timestampMs = timestampMs, intentLabel = label)

    private fun session(packageName: String, startMs: Long, durationMs: Long) = AppSessionEntity(
        packageName = packageName,
        startMs = startMs,
        endMs = startMs + durationMs,
        durationMs = durationMs,
        dayEpochDay = 0L,
    )

    @Test
    fun `the session after a prompt becomes its follow-up`() {
        val summaries = IntentStats.summarize(
            logs = listOf(log("app.a", "Scrolling", timestampMs = 1_000L)),
            sessions = listOf(session("app.a", startMs = 2_000L, durationMs = 4 * 60_000L)),
        )

        assertEquals(1, summaries.size)
        assertEquals("Scrolling", summaries.single().label)
        assertEquals(1, summaries.single().count)
        assertEquals(4 * 60_000L, summaries.single().averageFollowUpMs)
    }

    @Test
    fun `a session that began before the prompt is not the follow-up`() {
        val summaries = IntentStats.summarize(
            logs = listOf(log("app.a", "Replying to someone", timestampMs = 1_000L)),
            sessions = listOf(session("app.a", startMs = 500L, durationMs = 10 * 60_000L)),
        )

        assertEquals(1, summaries.single().count)
        assertEquals(0L, summaries.single().averageFollowUpMs)
    }

    @Test
    fun `the earliest session after the prompt is the one that counts`() {
        val summaries = IntentStats.summarize(
            logs = listOf(log("app.a", "Scrolling", timestampMs = 1_000L)),
            sessions = listOf(
                session("app.a", startMs = 2_000L, durationMs = 2 * 60_000L),
                session("app.a", startMs = 30 * 60_000L, durationMs = 45 * 60_000L),
            ),
        )

        assertEquals(2 * 60_000L, summaries.single().averageFollowUpMs)
    }

    @Test
    fun `labels are aggregated across all the logs that used them`() {
        val summaries = IntentStats.summarize(
            logs = listOf(
                log("app.a", "Scrolling", timestampMs = 1_000L),
                log("app.b", "Scrolling", timestampMs = 1_000L),
                log("app.c", "Replying to someone", timestampMs = 1_000L),
            ),
            sessions = listOf(
                session("app.a", startMs = 2_000L, durationMs = 60_000L),
                session("app.b", startMs = 2_000L, durationMs = 3 * 60_000L),
            ),
        )

        val scrolling = summaries.first { it.label == "Scrolling" }
        assertEquals(2, scrolling.count)
        assertEquals(2 * 60_000L, scrolling.averageFollowUpMs)

        val replying = summaries.first { it.label == "Replying to someone" }
        assertEquals(1, replying.count)
        assertEquals(0L, replying.averageFollowUpMs)
    }

    @Test
    fun `the most common answer sorts first`() {
        val summaries = IntentStats.summarize(
            logs = listOf(
                log("app.a", "Scrolling", timestampMs = 1_000L),
                log("app.a", "Scrolling", timestampMs = 2_000L),
                log("app.b", "Checking something", timestampMs = 1_000L),
            ),
            sessions = emptyList(),
        )

        assertEquals("Scrolling", summaries.first().label)
    }
}
