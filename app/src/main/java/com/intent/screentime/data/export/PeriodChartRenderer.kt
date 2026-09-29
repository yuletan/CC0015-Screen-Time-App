package com.intent.screentime.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.intent.screentime.data.usage.HourlyBreakdown
import kotlin.math.max

/**
 * Draws the `photos/` half of a period download with plain Canvas.
 *
 * A zip export cannot screenshot Compose panels — the days being exported are not
 * on screen — so the charts are redrawn here from the same numbers the Insights
 * and DayCard screens plot. Same data, plainer style: white background, dark ink,
 * one title and one caption baked in so the PNG reads on its own outside the app.
 *
 * Everything runs on a background thread; no Compose, no resources, no theme.
 */
object PeriodChartRenderer {

    data class Point(val label: String, val valueMs: Long)

    data class Bar(val label: String, val valueMs: Long, val color: Int)

    const val PERIOD_W = 1600
    const val PERIOD_H = 1000
    const val DAY_W = 1200
    const val DAY_H = 800

    // --- public entry points ------------------------------------------------

    /** Period trend: daily points for a week/month, weekly points for longer ranges. */
    fun trend(
        points: List<Point>,
        title: String,
        subtitle: String,
        caption: String? = null,
        width: Int = PERIOD_W,
        height: Int = PERIOD_H,
    ): Bitmap = drawBase(width, height, title, subtitle, caption) { canvas, plot ->
        if (points.none { it.valueMs > 0L }) {
            drawEmpty(canvas, plot, "No usage in this period")
            return@drawBase
        }
        val max = max(points.maxOf { it.valueMs }, 60_000L).toFloat()
        drawGrid(canvas, plot, max)
        // Line + soft fill.
        val path = Path()
        val fill = Path()
        val n = points.size
        fun x(i: Int): Float =
            if (n == 1) plot.centerX()
            else plot.left + (plot.width() * i / (n - 1).toFloat())
        fun y(v: Long): Float =
            plot.bottom - (plot.height() * (v / max)).coerceIn(0f, 1f)
        points.forEachIndexed { i, p ->
            val px = x(i)
            val py = y(p.valueMs)
            if (i == 0) {
                path.moveTo(px, py)
                fill.moveTo(px, plot.bottom)
                fill.lineTo(px, py)
            } else {
                path.lineTo(px, py)
                fill.lineTo(px, py)
            }
        }
        // Fill under the line.
        fill.lineTo(x(n - 1), plot.bottom)
        fill.close()
        canvas.drawPath(fill, fillPaint())
        canvas.drawPath(path, linePaint())
        // Dots.
        points.forEachIndexed { i, p ->
            canvas.drawCircle(x(i), y(p.valueMs), 10f, dotPaint())
        }
        // X labels: first / middle / last, or every label when few.
        val labelPaint = labelPaint()
        if (n <= 8) {
            points.forEachIndexed { i, p ->
                canvas.drawText(
                    p.label, x(i), plot.bottom + 44f, centered(labelPaint),
                )
            }
        } else {
            val picks = listOf(0, n / 2, n - 1)
            picks.forEach { i ->
                canvas.drawText(
                    points[i].label, x(i), plot.bottom + 44f, centered(labelPaint),
                )
            }
        }
        // Peak annotation.
        val peak = points.maxBy { it.valueMs }
        canvas.drawText(
            "Peak ${short(peak.valueMs)} (${peak.label})",
            plot.left, plot.bottom + 92f, smallPaint(),
        )
    }

    /** Producing vs consuming vs the rest: one stacked bar + legend. */
    fun split(
        productionMs: Long,
        consumptionMs: Long,
        utilityMs: Long,
        neutralMs: Long,
        unsortedMs: Long,
        title: String,
        subtitle: String,
        width: Int = PERIOD_W,
        height: Int = PERIOD_H,
    ): Bitmap = drawBase(width, height, title, subtitle, null) { canvas, plot ->
        val total = productionMs + consumptionMs + utilityMs + neutralMs + unsortedMs
        if (total <= 0L) {
            drawEmpty(canvas, plot, "Nothing recorded in this period")
            return@drawBase
        }
        val segments = listOf(
            Triple("Producing", productionMs, Color.parseColor(PROD)),
            Triple("Consuming", consumptionMs, Color.parseColor(CONS)),
            Triple("Utility", utilityMs, Color.parseColor(UTIL)),
            Triple("Neutral", neutralMs, Color.parseColor(NEUT)),
            Triple("Unsorted", unsortedMs, Color.parseColor(INK_LINE)),
        ).filter { it.second > 0L }
        val barTop = plot.top + 60f
        val barH = 90f
        var dx = plot.left
        for ((_, value, color) in segments) {
            val w = plot.width() * (value.toFloat() / total)
            val rect = RectF(dx, barTop, dx + w, barTop + barH)
            canvas.drawRoundRect(rect, 18f, 18f, segmentPaint(color))
            dx += w
        }
        // Share line.
        val accountable = productionMs + consumptionMs
        val shareLine = if (accountable > 0L) {
            val pct = (productionMs * 100L / accountable)
            "$pct% of categorised time producing (${short(productionMs)} of ${short(accountable)})."
        } else {
            "No categorised time — sort apps and this split sharpens."
        }
        canvas.drawText(shareLine, plot.left, barTop + barH + 56f, bodyPaint())
        // Legend.
        var ly = barTop + barH + 120f
        for ((name, value, color) in segments) {
            canvas.drawCircle(plot.left + 14f, ly - 12f, 14f, segmentPaint(color))
            canvas.drawText(
                "$name — ${short(value)}", plot.left + 44f, ly, bodyPaint(),
            )
            ly += 52f
        }
    }

    /** Ranked horizontal bars for the period's biggest apps. */
    fun topApps(
        bars: List<Bar>,
        title: String,
        subtitle: String,
        width: Int = PERIOD_W,
        height: Int = PERIOD_H,
    ): Bitmap = drawBase(width, height, title, subtitle, null) { canvas, plot ->
        if (bars.isEmpty()) {
            drawEmpty(canvas, plot, "No app usage in this period")
            return@drawBase
        }
        val rows = bars.take(8)
        val max = max(rows.maxOf { it.valueMs }, 1L).toFloat()
        val rowH = minOf(96f, (plot.height() - 40f) / rows.size)
        rows.forEachIndexed { i, bar ->
            val top = plot.top + 20f + i * rowH
            val w = (plot.width() - 320f) * (bar.valueMs / max)
            // Label.
            canvas.drawText(
                bar.label.take(28), plot.left, top + rowH * 0.62f, bodyPaint(),
            )
            // Bar.
            val rect = RectF(plot.left + 330f, top + 12f, plot.left + 330f + w, top + rowH - 12f)
            canvas.drawRoundRect(rect, 14f, 14f, segmentPaint(bar.color))
            // Value.
            canvas.drawText(
                short(bar.valueMs), plot.left + 340f + w + 16f, top + rowH * 0.62f,
                smallPaint(),
            )
        }
    }

    /** 24 hourly bars — the shape of a day, or of a whole period. */
    fun hours(
        buckets: List<HourlyBreakdown.Bucket>,
        title: String,
        subtitle: String,
        caption: String? = null,
        width: Int = PERIOD_W,
        height: Int = PERIOD_H,
    ): Bitmap = drawBase(width, height, title, subtitle, caption) { canvas, plot ->
        val totals = (0 until 24).map { h -> buckets.firstOrNull { it.hour == h }?.totalMs ?: 0L }
        if (totals.none { it > 0L }) {
            drawEmpty(canvas, plot, "Nothing recorded yet")
            return@drawBase
        }
        val max = max(totals.max(), 60_000L).toFloat()
        drawGrid(canvas, plot, max)
        val slot = plot.width() / 24f
        totals.forEachIndexed { h, v ->
            val bh = plot.height() * (v / max).coerceIn(0f, 1f)
            val rect = RectF(
                plot.left + h * slot + 6f, plot.bottom - bh,
                plot.left + (h + 1) * slot - 6f, plot.bottom,
            )
            canvas.drawRoundRect(rect, 10f, 10f, segmentPaint(BAR_BLUE))
        }
        val lp = centered(labelPaint())
        listOf(0, 6, 12, 18, 23).forEach { h ->
            canvas.drawText(
                "%02d:00".format(h), plot.left + h * slot + slot / 2f,
                plot.bottom + 44f, lp,
            )
        }
        val peak = totals.indices.maxBy { totals[it] }
        canvas.drawText(
            "Busiest hour %02d:00 at %s".format(peak, short(totals[peak])),
            plot.left, plot.bottom + 92f, smallPaint(),
        )
    }

    // --- canvas plumbing ----------------------------------------------------

    private data class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun width(): Float = right - left
        fun height(): Float = bottom - top
        fun centerX(): Float = (left + right) / 2f
    }

    private fun drawBase(
        width: Int,
        height: Int,
        title: String,
        subtitle: String,
        caption: String?,
        plotBody: (Canvas, Plot) -> Unit,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor(BG))

        var y = 96f
        canvas.drawText(title, 72f, y, titlePaint())
        y += 52f
        if (subtitle.isNotBlank()) {
            canvas.drawText(subtitle, 72f, y, subtitlePaint())
            y += 44f
        }
        // Divider.
        canvas.drawLine(72f, y, width - 72f, y, dividerPaint())
        y += 28f

        // Reserve room below the plot for x-labels, the peak line and the caption,
        // so they can never print over each other (the old math stacked the peak
        // line onto the caption on short daily images).
        val reserved = if (!caption.isNullOrBlank()) 210f else 150f
        val plot = Plot(120f, y, width - 96f, height - 96f - reserved)
        plotBody(canvas, plot)

        if (!caption.isNullOrBlank()) {
            canvas.drawText(caption, 72f, height - 56f, smallPaint())
        }
        // Footer brand top-right, so a forwarded PNG still says where it came from
        // without ever colliding with the caption.
        canvas.drawText(
            "CC0015 Intent", width - 72f, 62f, rightAligned(footerPaint()),
        )
        return bitmap
    }

    private fun drawGrid(canvas: Canvas, plot: Plot, maxMs: Float) {
        val grid = gridPaint()
        val lp = smallPaint()
        val steps = listOf(0f, 0.5f, 1f)
        for (f in steps) {
            val y = plot.bottom - plot.height() * f
            canvas.drawLine(plot.left, y, plot.right, y, grid)
            canvas.drawText(short((maxMs * f).toLong()), 16f, y + 12f, lp)
        }
    }

    private fun drawEmpty(canvas: Canvas, plot: Plot, message: String) {
        canvas.drawText(message, plot.centerX(), (plot.top + plot.bottom) / 2f, centered(bodyPaint()))
    }

    private fun short(ms: Long): String {
        val m = (ms / 60_000.0).toLong()
        return when {
            m < 60L -> "${m}m"
            m % 60L == 0L -> "${m / 60}h"
            else -> "${m / 60}h ${m % 60}m"
        }
    }

    // --- paints -------------------------------------------------------------

    // Ink & Ember: the same dark theme as the app, so a generated fallback reads
    // as the same chart rather than a stranger. Values are the sRGB fallbacks from
    // ui.theme.Color (Ink.n900 background, Ember.e400 line, dark data hues).
    private const val BG = "#14100F"
    private const val INK_TITLE = "#F2E9E6"
    private const val INK_SUB = "#CBBAB3"
    private const val INK_SMALL = "#9E8A82"
    private const val INK_LINE = "#3A302D"
    private const val EMBER = "#FF7B52"
    private const val PROD = "#6FD3A6"
    private const val CONS = "#FF8A80"
    private const val UTIL = "#9DB4C8"
    private const val NEUT = "#B5A49C"

    private fun titlePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(INK_TITLE)
        textSize = 52f
        isFakeBoldText = true
    }

    private fun subtitlePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(INK_SUB)
        textSize = 34f
    }

    private fun bodyPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(INK_TITLE)
        textSize = 36f
    }

    private fun smallPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(INK_SMALL)
        textSize = 30f
    }

    private fun labelPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(INK_SUB)
        textSize = 30f
    }

    private fun footerPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(INK_SMALL)
        textSize = 28f
    }

    private fun centered(paint: Paint): Paint = Paint(paint).apply { textAlign = Paint.Align.CENTER }

    private fun rightAligned(paint: Paint): Paint =
        Paint(paint).apply { textAlign = Paint.Align.RIGHT }

    private fun linePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(EMBER)
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private fun fillPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(EMBER)
        style = Paint.Style.FILL
        alpha = 70
    }

    private fun dotPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(EMBER)
        style = Paint.Style.FILL
    }

    private fun segmentPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun gridPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(INK_LINE)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private fun dividerPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(INK_LINE)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val BAR_BLUE = Color.parseColor(EMBER)

    /** The dark-theme series from ui.theme.Color.DarkDataColors. */
    val SERIES = intArrayOf(
        Color.parseColor("#FF7B52"),
        Color.parseColor("#FFC14D"),
        Color.parseColor("#E86CC4"),
        Color.parseColor("#AFA0E0"),
        Color.parseColor("#6FCFC9"),
        Color.parseColor("#7FB6F0"),
        Color.parseColor("#A8D46A"),
        Color.parseColor("#EE85A8"),
    )
}
