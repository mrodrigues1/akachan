package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingActiveNotificationSettings
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.FeedSettingsRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.getBreastfeedingActiveNotificationSettings
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreastfeedingSessionNotificationCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val feedSettingsRepository: FeedSettingsRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler
) {

    suspend fun scheduleInitial(session: BreastfeedingSession) {
        val (maxPerBreastMinutes, maxTotalFeedMinutes) = scheduleMinutes()

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
        val settings = breastfeedingActiveNotificationSettings()
        NotificationHelper.showBreastfeedingActive(
            context = context,
            sessionId = session.id,
            currentSide = currentSideName(session),
            sessionStartEpochMs = session.startTime.toEpochMilli(),
            pausedDurationMs = pausedDurationMs,
            richEnabled = settings.richNotificationsEnabled,
            maxTotalMinutes = settings.maxTotalFeedMinutes,
            pausedAtEpochMs = null,
            canSwitchSides = session.switchTime == null
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
            pausedAtEpochMs = pausedAt.toEpochMilli(),
            canSwitchSides = session.switchTime == null
        )
    }

    fun cancelPerBreastScheduled() {
        notificationScheduler.cancelPerBreastNotification()
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
        val (maxPerBreastMinutes, maxTotalFeedMinutes) = scheduleMinutes()

        if (maxPerBreastMinutes > 0) {
            val adjustedTrigger = pausedSession.startTime
                .plusSeconds(maxPerBreastMinutes * 60L)
                .plusMillis(pausedSession.pausedDurationMs)
            val remaining = Duration.between(pausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxPerBreastNotificationAt(
                    triggerTime = resumeInstant.plus(remaining),
                    sessionId = pausedSession.id,
                    maxPerBreastMinutes = maxPerBreastMinutes,
                    currentSide = pausedSession.startingSide.name,
                    maxTotalMinutes = maxTotalFeedMinutes
                )
            }
        }

        if (maxTotalFeedMinutes > 0) {
            val adjustedTrigger = pausedSession.startTime
                .plusSeconds(maxTotalFeedMinutes * 60L)
                .plusMillis(pausedSession.pausedDurationMs)
            val remaining = Duration.between(pausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                    triggerTime = resumeInstant.plus(remaining),
                    sessionId = pausedSession.id,
                    maxTotalMinutes = maxTotalFeedMinutes,
                    currentSide = pausedSession.startingSide.name,
                    maxPerBreastMinutes = maxPerBreastMinutes
                )
            }
        }

        val currentPauseDurationMs = Duration.between(pausedAt, resumeInstant).toMillis()
        return pausedSession.pausedDurationMs + currentPauseDurationMs
    }

    suspend fun rearmAfterKeepGoing(session: BreastfeedingSession) {
        val (maxPerBreastMinutes, maxTotalFeedMinutes) = scheduleMinutes()
        if (maxTotalFeedMinutes > 0) {
            notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                triggerTime = Instant.now().plusSeconds(600),
                sessionId = session.id,
                maxTotalMinutes = maxTotalFeedMinutes,
                currentSide = currentSideName(session),
                maxPerBreastMinutes = maxPerBreastMinutes
            )
        }
    }

    private suspend fun breastfeedingActiveNotificationSettings(): BreastfeedingActiveNotificationSettings =
        feedSettingsRepository.getBreastfeedingActiveNotificationSettings(settingsRepository).first()

    /** (maxPerBreastMinutes, maxTotalFeedMinutes) — the feed-owned scheduling limits. */
    private suspend fun scheduleMinutes(): Pair<Int, Int> =
        combine(
            feedSettingsRepository.getMaxPerBreastMinutes(),
            feedSettingsRepository.getMaxTotalFeedMinutes(),
        ) { maxPerBreast, maxTotal -> maxPerBreast to maxTotal }.first()

    private fun currentSideName(session: BreastfeedingSession): String =
        if (session.switchTime != null) {
            if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT.name else BreastSide.LEFT.name
        } else {
            session.startingSide.name
        }
}
