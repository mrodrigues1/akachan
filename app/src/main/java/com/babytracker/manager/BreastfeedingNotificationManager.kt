package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.babytracker.receiver.BreastfeedingNotificationReceiver
import java.time.Instant

class BreastfeedingNotificationManager(private val context: Context) : NotificationScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val TAG = "NotificationManager"

    override fun scheduleMaxTotalTimeNotification(sessionStartTime: Instant, maxTotalMinutes: Int) {
        if (maxTotalMinutes <= 0) return

        val triggerTime = sessionStartTime.plusSeconds(maxTotalMinutes * 60L)
        scheduleAlarm(
            triggerTime = triggerTime,
            requestCode = REQUEST_CODE_MAX_TOTAL,
            notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL
        )
    }

    override fun scheduleMaxPerBreastNotification(sessionStartTime: Instant, maxPerBreastMinutes: Int) {
        if (maxPerBreastMinutes <= 0) return

        val triggerTime = sessionStartTime.plusSeconds(maxPerBreastMinutes * 60L)
        scheduleAlarm(
            triggerTime = triggerTime,
            requestCode = REQUEST_CODE_MAX_PER_BREAST,
            notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE
        )
    }

    override fun cancelAllScheduledNotifications() {
        cancelAlarm(REQUEST_CODE_MAX_TOTAL)
        cancelAlarm(REQUEST_CODE_MAX_PER_BREAST)
    }

    private fun scheduleAlarm(
        triggerTime: Instant,
        requestCode: Int,
        notificationType: String
    ) {
        Log.d(TAG, "Scheduling alarm for type=$notificationType at $triggerTime (requestCode=$requestCode)")

        val intent = Intent(context, BreastfeedingNotificationReceiver::class.java).apply {
            action = "com.babytracker.BREASTFEEDING_NOTIFICATION"
            putExtra("notification_type", notificationType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel any existing alarm with the same requestCode
        alarmManager.cancel(pendingIntent)

        // Check if we can schedule exact alarms
        val canScheduleExactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExactAlarms) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime.toEpochMilli(),
                pendingIntent
            )
            Log.d(TAG, "Exact alarm scheduled at ${triggerTime.toEpochMilli()}")
        } else {
            // Fallback to inexact alarm if permission not granted
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime.toEpochMilli(),
                pendingIntent
            )
            Log.d(TAG, "Inexact alarm scheduled at ${triggerTime.toEpochMilli()}")
        }
    }

    private fun cancelAlarm(requestCode: Int) {
        val intent = Intent(context, BreastfeedingNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        private const val REQUEST_CODE_MAX_TOTAL = 1001
        private const val REQUEST_CODE_MAX_PER_BREAST = 1002
    }
}
