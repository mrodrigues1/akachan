package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingActiveNotificationSettings
import com.babytracker.domain.model.BreastfeedingNotificationScheduleSettings
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.getBreastfeedingActiveNotificationSettings
import com.babytracker.domain.repository.getBreastfeedingNotificationScheduleSettings
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
        val settings = breastfeedingNotificationScheduleSettings()

        if (settings.maxPerBreastMinutes > 0) {
            notificationScheduler.scheduleMaxPerBreastNotification(
                sessionStartTime = session.startTime,
                maxPerBreastMinutes = settings.maxPerBreastMinutes,
                sessionId = session.id,
                currentSide = session.startingSide.name,
                maxTotalMinutes = settings.maxTotalFeedMinutes
            )
        }

        if (settings.maxTotalFeedMinutes > 0) {
            notificationScheduler.scheduleMaxTotalTimeNotification(
                sessionStartTime = session.startTime,
                maxTotalMinutes = settings.maxTotalFeedMinutes,
                sessionId = session.id,
                currentSide = session.startingSide.name,
                maxPerBreastMinutes = settings.maxPerBreastMinutes
            )
        }
    }

    suspend fun showRunning(
        session: BreastfeedingSession,
        pausedDurationMs: Long = session.pausedDurationMs
    ) {
        val settings = breastfeedingActiveNotificationSettings()
        NotificationHelper.showBreastfeedingActive(
            context = context,
            sessionId = session.id,
            currentSide = currentSideName(session),
            sessionStartEpochMs = session.startTime.toEpochMilli(),
            pausedDurationMs = pausedDurationMs,
            richEnabled = settings.richNotificationsEnabled,
            maxTotalMinutes = settings.maxTotalFeedMinutes,
            pausedAtEpochMs = null
        )
    }

    suspend fun showPaused(session: BreastfeedingSession, pausedAt: Instant) {
        val settings = breastfeedingActiveNotificationSettings()
        NotificationHelper.showBreastfeedingActive(
            context = context,
            sessionId = session.id,
            currentSide = currentSideName(session),
            sessionStartEpochMs = session.startTime.toEpochMilli(),
            pausedDurationMs = session.pausedDurationMs,
            richEnabled = settings.richNotificationsEnabled,
            maxTotalMinutes = settings.maxTotalFeedMinutes,
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
        NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_GROUP_SUMMARY_NOTIFICATION_ID)
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
        val settings = breastfeedingNotificationScheduleSettings()

        if (settings.maxPerBreastMinutes > 0) {
            val adjustedTrigger = pausedSession.startTime
                .plusSeconds(settings.maxPerBreastMinutes * 60L)
                .plusMillis(pausedSession.pausedDurationMs)
            val remaining = Duration.between(pausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxPerBreastNotificationAt(
                    triggerTime = resumeInstant.plus(remaining),
                    sessionId = pausedSession.id,
                    maxPerBreastMinutes = settings.maxPerBreastMinutes,
                    currentSide = pausedSession.startingSide.name,
                    maxTotalMinutes = settings.maxTotalFeedMinutes
                )
            }
        }

        if (settings.maxTotalFeedMinutes > 0) {
            val adjustedTrigger = pausedSession.startTime
                .plusSeconds(settings.maxTotalFeedMinutes * 60L)
                .plusMillis(pausedSession.pausedDurationMs)
            val remaining = Duration.between(pausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                    triggerTime = resumeInstant.plus(remaining),
                    sessionId = pausedSession.id,
                    maxTotalMinutes = settings.maxTotalFeedMinutes,
                    currentSide = pausedSession.startingSide.name,
                    maxPerBreastMinutes = settings.maxPerBreastMinutes
                )
            }
        }

        val currentPauseDurationMs = Duration.between(pausedAt, resumeInstant).toMillis()
        return pausedSession.pausedDurationMs + currentPauseDurationMs
    }

    suspend fun rearmAfterKeepGoing(sessionId: Long, currentSide: String) {
        val settings = breastfeedingNotificationScheduleSettings()
        if (settings.maxTotalFeedMinutes > 0) {
            notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                triggerTime = Instant.now().plusSeconds(600),
                sessionId = sessionId,
                maxTotalMinutes = settings.maxTotalFeedMinutes,
                currentSide = currentSide,
                maxPerBreastMinutes = settings.maxPerBreastMinutes
            )
        }
    }

    private suspend fun breastfeedingActiveNotificationSettings(): BreastfeedingActiveNotificationSettings =
        settingsRepository.getBreastfeedingActiveNotificationSettings().first()

    private suspend fun breastfeedingNotificationScheduleSettings(): BreastfeedingNotificationScheduleSettings =
        settingsRepository.getBreastfeedingNotificationScheduleSettings().first()

    private fun currentSideName(session: BreastfeedingSession): String =
        if (session.switchTime != null) {
            if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT.name else BreastSide.LEFT.name
        } else {
            session.startingSide.name
        }
}
