package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.manager.BreastfeedingSessionController
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.util.NotificationHelper
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Notification quick-action edge: validates the intent against the current active session and
 * delegates to [BreastfeedingSessionController], which owns the actual session-control behaviour
 * shared with the in-app buttons.
 */
@AndroidEntryPoint
class BreastfeedingActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: BreastfeedingRepository
    @Inject lateinit var sessionController: BreastfeedingSessionController
    @Inject lateinit var notificationCoordinator: BreastfeedingSessionNotificationCoordinator

    companion object {
        const val ACTION = "com.babytracker.BREASTFEEDING_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_SWITCH = "switch"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_STOP = "stop"
        const val ACTION_DISMISS = "dismiss"
        const val ACTION_KEEP_GOING = "keep_going"
        const val ACTION_REFRESH_ACTIVE = "refresh_active"
        private const val TAG = "BreastfeedingActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWithTimeout(TAG) {
            handle(context, intent)
        }
    }

    internal suspend fun handle(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        Log.d(TAG, "Handling action=$action sessionId=$sessionId")

        when (action) {
            ACTION_SWITCH -> handleSwitch(context, sessionId)
            ACTION_PAUSE -> activeSession(sessionId)?.let { sessionController.pause(it) }
            ACTION_RESUME -> activeSession(sessionId)?.let { sessionController.resume(it) }
            ACTION_STOP -> handleStop(sessionId)
            ACTION_DISMISS -> NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
            ACTION_KEEP_GOING -> handleKeepGoing(context, sessionId)
            ACTION_REFRESH_ACTIVE -> refreshActiveNotification(sessionId)
        }
    }

    private suspend fun handleSwitch(context: Context, sessionId: Long) {
        activeSession(sessionId)?.let { sessionController.switchSide(it) }
        NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
    }

    private suspend fun handleStop(sessionId: Long) {
        activeSession(sessionId)?.let { sessionController.stop(it) }
        notificationCoordinator.cancelPostedSessionNotifications()
    }

    private suspend fun handleKeepGoing(context: Context, sessionId: Long) {
        NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID)
        activeSession(sessionId)?.let { notificationCoordinator.rearmAfterKeepGoing(it) }
    }

    private suspend fun refreshActiveNotification(sessionId: Long) {
        val session = activeSession(sessionId) ?: return
        if (!session.isPaused) notificationCoordinator.showRunning(session)
    }

    private suspend fun activeSession(sessionId: Long): BreastfeedingSession? =
        repository.getActiveSession().first()?.takeIf { it.id == sessionId }
}
