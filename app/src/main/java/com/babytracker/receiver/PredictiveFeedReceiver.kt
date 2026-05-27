package com.babytracker.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.manager.PredictiveFeedScheduler
import com.babytracker.manager.isInQuietHours
import com.babytracker.util.NotificationHelper
import com.babytracker.util.showPredictiveReminder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class PredictiveFeedReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: PredictiveFeedScheduler
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val result: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                withTimeout(10_000L) {
                    when (intent.action) {
                        ACTION_FIRE -> postReminder(context, intent)
                        ACTION_SNOOZE -> snooze(context, intent)
                        else -> Log.w(TAG, "Unknown action ${intent.action}")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "onReceive timed out", e)
            } finally {
                result?.finish()
            }
        }
    }

    private suspend fun postReminder(context: Context, intent: Intent) {
        val predictedAtEpochMs = intent.getLongExtra(EXTRA_PREDICTED_AT_MS, 0L)
        if (predictedAtEpochMs <= 0L) {
            Log.w(TAG, "Missing predictedAt; dropping fire")
            return
        }
        val nowMs = System.currentTimeMillis()
        val staleAfterMs = predictedAtEpochMs + MAX_STALE_MINUTES * 60_000L
        if (nowMs > staleAfterMs) {
            Log.i(TAG, "Prediction stale by ${(nowMs - predictedAtEpochMs) / 60_000L}m; dropping fire")
            return
        }
        val enabled = settingsRepository.getPredictiveEnabled().first()
        if (!enabled) {
            Log.i(TAG, "Dropping predictive reminder — feature disabled")
            return
        }
        val quietStart = settingsRepository.getQuietHoursStartMinute().first()
        val quietEnd = settingsRepository.getQuietHoursEndMinute().first()
        if (isInQuietHours(Instant.now(), quietStart, quietEnd)) {
            Log.i(TAG, "Dropping predictive reminder — now is inside quiet hours")
            return
        }
        try {
            showPredictiveReminder(
                context = context,
                predictedAtEpochMs = predictedAtEpochMs,
                snoozePendingIntent = buildSnoozePendingIntent(context, predictedAtEpochMs),
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS denied; skipping reminder", e)
        }
    }

    private suspend fun snooze(context: Context, intent: Intent) {
        // Cancel the visible notification immediately so the user's tap is always honoured,
        // even if settings reads below stall or fail.
        NotificationHelper.cancelNotification(context, NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
        val predictedAtEpochMs = intent.getLongExtra(EXTRA_PREDICTED_AT_MS, 0L)
        val snoozeTrigger = Instant.now().plusSeconds(SNOOZE_MINUTES * 60L)
        val enabled = settingsRepository.getPredictiveEnabled().first()
        if (!enabled) {
            Log.i(TAG, "Dropping snooze reschedule — feature disabled")
            return
        }
        val quietStart = settingsRepository.getQuietHoursStartMinute().first()
        val quietEnd = settingsRepository.getQuietHoursEndMinute().first()
        if (isInQuietHours(snoozeTrigger, quietStart, quietEnd)) {
            Log.i(TAG, "Dropping snooze reschedule — trigger falls inside quiet hours")
            return
        }
        scheduler.schedulePredictiveReminderAt(
            triggerTime = snoozeTrigger,
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.babytracker.PREDICTIVE_FEED_FIRE"
        const val ACTION_SNOOZE = "com.babytracker.PREDICTIVE_FEED_SNOOZE"
        const val EXTRA_PREDICTED_AT_MS = "predicted_at_ms"
        const val REQUEST_CODE_PREDICTIVE = 1003
        const val REQUEST_CODE_SNOOZE = 1004
        private const val SNOOZE_MINUTES = 15L
        private const val MAX_STALE_MINUTES = 20L
        private const val TAG = "PredictiveFeedRx"
    }
}
