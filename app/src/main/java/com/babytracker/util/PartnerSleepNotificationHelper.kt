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

    // Was 1011, which collided with VaccineNotificationHelper.NOTIFICATION_ID (#772). A partner-
    // sleep notification lingering under 1011 from a pre-#772 release is migrated away by
    // [cancelLegacyCollidingNotification] at app startup.
    const val PARTNER_SLEEP_NOTIFICATION_ID = 1012
    private const val LEGACY_SHARED_NOTIFICATION_ID = 1011

    /**
     * One-shot upgrade migration (#772): cancels a partner-sleep notification still posted under
     * the pre-#772 shared ID 1011 — channel-checked so a live vaccine reminder under the same ID
     * survives. Call once at app startup, before any notification producer runs.
     *
     * ponytail: check-then-cancel is not atomic — a vaccine reminder posting 1011 in the
     * microsecond window between the check and the cancel would be erased. Accepted: the window
     * exists once, at the first launch after upgrade, and only while a legacy partner-sleep
     * notification is still in the tray.
     */
    fun cancelLegacyCollidingNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val legacyIsOurs = manager.activeNotifications.any {
            it.id == LEGACY_SHARED_NOTIFICATION_ID &&
                it.notification.channelId == PARTNER_SLEEP_CHANNEL_ID
        }
        if (legacyIsOurs) manager.cancel(LEGACY_SHARED_NOTIFICATION_ID)
    }

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
