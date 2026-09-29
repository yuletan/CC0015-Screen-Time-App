package com.intent.screentime.ui.components

import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.usage.HourlyBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the read-out actually says.
 *
 * The bubble is drawn on a Canvas, so its text never reaches the accessibility tree and
 * cannot be checked by looking at the app — which is exactly why the two functions that
 * compose it are pure and tested here instead.
 */
class HourlyStripReadoutTest {

    private fun bucket(
        hour: Int,
        minutes: Long?,
        kind: CategoryKind? = null,
        packageName: String? = null,
    ) = HourlyBreakdown.Bucket(
        hour = hour,
        totalMs = (minutes ?: 0L) * 60_000L,
        dominantKind = kind,
        dominantPackage = packageName,
    )

    private val names = mapOf(
        "com.google.android.youtube" to "YouTube",
        "com.android.chrome" to "Chrome",
    )
    private val labelOf: (String) -> String = { names[it] ?: it }

    @Test
    fun `the note names the app and what kind of time it was`() {
        val note = hourNote(
            bucket(18, 45, CategoryKind.CONSUMPTION, "com.google.android.youtube"),
            labelOf,
        )

        assertEquals("YouTube · Consuming", note)
    }

    @Test
    fun `a producing hour says so`() {
        val note = hourNote(bucket(9, 50, CategoryKind.PRODUCTION, "com.android.chrome"), labelOf)

        assertEquals("Chrome · Producing", note)
    }

    @Test
    fun `an hour with no dominant app has no note rather than a blank line`() {
        assertNull(hourNote(bucket(3, 10), labelOf))
    }

    @Test
    fun `without a namer the app is left out rather than shown as a package id`() {
        val note = hourNote(
            bucket(18, 45, CategoryKind.CONSUMPTION, "com.google.android.youtube"),
            null,
        )

        assertNull(note)
    }

    @Test
    fun `an uncategorised app is still named, just without a kind`() {
        val note = hourNote(bucket(18, 45, packageName = "com.example.thing"), labelOf)

        assertEquals("com.example.thing", note)
    }

    @Test
    fun `the spoken form carries the hour, the minutes and the app`() {
        val spoken = describeHour(
            bucket(18, 45, CategoryKind.CONSUMPTION, "com.google.android.youtube"),
            labelOf,
        )

        assertEquals("6p, 45m, YouTube · Consuming", spoken)
    }

    @Test
    fun `an empty hour is described as empty rather than as zero minutes`() {
        assertEquals("6p, no screen time", describeHour(bucket(18, null), labelOf))
    }

    @Test
    fun `a used hour with no named app still reports its minutes`() {
        assertEquals("9a, 12m", describeHour(bucket(9, 12), labelOf))
    }
}
