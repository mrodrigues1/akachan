package com.babytracker.receiver

import com.babytracker.util.PENDING_INTENT_IMMUTABLE_UPDATE

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.manager.PredictiveFeedScheduler
import com.babytracker.util.FireDecision
import com.babytracker.util.NotificationHelper
import com.babytracker.util.decideFire
import com.babytracker.util.goAsyncWithTimeout
import com.babytracker.util.showPredictiveReminder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class PredictiveFeedReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: PredictiveFeedScheduler
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> postReminder(context, intent)
            ACTION_SNOOZE -> snooze(context, intent)
            else -> Log.w(TAG, "Unknown action ${intent.action}")
        }
    }

    private fun postReminder(context: Context, intent: Intent) {
        val predictedAtEpochMs = intent.getLongExtra(EXTRA_PREDICTED_AT_MS, 0L)
        if (predictedAtEpochMs <= 0L) {
            Log.w(TAG, "Missing predictedAt; dropping fire")
            return
        }
        // Re-read quiet hours at fire time, matching PredictiveSleepReceiver: inexact-alarm
        // delivery may land inside a quiet window configured after the alarm was set.
        goAsyncWithTimeout(TAG) {
            val quietStart = settingsRepository.getQuietHoursStartMinute().first()
            val quietEnd = settingsRepository.getQuietHoursEndMinute().first()
            val decision = decideFire(
                now = Instant.now(),
                bestEstimate = Instant.ofEpochMilli(predictedAtEpochMs),
                quietStartMinute = quietStart,
                quietEndMinute = quietEnd,
            )
            when (decision) {
                FireDecision.Stale -> Log.i(TAG, "Prediction stale; dropping fire")
                FireDecision.QuietHours -> Log.d(TAG, "Fire time inside quiet hours; suppressing notification")
                FireDecision.Fire -> try {
                    showPredictiveReminder(
                        context = context,
                        predictedAtEpochMs = predictedAtEpochMs,
                        snoozePendingIntent = buildSnoozePendingIntent(context, predictedAtEpochMs),
                    )
                } catch (e: SecurityException) {
                    Log.w(TAG, "POST_NOTIFICATIONS denied; skipping reminder", e)
                }
            }
        }
    }

    private fun snooze(context: Context, intent: Intent) {
        NotificationHelper.cancelNotification(context, NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
        val predictedAtEpochMs = intent.getLongExtra(EXTRA_PREDICTED_AT_MS, 0L)
        scheduler.schedulePredictiveReminderAt(
            triggerTime = Instant.now().plusSeconds(SNOOZE_MINUTES * 60L),
            predictedAt = Instant.ofEpochMilli(predictedAtEpochMs),
        )
    }

    private fun buildSnoozePendingIntent(
        context: Context,
        predictedAtEpochMs: Long,
    ): PendingIntent {
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_PREDICTED_AT_MS, predictedAtEpochMs)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_SNOOZE, intent,
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.babytracker.PREDICTIVE_FEED_FIRE"
        const val ACTION_SNOOZE = "com.babytracker.PREDICTIVE_FEED_SNOOZE"
        const val EXTRA_PREDICTED_AT_MS = "predicted_at_ms"
        const val REQUEST_CODE_PREDICTIVE = 1003
        const val REQUEST_CODE_SNOOZE = 1004
        private const val SNOOZE_MINUTES = 15L
        private const val TAG = "PredictiveFeedRx"
    }
}
