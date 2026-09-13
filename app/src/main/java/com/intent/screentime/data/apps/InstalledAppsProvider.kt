package com.intent.screentime.data.apps

import android.content.Context
import android.content.Intent

/** An app the user could choose to be prompted about. */
data class InstalledApp(
    val packageName: String,
    val label: String,
)

/**
 * The launcher-visible apps on the device.
 *
 * Only apps with a launcher entry are listed: an app the user cannot open from the home
 * screen is not one the intent prompt could ever fire on.
 */
class InstalledAppsProvider(
    private val context: Context,
    private val labelOf: (String) -> String,
) {

    fun launcherApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filterNot { it == context.packageName }
            .map { InstalledApp(packageName = it, label = labelOf(it)) }
            .sortedBy { it.label.lowercase() }
    }
}
