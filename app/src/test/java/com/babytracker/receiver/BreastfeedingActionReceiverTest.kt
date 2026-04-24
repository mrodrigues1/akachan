package com.babytracker.receiver

import android.content.Context
import android.content.Intent
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedingActionReceiverTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var switchSide: SwitchBreastfeedingSideUseCase
    private lateinit var stopSession: StopBreastfeedingSessionUseCase
    private lateinit var pauseSession: PauseBreastfeedingSessionUseCase
    private lateinit var resumeSession: ResumeBreastfeedingSessionUseCase
    private lateinit var notificationCoordinator: BreastfeedingSessionNotificationCoordinator
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
        settingsRepository = mockk()
        switchSide = mockk()
        stopSession = mockk()
        pauseSession = mockk()
        resumeSession = mockk()
        notificationCoordinator = mockk()
        receiver = BreastfeedingActionReceiver()
        receiver.repository = repository
        receiver.settingsRepository = settingsRepository
        receiver.switchSide = switchSide
        receiver.stopSession = stopSession
        receiver.pauseSession = pauseSession
        receiver.resumeSession = resumeSession
        receiver.notificationCoordinator = notificationCoordinator

        coEvery { repository.getActiveSession() } returns flowOf(activeSession)
        every { settingsRepository.getRichNotificationsEnabled() } returns MutableStateFlow(false)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns MutableStateFlow(30)
        coEvery { pauseSession(any()) } returns Unit
        coEvery { resumeSession(any()) } returns Unit
        every { notificationCoordinator.cancelScheduled() } returns Unit
        every { notificationCoordinator.cancelAllSessionNotifications() } returns Unit
        coEvery { notificationCoordinator.showPaused(any(), any()) } returns Unit
        coEvery { notificationCoordinator.showRunning(any(), any()) } returns Unit
        coEvery { notificationCoordinator.rescheduleAfterResume(any(), any()) } returns 60_000L
        mockkObject(NotificationHelper)
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit
        every { NotificationHelper.showBreastfeedingActive(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `ACTION_SWITCH calls switchSide, refreshes active notification with new side, and cancels switch-side notification`() = runTest {
        coEvery { switchSide(activeSession) } returns Unit

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_SWITCH, 42L))

        coVerify { switchSide(activeSession) }
        verify {
            NotificationHelper.showBreastfeedingActive(
                context = context,
                sessionId = 42L,
                currentSide = "RIGHT",
                sessionStartEpochMs = activeSession.startTime.toEpochMilli(),
                pausedDurationMs = 0L,
                richEnabled = false,
                maxTotalMinutes = 30
            )
        }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID) }
        verify(exactly = 0) { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
    }

    @Test
    fun `ACTION_SWITCH does not refresh notification when already switched`() = runTest {
        val alreadySwitchedSession = activeSession.copy(switchTime = Instant.now().minusSeconds(60))
        coEvery { repository.getActiveSession() } returns flowOf(alreadySwitchedSession)
        coEvery { switchSide(alreadySwitchedSession) } returns Unit

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_SWITCH, 42L))

        verify(exactly = 0) { NotificationHelper.showBreastfeedingActive(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `ACTION_STOP calls stopSession and cancels feeding-limit, switch-side, and active notifications`() = runTest {
        coEvery { stopSession(activeSession) } returns Unit

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_STOP, 42L))

        coVerify { stopSession(activeSession) }
        verify { notificationCoordinator.cancelAllSessionNotifications() }
    }

    @Test
    fun `ACTION_PAUSE pauses active session cancels scheduled alarms and shows paused notification`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_PAUSE, 42L))

        coVerify { pauseSession(activeSession) }
        verify { notificationCoordinator.cancelScheduled() }
        coVerify { notificationCoordinator.showPaused(activeSession, any()) }
    }

    @Test
    fun `ACTION_PAUSE ignores wrong session id`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_PAUSE, 999L))

        coVerify(exactly = 0) { pauseSession(any()) }
        verify(exactly = 0) { notificationCoordinator.cancelScheduled() }
        coVerify(exactly = 0) { notificationCoordinator.showPaused(any(), any()) }
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
        coVerify(exactly = 0) { stopSession(any()) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID) }
        verify(exactly = 0) { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
    }

    @Test
    fun `ACTION_KEEP_GOING cancels only feeding-limit notification without touching repository`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_KEEP_GOING, 42L))

        coVerify(exactly = 0) { switchSide(any()) }
        coVerify(exactly = 0) { stopSession(any()) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
        verify(exactly = 0) { NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID) }
    }

    @Test
    fun `ACTION_REFRESH_ACTIVE reposts active notification with current progress target`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_REFRESH_ACTIVE, 42L))

        verify {
            NotificationHelper.showBreastfeedingActive(
                context = context,
                sessionId = 42L,
                currentSide = "LEFT",
                sessionStartEpochMs = activeSession.startTime.toEpochMilli(),
                pausedDurationMs = 0L,
                richEnabled = false,
                maxTotalMinutes = 30
            )
        }
    }

    @Test
    fun `ACTION_REFRESH_ACTIVE ignores wrong session id`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_REFRESH_ACTIVE, 999L))

        verify(exactly = 0) {
            NotificationHelper.showBreastfeedingActive(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `ACTION_REFRESH_ACTIVE ignores missing active session`() = runTest {
        coEvery { repository.getActiveSession() } returns flowOf(null)

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_REFRESH_ACTIVE, 42L))

        verify(exactly = 0) {
            NotificationHelper.showBreastfeedingActive(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `ACTION_REFRESH_ACTIVE ignores paused active session`() = runTest {
        val pausedSession = activeSession.copy(pausedAt = Instant.now())
        coEvery { repository.getActiveSession() } returns flowOf(pausedSession)

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_REFRESH_ACTIVE, 42L))

        verify(exactly = 0) {
            NotificationHelper.showBreastfeedingActive(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    private fun intent(action: String, sessionId: Long) = mockk<Intent>(relaxed = true).also {
        every { it.getStringExtra(BreastfeedingActionReceiver.EXTRA_ACTION) } returns action
        every { it.getLongExtra(BreastfeedingActionReceiver.EXTRA_SESSION_ID, -1L) } returns sessionId
    }
}
