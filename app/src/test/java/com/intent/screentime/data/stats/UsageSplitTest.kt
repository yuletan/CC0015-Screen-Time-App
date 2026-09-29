package com.intent.screentime.data.stats

import com.intent.screentime.data.local.DefaultCategories
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.repository.AppCategoryRef
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageSplitTest {
    private val minute = 60_000L

    @Test
    fun `explicit neutral and unsorted time stay in separate buckets`() {
        val rows = listOf(
            row("work", 30 * minute),
            row("social", 20 * minute),
            row("maps", 10 * minute),
            row("neutral", 15 * minute),
            row("unknown", 5 * minute),
            row("placeholder", 7 * minute),
        )
        val categories = mapOf(
            "work" to ref("work", CategoryKind.PRODUCTION),
            "social" to ref("social", CategoryKind.CONSUMPTION),
            "maps" to ref("maps", CategoryKind.UTILITY),
            "neutral" to ref("neutral", CategoryKind.NEUTRAL),
            "placeholder" to ref(DefaultCategories.UNCATEGORIZED, CategoryKind.NEUTRAL),
        )

        val split = UsageSplit.from(rows, categories)

        assertEquals(30 * minute, split.productionMs)
        assertEquals(20 * minute, split.consumptionMs)
        assertEquals(10 * minute, split.utilityMs)
        assertEquals(15 * minute, split.neutralMs)
        assertEquals(12 * minute, split.unsortedMs)
        assertEquals(2, split.unsortedCount)
        assertEquals(87 * minute, split.totalMs)
    }

    @Test
    fun `production share excludes utility neutral and unsorted time`() {
        val split = UsageSplit.from(
            rows = listOf(
                row("work", 30 * minute),
                row("social", 90 * minute),
                row("maps", 10 * minute),
                row("neutral", 5 * minute),
                row("unknown", 25 * minute),
            ),
            categories = mapOf(
                "work" to ref("work", CategoryKind.PRODUCTION),
                "social" to ref("social", CategoryKind.CONSUMPTION),
                "maps" to ref("maps", CategoryKind.UTILITY),
                "neutral" to ref("neutral", CategoryKind.NEUTRAL),
            ),
        )

        assertEquals(0.25f, split.productionShare, 0.0001f)
    }

    private fun row(packageName: String, totalMs: Long) = DailyAppUsageEntity(
        dayEpochDay = 1L,
        packageName = packageName,
        totalMs = totalMs,
        sessionCount = 1,
        firstUseMs = null,
        lastUseMs = null,
    )

    private fun ref(id: String, kind: CategoryKind) = AppCategoryRef(
        id = id,
        name = id,
        kind = kind,
        colorHex = "#000000",
    )
}
