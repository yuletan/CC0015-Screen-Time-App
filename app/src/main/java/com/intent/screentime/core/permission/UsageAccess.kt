package com.intent.screentime.core.permission

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

/**
 * Usage access is a special permission the user grants in Settings — it cannot be
 * requested with a normal runtime-permission dialog.
 */
object UsageAccess {
    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opens the system "Usage access" screen where the user toggles the app on. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * On Android 14+ the usage-access screen is a per-app list, so we try to deep-link
     * straight to our own entry and fall back to the generic screen when an OEM build
     * does not support the package-scoped form.
     */
    fun appSpecificSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = Uri.fromParts("package", context.packageName, null)
        }

    fun openSettings(context: Context) {
        try {
            context.startActivity(appSpecificSettingsIntent(context))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(settingsIntent())
        }
    }
}
