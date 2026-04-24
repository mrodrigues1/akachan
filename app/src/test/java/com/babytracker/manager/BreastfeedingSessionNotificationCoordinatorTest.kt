package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedingSessionNotificationCoordinatorTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationScheduler: NotificationScheduler
    private lateinit var coordinator: BreastfeedingSessionNotificationCoordinator

    private val session = BreastfeedingSession(
        id = 42L,
        startTime = Instant.parse("2026-04-24T10:00:00Z"),
        startingSide = BreastSide.LEFT
    )

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        settingsRepository = mockk()
        notificationScheduler = mockk()

        every { settingsRepository.getMaxPerBreastMinutes() } returns flowOf(15)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns flowOf(30)
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(false)

        every { notificationScheduler.scheduleMaxPerBreastNotification(any(), any(), any(), any(), any()) } returns Unit
        every { notificationScheduler.scheduleMaxTotalTimeNotification(any(), any(), any(), any(), any()) } returns Unit
        every { notificationScheduler.scheduleMaxPerBreastNotificationAt(any(), any(), any(), any(), any()) } returns Unit
        every { notificationScheduler.scheduleMaxTotalTimeNotificationAt(any(), any(), any(), any(), any()) } returns Unit
        every { notificationScheduler.cancelAllScheduledNotifications() } returns Unit

        mockkObject(NotificationHelper)
        every { NotificationHelper.showBreastfeedingActive(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit

        coordinator = BreastfeedingSessionNotificationCoordinator(
            context = context,
            settingsRepository = settingsRepository,
            notificationScheduler = notificationScheduler
        )
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `scheduleInitial schedules per breast and total alarms`() = runTest {
        coordinator.scheduleInitial(session)

        verify {
            notificationScheduler.scheduleMaxPerBreastNotification(
                sessionStartTime = session.startTime,
                maxPerBreastMinutes = 15,
                sessionId = 42L,
                currentSide = "LEFT",
                maxTotalMinutes = 30
            )
        }
        verify {
            notificationScheduler.scheduleMaxTotalTimeNotification(
                sessionStartTime = session.startTime,
                maxTotalMinutes = 30,
                sessionId = 42L,
                currentSide = "LEFT",
                maxPerBreastMinutes = 15
            )
        }
    }

    @Test
    fun `showRunning posts running active notification`() = runTest {
        coordinator.showRunning(session)

        verify {
            NotificationHelper.showBreastfeedingActive(
                context = context,
                sessionId = 42L,
                currentSide = "LEFT",
                sessionStartEpochMs = session.startTime.toEpochMilli(),
                pausedDurationMs = 0L,
                richEnabled = false,
                maxTotalMinutes = 30,
                pausedAtEpochMs = null
            )
        }
    }

    @Test
    fun `showPaused posts paused active notification with paused timestamp`() = runTest {
        val pausedAt = Instant.parse("2026-04-24T10:05:00Z")

        coordinator.showPaused(session, pausedAt)

        verify {
            NotificationHelper.showBreastfeedingActive(
                context = context,
                sessionId = 42L,
                currentSide = "LEFT",
                sessionStartEpochMs = session.startTime.toEpochMilli(),
                pausedDurationMs = 0L,
                richEnabled = false,
                maxTotalMinutes = 30,
                pausedAtEpochMs = pausedAt.toEpochMilli()
            )
        }
    }

    @Test
    fun `rescheduleAfterResume reschedules remaining alarms and returns total paused duration`() = runTest {
        val pausedSession = session.copy(pausedAt = Instant.parse("2026-04-24T10:05:00Z"))
        val resumeInstant = Instant.parse("2026-04-24T10:06:00Z")

        val totalPausedMs = coordinator.rescheduleAfterResume(pausedSession, resumeInstant)

        assertEquals(60_000L, totalPausedMs)
        verify { notificationScheduler.scheduleMaxPerBreastNotificationAt(any(), 42L, 15, "LEFT", 30) }
        verify { notificationScheduler.scheduleMaxTotalTimeNotificationAt(any(), 42L, 30, "LEFT", 15) }
    }

    @Test
    fun `cancelPostedSessionNotifications cancels all breastfeeding notifications without cancelling alarms`() {
        coordinator.cancelPostedSessionNotifications()

        verify(exactly = 0) { notificationScheduler.cancelAllScheduledNotifications() }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_ACTIVE_NOTIFICATION_ID) }
    }

    @Test
    fun `cancelAllSessionNotifications cancels alarms and all breastfeeding notifications`() {
        coordinator.cancelAllSessionNotifications()

        verify { notificationScheduler.cancelAllScheduledNotifications() }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID) }
        verify { NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_ACTIVE_NOTIFICATION_ID) }
    }
}
