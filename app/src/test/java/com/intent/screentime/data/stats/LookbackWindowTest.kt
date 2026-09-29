package com.intent.screentime.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookbackWindowTest {

    private val today = 20_000L

    @Test
    fun `offset zero is the window ending today`() {
        val window = LookbackWindow.of(today, days = 7, offset = 0)

        assertEquals(today - 6, window.first)
        assertEquals(today, window.last)
        assertEquals(7L, window.count().toLong())
    }

    @Test
    fun `stepping back lands immediately before the previous window`() {
        val current = LookbackWindow.of(today, days = 7, offset = 0)
        val previous = LookbackWindow.of(today, days = 7, offset = 1)

        assertEquals(current.first - 1, previous.last)
        assertEquals(7L, previous.count().toLong())
    }

    @Test
    fun `a single day range steps one day at a time`() {
        assertEquals(today - 1..today - 1, LookbackWindow.of(today, days = 1, offset = 1))
    }

    @Test
    fun `there is nowhere to step without history`() {
        assertEquals(0, LookbackWindow.maxOffset(today, earliestDay = null, days = 7))
        assertEquals(0, LookbackWindow.maxOffset(today, earliestDay = today, days = 7))
    }

    @Test
    fun `the current window covers a week of history without any step back`() {
        assertEquals(0, LookbackWindow.maxOffset(today, earliestDay = today - 6, days = 7))
    }

    @Test
    fun `stepping stops at the window that first contains tracked history`() {
        assertEquals(1, LookbackWindow.maxOffset(today, earliestDay = today - 7, days = 7))
        assertEquals(2, LookbackWindow.maxOffset(today, earliestDay = today - 14, days = 7))
    }

    @Test
    fun `the deepest allowed window still contains the first tracked day`() {
        val earliest = today - 20
        val steps = LookbackWindow.maxOffset(today, earliest, days = 7)
        val deepest = LookbackWindow.of(today, days = 7, offset = steps)

        assertTrue(deepest.first <= earliest && earliest <= deepest.last)
    }
}
