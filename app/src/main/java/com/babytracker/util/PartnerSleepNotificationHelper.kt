package com.babytracker.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    // NOTE: this notification ID (passed to NotificationManager.notify) also collides with
    // VaccineNotificationHelper.NOTIFICATION_ID (both 1011) — a separate collision domain from the
    // PendingIntent tap request codes this file fixes (#745). Left as-is: renumbering it safely
    // needs a channel-aware upgrade migration to cancel any already-posted legacy notification,
    // which is out of scope here. Tracked for a follow-up issue.
    const val PARTNER_SLEEP_NOTIFICATION_ID = 1011

    fun createPartnerSleepNotificationChannel(context: Context) {
        createNotificationChannel(
            context = context,
            channelId = PARTNER_SLEEP_CHANNEL_ID,
            nameRes = R.string.notif_channel_partner_sleep_name,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            descriptionRes = R.string.notif_channel_partner_sleep_description,
        )
    }

    fun showPartnerSleepChange(context: Context, notification: PartnerSleepNotification) {
        val title = context.getString(messageRes(notification))
        val accent = context.resolveNotificationAccent(Purple700, Purple200)
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
            NotificationTapRequestCodes.PARTNER_SLEEP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.SLEEP_TRACKING)
            },
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )

}
