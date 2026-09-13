package com.intent.screentime.data.local

import com.intent.screentime.data.local.entity.CategoryEntity
import com.intent.screentime.data.local.entity.CategoryKind

/**
 * Categories seeded on database creation.
 *
 * Ids are referenced by the automatic classifier, so they are stable constants rather
 * than generated values.
 */
object DefaultCategories {
    const val PRODUCTION = "production"
    const val LEARNING = "learning"
    const val SOCIAL = "social"
    const val VIDEO = "video"
    const val GAMES = "games"
    const val NEWS = "news"
    const val UTILITY = "utility"
    const val UNCATEGORIZED = "uncategorized"

    val ALL: List<CategoryEntity> = listOf(
        CategoryEntity(PRODUCTION, "Deep Work", CategoryKind.PRODUCTION, "#2E7D32", isDefault = true),
        CategoryEntity(LEARNING, "Learning", CategoryKind.PRODUCTION, "#7CB342", isDefault = true),
        CategoryEntity(SOCIAL, "Social", CategoryKind.CONSUMPTION, "#E53935", isDefault = true),
        CategoryEntity(VIDEO, "Video", CategoryKind.CONSUMPTION, "#FB8C00", isDefault = true),
        CategoryEntity(GAMES, "Games", CategoryKind.CONSUMPTION, "#8E24AA", isDefault = true),
        CategoryEntity(NEWS, "News & Feeds", CategoryKind.CONSUMPTION, "#6D4C41", isDefault = true),
        CategoryEntity(UTILITY, "Utility", CategoryKind.UTILITY, "#546E7A", isDefault = true),
        CategoryEntity(UNCATEGORIZED, "Uncategorised", CategoryKind.NEUTRAL, "#9E9E9E", isDefault = true),
    )

    /**
     * Insert statements run from [IntentDatabase]'s `onCreate`. Values come from the
     * compile-time constants above, so there is no injection surface.
     */
    val SEED_SQL: List<String> = ALL.map { category ->
        val isDefault = if (category.isDefault) 1 else 0
        "INSERT OR IGNORE INTO category (id, name, kind, colorHex, isDefault) " +
            "VALUES ('${category.id}', '${category.name}', '${category.kind.name}', " +
            "'${category.colorHex}', $isDefault)"
    }
}
