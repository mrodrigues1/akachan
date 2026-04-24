package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreastfeedingSessionNotificationCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler
) {

    suspend fun scheduleInitial(session: BreastfeedingSession) {
        val maxPerBreastMinutes = settingsRepository.getMaxPerBreastMinutes().first()
        val maxTotalFeedMinutes = settingsRepository.getMaxTotalFeedMinutes().first()

        if (maxPerBreastMinutes > 0) {
            notificationScheduler.scheduleMaxPerBreastNotification(
                sessionStartTime = session.startTime,
                maxPerBreastMinutes = maxPerBreastMinutes,
                sessionId = session.id,
                currentSide = session.startingSide.name,
                maxTotalMinutes = maxTotalFeedMinutes
            )
        }

        if (maxTotalFeedMinutes > 0) {
            notificationScheduler.scheduleMaxTotalTimeNotification(
                sessionStartTime = session.startTime,
                maxTotalMinutes = maxTotalFeedMinutes,
                sessionId = session.id,
                currentSide = session.startingSide.name,
                maxPerBreastMinutes = maxPerBreastMinutes
            )
        }
    }

    suspend fun showRunning(
        session: BreastfeedingSession,
        pausedDurationMs: Long = session.pausedDurationMs
    ) {
        val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
        val maxTotalMinutes = settingsRepository.getMaxTotalFeedMinutes().first()
        NotificationHelper.showBreastfeedingActive(
            context = context,
            sessionId = session.id,
            currentSide = currentSideName(session),
            sessionStartEpochMs = session.startTime.toEpochMilli(),
            pausedDurationMs = pausedDurationMs,
            richEnabled = richEnabled,
            maxTotalMinutes = maxTotalMinutes,
            pausedAtEpochMs = null
        )
    }

    suspend fun showPaused(session: BreastfeedingSession, pausedAt: Instant) {
        val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
        val maxTotalMinutes = settingsRepository.getMaxTotalFeedMinutes().first()
        NotificationHelper.showBreastfeedingActive(
            context = context,
            sessionId = session.id,
            currentSide = currentSideName(session),
            sessionStartEpochMs = session.startTime.toEpochMilli(),
            pausedDurationMs = session.pausedDurationMs,
            richEnabled = richEnabled,
            maxTotalMinutes = maxTotalMinutes,
            pausedAtEpochMs = pausedAt.toEpochMilli()
        )
    }

    fun cancelScheduled() {
        notificationScheduler.cancelAllScheduledNotifications()
    }

    fun cancelPostedSessionNotifications() {
        NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID)
        NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
        NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_ACTIVE_NOTIFICATION_ID)
    }

    fun cancelAllSessionNotifications() {
        cancelScheduled()
        cancelPostedSessionNotifications()
    }

    suspend fun rescheduleAfterResume(
        pausedSession: BreastfeedingSession,
        resumeInstant: Instant = Instant.now()
    ): Long {
        val pausedAt = pausedSession.pausedAt ?: return pausedSession.pausedDurationMs
        val maxPerBreast = settingsRepository.getMaxPerBreastMinutes().first()
        val maxTotal = settingsRepository.getMaxTotalFeedMinutes().first()

        if (maxPerBreast > 0) {
            val adjustedTrigger = pausedSession.startTime
                .plusSeconds(maxPerBreast * 60L)
                .plusMillis(pausedSession.pausedDurationMs)
            val remaining = Duration.between(pausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxPerBreastNotificationAt(
                    triggerTime = resumeInstant.plus(remaining),
                    sessionId = pausedSession.id,
                    maxPerBreastMinutes = maxPerBreast,
                    currentSide = pausedSession.startingSide.name,
                    maxTotalMinutes = maxTotal
                )
            }
        }

        if (maxTotal > 0) {
            val adjustedTrigger = pausedSession.startTime
                .plusSeconds(maxTotal * 60L)
                .plusMillis(pausedSession.pausedDurationMs)
            val remaining = Duration.between(pausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                    triggerTime = resumeInstant.plus(remaining),
                    sessionId = pausedSession.id,
                    maxTotalMinutes = maxTotal,
                    currentSide = pausedSession.startingSide.name,
                    maxPerBreastMinutes = maxPerBreast
                )
            }
        }

        val currentPauseDurationMs = Duration.between(pausedAt, resumeInstant).toMillis()
        return pausedSession.pausedDurationMs + currentPauseDurationMs
    }

    private fun currentSideName(session: BreastfeedingSession): String =
        if (session.switchTime != null) {
            if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT.name else BreastSide.LEFT.name
        } else {
            session.startingSide.name
        }
}
