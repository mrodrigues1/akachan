package com.babytracker.manager

import com.babytracker.util.PENDING_INTENT_IMMUTABLE_UPDATE

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.babytracker.receiver.PredictiveFeedReceiver
import com.babytracker.util.NotificationHelper
import com.babytracker.util.setExactWithFallback
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

interface PredictiveFeedScheduler {
    fun schedulePredictiveReminderAt(triggerTime: Instant, predictedAt: Instant)
    fun cancelPredictiveReminder()
}

class PredictiveFeedSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PredictiveFeedScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedulePredictiveReminderAt(
        triggerTime: Instant,
        predictedAt: Instant,
    ) {
        val pi = buildPendingIntent(predictedAt)
        alarmManager.cancel(pi)
        alarmManager.setExactWithFallback(AlarmManager.RTC_WAKEUP, triggerTime.toEpochMilli(), pi, TAG)
    }

    override fun cancelPredictiveReminder() {
        alarmManager.cancel(buildPendingIntent(Instant.EPOCH))
        context.getSystemService(NotificationManager::class.java)
            .cancel(NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
    }

    private fun buildPendingIntent(predictedAt: Instant): PendingIntent {
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_FIRE
            putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, predictedAt.toEpochMilli())
        }
        return PendingIntent.getBroadcast(
            context, PredictiveFeedReceiver.REQUEST_CODE_PREDICTIVE, intent,
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )
    }

    companion object {
        private const val TAG = "PredictiveFeedScheduler"
    }
}
