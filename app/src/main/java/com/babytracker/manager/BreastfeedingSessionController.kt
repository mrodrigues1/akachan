package com.babytracker.manager

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of the running-session control operations (switch, pause, resume, stop): persists
 * the change, keeps session notifications in step, and pushes the result to the partner snapshot.
 * Called from both [com.babytracker.ui.breastfeeding.BreastfeedingViewModel] (in-app buttons) and
 * [com.babytracker.receiver.BreastfeedingActionReceiver] (notification quick-actions), which used
 * to carry diverging copies of this choreography.
 */
@Singleton
class BreastfeedingSessionController @Inject constructor(
    private val repository: BreastfeedingRepository,
    private val switchSideUseCase: SwitchBreastfeedingSideUseCase,
    private val pauseSessionUseCase: PauseBreastfeedingSessionUseCase,
    private val resumeSessionUseCase: ResumeBreastfeedingSessionUseCase,
    private val notificationCoordinator: BreastfeedingSessionNotificationCoordinator,
    private val syncedWrite: SyncedWrite,
) {

    suspend fun switchSide(session: BreastfeedingSession) {
        switchSideUseCase(session)
        if (session.switchTime == null) {
            notificationCoordinator.cancelPerBreastScheduled()
            notificationCoordinator.showRunning(session.copy(switchTime = Instant.now()))
        }
        syncedWrite.sync(SyncType.SESSIONS)
    }

    suspend fun pause(session: BreastfeedingSession) {
        if (session.isPaused) return
        val pausedAt = Instant.now()
        pauseSessionUseCase(session)
        notificationCoordinator.cancelScheduled()
        notificationCoordinator.showPaused(session, pausedAt)
        syncedWrite.sync(SyncType.SESSIONS)
    }

    suspend fun resume(session: BreastfeedingSession) {
        if (!session.isPaused) return
        val resumeInstant = Instant.now()
        resumeSessionUseCase(session)
        val totalPausedMs = notificationCoordinator.rescheduleAfterResume(session, resumeInstant)
        notificationCoordinator.showRunning(session, pausedDurationMs = totalPausedMs)
        syncedWrite.sync(SyncType.SESSIONS)
    }

    /**
     * Ends the session. Returns false when persisting the end time failed, in which case
     * notifications and the partner snapshot are left untouched.
     */
    suspend fun stop(session: BreastfeedingSession): Boolean {
        val result = runCatching { repository.updateSession(session.copy(endTime = Instant.now())) }
        if (result.isFailure) return false
        notificationCoordinator.cancelAllSessionNotifications()
        syncedWrite.sync(SyncType.SESSIONS)
        return true
    }
}
