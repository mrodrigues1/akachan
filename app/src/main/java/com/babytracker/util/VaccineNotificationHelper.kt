package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R
import com.babytracker.navigation.Routes
import com.babytracker.ui.theme.VaccineIndigo
import com.babytracker.ui.theme.VaccineIndigoDark
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Vaccine reminder notification channel + builder, kept out of [NotificationHelper] to keep that
 * class under detekt's LargeClass threshold. Tapping the reminder opens the vaccine history.
 */
object VaccineNotificationHelper {
    const val CHANNEL_ID = "vaccine_reminders"
    const val NOTIFICATION_ID = 1011
    private const val RC_TAP = 3005
    private const val TAG = "VaccineNotification"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_vaccine_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_vaccine_reminders_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            Log.d(TAG, "Channel created: $CHANNEL_ID")
        }
    }

    fun show(context: Context, vaccineName: String, scheduledDate: Instant) {
        val dateLabel = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            .format(scheduledDate.atZone(ZoneId.systemDefault()).toLocalDate())
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val accent = (if (nightMask == Configuration.UI_MODE_NIGHT_YES) VaccineIndigoDark else VaccineIndigo).toArgb()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_limit)
            .setColor(accent)
            .setColorized(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentTitle(context.getString(R.string.vaccine_reminder_title))
            .setContentText(context.getString(R.string.vaccine_reminder_body, vaccineName, dateLabel))
            .setTicker(context.getString(R.string.vaccine_reminder_title))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapPendingIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "show posted (name=$vaccineName)")
    }

    private fun tapPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            RC_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.VACCINE_HISTORY)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
