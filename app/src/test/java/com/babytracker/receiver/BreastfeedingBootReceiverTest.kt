package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.util.NotificationHelper
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedingBootReceiverTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var coordinator: BreastfeedingSessionNotificationCoordinator
    private lateinit var context: Context
    private lateinit var receiver: BreastfeedingBootReceiver

    private val session = BreastfeedingSession(
        id = 7L,
        startTime = Instant.parse("2026-04-24T10:00:00Z"),
        startingSide = BreastSide.LEFT
    )

    @BeforeEach
    fun setup() {
        repository = mockk()
        coordinator = mockk(relaxed = true)
        context = mockk(relaxed = true)
        mockkObject(NotificationHelper)
        every { NotificationHelper.createBreastfeedingNotificationChannel(any()) } returns Unit
        receiver = BreastfeedingBootReceiver().apply {
            repository = this@BreastfeedingBootReceiverTest.repository
            notificationCoordinator = coordinator
        }
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `restores notification and alarms when a session is active`() = runTest {
        every { repository.getActiveSession() } returns flowOf(session)

        receiver.handle(context)

        coVerify(exactly = 1) { coordinator.restoreActiveSession(session) }
    }

    @Test
    fun `does nothing when no session is active`() = runTest {
        every { repository.getActiveSession() } returns flowOf(null)

        receiver.handle(context)

        coVerify(exactly = 0) { coordinator.restoreActiveSession(any()) }
    }

    @Test
    fun `handles boot and clock-change actions only`() {
        assertTrue(receiver.shouldHandle(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(receiver.shouldHandle(Intent.ACTION_TIME_CHANGED))
        assertTrue(receiver.shouldHandle(Intent.ACTION_TIMEZONE_CHANGED))
        assertFalse(receiver.shouldHandle(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        assertFalse(receiver.shouldHandle(null))
    }
}
