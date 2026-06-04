package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R
import com.babytracker.navigation.Routes
import com.babytracker.ui.theme.Blue700
import com.babytracker.ui.theme.SecondaryBlueDark
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val RC_PREDICTIVE_SLEEP_START = 2001

fun createPredictiveSleepNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            NotificationHelper.PREDICTIVE_SLEEP_CHANNEL_ID,
            context.getString(R.string.notif_channel_predictive_sleep_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_predictive_sleep_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}

fun showPredictiveSleepReminder(
    context: Context,
    bestEstimateMs: Long,
) {
    val nowMs = System.currentTimeMillis()
    val minutesUntil = ((bestEstimateMs - nowMs).coerceAtLeast(0L) / 60_000L).toInt()
    val timeLabel = java.time.Instant.ofEpochMilli(bestEstimateMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a"))
    val title = context.getString(R.string.notif_title_predictive_sleep)
    val body = if (minutesUntil > 0) {
        context.getString(R.string.notif_body_predictive_sleep, timeLabel, minutesUntil)
    } else {
        context.getString(R.string.notif_body_predictive_sleep_now, timeLabel)
    }
    val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val accent = (if (nightMask == Configuration.UI_MODE_NIGHT_YES) SecondaryBlueDark else Blue700).toArgb()

    val startIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.SLEEP_TRACKING)
    }
    val startPi = PendingIntent.getActivity(
        context,
        RC_PREDICTIVE_SLEEP_START,
        startIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
