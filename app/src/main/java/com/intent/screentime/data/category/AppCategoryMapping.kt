package com.intent.screentime.data.category

import android.content.pm.ApplicationInfo
import com.intent.screentime.data.local.DefaultCategories

/**
 * Android's own app categories mapped onto Intent's seed categories.
 *
 * Split out from [CategoryClassifier] so the mapping is a pure function: the fallback
 * behaviour for an app Android cannot classify is the part most worth testing, and it
 * needs no `PackageManager` to get right.
 */
object AppCategoryMapping {

    fun defaultCategoryId(androidCategory: Int): String = when (androidCategory) {
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> DefaultCategories.PRODUCTION
        ApplicationInfo.CATEGORY_SOCIAL -> DefaultCategories.SOCIAL
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_AUDIO,
        -> DefaultCategories.VIDEO
        ApplicationInfo.CATEGORY_GAME -> DefaultCategories.GAMES
        ApplicationInfo.CATEGORY_NEWS -> DefaultCategories.NEWS
        ApplicationInfo.CATEGORY_MAPS,
        ApplicationInfo.CATEGORY_IMAGE,
        -> DefaultCategories.UTILITY
        else -> DefaultCategories.UNCATEGORIZED
    }
}
