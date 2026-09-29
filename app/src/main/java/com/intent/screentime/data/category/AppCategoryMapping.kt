package com.intent.screentime.data.category

import android.content.pm.ApplicationInfo
import com.intent.screentime.data.local.DefaultCategories

/**
 * Android's own app categories mapped onto CC0015 Intent's seed categories, with a
 * package-level pass in front of it.
 *
 * Split out from [CategoryClassifier] so the mapping is a pure function: the fallback
 * behaviour for an app Android cannot classify is the part most worth testing, and it
 * needs no `PackageManager` to get right.
 *
 * Document editors and readers are recognised by package name rather than left to
 * Android, because the OS files most of them loosely — a day spent writing in Docs was
 * landing outside the producing split entirely, and outside the focus detector with it.
 * Writing, annotating and reading documents is the work this app exists to make visible,
 * so those apps are Deep Work by name.
 */
object AppCategoryMapping {

    /**
     * Bump when the mapping below changes.
     *
     * A package is never re-asked once it has a stored row, so without a bump an improved
     * mapping would only ever reach apps the user had never opened. The version tells the
     * app to re-classify every automatic row once and re-score recent history after an
     * update, which is what `IntentApp`'s re-classification backfill reads.
     */
    const val CLASSIFIER_VERSION = 2

    /**
     * Apps whose whole purpose is documents: writing, spreadsheets, slides, notes, and
     * reading them. Google's Docs, Sheets, Slides and Drive all ship as one package.
     */
    private val DOCUMENT_PACKAGES = setOf(
        "com.google.android.apps.docs",
        "com.google.android.keep",
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "com.microsoft.office.onenote",
        "com.microsoft.office.officehubrow",
        "cn.wps.moffice_eng",
        "notion.id",
        "com.evernote",
        "com.samsung.android.app.notes",
        "md.obsidian",
        "com.adobe.reader",
    )

    fun defaultCategoryId(packageName: String, androidCategory: Int): String =
        if (packageName in DOCUMENT_PACKAGES) {
            DefaultCategories.PRODUCTION
        } else {
            androidCategoryId(androidCategory)
        }

    private fun androidCategoryId(androidCategory: Int): String = when (androidCategory) {
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
