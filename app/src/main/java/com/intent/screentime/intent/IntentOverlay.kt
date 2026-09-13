package com.intent.screentime.intent

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import kotlin.math.roundToInt

/**
 * The "pause and say why" overlay.
 *
 * Built from plain views rather than Compose on purpose: this window lives outside the
 * activity, and a ComposeView there would drag a composition into a context that was
 * never meant to host one — for a card with five buttons, that is a poor trade.
 *
 * It is deliberately dismissible, and answers are optional. A prompt you cannot skip is
 * a barrier, and the point is a moment of reflection, not a gate.
 */
class IntentOverlay(private val context: Context) {

    private val windowManager: WindowManager? =
        context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var root: View? = null
    private var countdownView: TextView? = null
    private var secondsLeft = SECONDS_TO_ANSWER
    private var onTimeout: (() -> Unit)? = null

    val showing: Boolean get() = root != null

    private val countdown = object : Runnable {
        override fun run() {
            if (root == null) return
            secondsLeft -= 1
            countdownView?.text = if (secondsLeft > 0) {
                "Closing in ${secondsLeft}s"
            } else {
                "Closing…"
            }
            if (secondsLeft <= 0) {
                hide()
                onTimeout?.invoke()
            } else {
                handler.postDelayed(this, 1_000L)
            }
        }
    }

    fun show(
        appLabel: String,
        onAnswer: (String) -> Unit,
        onTimeoutAnswer: () -> Unit,
    ) {
        if (root != null) return
        val manager = windowManager ?: return

        onTimeout = onTimeoutAnswer
        secondsLeft = SECONDS_TO_ANSWER

        val night = isNightMode()
        val cardColor = if (night) "#241D1B".toColorInt() else "#FFFBF9".toColorInt()
        val textColor = if (night) "#F2E9E6".toColorInt() else "#14100F".toColorInt()
        val mutedColor = if (night) "#B5A49C".toColorInt() else "#75645D".toColorInt()
        val pillColor = if (night) "#3A302D".toColorInt() else "#F0E5E0".toColorInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(cardColor)
            }
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }

        card.addView(
            textView(
                text = "Why $appLabel?",
                size = 19f,
                color = textColor,
                bold = true,
            ),
        )
        card.addView(
            textView(
                text = "Two seconds of honesty here is what turns this into a number you " +
                    "can act on.",
                size = 13f,
                color = mutedColor,
            ).apply { setPadding(0, dp(6), 0, dp(14)) },
        )

        val options = listOf(
            "Replying to someone",
            "Checking something",
            "Scrolling",
            "Killing time",
        )
        options.chunked(2).forEachIndexed { index, rowOptions ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                if (index > 0) setPadding(0, dp(8), 0, 0)
            }
            rowOptions.forEach { label ->
                row.addView(
                    textView(
                        text = label,
                        size = 13f,
                        color = textColor,
                        bold = true,
                    ).apply {
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            cornerRadius = dp(18).toFloat()
                            setColor(pillColor)
                        }
                        isClickable = true
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            .apply { if (label != rowOptions.last()) rightMargin = dp(8) }
                        setOnClickListener {
                            hide()
                            onAnswer(label)
                        }
                    },
                )
            }
            card.addView(row)
        }

        countdownView = textView(
            text = "Closing in ${SECONDS_TO_ANSWER}s",
            size = 11f,
            color = mutedColor,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        card.addView(countdownView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = dp(110)
        }

        val container = LinearLayout(context).apply {
            setPadding(dp(16), 0, dp(16), 0)
            addView(card)
        }

        try {
            manager.addView(container, params)
            root = container
            handler.postDelayed(countdown, 1_000L)
        } catch (_: Throwable) {
            // Losing the overlay is not worth crashing the watch service over.
            root = null
        }
    }

    fun hide() {
        handler.removeCallbacks(countdown)
        root?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Throwable) {
                // Already detached.
            }
        }
        root = null
        countdownView = null
        onTimeout = null
    }

    private fun textView(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun isNightMode(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val SECONDS_TO_ANSWER = 15
    }
}
