package com.intent.screentime.data.stats

import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.repository.AppCategoryRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageInsightsTest {
    private val minute = 60_000L

    @Test
    fun `a cap breach takes precedence over other insight types`() {
        val insight = AppUsageInsights.forApp(
            todayMs = 90 * minute,
            averageMs = 30 * minute,
            capMinutes = 60,
            category = ref(CategoryKind.CONSUMPTION),
        )

        assertEquals("This app is over its cap", insight?.title)
        assertEquals(AppUsageInsight.Action.CHANGE_CAP, insight?.action)
        assertTrue(insight?.body?.contains("30m over") == true)
    }

    @Test
    fun `uncategorised usage asks for a category before interpretation`() {
        val insight = AppUsageInsights.forApp(
            todayMs = 90 * minute,
            averageMs = 30 * minute,
            capMinutes = null,
            category = null,
        )

        assertEquals("Sort this app before judging its time", insight?.title)
        assertEquals(AppUsageInsight.Action.CHOOSE_CATEGORY, insight?.action)
    }

    @Test
    fun `usage above the prior average suggests a cap`() {
        val insight = AppUsageInsights.forApp(
            todayMs = 90 * minute,
            averageMs = 60 * minute,
            capMinutes = null,
            category = ref(CategoryKind.CONSUMPTION),
        )

        assertEquals("Today is higher than usual here", insight?.title)
        assertEquals(AppUsageInsight.Action.SET_CAP, insight?.action)
    }

    @Test
    fun `normal and trivial usage stay quiet`() {
        assertNull(
            AppUsageInsights.forApp(
                todayMs = 10 * minute,
                averageMs = 5 * minute,
                capMinutes = null,
                category = ref(CategoryKind.CONSUMPTION),
            ),
        )
        assertNull(
            AppUsageInsights.forApp(
                todayMs = 60 * minute,
                averageMs = 60 * minute,
                capMinutes = null,
                category = ref(CategoryKind.CONSUMPTION),
            ),
        )
    }

    @Test
    fun `long consumption usage offers a pause without a cap`() {
        val insight = AppUsageInsights.forApp(
            todayMs = 3 * 60 * minute,
            averageMs = 3 * 60 * minute,
            capMinutes = null,
            category = ref(CategoryKind.CONSUMPTION),
        )

        assertEquals("Worth a pause", insight?.title)
        assertEquals(AppUsageInsight.Action.SET_CAP, insight?.action)
    }

    private fun ref(kind: CategoryKind) = AppCategoryRef(
        id = "category",
        name = "Category",
        kind = kind,
        colorHex = "#000000",
    )
}
