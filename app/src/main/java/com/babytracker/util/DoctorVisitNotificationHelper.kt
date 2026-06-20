package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R
import com.babytracker.navigation.Routes
import com.babytracker.ui.theme.DoctorSlate
import com.babytracker.ui.theme.DoctorSlateDark
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Doctor-visit reminder notification channel + builder, kept out of [NotificationHelper] to keep
 * that class under detekt's LargeClass threshold. Tapping the reminder opens the visit history.
 */
object DoctorVisitNotificationHelper {
    const val CHANNEL_ID = "doctor_visit_reminders"
    const val NOTIFICATION_ID = 1021
    private const val RC_TAP = 3015
    private const val TAG = "DoctorVisitNotification"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_doctor_visit_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_doctor_visit_reminders_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            Log.d(TAG, "Channel created: $CHANNEL_ID")
        }
    }

    fun show(context: Context, providerName: String?, date: Instant) {
        val dateLabel = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            .format(date.atZone(ZoneId.systemDefault()).toLocalDate())
        val accent = NotificationHelper.resolveAccent(context, DoctorSlate, DoctorSlateDark)
        val body = if (!providerName.isNullOrBlank()) {
            context.getString(R.string.doctor_visit_reminder_body, providerName, dateLabel)
        } else {
            context.getString(R.string.doctor_visit_reminder_body_generic, dateLabel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_limit)
            .setColor(accent)
            .setColorized(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentTitle(context.getString(R.string.doctor_visit_reminder_title))
            .setContentText(body)
            .setTicker(context.getString(R.string.doctor_visit_reminder_title))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapPendingIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "show posted (provider=$providerName)")
    }

    private fun tapPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            RC_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.DOCTOR_VISIT_HISTORY)
            },
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )
}
