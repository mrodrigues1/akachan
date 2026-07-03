package com.babytracker.manager

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of the session control operations (start, switch, pause, resume, stop): persists
 * the change, keeps session notifications in step, and pushes the result to the partner snapshot.
 * Called from [com.babytracker.ui.breastfeeding.BreastfeedingViewModel] (in-app buttons),
 * [com.babytracker.receiver.BreastfeedingActionReceiver] (notification quick-actions), and
 * [com.babytracker.tile.TileToggleHandler] (quick-settings tile), which used to carry diverging
 * copies of this choreography.
 */
@Singleton
class BreastfeedingSessionController @Inject constructor(
    private val repository: BreastfeedingRepository,
    private val switchSideUseCase: SwitchBreastfeedingSideUseCase,
    private val pauseSessionUseCase: PauseBreastfeedingSessionUseCase,
    private val resumeSessionUseCase: ResumeBreastfeedingSessionUseCase,
    private val notificationCoordinator: BreastfeedingSessionNotificationCoordinator,
    private val syncedWrite: SyncedWrite,
    private val clock: Clock,
) {

    /**
     * Starts a new session for [side] via [BreastfeedingRepository.startSessionIfNone], which
     * guards against a concurrent start with a DB-level unique-active-session constraint. Returns
     * null without side effects when a session was already active — a benign race, not an error.
     * A notification failure does not fail the start; the session is still persisted and synced.
     */
    suspend fun start(side: BreastSide): BreastfeedingSession? {
        val session = BreastfeedingSession(startTime = clock.instant(), startingSide = side)
        val id = repository.startSessionIfNone(session) ?: return null
        val created = session.copy(id = id)
        runCatching { notificationCoordinator.scheduleInitial(created) }
        runCatching { notificationCoordinator.showRunning(created) }
        syncedWrite.sync(SyncType.SESSIONS)
        return created
    }

    suspend fun switchSide(session: BreastfeedingSession) {
        val switched = switchSideUseCase(session)
        if (session.switchTime == null) {
            notificationCoordinator.rearmPerBreastAfterSwitch(switched)
            notificationCoordinator.showRunning(switched)
        }
        syncedWrite.sync(SyncType.SESSIONS)
    }

    suspend fun pause(session: BreastfeedingSession) {
        if (session.isPaused) return
        val paused = pauseSessionUseCase(session)
        notificationCoordinator.cancelScheduled()
        notificationCoordinator.showPaused(paused, checkNotNull(paused.pausedAt))
        syncedWrite.sync(SyncType.SESSIONS)
    }

    suspend fun resume(session: BreastfeedingSession) {
        if (!session.isPaused) return
        val resumeInstant = clock.instant()
        val resumed = resumeSessionUseCase(session)
        val totalPausedMs = notificationCoordinator.rescheduleAfterResume(session, resumed, resumeInstant)
        notificationCoordinator.showRunning(resumed, pausedDurationMs = totalPausedMs)
        syncedWrite.sync(SyncType.SESSIONS)
    }

    /**
     * Ends the active session atomically: the DAO re-reads the active row inside a transaction
     * and folds any open pause into its paused duration so the trailing pause is not counted as
     * feeding time (AKACHAN-333) — no caller snapshot is trusted for the write. Returns false
     * when there is no active session or persisting failed, in which case notifications and the
     * partner snapshot are left untouched.
     */
    suspend fun stop(): Boolean {
        val stopped = runCatching { repository.stopActiveSession(clock.instant()) }.getOrDefault(false)
        if (!stopped) return false
        notificationCoordinator.cancelAllSessionNotifications()
        syncedWrite.sync(SyncType.SESSIONS)
        return true
    }
}
