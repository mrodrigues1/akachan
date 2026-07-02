package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.manager.BreastfeedingSessionController
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.util.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedingActionReceiverTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var switchSide: SwitchBreastfeedingSideUseCase
    private lateinit var pauseSession: PauseBreastfeedingSessionUseCase
    private lateinit var resumeSession: ResumeBreastfeedingSessionUseCase
    private lateinit var notificationCoordinator: BreastfeedingSessionNotificationCoordinator
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var receiver: BreastfeedingActionReceiver
    private val context = mockk<Context>(relaxed = true)

    private val activeSession = BreastfeedingSession(
        id = 42L,
        startTime = Instant.now(),
        startingSide = BreastSide.LEFT
    )

    @BeforeEach
    fun setup() {
        repository = mockk()
        switchSide = mockk()
        pauseSession = mockk()
        resumeSession = mockk()
        notificationCoordinator = mockk()
        syncToFirestore = mockk()
        receiver = BreastfeedingActionReceiver()
        receiver.repository = repository
        receiver.notificationCoordinator = notificationCoordinator
        // Real controller over the same mocks: the receiver contract is "delegates to the shared
        // session-control behaviour", so tests verify through to the underlying collaborators.
        receiver.sessionController = BreastfeedingSessionController(
            repository = repository,
            switchSideUseCase = switchSide,
            pauseSessionUseCase = pauseSession,
            resumeSessionUseCase = resumeSession,
            notificationCoordinator = notificationCoordinator,
            syncedWrite = SyncedWrite(syncToFirestore),
        )

        coEvery { repository.getActiveSession() } returns flowOf(activeSession)
        coEvery { syncToFirestore(any()) } returns Unit
        coEvery { pauseSession(any()) } returns Unit
        coEvery { resumeSession(any()) } returns Unit
        every { notificationCoordinator.cancelPerBreastScheduled() } returns Unit
        every { notificationCoordinator.cancelScheduled() } returns Unit
        every { notificationCoordinator.cancelPostedSessionNotifications() } returns Unit
        every { notificationCoordinator.cancelAllSessionNotifications() } returns Unit
        coEvery { notificationCoordinator.showPaused(any(), any()) } returns Unit
        coEvery { notificationCoordinator.showRunning(any(), any()) } returns Unit
        coEvery { notificationCoordinator.rescheduleAfterResume(any(), any()) } returns 60_000L
        coEvery { notificationCoordinator.rearmAfterKeepGoing(any()) } returns Unit
        mockkObject(NotificationHelper)
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `ACTION_SWITCH calls switchSide, refreshes active notification with new side, and cancels switch-side notification`() = runTest {
        coEvery { switchSide(activeSession) } returns Unit

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_SWITCH, 42L))

        coVerify { switchSide(activeSession) }
        verify { notificationCoordinator.cancelPerBreastScheduled() }
        coVerify {
            notificationCoordinator.showRunning(
                match { it.id == 42L && it.switchTime != null },
                pausedDurationMs = 0L
            )
        }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID) }
        verify(exactly = 0) { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun `ACTION_SWITCH does not refresh notification when already switched`() = runTest {
        val alreadySwitchedSession = activeSession.copy(switchTime = Instant.now().minusSeconds(60))
        coEvery { repository.getActiveSession() } returns flowOf(alreadySwitchedSession)
        coEvery { switchSide(alreadySwitchedSession) } returns Unit

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_SWITCH, 42L))

        coVerify(exactly = 0) { notificationCoordinator.showRunning(any(), any()) }
    }

    @Test
    fun `ACTION_STOP stops session, cancels all session notifications, and syncs`() = runTest {
        coEvery { repository.updateSession(any()) } returns Unit

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_STOP, 42L))

        coVerify { repository.updateSession(match { it.id == activeSession.id && it.endTime != null }) }
        verify(exactly = 1) { notificationCoordinator.cancelAllSessionNotifications() }
        verify(exactly = 1) { notificationCoordinator.cancelPostedSessionNotifications() }
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun `ACTION_STOP ignores wrong session id but still clears posted notifications`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_STOP, 999L))

        coVerify(exactly = 0) { repository.updateSession(any()) }
        verify(exactly = 0) { notificationCoordinator.cancelAllSessionNotifications() }
        verify(exactly = 1) { notificationCoordinator.cancelPostedSessionNotifications() }
        coVerify(exactly = 0) { syncToFirestore(any()) }
    }

    @Test
    fun `ACTION_PAUSE pauses active session cancels scheduled alarms and shows paused notification`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_PAUSE, 42L))

        coVerify { pauseSession(activeSession) }
        verify { notificationCoordinator.cancelScheduled() }
        coVerify { notificationCoordinator.showPaused(activeSession, any()) }
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun `ACTION_PAUSE ignores wrong session id`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_PAUSE, 999L))

        coVerify(exactly = 0) { pauseSession(any()) }
        verify(exactly = 0) { notificationCoordinator.cancelScheduled() }
        coVerify(exactly = 0) { notificationCoordinator.showPaused(any(), any()) }
        coVerify(exactly = 0) { syncToFirestore(any()) }
    }

    @Test
    fun `ACTION_PAUSE ignores already paused session`() = runTest {
        val pausedSession = activeSession.copy(pausedAt = Instant.now())
        coEvery { repository.getActiveSession() } returns flowOf(pausedSession)

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_PAUSE, 42L))

        coVerify(exactly = 0) { pauseSession(any()) }
        verify(exactly = 0) { notificationCoordinator.cancelScheduled() }
        coVerify(exactly = 0) { notificationCoordinator.showPaused(any(), any()) }
    }

    @Test
    fun `ACTION_RESUME resumes paused session reschedules alarms and shows running notification`() = runTest {
        val pausedSession = activeSession.copy(pausedAt = Instant.now().minusSeconds(30))
        coEvery { repository.getActiveSession() } returns flowOf(pausedSession)
        coEvery { notificationCoordinator.rescheduleAfterResume(pausedSession, any()) } returns 30_000L

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_RESUME, 42L))

        coVerify { resumeSession(pausedSession) }
        coVerify { notificationCoordinator.rescheduleAfterResume(pausedSession, any()) }
        coVerify { notificationCoordinator.showRunning(pausedSession, pausedDurationMs = 30_000L) }
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun `ACTION_RESUME ignores wrong session id`() = runTest {
        val pausedSession = activeSession.copy(pausedAt = Instant.now().minusSeconds(30))
        coEvery { repository.getActiveSession() } returns flowOf(pausedSession)

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_RESUME, 999L))

        coVerify(exactly = 0) { resumeSession(any()) }
        coVerify(exactly = 0) { notificationCoordinator.rescheduleAfterResume(any(), any()) }
        coVerify(exactly = 0) { notificationCoordinator.showRunning(any(), any()) }
    }

    @Test
    fun `ACTION_RESUME ignores running session`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_RESUME, 42L))

        coVerify(exactly = 0) { resumeSession(any()) }
        coVerify(exactly = 0) { notificationCoordinator.rescheduleAfterResume(any(), any()) }
        coVerify(exactly = 0) { notificationCoordinator.showRunning(any(), any()) }
    }

    @Test
    fun `ACTION_DISMISS cancels only switch-side notification without touching repository`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_DISMISS, 42L))

        coVerify(exactly = 0) { switchSide(any()) }
        coVerify(exactly = 0) { repository.updateSession(any()) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID) }
        verify(exactly = 0) { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
    }

    @Test
    fun `ACTION_KEEP_GOING cancels feeding-limit notification and rearms alarm for matching session`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_KEEP_GOING, 42L))

        verify { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
        verify(exactly = 0) { NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID) }
        coVerify { notificationCoordinator.rearmAfterKeepGoing(activeSession) }
    }

    @Test
    fun `ACTION_KEEP_GOING skips rearm for wrong session id`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_KEEP_GOING, 999L))

        verify { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
        coVerify(exactly = 0) { notificationCoordinator.rearmAfterKeepGoing(any()) }
    }

    @Test
    fun `ACTION_KEEP_GOING skips rearm when no active session`() = runTest {
        coEvery { repository.getActiveSession() } returns flowOf(null)

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_KEEP_GOING, 42L))

        verify { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
        coVerify(exactly = 0) { notificationCoordinator.rearmAfterKeepGoing(any()) }
    }

    @Test
    fun `ACTION_REFRESH_ACTIVE reposts active notification for matching running session`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_REFRESH_ACTIVE, 42L))

        coVerify { notificationCoordinator.showRunning(activeSession) }
    }

    @Test
    fun `ACTION_REFRESH_ACTIVE ignores wrong session id`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_REFRESH_ACTIVE, 999L))

        coVerify(exactly = 0) { notificationCoordinator.showRunning(any(), any()) }
    }

    @Test
    fun `ACTION_REFRESH_ACTIVE ignores missing active session`() = runTest {
        coEvery { repository.getActiveSession() } returns flowOf(null)

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_REFRESH_ACTIVE, 42L))

        coVerify(exactly = 0) { notificationCoordinator.showRunning(any(), any()) }
    }

    @Test
    fun `ACTION_REFRESH_ACTIVE ignores paused active session`() = runTest {
        val pausedSession = activeSession.copy(pausedAt = Instant.now())
        coEvery { repository.getActiveSession() } returns flowOf(pausedSession)

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_REFRESH_ACTIVE, 42L))

        coVerify(exactly = 0) { notificationCoordinator.showRunning(any(), any()) }
    }

    private fun intent(action: String, sessionId: Long) = mockk<Intent>(relaxed = true).also {
        every { it.getStringExtra(BreastfeedingActionReceiver.EXTRA_ACTION) } returns action
        every { it.getLongExtra(BreastfeedingActionReceiver.EXTRA_SESSION_ID, -1L) } returns sessionId
    }
}
