package com.intent.screentime.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Target progress maths as the Today screen states it.
 *
 * The audit called this out as untested, and it is the arithmetic behind the hero ring:
 * "how much room is left" and "how far over" have to be exactly right or the whole
 * screen lies.
 */
class TodayUiStateTest {

    private val hour = 60 * 60_000L

    @Test
    fun `no cap means no target and no overage`() {
        val state = TodayUiState(screenTimeMs = 3 * hour, capMinutes = null)

        assertNull(state.capMs)
        assertFalse(state.overCap)
        assertEquals(0L, state.overageMs)
    }

    @Test
    fun `a zero or negative cap is treated as no cap`() {
        assertNull(TodayUiState(capMinutes = 0).capMs)
        assertNull(TodayUiState(capMinutes = -30).capMs)
    }

    @Test
    fun `a cap under the day's usage reports the overage exactly`() {
        val state = TodayUiState(screenTimeMs = 4 * hour + 12 * 60_000L, capMinutes = 240)

        assertEquals(4 * hour, state.capMs)
        assertTrue(state.overCap)
        assertEquals(12 * 60_000L, state.overageMs)
    }

    @Test
    fun `being exactly at the cap is not over it`() {
        val state = TodayUiState(screenTimeMs = 4 * hour, capMinutes = 240)

        assertFalse(state.overCap)
        assertEquals(0L, state.overageMs)
    }

    @Test
    fun `being under the cap has no overage`() {
        val state = TodayUiState(screenTimeMs = 2 * hour, capMinutes = 240)

        assertFalse(state.overCap)
        assertEquals(0L, state.overageMs)
    }

    @Test
    fun `production share is zero when nothing is categorised`() {
        val state = TodayUiState(productionMs = 0L, consumptionMs = 0L)

        assertEquals(0f, state.productionShare, 0.0001f)
    }

    @Test
    fun `production share ignores utility and unsorted time`() {
        val state = TodayUiState(
            productionMs = 30 * 60_000L,
            consumptionMs = 90 * 60_000L,
            utilityMs = 10 * hour,
            neutralMs = 5 * hour,
        )

        assertEquals(0.25f, state.productionShare, 0.0001f)
    }

    @Test
    fun `has data only once something has been tracked`() {
        assertFalse(TodayUiState().hasData)
        assertTrue(TodayUiState(screenTimeMs = 60_000L).hasData)
    }
}
