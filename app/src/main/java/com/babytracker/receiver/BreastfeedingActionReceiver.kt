package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BreastfeedingActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: BreastfeedingRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var switchSide: SwitchBreastfeedingSideUseCase
    @Inject lateinit var stopSession: StopBreastfeedingSessionUseCase

    companion object {
        const val ACTION = "com.babytracker.BREASTFEEDING_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_SWITCH = "switch"
        const val ACTION_STOP = "stop"
        const val ACTION_DISMISS = "dismiss"
        const val ACTION_KEEP_GOING = "keep_going"
        private const val TAG = "BreastfeedingActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                handle(context, intent)
            } finally {
                result.finish()
            }
        }
    }

    internal suspend fun handle(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        Log.d(TAG, "Handling action=$action sessionId=$sessionId")

        when (action) {
            ACTION_SWITCH -> {
                val session = repository.getActiveSession().first()
                if (session?.id == sessionId) {
                    switchSide(session)
                    // Refresh the ongoing notification with the new side.
                    // Only the first switch is supported; after it the current side is opposite startingSide.
                    if (session.switchTime == null) {
                        val newSide = if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
                        val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
                        NotificationHelper.showBreastfeedingActive(
                            context = context,
                            sessionId = session.id,
                            currentSide = newSide.name,
                            sessionStartEpochMs = session.startTime.toEpochMilli(),
                            pausedDurationMs = session.pausedDurationMs,
                            richEnabled = richEnabled
                        )
                    }
                }
                NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
            }
            ACTION_STOP -> {
                val session = repository.getActiveSession().first()
                if (session?.id == sessionId) stopSession(session)
                NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID)
                NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
                NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_ACTIVE_NOTIFICATION_ID)
            }
            ACTION_DISMISS -> {
                NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
            }
            ACTION_KEEP_GOING -> {
                NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID)
            }
        }
    }
}
