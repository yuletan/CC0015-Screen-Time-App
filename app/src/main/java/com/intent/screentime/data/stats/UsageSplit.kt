package com.intent.screentime.data.stats

import com.intent.screentime.data.local.DefaultCategories
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.repository.AppCategoryRef

/** The mutually exclusive buckets shown by the day split. */
data class UsageSplit(
    val productionMs: Long = 0L,
    val consumptionMs: Long = 0L,
    val utilityMs: Long = 0L,
    val neutralMs: Long = 0L,
    val unsortedMs: Long = 0L,
    val unsortedCount: Int = 0,
) {
    val totalMs: Long
        get() = productionMs + consumptionMs + utilityMs + neutralMs + unsortedMs

    val accountableMs: Long
        get() = productionMs + consumptionMs

    val productionShare: Float
        get() = if (accountableMs <= 0L) 0f else productionMs.toFloat() / accountableMs

    companion object {
        fun from(
            rows: List<DailyAppUsageEntity>,
            categories: Map<String, AppCategoryRef>,
        ): UsageSplit {
            var productionMs = 0L
            var consumptionMs = 0L
            var utilityMs = 0L
            var neutralMs = 0L
            var unsortedMs = 0L
            var unsortedCount = 0

            rows.forEach { row ->
                val category = categories[row.packageName]
                if (category == null || category.id == DefaultCategories.UNCATEGORIZED) {
                    unsortedMs += row.totalMs
                    unsortedCount += 1
                } else {
                    when (category.kind) {
                        CategoryKind.PRODUCTION -> productionMs += row.totalMs
                        CategoryKind.CONSUMPTION -> consumptionMs += row.totalMs
                        CategoryKind.UTILITY -> utilityMs += row.totalMs
                        CategoryKind.NEUTRAL -> neutralMs += row.totalMs
                    }
                }
            }

            return UsageSplit(
                productionMs = productionMs,
                consumptionMs = consumptionMs,
                utilityMs = utilityMs,
                neutralMs = neutralMs,
                unsortedMs = unsortedMs,
                unsortedCount = unsortedCount,
            )
        }
    }
}
