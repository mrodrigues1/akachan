package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.FeedSettingsRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedingSessionNotificationCoordinatorTest {

    private lateinit var context: Context
    private lateinit var feedSettingsRepository: FeedSettingsRepository
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
        feedSettingsRepository = mockk()
        settingsRepository = mockk()
        notificationScheduler = mockk()

        every { feedSettingsRepository.getMaxPerBreastMinutes() } returns flowOf(15)
        every { feedSettingsRepository.getMaxTotalFeedMinutes() } returns flowOf(30)
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(false)

        every { notificationScheduler.scheduleMaxPerBreastNotification(any(), any(), any(), any(), any()) } returns Unit
        every { notificationScheduler.scheduleMaxTotalTimeNotification(any(), any(), any(), any(), any()) } returns Unit
        every { notificationScheduler.scheduleMaxPerBreastNotificationAt(any(), any(), any(), any(), any()) } returns Unit
        every { notificationScheduler.scheduleMaxTotalTimeNotificationAt(any(), any(), any(), any(), any()) } returns Unit
        every { notificationScheduler.cancelAllScheduledNotifications() } returns Unit
        every { notificationScheduler.cancelPerBreastNotification() } returns Unit

        mockkObject(NotificationHelper)
        every { NotificationHelper.showBreastfeedingActive(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
        every { NotificationHelper.cancelNotification(any(), any()) } returns Unit

        coordinator = BreastfeedingSessionNotificationCoordinator(
            context = context,
            feedSettingsRepository = feedSettingsRepository,
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
    fun `scheduleInitial collects schedule settings in parallel`() = runTest {
        stubScheduleSettingsAwaitingParallelCollection()

        withTimeout(1_000L) {
            coordinator.scheduleInitial(session)
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
    fun `showRunning collects active notification settings in parallel`() = runTest {
        stubActiveNotificationSettingsAwaitingParallelCollection()

        withTimeout(1_000L) {
            coordinator.showRunning(session)
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
    fun `rearmPerBreastAfterSwitch cancels first-breast alarm and arms new side from switch time`() = runTest {
        val switched = session.copy(switchTime = Instant.parse("2026-04-24T10:10:00Z"))

        coordinator.rearmPerBreastAfterSwitch(switched)

        verify { notificationScheduler.cancelPerBreastNotification() }
        verify {
            notificationScheduler.scheduleMaxPerBreastNotificationAt(
                triggerTime = Instant.parse("2026-04-24T10:25:00Z"),
                sessionId = 42L,
                maxPerBreastMinutes = 15,
                currentSide = "RIGHT",
                maxTotalMinutes = 30
            )
        }
    }

    @Test
    fun `rearmPerBreastAfterSwitch only cancels while paused`() = runTest {
        val switched = session.copy(
            switchTime = Instant.parse("2026-04-24T10:10:00Z"),
            pausedAt = Instant.parse("2026-04-24T10:05:00Z")
        )

        coordinator.rearmPerBreastAfterSwitch(switched)

        verify { notificationScheduler.cancelPerBreastNotification() }
        verify(exactly = 0) { notificationScheduler.scheduleMaxPerBreastNotificationAt(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `rearmPerBreastAfterSwitch skips scheduling when maxPerBreastMinutes is zero`() = runTest {
        every { feedSettingsRepository.getMaxPerBreastMinutes() } returns flowOf(0)

        coordinator.rearmPerBreastAfterSwitch(session.copy(switchTime = Instant.parse("2026-04-24T10:10:00Z")))

        verify(exactly = 0) { notificationScheduler.scheduleMaxPerBreastNotificationAt(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `rescheduleAfterResume anchors per-breast alarm on switch time with new side`() = runTest {
        val pausedSession = session.copy(
            switchTime = Instant.parse("2026-04-24T10:10:00Z"),
            pausedAt = Instant.parse("2026-04-24T10:15:00Z")
        )
        val resumeInstant = Instant.parse("2026-04-24T10:16:00Z")

        coordinator.rescheduleAfterResume(pausedSession, resumeInstant)

        // 5 of the 15 per-breast minutes used on the new side -> 10 remaining from resume.
        verify {
            notificationScheduler.scheduleMaxPerBreastNotificationAt(
                triggerTime = Instant.parse("2026-04-24T10:26:00Z"),
                sessionId = 42L,
                maxPerBreastMinutes = 15,
                currentSide = "RIGHT",
                maxTotalMinutes = 30
            )
        }
        verify { notificationScheduler.scheduleMaxTotalTimeNotificationAt(any(), 42L, 30, "RIGHT", 15) }
    }

    @Test
    fun `rescheduleAfterResume skips per-breast alarm when switched session is past its limit`() = runTest {
        val pausedSession = session.copy(
            switchTime = Instant.parse("2026-04-24T10:10:00Z"),
            pausedAt = Instant.parse("2026-04-24T10:26:00Z")
        )
        val resumeInstant = Instant.parse("2026-04-24T10:27:00Z")

        coordinator.rescheduleAfterResume(pausedSession, resumeInstant)

        verify(exactly = 0) { notificationScheduler.scheduleMaxPerBreastNotificationAt(any(), any(), any(), any(), any()) }
        verify { notificationScheduler.scheduleMaxTotalTimeNotificationAt(any(), 42L, 30, "RIGHT", 15) }
    }

    @Test
    fun `rescheduleAfterResume arms full per-breast window when switch happened while paused`() = runTest {
        val pausedSession = session.copy(
            pausedAt = Instant.parse("2026-04-24T10:05:00Z"),
            switchTime = Instant.parse("2026-04-24T10:06:00Z")
        )
        val resumeInstant = Instant.parse("2026-04-24T10:16:00Z")

        coordinator.rescheduleAfterResume(pausedSession, resumeInstant)

        // New breast has zero active time -> full 15 minutes from resume.
        verify {
            notificationScheduler.scheduleMaxPerBreastNotificationAt(
                triggerTime = Instant.parse("2026-04-24T10:31:00Z"),
                sessionId = 42L,
                maxPerBreastMinutes = 15,
                currentSide = "RIGHT",
                maxTotalMinutes = 30
            )
        }
    }

    @Test
    fun `rearmAfterKeepGoing schedules total-time alarm 600 seconds out`() = runTest {
        coordinator.rearmAfterKeepGoing(session)

        verify {
            notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                triggerTime = any(),
                sessionId = 42L,
                maxTotalMinutes = 30,
                currentSide = "LEFT",
                maxPerBreastMinutes = 15
            )
        }
    }

    @Test
    fun `rearmAfterKeepGoing uses switched side for already-switched session`() = runTest {
        coordinator.rearmAfterKeepGoing(session.copy(switchTime = Instant.parse("2026-04-24T10:10:00Z")))

        verify {
            notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                triggerTime = any(),
                sessionId = 42L,
                maxTotalMinutes = 30,
                currentSide = "RIGHT",
                maxPerBreastMinutes = 15
            )
        }
    }

    @Test
    fun `rearmAfterKeepGoing skips scheduling when maxTotalFeedMinutes is zero`() = runTest {
        every { feedSettingsRepository.getMaxTotalFeedMinutes() } returns flowOf(0)

        coordinator.rearmAfterKeepGoing(session)

        verify(exactly = 0) { notificationScheduler.scheduleMaxTotalTimeNotificationAt(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `restoreActiveSession re-posts running notification and re-arms future alarms`() = runTest {
        val runningSession = session.copy(pausedDurationMs = 60_000L)
        val now = Instant.parse("2026-04-24T10:05:00Z")

        coordinator.restoreActiveSession(runningSession, now)

        verify {
            NotificationHelper.showBreastfeedingActive(
                context = context,
                sessionId = 42L,
                currentSide = "LEFT",
                sessionStartEpochMs = runningSession.startTime.toEpochMilli(),
                pausedDurationMs = 60_000L,
                richEnabled = false,
                maxTotalMinutes = 30,
                pausedAtEpochMs = null
            )
        }
        verify {
            notificationScheduler.scheduleMaxPerBreastNotificationAt(
                triggerTime = Instant.parse("2026-04-24T10:16:00Z"),
                sessionId = 42L,
                maxPerBreastMinutes = 15,
                currentSide = "LEFT",
                maxTotalMinutes = 30
            )
        }
        verify {
            notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                triggerTime = Instant.parse("2026-04-24T10:31:00Z"),
                sessionId = 42L,
                maxTotalMinutes = 30,
                currentSide = "LEFT",
                maxPerBreastMinutes = 15
            )
        }
    }

    @Test
    fun `restoreActiveSession anchors per-breast alarm on switch time without session-wide pauses`() = runTest {
        val switchedSession = session.copy(
            switchTime = Instant.parse("2026-04-24T10:10:00Z"),
            pausedDurationMs = 120_000L
        )
        val now = Instant.parse("2026-04-24T10:12:00Z")

        coordinator.restoreActiveSession(switchedSession, now)

        verify {
            notificationScheduler.scheduleMaxPerBreastNotificationAt(
                triggerTime = Instant.parse("2026-04-24T10:25:00Z"),
                sessionId = 42L,
                maxPerBreastMinutes = 15,
                currentSide = "RIGHT",
                maxTotalMinutes = 30
            )
        }
        verify {
            notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                triggerTime = Instant.parse("2026-04-24T10:32:00Z"),
                sessionId = 42L,
                maxTotalMinutes = 30,
                currentSide = "RIGHT",
                maxPerBreastMinutes = 15
            )
        }
    }

    @Test
    fun `restoreActiveSession skips alarms whose trigger has already passed`() = runTest {
        val now = Instant.parse("2026-04-24T11:00:00Z")

        coordinator.restoreActiveSession(session, now)

        verify {
            NotificationHelper.showBreastfeedingActive(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
        verify(exactly = 0) { notificationScheduler.scheduleMaxPerBreastNotificationAt(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { notificationScheduler.scheduleMaxTotalTimeNotificationAt(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `restoreActiveSession posts paused notification only for paused session`() = runTest {
        val pausedAt = Instant.parse("2026-04-24T10:07:00Z")
        val pausedSession = session.copy(pausedAt = pausedAt)
        val now = Instant.parse("2026-04-24T10:08:00Z")

        coordinator.restoreActiveSession(pausedSession, now)

        verify {
            NotificationHelper.showBreastfeedingActive(
                context = context,
                sessionId = 42L,
                currentSide = "LEFT",
                sessionStartEpochMs = pausedSession.startTime.toEpochMilli(),
                pausedDurationMs = 0L,
                richEnabled = false,
                maxTotalMinutes = 30,
                pausedAtEpochMs = pausedAt.toEpochMilli()
            )
        }
        verify(exactly = 0) { notificationScheduler.scheduleMaxPerBreastNotificationAt(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { notificationScheduler.scheduleMaxTotalTimeNotificationAt(any(), any(), any(), any(), any()) }
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

    private fun stubActiveNotificationSettingsAwaitingParallelCollection() {
        val maxTotalStarted = CompletableDeferred<Unit>()
        val richNotificationsStarted = CompletableDeferred<Unit>()

        every { feedSettingsRepository.getMaxTotalFeedMinutes() } returns flow {
            maxTotalStarted.complete(Unit)
            richNotificationsStarted.await()
            emit(30)
        }
        every { settingsRepository.getRichNotificationsEnabled() } returns flow {
            richNotificationsStarted.complete(Unit)
            maxTotalStarted.await()
            emit(false)
        }
    }

    private fun stubScheduleSettingsAwaitingParallelCollection() {
        val maxPerBreastStarted = CompletableDeferred<Unit>()
        val maxTotalStarted = CompletableDeferred<Unit>()

        every { feedSettingsRepository.getMaxPerBreastMinutes() } returns flow {
            maxPerBreastStarted.complete(Unit)
            maxTotalStarted.await()
            emit(15)
        }
        every { feedSettingsRepository.getMaxTotalFeedMinutes() } returns flow {
            maxTotalStarted.complete(Unit)
            maxPerBreastStarted.await()
            emit(30)
        }
    }
}
