package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class BreastfeedingNotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    companion object {
        const val NOTIFICATION_TYPE_MAX_TOTAL = "max_total"
        const val NOTIFICATION_TYPE_SWITCH_SIDE = "switch_side"
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWithTimeout(TAG) {
            handle(context, intent)
        }
    }

    internal suspend fun handle(context: Context, intent: Intent) {
        val notificationType = intent.getStringExtra("notification_type") ?: return
        val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
        val sessionId = intent.getLongExtra("session_id", -1L)
        val currentSide = intent.getStringExtra("current_side") ?: "LEFT"
        val elapsedMinutes = intent.getIntExtra("elapsed_minutes", 0)
        val maxTotalMinutes = intent.getIntExtra("max_total_minutes", 0)

        when (notificationType) {
            NOTIFICATION_TYPE_SWITCH_SIDE -> {
                Log.i(TAG, "Triggering switch side notification (rich=$richEnabled)")
                NotificationHelper.showSwitchSide(
                    context, sessionId, currentSide, elapsedMinutes, richEnabled
                )
            }
            NOTIFICATION_TYPE_MAX_TOTAL -> {
                Log.i(TAG, "Triggering feeding limit notification (rich=$richEnabled)")
                NotificationHelper.showFeedingLimit(
                    context, sessionId, maxTotalMinutes, richEnabled
                )
                // Snap the active notification progress to 100% immediately. Without this, the
                // progress bar (refreshed every 30s) can show < 100% while the live chronometer
                // already shows the max time. The alarm fires when actual elapsed == max, so
                // synthetically placing sessionStart = now - maxTotalMinutes * 60s gives the
                // correct elapsed and keeps the chronometer accurate.
                NotificationHelper.showBreastfeedingActive(
                    context = context,
                    sessionId = sessionId,
                    currentSide = currentSide,
                    sessionStartEpochMs = System.currentTimeMillis() - maxTotalMinutes * 60_000L,
                    pausedDurationMs = 0L,
                    richEnabled = richEnabled,
                    maxTotalMinutes = maxTotalMinutes
                )
            }
            else -> Log.w(TAG, "Unknown notification type: $notificationType")
        }
    }
}
