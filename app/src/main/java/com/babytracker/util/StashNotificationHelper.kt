package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R
import com.babytracker.navigation.Routes
import com.babytracker.ui.theme.Pink700
import com.babytracker.ui.theme.PrimaryPinkDark
import com.babytracker.ui.theme.WarningAmber
import com.babytracker.ui.theme.WarningAmberDark

/**
 * Notifications for the milk-stash domain: stash-expiration reminders and
 * partner-consumed-stash alerts. Split out of [NotificationHelper] to keep each
 * object focused on a single bounded context.
 */
object StashNotificationHelper {
    const val STASH_EXPIRATION_CHANNEL_ID = "stash_expiration_notifications"
    const val STASH_EXPIRATION_NOTIFICATION_ID = 1009
    const val PARTNER_STASH_CHANNEL_ID = "partner_stash_notifications"
    const val PARTNER_STASH_NOTIFICATION_ID = 1010
    private const val RC_STASH_EXPIRATION_TAP = 3003
    private const val RC_PARTNER_STASH_TAP = 3004
    private const val TAG = "StashNotificationHelper"

    fun createStashExpirationNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STASH_EXPIRATION_CHANNEL_ID,
                context.getString(R.string.notif_channel_stash_expiration_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_stash_expiration_description) }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            Log.d(TAG, "Channel created: $STASH_EXPIRATION_CHANNEL_ID")
        }
    }

    fun createPartnerStashNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PARTNER_STASH_CHANNEL_ID,
                context.getString(R.string.notif_channel_partner_stash_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_partner_stash_description) }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            Log.d(TAG, "Channel created: $PARTNER_STASH_CHANNEL_ID")
        }
    }

    fun showStashExpiration(context: Context, count: Int, totalMl: Int) {
        val title = context.getString(R.string.notif_title_stash_expiration)
        val body = context.resources.getQuantityString(
            R.plurals.notif_body_stash_expiration, count, count, totalMl,
        )
        val accent = resolveAccent(context, WarningAmber, WarningAmberDark)
        val notification = NotificationCompat.Builder(context, STASH_EXPIRATION_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_limit)
            .setContentTitle(title)
            .setContentText(body)
            .setTicker(title)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(stashExpirationTapPendingIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(STASH_EXPIRATION_NOTIFICATION_ID, notification)
        Log.d(TAG, "showStashExpiration posted (count=$count, totalMl=$totalMl)")
    }

    private fun stashExpirationTapPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            RC_STASH_EXPIRATION_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.INVENTORY)
            },
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )

    fun showPartnerStashConsumed(context: Context, feedCount: Int, totalMl: Int) {
        val title = context.resources.getQuantityString(
            R.plurals.notif_title_partner_stash, feedCount, feedCount,
        )
        val body = if (feedCount == 1) {
            context.getString(R.string.notif_body_partner_stash_single, totalMl)
        } else {
            context.getString(R.string.notif_body_partner_stash_multi, totalMl)
        }
        val accent = resolveAccent(context, Pink700, PrimaryPinkDark)
        val notification = NotificationCompat.Builder(context, PARTNER_STASH_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
            .setContentTitle(title)
            .setContentText(body)
            .setTicker(title)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(partnerStashTapPendingIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(PARTNER_STASH_NOTIFICATION_ID, notification)
        Log.d(TAG, "showPartnerStashConsumed posted (feedCount=$feedCount, totalMl=$totalMl)")
    }

    private fun partnerStashTapPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            RC_PARTNER_STASH_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.INVENTORY)
            },
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )

    private fun resolveAccent(context: Context, light: Color, dark: Color): Int {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightMask == Configuration.UI_MODE_NIGHT_YES
        return (if (isDark) dark else light).toArgb()
    }

    private fun NotificationCompat.Builder.applyDesignSystem(
        accentColor: Int,
        smallIconRes: Int,
        visibility: Int = NotificationCompat.VISIBILITY_PRIVATE,
    ): NotificationCompat.Builder = this
        .setSmallIcon(smallIconRes)
        .setColor(accentColor)
        .setColorized(false)
        .setOnlyAlertOnce(true)
        .setVisibility(visibility)
}
