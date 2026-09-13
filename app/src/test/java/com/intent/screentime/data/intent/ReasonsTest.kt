package com.intent.screentime.data.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chip vocabulary has to survive a round trip, because a row's key is what the ledger
 * groups by while its label is what the user reads. If they ever drift, one of them is
 * silently wrong.
 */
class ReasonsTest {

    @Test
    fun `every option label round-trips back to itself`() {
        Reasons.OPTIONS.forEach { option ->
            val key = Reasons.keyForLabel(option.label)
            assertEquals(option.key, key)
            assertEquals(option.label, Reasons.labelFor(key))
        }
    }

    @Test
    fun `optionFor resolves the whole option`() {
        Reasons.OPTIONS.forEach { option ->
            assertEquals(option, Reasons.optionFor(option.key))
        }
    }

    @Test
    fun `keys are unique`() {
        val keys = Reasons.OPTIONS.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun `an unknown label resolves to null`() {
        assertNull(Reasons.keyForLabel("Doomscrolling"))
        assertNull(Reasons.labelFor("not-a-key"))
        assertNull(Reasons.optionFor(null))
    }

    @Test
    fun `the vocabulary is not empty`() {
        assertTrue(Reasons.OPTIONS.isNotEmpty())
    }
}
