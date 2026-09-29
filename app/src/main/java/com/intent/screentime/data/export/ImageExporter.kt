package com.intent.screentime.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes a captured chart out as a PNG and hands back a shareable URI.
 *
 * The same hand-off as [CsvExporter] and for the same reason: the cache, not external
 * storage, because a shared image is a one-way hand-off to whatever app the user picks and
 * nothing should be left lying around afterwards. The FileProvider path already exposed
 * for CSV exports covers this too, so images cost no new manifest surface.
 */
class ImageExporter(private val context: Context) {

    /**
     * Null when the bitmap could not be written, so a caller has one failure to handle
     * rather than an exception to catch.
     */
    suspend fun exportPng(bitmap: Bitmap, name: String): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, EXPORT_DIR)
            if (!directory.exists()) directory.mkdirs()

            val file = File(directory, "$name.png")
            file.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_IGNORES_QUALITY, stream)
            }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    companion object {
        private const val EXPORT_DIR = "exports"

        /** PNG is lossless, so the quality argument is ignored. The API still wants one. */
        private const val PNG_IGNORES_QUALITY = 100

        fun shareIntent(uri: Uri, subject: String): Intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        fun chooserFor(uri: Uri, subject: String, title: String): Intent =
            Intent.createChooser(shareIntent(uri, subject), title)
    }
}
