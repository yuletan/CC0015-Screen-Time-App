package com.intent.screentime.data.category

import android.content.pm.ApplicationInfo
import com.intent.screentime.data.local.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryMappingTest {

    private val plainPackage = "com.example.plain"

    @Test
    fun `android productivity maps to production`() {
        assertEquals(
            DefaultCategories.PRODUCTION,
            AppCategoryMapping.defaultCategoryId(plainPackage, ApplicationInfo.CATEGORY_PRODUCTIVITY),
        )
    }

    @Test
    fun `social maps to the social category`() {
        assertEquals(
            DefaultCategories.SOCIAL,
            AppCategoryMapping.defaultCategoryId(plainPackage, ApplicationInfo.CATEGORY_SOCIAL),
        )
    }

    @Test
    fun `video and audio both map to video`() {
        assertEquals(
            DefaultCategories.VIDEO,
            AppCategoryMapping.defaultCategoryId(plainPackage, ApplicationInfo.CATEGORY_VIDEO),
        )
        assertEquals(
            DefaultCategories.VIDEO,
            AppCategoryMapping.defaultCategoryId(plainPackage, ApplicationInfo.CATEGORY_AUDIO),
        )
    }

    @Test
    fun `games map to games`() {
        assertEquals(
            DefaultCategories.GAMES,
            AppCategoryMapping.defaultCategoryId(plainPackage, ApplicationInfo.CATEGORY_GAME),
        )
    }

    @Test
    fun `news maps to news and feeds`() {
        assertEquals(
            DefaultCategories.NEWS,
            AppCategoryMapping.defaultCategoryId(plainPackage, ApplicationInfo.CATEGORY_NEWS),
        )
    }

    @Test
    fun `maps and image tools are utility rather than either side of the split`() {
        assertEquals(
            DefaultCategories.UTILITY,
            AppCategoryMapping.defaultCategoryId(plainPackage, ApplicationInfo.CATEGORY_MAPS),
        )
        assertEquals(
            DefaultCategories.UTILITY,
            AppCategoryMapping.defaultCategoryId(plainPackage, ApplicationInfo.CATEGORY_IMAGE),
        )
    }

    @Test
    fun `document apps are deep work whatever Android calls them`() {
        // Google's Docs, Sheets, Slides and Drive all ship as one package, and Android
        // does not give it a reliable category — the package name is the fact, so it has
        // to win over an undefined, social or utility label.
        assertEquals(
            DefaultCategories.PRODUCTION,
            AppCategoryMapping.defaultCategoryId(
                "com.google.android.apps.docs",
                ApplicationInfo.CATEGORY_UNDEFINED,
            ),
        )
        assertEquals(
            DefaultCategories.PRODUCTION,
            AppCategoryMapping.defaultCategoryId(
                "com.microsoft.office.word",
                ApplicationInfo.CATEGORY_SOCIAL,
            ),
        )
        assertEquals(
            DefaultCategories.PRODUCTION,
            AppCategoryMapping.defaultCategoryId(
                "com.microsoft.office.excel",
                ApplicationInfo.CATEGORY_VIDEO,
            ),
        )
        assertEquals(
            DefaultCategories.PRODUCTION,
            AppCategoryMapping.defaultCategoryId(
                "notion.id",
                ApplicationInfo.CATEGORY_UNDEFINED,
            ),
        )
        assertEquals(
            DefaultCategories.PRODUCTION,
            AppCategoryMapping.defaultCategoryId(
                "com.adobe.reader",
                ApplicationInfo.CATEGORY_MAPS,
            ),
        )
    }

    @Test
    fun `a package outside the document list still follows Android's category`() {
        assertEquals(
            DefaultCategories.SOCIAL,
            AppCategoryMapping.defaultCategoryId(plainPackage, ApplicationInfo.CATEGORY_SOCIAL),
        )
    }

    @Test
    fun `an undefined category falls back to uncategorised`() {
        assertEquals(
            DefaultCategories.UNCATEGORIZED,
            AppCategoryMapping.defaultCategoryId(plainPackage, ApplicationInfo.CATEGORY_UNDEFINED),
        )
    }

    @Test
    fun `an unknown category value falls back to uncategorised`() {
        assertEquals(
            DefaultCategories.UNCATEGORIZED,
            AppCategoryMapping.defaultCategoryId(plainPackage, Int.MIN_VALUE),
        )
    }
}
