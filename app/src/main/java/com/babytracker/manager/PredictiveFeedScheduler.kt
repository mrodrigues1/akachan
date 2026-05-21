package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.babytracker.receiver.PredictiveFeedReceiver
import java.time.Instant

interface PredictiveFeedScheduler {
    fun schedulePredictiveReminderAt(triggerTime: Instant, predictedAt: Instant)
    fun cancelPredictiveReminder()
}

class PredictiveFeedSchedulerImpl(private val context: Context) : PredictiveFeedScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedulePredictiveReminderAt(
        triggerTime: Instant,
        predictedAt: Instant,
    ) {
        val pi = buildPendingIntent(predictedAt)
        alarmManager.cancel(pi)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM revoked; falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pi)
        }
    }

    override fun cancelPredictiveReminder() {
        alarmManager.cancel(buildPendingIntent(Instant.EPOCH))
    }

    private fun buildPendingIntent(predictedAt: Instant): PendingIntent {
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_FIRE
            putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, predictedAt.toEpochMilli())
        }
        return PendingIntent.getBroadcast(
            context, PredictiveFeedReceiver.REQUEST_CODE_PREDICTIVE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "PredictiveFeedScheduler"
    }
}
