package com.intent.screentime.data.category

import com.intent.screentime.data.local.entity.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The narrow suggestion rule.
 *
 * The rule is the only place the app guesses at an unsorted app's kind, so its bounds are
 * the contract: it must fire on the pattern it names, and stay silent everywhere else. A
 * suggestion on thin evidence would cost more trust than it saves taps.
 */
class TriageSuggestionTest {

    private val shortSession = 90_000L // 90 seconds
    private val longSession = 10 * 60_000L // 10 minutes

    @Test
    fun `many short sessions suggest consuming`() {
        assertEquals(
            CategoryKind.CONSUMPTION,
            TriageSuggestion.of(sessionCount = 6, averageSessionMs = shortSession),
        )
    }

    @Test
    fun `the boundary of both bounds still suggests consuming`() {
        assertEquals(
            CategoryKind.CONSUMPTION,
            TriageSuggestion.of(
                sessionCount = TriageSuggestion.MIN_SESSIONS,
                averageSessionMs = TriageSuggestion.SHORT_SESSION_MS,
            ),
        )
    }

    @Test
    fun `two sessions are not enough to suggest anything`() {
        assertNull(TriageSuggestion.of(sessionCount = 2, averageSessionMs = shortSession))
    }

    @Test
    fun `a long average session suggests nothing`() {
        assertNull(TriageSuggestion.of(sessionCount = 12, averageSessionMs = longSession))
    }

    @Test
    fun `a zero average suggests nothing`() {
        assertNull(TriageSuggestion.of(sessionCount = 12, averageSessionMs = 0L))
    }
}
