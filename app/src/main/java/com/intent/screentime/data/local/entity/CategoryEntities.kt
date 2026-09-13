package com.intent.screentime.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * How time in a category counts towards the Production vs Consumption score.
 *
 * UTILITY is deliberately neutral — banking, maps and camera time is neither productive
 * nor procrastination, and counting it either way would distort the score.
 */
enum class CategoryKind {
    PRODUCTION,
    CONSUMPTION,
    UTILITY,
    NEUTRAL,
}

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: CategoryKind,
    val colorHex: String,
    val isDefault: Boolean = false,
)

@Entity(
    tableName = "app_category",
    indices = [Index(value = ["categoryId"])],
)
data class AppCategoryEntity(
    @PrimaryKey val packageName: String,
    val categoryId: String,
    /** True once the user has explicitly set this, so re-classification never overwrites it. */
    val isUserOverride: Boolean = false,
)
