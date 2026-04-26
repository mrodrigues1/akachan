package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        val notificationType = intent.getStringExtra("notification_type")
        Log.d(TAG, "Broadcast received: type=$notificationType")
        if (notificationType == null) {
            Log.w(TAG, "No notification_type extra found")
            return
        }

        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
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
                    }
                    else -> Log.w(TAG, "Unknown notification type: $notificationType")
                }
            } finally {
                result.finish()
            }
        }
    }
}
