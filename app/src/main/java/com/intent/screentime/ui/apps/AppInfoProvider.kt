package com.intent.screentime.ui.apps

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves app icons and labels, with caching.
 *
 * Icon decoding is genuinely expensive — roughly a millisecond per app, and there can be
 * a hundred apps on screen after a week of tracking. Resolving them inline would drop
 * frames, so icons are preloaded off the main thread and read back synchronously from
 * the cache. Anything not yet cached returns null and the UI draws a monogram instead,
 * which means a slow disk never shows up as a blank row.
 */
class AppInfoProvider(
    context: Context,
    private val iconSizePx: Int,
) {
    private val packageManager: PackageManager = context.packageManager

    private val icons = LruCache<String, ImageBitmap>(MAX_ICONS)
    private val labels = LruCache<String, String>(MAX_LABELS)
    private val monogramColors = LruCache<String, Int>(MAX_LABELS)

    fun icon(packageName: String): ImageBitmap? = icons.get(packageName)

    fun label(packageName: String): String {
        labels.get(packageName)?.let { return it }
        val resolved = resolveLabel(packageName)
        labels.put(packageName, resolved)
        return resolved
    }

    /**
     * A stable colour for the monogram fallback, derived from the package name so the
     * same app is always the same colour.
     */
    fun monogramColorIndex(packageName: String): Int {
        monogramColors.get(packageName)?.let { return it }
        val index = (packageName.hashCode().let { if (it < 0) -it else it }) % MONOGRAM_COLORS
        monogramColors.put(packageName, index)
        return index
    }

    /** Warm the cache for a batch of packages. Safe to call repeatedly. */
    suspend fun preload(packageNames: Collection<String>) = withContext(Dispatchers.IO) {
        for (packageName in packageNames.distinct()) {
            if (icons.get(packageName) != null) continue
            val drawable = loadDrawable(packageName) ?: continue
            icons.put(packageName, drawable.toSquareBitmap(iconSizePx).asImageBitmap())
        }
    }

    private fun resolveLabel(packageName: String): String =
        try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.').replaceFirstChar(Char::uppercase)
        }

    private fun loadDrawable(packageName: String): Drawable? =
        try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    /**
     * Adaptive icons report an intrinsic size that does not match their mask, so they
     * are drawn into a fixed square rather than trusted at their own bounds.
     */
    private fun Drawable.toSquareBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bitmap
    }

    private companion object {
        const val MAX_ICONS = 220
        const val MAX_LABELS = 400
        const val MONOGRAM_COLORS = 8
    }
}
