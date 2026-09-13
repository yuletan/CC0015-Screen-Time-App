package com.intent.screentime.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.intent.screentime.R
import com.intent.screentime.core.format.DurationFormat
import com.intent.screentime.data.goals.CapAlert

/**
 * Every notification the app can post, and the channels they live on.
 *
 * Channels are separated by *what the user would want to do about it*, not by feature:
 * a breached limit is worth interrupting for, a digest is worth reading at a glance, and
 * a running focus timer should be silent. That way one Android setting can turn off the
 * part someone finds annoying without losing the part they rely on.
 */
class NotificationHelper(private val context: Context) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_CAPS,
                    "Limit alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Told once when a screen time cap you set is passed."
                },
                NotificationChannel(
                    CHANNEL_DIGEST,
                    "Daily digest",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "A one-line summary of the day, at a time you choose."
                },
                NotificationChannel(
                    CHANNEL_STREAKS,
                    "Streaks",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Milestones on your run of days inside the cap."
                },
                NotificationChannel(
                    CHANNEL_FOCUS,
                    "Focus sessions",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "The timer while a focus session is running. Silent."
                },
                NotificationChannel(
                    CHANNEL_WATCH,
                    "Intent prompts",
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = "Keeps the prompt running while you have it switched on. " +
                        "Never makes a sound."
                },
            ),
        )
    }

    /** One summary notification covering every cap passed today. */
    fun notifyCapAlerts(alerts: List<CapAlert>) {
        if (alerts.isEmpty()) return
        if (!canPost()) return

        val title = if (alerts.size == 1) alerts.first().title else "${alerts.size} caps passed"
        val body = alerts.joinToString("\n") { it.body }

        val notification = base(CHANNEL_CAPS)
            .setContentTitle(title)
            .setContentText(alerts.first().body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        post(ID_CAPS, notification)
    }

    fun notifyDigest(screenTimeMs: Long, topLabel: String?, topMs: Long) {
        if (!canPost()) return

        val body = if (topLabel != null && topMs > 0L) {
            "${DurationFormat.compact(screenTimeMs)} today, mostly $topLabel " +
                "(${DurationFormat.compact(topMs)})."
        } else {
            "${DurationFormat.compact(screenTimeMs)} today."
        }

        val notification = base(CHANNEL_DIGEST)
            .setContentTitle("Your day, so far")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        post(ID_DIGEST, notification)
    }

    fun notifyMilestone(days: Int) {
        if (!canPost()) return

        val body = when (days) {
            3 -> "Three days inside your cap. The first ones are the hard ones."
            7 -> "A full week inside your cap. Whatever you changed, it is working."
            else -> "$days days inside your cap and counting."
        }

        val notification = base(CHANNEL_STREAKS)
            .setContentTitle("$days-day streak")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        post(ID_STREAK, notification)
    }

    /**
     * Posts defensively: `notify` throws if the permission was revoked between the
     * check above and this call, and a missed notification is never worth a crash.
     */
    private fun post(id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications were switched off in the intervening moment.
        }
    }

    private fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** The ongoing timer. Counts down in the shade without a single wakeup of our own. */
    fun focusNotification(
        plannedEndMs: Long,
        label: String?,
        cancelIntent: PendingIntent,
    ): Notification =
        base(CHANNEL_FOCUS)
            .setContentTitle(label ?: "Focus session")
            .setContentText("Stay with it.")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(plannedEndMs)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .addAction(0, "End session", cancelIntent)
            .build()

    /** Keeps the Phase 7 watch service alive without ever making a sound. */
    fun watchNotification(): Notification =
        base(CHANNEL_WATCH)
            .setContentTitle("Intent prompt is on")
            .setContentText("Asking before the apps you flagged.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun base(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_intent)
            .setColor(COLOR_ACCENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    companion object {
        const val CHANNEL_CAPS = "caps"
        const val CHANNEL_DIGEST = "digest"
        const val CHANNEL_STREAKS = "streaks"
        const val CHANNEL_FOCUS = "focus"
        const val CHANNEL_WATCH = "watch"

        const val ID_CAPS = 1001
        const val ID_DIGEST = 1002
        const val ID_STREAK = 1003
        const val ID_FOCUS = 1004
        const val ID_WATCH = 1005

        /** Ember e600, the brand accent, used as the notification accent colour. */
        private const val COLOR_ACCENT = 0xFFD93B12.toInt()
    }
}
