package com.intent.screentime.data.category

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Automatic category assignment.
 *
 * Uses the categories Android itself attaches to every installed app, so most apps are
 * classified with no effort from the user. Anything Android cannot classify falls back
 * to "Uncategorised", which the Apps screen prompts the user to resolve; the mapping
 * itself lives in [AppCategoryMapping].
 */
class CategoryClassifier(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager

    fun categoryIdFor(packageName: String): String =
        AppCategoryMapping.defaultCategoryId(packageName, appCategoryOf(packageName))

    private fun appCategoryOf(packageName: String): Int =
        try {
            packageManager.getApplicationInfo(packageName, 0).category
        } catch (_: PackageManager.NameNotFoundException) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }

    fun labelOf(packageName: String): String =
        try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
        }
}
