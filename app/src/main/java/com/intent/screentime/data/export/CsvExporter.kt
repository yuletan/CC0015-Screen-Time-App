package com.intent.screentime.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.IntentDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Writes the local history out as a CSV in the cache and returns a shareable URI.
 *
 * The cache, not external storage: the export is a one-way hand-off to whatever app the
 * user picks in the share sheet, and nothing should be left lying around afterwards.
 */
class CsvExporter(
    private val context: Context,
    private val database: IntentDatabase,
) {

    /** Null when there is nothing to export yet. */
    suspend fun exportDailySummaries(days: Long = DEFAULT_DAYS): Uri? = withContext(Dispatchers.IO) {
        val toDay = DayWindow.todayEpochDay()
        val rows = database.dailySummaryDao().between(toDay - days, toDay)
        if (rows.isEmpty()) return@withContext null

        val directory = File(context.cacheDir, EXPORT_DIR)
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "intent-${LocalDate.now()}.csv")
        file.writeText(CsvFormat.dailySummaries(rows))

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        const val DEFAULT_DAYS = 90L
    }
}
