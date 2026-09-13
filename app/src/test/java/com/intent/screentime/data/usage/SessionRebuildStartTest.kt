package com.intent.screentime.data.usage

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the session rebuild window, which was the source of a real bug: using the
 * *maximum* of the ingest delta and the rebuild window meant days brought in by a
 * first-run backfill were stored but never aggregated.
 */
class SessionRebuildStartTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val now = 1_800_000_000_000L

    @Test
    fun `steady state reaches back the full rebuild window`() {
        // A normal 15-minute harvest must still rebuild far enough back to catch a
        // session that has been in the foreground for hours.
        val queryStart = now - 15 * minute
        assertEquals(now - 3 * day, UsageIngestor.sessionRebuildStart(queryStart, now))
    }

    @Test
    fun `a long gap rebuilds from the start of the gap`() {
        // After the phone has been off, sessions must be rebuilt for everything
        // re-ingested, otherwise the gap's events would never be aggregated.
        val queryStart = now - 5 * day
        assertEquals(queryStart, UsageIngestor.sessionRebuildStart(queryStart, now))
    }

    @Test
    fun `a first-run backfill rebuilds from the start of the backfill`() {
        val queryStart = now - 7 * day
        assertEquals(queryStart, UsageIngestor.sessionRebuildStart(queryStart, now))
    }

    @Test
    fun `never returns a negative timestamp`() {
        val earlyNow = 2 * day
        assertEquals(0L, UsageIngestor.sessionRebuildStart(0L, earlyNow))
    }
}
