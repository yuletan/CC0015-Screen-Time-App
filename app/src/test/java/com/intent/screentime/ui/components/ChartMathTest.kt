package com.intent.screentime.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    @Test
    fun `a peak is rounded up to the next clean step`() {
        assertEquals(0L, niceCeilMs(0L))
        assertEquals(15 * 60_000L, niceCeilMs(5 * 60_000L))
        assertEquals(30 * 60_000L, niceCeilMs(16 * 60_000L))
        assertEquals(3_600_000L, niceCeilMs(3_600_000L))
        assertEquals(5_400_000L, niceCeilMs(3_900_000L))
    }

    @Test
    fun `an axis never clips the peak it is scaling`() {
        val peaks = listOf(1L, 60_000L, 3_599_999L, 12_345_678L, 90_000_000L, 200_000_000L)

        peaks.forEach { peak ->
            assertTrue("ceil($peak) < peak", niceCeilMs(peak) >= peak)
        }
    }

    @Test
    fun `percentages round up on their own scale, not the duration one`() {
        assertEquals(50L, niceCeilPercent(48L))
        assertEquals(100L, niceCeilPercent(88L))
        assertEquals(10L, niceCeilPercent(1L))
        assertEquals(0L, niceCeilPercent(0L))
    }

    @Test
    fun `ticks always include both ends and stay within budget`() {
        val counts = listOf(1, 2, 3, 7, 24, 30, 90)

        counts.forEach { count ->
            val ticks = axisTickIndices(count, maxTicks = 5)

            assertEquals(0, ticks.first())
            assertEquals(count - 1, ticks.last())
            assertEquals(ticks.distinct(), ticks)
            assertTrue("$count produced ${ticks.size} ticks", ticks.size <= 5)
        }
    }

    @Test
    fun `hourly and daily series pick readable strides`() {
        assertEquals(listOf(0, 6, 12, 18, 23), axisTickIndices(24))
        assertEquals(listOf(0, 8, 16, 24, 29), axisTickIndices(30))
        assertEquals(listOf(0, 2, 4, 6), axisTickIndices(7))
    }

    @Test
    fun `an empty series has no ticks`() {
        assertEquals(emptyList<Int>(), axisTickIndices(0))
    }

    @Test
    fun `a scrub position maps to the nearest point and clamps at the edges`() {
        assertEquals(0, indexAtX(0f, count = 7, left = 0f, width = 300f))
        assertEquals(6, indexAtX(300f, count = 7, left = 0f, width = 300f))
        assertEquals(0, indexAtX(-40f, count = 7, left = 0f, width = 300f))
        assertEquals(6, indexAtX(900f, count = 7, left = 0f, width = 300f))
        assertEquals(3, indexAtX(152f, count = 7, left = 0f, width = 300f))
    }

    @Test
    fun `a scrub position is measured from the plot, not the canvas`() {
        assertEquals(0, indexAtX(left = 40f, x = 40f, count = 5, width = 100f))
        assertEquals(4, indexAtX(left = 40f, x = 140f, count = 5, width = 100f))
    }

    @Test
    fun `there is nothing to point at without an area to point in`() {
        assertNull(indexAtX(10f, count = 0, left = 0f, width = 100f))
        assertNull(indexAtX(10f, count = 7, left = 0f, width = 0f))
        assertNull(barIndexAtX(10f, count = 7, left = 0f, width = 0f))
    }

    @Test
    fun `every pixel inside a bar chart belongs to a bar`() {
        assertEquals(0, barIndexAtX(1f, count = 7, left = 0f, width = 350f))
        assertEquals(6, barIndexAtX(349f, count = 7, left = 0f, width = 350f))
        assertEquals(0, barIndexAtX(-20f, count = 7, left = 0f, width = 350f))
        assertEquals(6, barIndexAtX(900f, count = 7, left = 0f, width = 350f))
    }

    @Test
    fun `bar centres share the width evenly`() {
        assertEquals(25f, barCenterX(0, count = 4, left = 0f, width = 200f), 0.001f)
        assertEquals(175f, barCenterX(3, count = 4, left = 0f, width = 200f), 0.001f)
    }

    @Test
    fun `axis labels stay short enough to sit in a gutter`() {
        assertEquals("0", axisTickLabel(0L))
        assertEquals("45m", axisTickLabel(45 * 60_000L))
        assertEquals("3h", axisTickLabel(3 * 3_600_000L))
        assertEquals("1.5h", axisTickLabel(5_400_000L))
    }
}
