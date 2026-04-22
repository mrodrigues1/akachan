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

    override fun scheduleMaxTotalTimeNotification(
        sessionStartTime: Instant,
        maxTotalMinutes: Int,
        sessionId: Long,
        currentSide: String,
        maxPerBreastMinutes: Int
    ) {
        if (maxTotalMinutes <= 0) return
        scheduleAlarm(
            triggerTime = sessionStartTime.plusSeconds(maxTotalMinutes * 60L),
            requestCode = REQUEST_CODE_MAX_TOTAL,
            notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL,
            sessionId = sessionId,
            currentSide = currentSide,
            elapsedMinutes = maxTotalMinutes,
            maxPerBreastMinutes = maxPerBreastMinutes,
            maxTotalMinutes = maxTotalMinutes
        )
    }

    override fun scheduleMaxPerBreastNotification(
        sessionStartTime: Instant,
        maxPerBreastMinutes: Int,
        sessionId: Long,
        currentSide: String,
        maxTotalMinutes: Int
    ) {
        if (maxPerBreastMinutes <= 0) return
        scheduleAlarm(
            triggerTime = sessionStartTime.plusSeconds(maxPerBreastMinutes * 60L),
            requestCode = REQUEST_CODE_MAX_PER_BREAST,
            notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
            sessionId = sessionId,
            currentSide = currentSide,
            elapsedMinutes = maxPerBreastMinutes,
            maxPerBreastMinutes = maxPerBreastMinutes,
            maxTotalMinutes = maxTotalMinutes
        )
    }

    override fun scheduleMaxTotalTimeNotificationAt(
        triggerTime: Instant,
        sessionId: Long,
        maxTotalMinutes: Int,
        currentSide: String,
        maxPerBreastMinutes: Int
    ) {
        scheduleAlarm(
            triggerTime = triggerTime,
            requestCode = REQUEST_CODE_MAX_TOTAL,
            notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL,
            sessionId = sessionId,
            currentSide = currentSide,
            elapsedMinutes = maxTotalMinutes,
            maxPerBreastMinutes = maxPerBreastMinutes,
            maxTotalMinutes = maxTotalMinutes
        )
    }

    override fun scheduleMaxPerBreastNotificationAt(
        triggerTime: Instant,
        sessionId: Long,
        maxPerBreastMinutes: Int,
        currentSide: String,
        maxTotalMinutes: Int
    ) {
        scheduleAlarm(
            triggerTime = triggerTime,
            requestCode = REQUEST_CODE_MAX_PER_BREAST,
            notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
            sessionId = sessionId,
            currentSide = currentSide,
            elapsedMinutes = maxPerBreastMinutes,
            maxPerBreastMinutes = maxPerBreastMinutes,
            maxTotalMinutes = maxTotalMinutes
        )
    }

    override fun cancelAllScheduledNotifications() {
        cancelAlarm(REQUEST_CODE_MAX_TOTAL)
        cancelAlarm(REQUEST_CODE_MAX_PER_BREAST)
    }

    private fun scheduleAlarm(
        triggerTime: Instant,
        requestCode: Int,
        notificationType: String,
        sessionId: Long,
        currentSide: String,
        elapsedMinutes: Int,
        maxPerBreastMinutes: Int,
        maxTotalMinutes: Int
    ) {
        Log.d(TAG, "Scheduling alarm type=$notificationType at $triggerTime (rc=$requestCode)")
        val intent = Intent(context, BreastfeedingNotificationReceiver::class.java).apply {
            action = NOTIFICATION_ACTION
            putExtra("notification_type", notificationType)
            putExtra("session_id", sessionId)
            putExtra("current_side", currentSide)
            putExtra("elapsed_minutes", elapsedMinutes)
            putExtra("max_per_breast_minutes", maxPerBreastMinutes)
            putExtra("max_total_minutes", maxTotalMinutes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pendingIntent)
        }
    }

    private fun cancelAlarm(requestCode: Int) {
        val intent = Intent(context, BreastfeedingNotificationReceiver::class.java).apply {
            action = NOTIFICATION_ACTION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val NOTIFICATION_ACTION = "com.babytracker.BREASTFEEDING_NOTIFICATION"
        private const val REQUEST_CODE_MAX_TOTAL = 1001
        private const val REQUEST_CODE_MAX_PER_BREAST = 1002
    }
}
