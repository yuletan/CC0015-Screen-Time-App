package com.intent.screentime.data.intent

import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.IntentLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class IntentStatsTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun log(
        packageName: String,
        label: String,
        timestampMs: Long,
        skipped: Boolean = false,
    ) = IntentLogEntity(
        packageName = packageName,
        timestampMs = timestampMs,
        intentLabel = label,
        skipped = skipped,
    )

    private fun session(packageName: String, startMs: Long, durationMs: Long) = AppSessionEntity(
        packageName = packageName,
        startMs = startMs,
        endMs = startMs + durationMs,
        durationMs = durationMs,
        dayEpochDay = 0L,
    )

    /** A deterministic local timestamp, so hour bucketing does not depend on the JVM zone. */
    private fun atHour(hour: Int): Long =
        LocalDate.of(2024, 1, 1)
            .atTime(hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

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

    @Test
    fun `skipped rows never reach the reason breakdown`() {
        val summaries = IntentStats.summarize(
            logs = listOf(
                log("app.a", "Scrolling", timestampMs = 1_000L),
                log("app.a", "", timestampMs = 2_000L, skipped = true),
                log("app.a", "", timestampMs = 3_000L, skipped = true),
            ),
            sessions = emptyList(),
        )

        // One row, not two — and certainly not a blank-labelled third.
        assertEquals(1, summaries.size)
        assertEquals("Scrolling", summaries.single().label)
        assertEquals(1, summaries.single().count)
    }

    @Test
    fun `the ledger counts answered against skipped`() {
        val ledger = IntentStats.ledger(
            logs = listOf(
                log("app.a", "Scrolling", timestampMs = atHour(9)),
                log("app.a", "Scrolling", timestampMs = atHour(10)),
                log("app.a", "", timestampMs = atHour(11), skipped = true),
                log("app.a", "", timestampMs = atHour(12), skipped = true),
                log("app.a", "", timestampMs = atHour(13), skipped = true),
            ),
            sessions = emptyList(),
            zone = zone,
        )

        assertEquals(2, ledger.answered)
        assertEquals(3, ledger.skipped)
        assertEquals(2, ledger.overall.single().count)
    }

    @Test
    fun `hour counts bucket answered prompts by local hour`() {
        val ledger = IntentStats.ledger(
            logs = listOf(
                log("app.a", "Scrolling", timestampMs = atHour(9)),
                log("app.a", "Checking something", timestampMs = atHour(9)),
                log("app.a", "Scrolling", timestampMs = atHour(21)),
                // A skipped prompt is not an open, so it must not fill an hour.
                log("app.a", "", timestampMs = atHour(9), skipped = true),
            ),
            sessions = emptyList(),
            zone = zone,
        )

        assertEquals(24, ledger.hourCounts.size)
        assertEquals(2, ledger.hourCounts[9])
        assertEquals(1, ledger.hourCounts[21])
        assertEquals(0, ledger.hourCounts[0])
        assertEquals(3, ledger.hourCounts.sum())
    }

    @Test
    fun `the top reason window wraps around midnight`() {
        val ledger = IntentStats.ledger(
            logs = listOf(
                log("app.a", "Killing time", timestampMs = atHour(22)),
                log("app.a", "Killing time", timestampMs = atHour(22)),
                log("app.a", "Killing time", timestampMs = atHour(23)),
                log("app.a", "Killing time", timestampMs = atHour(23)),
                log("app.a", "Killing time", timestampMs = atHour(0)),
            ),
            sessions = emptyList(),
            zone = zone,
        )

        assertEquals("Killing time", ledger.topReasonLabel)
        // A non-wrapping scan would report "22:00–23:00" and miss the 00:00 sample.
        assertEquals("22:00\u201301:00", ledger.topReasonWindow)
    }

    @Test
    fun `the window stays silent below the sample floor`() {
        val ledger = IntentStats.ledger(
            logs = listOf(
                log("app.a", "Scrolling", timestampMs = atHour(21)),
                log("app.a", "Scrolling", timestampMs = atHour(21)),
                log("app.a", "Scrolling", timestampMs = atHour(22)),
                log("app.a", "Scrolling", timestampMs = atHour(22)),
            ),
            sessions = emptyList(),
            zone = zone,
        )

        // Four samples is under MIN_CLUSTER_SAMPLES, so the label stands but no window
        // is claimed — a cluster from four opens is noise.
        assertEquals("Scrolling", ledger.topReasonLabel)
        assertNull(ledger.topReasonWindow)
    }
}
