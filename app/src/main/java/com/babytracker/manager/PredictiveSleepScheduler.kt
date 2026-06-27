package com.babytracker.manager

import com.babytracker.util.PENDING_INTENT_IMMUTABLE_UPDATE

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.babytracker.receiver.PredictiveSleepReceiver
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

interface PredictiveSleepScheduler {
    fun schedulePredictiveReminderAt(triggerTime: Instant, bestEstimate: Instant, recommendationId: Long)
    fun cancelPredictiveReminder()
}

class PredictiveSleepSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PredictiveSleepScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedulePredictiveReminderAt(
        triggerTime: Instant,
        bestEstimate: Instant,
        recommendationId: Long,
    ) {
        val pi = buildPendingIntent(bestEstimate, recommendationId)
        alarmManager.cancel(pi)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.toEpochMilli(),
                    pi,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.toEpochMilli(),
                    pi,
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM revoked; falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pi)
        }
    }

    override fun cancelPredictiveReminder() {
        alarmManager.cancel(buildPendingIntent(Instant.EPOCH, 0L))
        context.getSystemService(NotificationManager::class.java)
            .cancel(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
    }

    private fun buildPendingIntent(bestEstimate: Instant, recommendationId: Long): PendingIntent {
        val intent = Intent(context, PredictiveSleepReceiver::class.java).apply {
            action = PredictiveSleepReceiver.ACTION_FIRE
            putExtra(PredictiveSleepReceiver.EXTRA_BEST_ESTIMATE_MS, bestEstimate.toEpochMilli())
            putExtra(PredictiveSleepReceiver.EXTRA_RECOMMENDATION_ID, recommendationId)
        }
        return PendingIntent.getBroadcast(
            context,
            PredictiveSleepReceiver.REQUEST_CODE_PREDICTIVE_SLEEP,
            intent,
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )
    }

    companion object {
        private const val TAG = "PredictiveSleepScheduler"
    }
}
