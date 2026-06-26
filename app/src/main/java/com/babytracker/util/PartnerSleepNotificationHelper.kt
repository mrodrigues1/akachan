package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R
import com.babytracker.domain.model.SleepType
import com.babytracker.navigation.Routes
import com.babytracker.sharing.usecase.PartnerSleepNotification
import com.babytracker.sharing.usecase.SleepNotifyKind
import com.babytracker.ui.theme.Purple200
import com.babytracker.ui.theme.Purple700

/**
 * Posts the "main partner" notification when the connected partner starts, stops, or edits a sleep
 * session on the primary device. One coalesced notification per applied op batch.
 */
object PartnerSleepNotificationHelper {
    const val PARTNER_SLEEP_CHANNEL_ID = "partner_sleep_notifications"
    const val PARTNER_SLEEP_NOTIFICATION_ID = 1011
    private const val RC_PARTNER_SLEEP_TAP = 3005

    fun createPartnerSleepNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PARTNER_SLEEP_CHANNEL_ID,
                context.getString(R.string.notif_channel_partner_sleep_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_partner_sleep_description) }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun showPartnerSleepChange(context: Context, notification: PartnerSleepNotification) {
        val title = context.getString(messageRes(notification))
        val accent = resolveAccent(context, Purple700, Purple200)
        val built = NotificationCompat.Builder(context, PARTNER_SLEEP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_sleep)
            .setColor(accent)
            .setColorized(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentTitle(title)
            .setTicker(title)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapPendingIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(PARTNER_SLEEP_NOTIFICATION_ID, built)
    }

    private fun messageRes(n: PartnerSleepNotification): Int = when (n.kind) {
        SleepNotifyKind.STARTED ->
            if (n.sleepType == SleepType.NAP) R.string.notif_partner_sleep_started_nap
            else R.string.notif_partner_sleep_started_night
        SleepNotifyKind.STOPPED ->
            if (n.sleepType == SleepType.NAP) R.string.notif_partner_sleep_ended_nap
            else R.string.notif_partner_sleep_ended_night
        SleepNotifyKind.EDITED -> R.string.notif_partner_sleep_edited
    }

    private fun tapPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            RC_PARTNER_SLEEP_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.SLEEP_TRACKING)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun resolveAccent(context: Context, light: Color, dark: Color): Int {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightMask == Configuration.UI_MODE_NIGHT_YES
        return (if (isDark) dark else light).toArgb()
    }
}
