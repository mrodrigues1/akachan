package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class BreastfeedingActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: BreastfeedingRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var switchSide: SwitchBreastfeedingSideUseCase
    @Inject lateinit var stopSession: StopBreastfeedingSessionUseCase
    @Inject lateinit var pauseSession: PauseBreastfeedingSessionUseCase
    @Inject lateinit var resumeSession: ResumeBreastfeedingSessionUseCase
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
            ACTION_SWITCH -> handleSwitch(context, sessionId)
            ACTION_PAUSE -> handlePause(sessionId)
            ACTION_RESUME -> handleResume(sessionId)
            ACTION_STOP -> handleStop(sessionId)
            ACTION_DISMISS -> NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
            ACTION_KEEP_GOING -> NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID)
            ACTION_REFRESH_ACTIVE -> refreshActiveNotification(context, sessionId)
        }
    }

    private suspend fun handleSwitch(context: Context, sessionId: Long) {
        val session = repository.getActiveSession().first()
        if (session?.id == sessionId) {
            switchSide(session)
            if (session.switchTime == null) {
                showSwitchedActiveNotification(context, session)
            }
        }
        NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
    }

    private suspend fun showSwitchedActiveNotification(context: Context, session: BreastfeedingSession) {
        val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
        val maxTotalMinutes = settingsRepository.getMaxTotalFeedMinutes().first()
        NotificationHelper.showBreastfeedingActive(
            context = context,
            sessionId = session.id,
            currentSide = oppositeSide(session.startingSide),
            sessionStartEpochMs = session.startTime.toEpochMilli(),
            pausedDurationMs = session.pausedDurationMs,
            richEnabled = richEnabled,
            maxTotalMinutes = maxTotalMinutes
        )
    }

    private suspend fun handlePause(sessionId: Long) {
        val session = repository.getActiveSession().first()
        if (session?.id == sessionId && !session.isPaused) {
            val pausedAt = Instant.now()
            pauseSession(session)
            notificationCoordinator.cancelScheduled()
            notificationCoordinator.showPaused(session, pausedAt)
        }
    }

    private suspend fun handleResume(sessionId: Long) {
        val session = repository.getActiveSession().first()
        if (session?.id == sessionId && session.isPaused) {
            val resumeInstant = Instant.now()
            resumeSession(session)
            val totalPausedMs = notificationCoordinator.rescheduleAfterResume(session, resumeInstant)
            notificationCoordinator.showRunning(session, pausedDurationMs = totalPausedMs)
        }
    }

    private suspend fun handleStop(sessionId: Long) {
        val session = repository.getActiveSession().first()
        if (session?.id == sessionId) {
            stopSession(session)
            notificationCoordinator.cancelScheduled()
        }
        notificationCoordinator.cancelPostedSessionNotifications()
    }

    private suspend fun refreshActiveNotification(context: Context, sessionId: Long) {
        val session = repository.getActiveSession().first()
        if (session == null || session.id != sessionId || session.isPaused) return

        val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
        val maxTotalMinutes = settingsRepository.getMaxTotalFeedMinutes().first()
        NotificationHelper.showBreastfeedingActive(
            context = context,
            sessionId = session.id,
            currentSide = currentSide(session),
            sessionStartEpochMs = session.startTime.toEpochMilli(),
            pausedDurationMs = session.pausedDurationMs,
            richEnabled = richEnabled,
            maxTotalMinutes = maxTotalMinutes
        )
    }

    private fun currentSide(session: BreastfeedingSession): String =
        if (session.switchTime != null) {
            oppositeSide(session.startingSide)
        } else {
            session.startingSide.name
        }

    private fun oppositeSide(side: BreastSide): String =
        if (side == BreastSide.LEFT) BreastSide.RIGHT.name else BreastSide.LEFT.name
}
