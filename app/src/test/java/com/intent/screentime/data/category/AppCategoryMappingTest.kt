package com.intent.screentime.data.category

import android.content.pm.ApplicationInfo
import com.intent.screentime.data.local.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryMappingTest {

    @Test
    fun `android productivity maps to production`() {
        assertEquals(
            DefaultCategories.PRODUCTION,
            AppCategoryMapping.defaultCategoryId(ApplicationInfo.CATEGORY_PRODUCTIVITY),
        )
    }

    @Test
    fun `social maps to the social category`() {
        assertEquals(
            DefaultCategories.SOCIAL,
            AppCategoryMapping.defaultCategoryId(ApplicationInfo.CATEGORY_SOCIAL),
        )
    }

    @Test
    fun `video and audio both map to video`() {
        assertEquals(
            DefaultCategories.VIDEO,
            AppCategoryMapping.defaultCategoryId(ApplicationInfo.CATEGORY_VIDEO),
        )
        assertEquals(
            DefaultCategories.VIDEO,
            AppCategoryMapping.defaultCategoryId(ApplicationInfo.CATEGORY_AUDIO),
        )
    }

    @Test
    fun `games map to games`() {
        assertEquals(
            DefaultCategories.GAMES,
            AppCategoryMapping.defaultCategoryId(ApplicationInfo.CATEGORY_GAME),
        )
    }

    @Test
    fun `news maps to news and feeds`() {
        assertEquals(
            DefaultCategories.NEWS,
            AppCategoryMapping.defaultCategoryId(ApplicationInfo.CATEGORY_NEWS),
        )
    }

    @Test
    fun `maps and image tools are utility rather than either side of the split`() {
        assertEquals(
            DefaultCategories.UTILITY,
            AppCategoryMapping.defaultCategoryId(ApplicationInfo.CATEGORY_MAPS),
        )
        assertEquals(
            DefaultCategories.UTILITY,
            AppCategoryMapping.defaultCategoryId(ApplicationInfo.CATEGORY_IMAGE),
        )
    }

    @Test
    fun `an undefined category falls back to uncategorised`() {
        assertEquals(
            DefaultCategories.UNCATEGORIZED,
            AppCategoryMapping.defaultCategoryId(ApplicationInfo.CATEGORY_UNDEFINED),
        )
    }

    @Test
    fun `an unknown category value falls back to uncategorised`() {
        assertEquals(
            DefaultCategories.UNCATEGORIZED,
            AppCategoryMapping.defaultCategoryId(Int.MIN_VALUE),
        )
    }
}
