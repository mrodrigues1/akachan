package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedingActionReceiverTest {

    private lateinit var repository: BreastfeedingRepository
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
        switchSide = mockk()
        stopSession = mockk()
        receiver = BreastfeedingActionReceiver()
        receiver.repository = repository
        receiver.switchSide = switchSide
        receiver.stopSession = stopSession

        coEvery { repository.getActiveSession() } returns flowOf(activeSession)
        mockkObject(NotificationHelper)
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `ACTION_SWITCH calls switchSide for matching session`() = runTest {
        coEvery { switchSide(activeSession) } returns Unit
        every { (mockk<Intent>()).getStringExtra(BreastfeedingActionReceiver.EXTRA_ACTION) } returns BreastfeedingActionReceiver.ACTION_SWITCH

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_SWITCH, 42L))

        coVerify { switchSide(activeSession) }
    }

    @Test
    fun `ACTION_STOP calls stopSession for matching session`() = runTest {
        coEvery { stopSession(activeSession) } returns Unit

        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_STOP, 42L))

        coVerify { stopSession(activeSession) }
    }

    @Test
    fun `ACTION_DISMISS cancels notification without touching repository`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_DISMISS, 42L))

        coVerify(exactly = 0) { switchSide(any()) }
        coVerify(exactly = 0) { stopSession(any()) }
        verify { NotificationHelper.cancelNotification(any(), any()) }
    }

    @Test
    fun `ACTION_KEEP_GOING cancels notification without touching repository`() = runTest {
        receiver.handle(context, intent(BreastfeedingActionReceiver.ACTION_KEEP_GOING, 42L))

        coVerify(exactly = 0) { switchSide(any()) }
        coVerify(exactly = 0) { stopSession(any()) }
        verify { NotificationHelper.cancelNotification(any(), any()) }
    }

    private fun intent(action: String, sessionId: Long) = mockk<Intent>(relaxed = true).also {
        every { it.getStringExtra(BreastfeedingActionReceiver.EXTRA_ACTION) } returns action
        every { it.getLongExtra(BreastfeedingActionReceiver.EXTRA_SESSION_ID, -1L) } returns sessionId
    }
}
