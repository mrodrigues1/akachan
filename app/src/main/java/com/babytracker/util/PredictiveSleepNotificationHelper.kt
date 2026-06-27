package com.babytracker.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R
import com.babytracker.navigation.Routes
import com.babytracker.ui.theme.Blue700
import com.babytracker.ui.theme.SecondaryBlueDark
import java.time.Instant

private const val RC_PREDICTIVE_SLEEP_START = 2001

fun createPredictiveSleepNotificationChannel(context: Context) {
    createNotificationChannel(
        context = context,
        channelId = NotificationHelper.PREDICTIVE_SLEEP_CHANNEL_ID,
        nameRes = R.string.notif_channel_predictive_sleep_name,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        descriptionRes = R.string.notif_channel_predictive_sleep_description,
    )
}

fun showPredictiveSleepReminder(
    context: Context,
    bestEstimateMs: Long,
) {
    val nowMs = System.currentTimeMillis()
    val minutesUntil = ((bestEstimateMs - nowMs).coerceAtLeast(0L) / 60_000L).toInt()
    val timeLabel = Instant.ofEpochMilli(bestEstimateMs).formatClockTime12h()
    val title = context.getString(R.string.notif_title_predictive_sleep)
    val body = if (minutesUntil > 0) {
        context.getString(R.string.notif_body_predictive_sleep, timeLabel, minutesUntil)
    } else {
        context.getString(R.string.notif_body_predictive_sleep_now, timeLabel)
    }
    val accent = NotificationHelper.resolveAccent(context, Blue700, SecondaryBlueDark)

    val startIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.SLEEP_TRACKING)
    }
    val startPi = PendingIntent.getActivity(
        context,
        RC_PREDICTIVE_SLEEP_START,
        startIntent,
        PENDING_INTENT_IMMUTABLE_UPDATE,
    )

    val builder = NotificationCompat.Builder(context, NotificationHelper.PREDICTIVE_SLEEP_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notif_sleep)
        .setColor(accent)
        .setColorized(false)
        .setOnlyAlertOnce(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setOngoing(false)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(startPi)
        .addAction(0, context.getString(R.string.notif_action_start_sleep), startPi)

    context.getSystemService(NotificationManager::class.java)
        .notify(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID, builder.build())
}
