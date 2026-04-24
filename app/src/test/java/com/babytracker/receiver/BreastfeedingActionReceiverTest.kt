package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
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
        receiver = BreastfeedingActionReceiver()
        receiver.repository = repository
        receiver.settingsRepository = settingsRepository
        receiver.switchSide = switchSide
        receiver.stopSession = stopSession

        coEvery { repository.getActiveSession() } returns flowOf(activeSession)
        every { settingsRepository.getRichNotificationsEnabled() } returns MutableStateFlow(false)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns MutableStateFlow(30)
        mockkObject(NotificationHelper)
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit
        every { NotificationHelper.showBreastfeedingActive(any(), any(), any(), any(), any(), any(), any()) } returns Unit
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

        verify(exactly = 0) { NotificationHelper.showBreastfeedingActive(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `ACTION_STOP calls stopSession and cancels feeding-limit, switch-side, and active notifications`() = runTest {
        coEvery { stopSession(activeSession) } returns Unit

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_STOP, 42L))

        coVerify { stopSession(activeSession) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_ACTIVE_NOTIFICATION_ID) }
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

    private fun intent(action: String, sessionId: Long) = mockk<Intent>(relaxed = true).also {
        every { it.getStringExtra(BreastfeedingActionReceiver.EXTRA_ACTION) } returns action
        every { it.getLongExtra(BreastfeedingActionReceiver.EXTRA_SESSION_ID, -1L) } returns sessionId
    }
}
