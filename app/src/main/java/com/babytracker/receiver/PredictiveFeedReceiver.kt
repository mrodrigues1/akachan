package com.babytracker.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.manager.PredictiveFeedScheduler
import com.babytracker.util.NotificationHelper
import com.babytracker.util.showPredictiveReminder
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class PredictiveFeedReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: PredictiveFeedScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> postReminder(context, intent)
            ACTION_SNOOZE -> snooze(context, intent)
            else -> Log.w(TAG, "Unknown action ${intent.action}")
        }
    }

    /**
     * Time-boundary guard only. Drops the notification if:
     *  - predictedAtEpochMs is missing/zero, or
     *  - now > predictedAtEpochMs + MAX_STALE_MINUTES (late delivery / snoozed past window).
     *
     * Full revalidation (predictiveEnabled, AppMode, active session, stale prediction id)
     * is deferred to Task 5's PredictiveFeedNotificationCoordinator, which will inject
     * a use case and own the cancel-on-settings-change lifecycle. Until Task 5 ships,
     * a cancelled alarm from the coordinator is the primary suppression path; this
     * guard is the last-resort fallback for time-expired alarms only.
     */
    private fun postReminder(context: Context, intent: Intent) {
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

    /**
     * Reschedules a fresh ACTION_FIRE 15 minutes from now. The original predictedAtEpochMs
     * is carried forward unchanged so the displayed time stays anchored to the actual
     * prediction; the receiver recomputes "in N min" at fire time.
     */
    private fun snooze(context: Context, intent: Intent) {
        val predictedAtEpochMs = intent.getLongExtra(EXTRA_PREDICTED_AT_MS, 0L)
        scheduler.schedulePredictiveReminderAt(
            triggerTime = Instant.now().plusSeconds(SNOOZE_MINUTES * 60L),
            predictedAt = Instant.ofEpochMilli(predictedAtEpochMs),
        )
        NotificationHelper.cancelNotification(context, NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
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
