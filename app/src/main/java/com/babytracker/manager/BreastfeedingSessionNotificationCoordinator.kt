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

    /**
     * Cancels the first breast's limit alarm and arms one for the breast just switched to,
     * anchored at [switchedSession]'s switchTime. Skipped while paused — [rescheduleAfterResume]
     * arms it on resume instead.
     */
    suspend fun rearmPerBreastAfterSwitch(switchedSession: BreastfeedingSession) {
        notificationScheduler.cancelPerBreastNotification()
        val switchTime = switchedSession.switchTime ?: return
        if (switchedSession.pausedAt != null) return
        val (maxPerBreastMinutes, maxTotalFeedMinutes) = scheduleMinutes()
        if (maxPerBreastMinutes <= 0) return
        notificationScheduler.scheduleMaxPerBreastNotificationAt(
            triggerTime = switchTime.plusSeconds(maxPerBreastMinutes * 60L),
            sessionId = switchedSession.id,
            maxPerBreastMinutes = maxPerBreastMinutes,
            currentSide = currentSideName(switchedSession),
            maxTotalMinutes = maxTotalFeedMinutes
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

    /**
     * Reschedules the not-yet-due limit alarms after a resume, anchored on [pausedSession] (the
     * pre-resume snapshot, still carrying `pausedAt`). Returns [resumedSession]'s already-folded
     * `pausedDurationMs` — the value [ResumeBreastfeedingSessionUseCase] persisted, clamp and all —
     * instead of recomputing it here, so this and the DB never independently derive the total.
     */
    suspend fun rescheduleAfterResume(
        pausedSession: BreastfeedingSession,
        resumedSession: BreastfeedingSession,
        resumeInstant: Instant,
    ): Long {
        val pausedAt = pausedSession.pausedAt ?: return resumedSession.pausedDurationMs
        val (maxPerBreastMinutes, maxTotalFeedMinutes) = scheduleMinutes()

        if (maxPerBreastMinutes > 0) {
            // After a switch the alarm is anchored on switchTime, so pre-switch pauses are
            // excluded by dropping pausedDurationMs (it is session-wide, not per side).
            // ponytail: closed pauses after the switch aren't tracked per side, so the alarm can
            // fire early on the second breast after repeated pause/resume; add a per-side pause
            // ledger if that ever matters.
            val switchTime = pausedSession.switchTime
            val adjustedTrigger = if (switchTime != null) {
                switchTime.plusSeconds(maxPerBreastMinutes * 60L)
            } else {
                pausedSession.startTime
                    .plusSeconds(maxPerBreastMinutes * 60L)
                    .plusMillis(pausedSession.pausedDurationMs)
            }
            // A switch while paused means the new breast hasn't been fed yet — count from the
            // switch, not from the pause start.
            val effectivePausedAt = switchTime?.let { maxOf(pausedAt, it) } ?: pausedAt
            val remaining = Duration.between(effectivePausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxPerBreastNotificationAt(
                    triggerTime = resumeInstant.plus(remaining),
                    sessionId = pausedSession.id,
                    maxPerBreastMinutes = maxPerBreastMinutes,
                    currentSide = currentSideName(pausedSession),
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
                    currentSide = currentSideName(pausedSession),
                    maxPerBreastMinutes = maxPerBreastMinutes
                )
            }
        }

        return resumedSession.pausedDurationMs
    }

    /**
     * Re-posts the active-session notification and re-arms the not-yet-due limit alarms after a
     * reboot (or wall-clock change) wiped them. A paused session gets only the paused
     * notification — its alarms are re-armed by [rescheduleAfterResume] on resume, matching the
     * pause flow.
     */
    suspend fun restoreActiveSession(session: BreastfeedingSession, now: Instant = Instant.now()) {
        val pausedAt = session.pausedAt
        if (pausedAt != null) {
            showPaused(session, pausedAt)
            return
        }
        showRunning(session)
        val (maxPerBreastMinutes, maxTotalFeedMinutes) = scheduleMinutes()
        // ponytail: triggers already in the past are skipped — a reboot spanning the exact
        // trigger moment loses that one reminder rather than re-nagging late.
        if (maxPerBreastMinutes > 0) {
            // Same anchoring as rescheduleAfterResume: switchTime after a switch (session-wide
            // pausedDurationMs excluded), startTime + pausedDurationMs otherwise.
            val trigger = (session.switchTime ?: session.startTime.plusMillis(session.pausedDurationMs))
                .plusSeconds(maxPerBreastMinutes * 60L)
            if (trigger.isAfter(now)) {
                notificationScheduler.scheduleMaxPerBreastNotificationAt(
                    triggerTime = trigger,
                    sessionId = session.id,
                    maxPerBreastMinutes = maxPerBreastMinutes,
                    currentSide = currentSideName(session),
                    maxTotalMinutes = maxTotalFeedMinutes
                )
            }
        }
        if (maxTotalFeedMinutes > 0) {
            val trigger = session.startTime
                .plusSeconds(maxTotalFeedMinutes * 60L)
                .plusMillis(session.pausedDurationMs)
            if (trigger.isAfter(now)) {
                notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                    triggerTime = trigger,
                    sessionId = session.id,
                    maxTotalMinutes = maxTotalFeedMinutes,
                    currentSide = currentSideName(session),
                    maxPerBreastMinutes = maxPerBreastMinutes
                )
            }
        }
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
