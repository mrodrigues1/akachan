package com.babytracker.manager

import com.babytracker.util.PENDING_INTENT_IMMUTABLE_UPDATE

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.receiver.BreastfeedingNotificationReceiver
import com.babytracker.util.setExactWithFallback
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

class BreastfeedingNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun scheduleMaxTotalTimeNotification(
        sessionStartTime: Instant,
        maxTotalMinutes: Int,
        sessionId: Long,
        currentSide: String,
        maxPerBreastMinutes: Int
    ) {
        if (maxTotalMinutes <= 0) return
        scheduleAlarm(
            AlarmRequest(
                triggerTime = sessionStartTime.plusSeconds(maxTotalMinutes * SECONDS_PER_MINUTE),
                requestCode = REQUEST_CODE_MAX_TOTAL,
                notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL,
                sessionId = sessionId,
                currentSide = currentSide,
                elapsedMinutes = maxTotalMinutes,
                maxTotalMinutes = maxTotalMinutes
            )
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
            AlarmRequest(
                triggerTime = sessionStartTime.plusSeconds(maxPerBreastMinutes * SECONDS_PER_MINUTE),
                requestCode = REQUEST_CODE_MAX_PER_BREAST,
                notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
                sessionId = sessionId,
                currentSide = currentSide,
                elapsedMinutes = maxPerBreastMinutes,
                maxTotalMinutes = maxTotalMinutes
            )
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
            AlarmRequest(
                triggerTime = triggerTime,
                requestCode = REQUEST_CODE_MAX_TOTAL,
                notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL,
                sessionId = sessionId,
                currentSide = currentSide,
                elapsedMinutes = maxTotalMinutes,
                maxTotalMinutes = maxTotalMinutes
            )
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
            AlarmRequest(
                triggerTime = triggerTime,
                requestCode = REQUEST_CODE_MAX_PER_BREAST,
                notificationType = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
                sessionId = sessionId,
                currentSide = currentSide,
                elapsedMinutes = maxPerBreastMinutes,
                maxTotalMinutes = maxTotalMinutes
            )
        )
    }

    override fun cancelPerBreastNotification() {
        cancelAlarm(REQUEST_CODE_MAX_PER_BREAST)
    }

    override fun cancelAllScheduledNotifications() {
        cancelAlarm(REQUEST_CODE_MAX_TOTAL)
        cancelAlarm(REQUEST_CODE_MAX_PER_BREAST)
    }

    private fun scheduleAlarm(request: AlarmRequest) {
        Log.d(TAG, "Scheduling alarm type=${request.notificationType} at ${request.triggerTime} (rc=${request.requestCode})")
        val intent = Intent(context, BreastfeedingNotificationReceiver::class.java).apply {
            action = NOTIFICATION_ACTION
            putExtra("notification_type", request.notificationType)
            putExtra("session_id", request.sessionId)
            putExtra("current_side", request.currentSide)
            putExtra("elapsed_minutes", request.elapsedMinutes)
            putExtra("max_total_minutes", request.maxTotalMinutes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, request.requestCode, intent,
            PENDING_INTENT_IMMUTABLE_UPDATE
        )
        alarmManager.cancel(pendingIntent)
        alarmManager.setExactWithFallback(AlarmManager.RTC_WAKEUP, request.triggerTime.toEpochMilli(), pendingIntent, TAG)
    }

    private data class AlarmRequest(
        val triggerTime: Instant,
        val requestCode: Int,
        val notificationType: String,
        val sessionId: Long,
        val currentSide: String,
        val elapsedMinutes: Int,
        val maxTotalMinutes: Int
    )

    private fun cancelAlarm(requestCode: Int) {
        val intent = Intent(context, BreastfeedingNotificationReceiver::class.java).apply {
            action = NOTIFICATION_ACTION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PENDING_INTENT_IMMUTABLE_UPDATE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val NOTIFICATION_ACTION = "com.babytracker.BREASTFEEDING_NOTIFICATION"
        private const val TAG = "NotificationManager"
        private const val REQUEST_CODE_MAX_TOTAL = 1001
        private const val REQUEST_CODE_MAX_PER_BREAST = 1002
        private const val SECONDS_PER_MINUTE = 60L
    }
}
