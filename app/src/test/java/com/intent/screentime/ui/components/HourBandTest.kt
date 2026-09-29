package com.intent.screentime.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The thresholds are the whole feature, so the boundaries are what these tests are about:
 * a minute either side of thirty and forty-five decides which colour an hour wears, and
 * off-by-one there is the difference between "fine" and "the phone ate the evening".
 */
class HourBandTest {

    private val minute = 60_000L

    @Test
    fun `an untouched hour is calm`() {
        assertEquals(HourBand.CALM, HourBand.of(0L))
    }

    @Test
    fun `just under half an hour is still calm`() {
        assertEquals(HourBand.CALM, HourBand.of(29 * minute + 59_000L))
    }

    @Test
    fun `exactly half an hour turns amber`() {
        assertEquals(HourBand.WATCHFUL, HourBand.of(30 * minute))
    }

    @Test
    fun `just under three quarters of an hour is still amber`() {
        assertEquals(HourBand.WATCHFUL, HourBand.of(44 * minute + 59_000L))
    }

    @Test
    fun `three quarters of an hour turns red`() {
        assertEquals(HourBand.STRAINED, HourBand.of(45 * minute))
    }

    @Test
    fun `a full hour is red`() {
        assertEquals(HourBand.STRAINED, HourBand.of(60 * minute))
    }

    @Test
    fun `every band carries a word as well as a colour`() {
        val labels = HourBand.entries.map { it.label() }

        assertEquals(3, labels.size)
        assertEquals(3, labels.toSet().size)
    }
}
