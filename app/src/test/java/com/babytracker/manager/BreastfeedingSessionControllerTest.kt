package com.babytracker.manager

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BreastfeedingSessionControllerTest {

    private val now = Instant.parse("2026-01-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var repository: BreastfeedingRepository
    private lateinit var switchSideUseCase: SwitchBreastfeedingSideUseCase
    private lateinit var pauseSessionUseCase: PauseBreastfeedingSessionUseCase
    private lateinit var resumeSessionUseCase: ResumeBreastfeedingSessionUseCase
    private lateinit var notificationCoordinator: BreastfeedingSessionNotificationCoordinator
    private lateinit var syncedWrite: SyncedWrite
    private lateinit var controller: BreastfeedingSessionController

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        switchSideUseCase = mockk(relaxed = true)
        pauseSessionUseCase = mockk(relaxed = true)
        resumeSessionUseCase = mockk(relaxed = true)
        notificationCoordinator = mockk(relaxed = true)
        syncedWrite = mockk(relaxed = true)
        controller = BreastfeedingSessionController(
            repository = repository,
            switchSideUseCase = switchSideUseCase,
            pauseSessionUseCase = pauseSessionUseCase,
            resumeSessionUseCase = resumeSessionUseCase,
            notificationCoordinator = notificationCoordinator,
            syncedWrite = syncedWrite,
            clock = clock,
        )
    }

    private fun session(
        switchTime: Instant? = null,
        pausedAt: Instant? = null,
        pausedDurationMs: Long = 0L,
    ) = BreastfeedingSession(
        id = 5L,
        startTime = now.minusSeconds(600),
        startingSide = BreastSide.LEFT,
        switchTime = switchTime,
        pausedAt = pausedAt,
        pausedDurationMs = pausedDurationMs,
    )

    @Test
    fun `start persists via startSessionIfNone, schedules, shows, and syncs`() = runTest {
        val saved = slot<BreastfeedingSession>()
        coEvery { repository.startSessionIfNone(capture(saved)) } returns 5L

        val session = controller.start(BreastSide.LEFT)

        assertNotNull(session)
        assertEquals(5L, session?.id)
        assertEquals(BreastSide.LEFT, session?.startingSide)
        assertEquals(BreastSide.LEFT, saved.captured.startingSide)
        coVerify { notificationCoordinator.scheduleInitial(match { it.id == 5L }) }
        coVerify { notificationCoordinator.showRunning(match { it.id == 5L }) }
        coVerify { syncedWrite.sync(any()) }
    }

    @Test
    fun `start returns null and skips side effects when a session is already active`() = runTest {
        coEvery { repository.startSessionIfNone(any()) } returns null

        val session = controller.start(BreastSide.LEFT)

        assertNull(session)
        coVerify(exactly = 0) { notificationCoordinator.scheduleInitial(any()) }
        coVerify(exactly = 0) { notificationCoordinator.showRunning(any()) }
        coVerify(exactly = 0) { syncedWrite.sync(any()) }
    }

    @Test
    fun `start still returns the session and syncs when notifications throw`() = runTest {
        coEvery { repository.startSessionIfNone(any()) } returns 5L
        coEvery { notificationCoordinator.scheduleInitial(any()) } throws RuntimeException("scheduler dead")

        val session = controller.start(BreastSide.RIGHT)

        assertNotNull(session)
        assertEquals(5L, session?.id)
        coVerify { syncedWrite.sync(any()) }
    }

    // Pause-folding on stop is DAO behaviour (atomic @Transaction re-read); it is covered by the
    // instrumented BreastfeedingDaoTest. Here we only verify the delegation choreography.

    @Test
    fun `stop delegates to atomic repository stop, cancels notifications, and syncs`() = runTest {
        coEvery { repository.stopActiveSession(any()) } returns true

        val stopped = controller.stop()

        assertTrue(stopped)
        coVerify(exactly = 1) { repository.stopActiveSession(any()) }
        coVerify(exactly = 1) { notificationCoordinator.cancelAllSessionNotifications() }
        coVerify(exactly = 1) { syncedWrite.sync(any()) }
    }

    @Test
    fun `stop returns false and skips side effects when no session is active`() = runTest {
        coEvery { repository.stopActiveSession(any()) } returns false

        val stopped = controller.stop()

        assertFalse(stopped)
        coVerify(exactly = 0) { notificationCoordinator.cancelAllSessionNotifications() }
        coVerify(exactly = 0) { syncedWrite.sync(any()) }
    }

    @Test
    fun `stop returns false and skips side effects when persist fails`() = runTest {
        coEvery { repository.stopActiveSession(any()) } throws RuntimeException("db gone")

        val stopped = controller.stop()

        assertFalse(stopped)
        coVerify(exactly = 0) { notificationCoordinator.cancelAllSessionNotifications() }
        coVerify(exactly = 0) { syncedWrite.sync(any()) }
    }

    @Test
    fun `switchSide on a fresh switch re-arms the per-breast alarm, shows running, and syncs`() = runTest {
        val input = session()
        val switched = input.copy(switchTime = now)
        coEvery { switchSideUseCase(input) } returns switched

        controller.switchSide(input)

        coVerify(exactly = 1) { notificationCoordinator.rearmPerBreastAfterSwitch(switched) }
        coVerify(exactly = 1) { notificationCoordinator.showRunning(switched, any()) }
        coVerify(exactly = 0) { notificationCoordinator.showPaused(any(), any()) }
        coVerify(exactly = 1) { syncedWrite.sync(any()) }
    }

    @Test
    fun `switchSide on an already-switched session skips notifications but still syncs`() = runTest {
        val input = session(switchTime = now.minusSeconds(300))
        coEvery { switchSideUseCase(input) } returns input

        controller.switchSide(input)

        coVerify(exactly = 0) { notificationCoordinator.rearmPerBreastAfterSwitch(any()) }
        coVerify(exactly = 0) { notificationCoordinator.showRunning(any(), any()) }
        coVerify(exactly = 0) { notificationCoordinator.showPaused(any(), any()) }
        coVerify(exactly = 1) { syncedWrite.sync(any()) }
    }

    @Test
    fun `switchSide while paused re-posts the paused notification, never a running one`() = runTest {
        val pausedAt = now.minusSeconds(60)
        val input = session(pausedAt = pausedAt)
        val switched = input.copy(switchTime = now)
        coEvery { switchSideUseCase(input) } returns switched

        controller.switchSide(input)

        coVerify(exactly = 1) { notificationCoordinator.rearmPerBreastAfterSwitch(switched) }
        coVerify(exactly = 1) { notificationCoordinator.showPaused(switched, pausedAt) }
        coVerify(exactly = 0) { notificationCoordinator.showRunning(any(), any()) }
        coVerify(exactly = 1) { syncedWrite.sync(any()) }
    }

    @Test
    fun `pause cancels scheduled alarms before showing the paused notification, then syncs`() = runTest {
        val input = session()
        val paused = input.copy(pausedAt = now)
        coEvery { pauseSessionUseCase(input) } returns paused

        controller.pause(input)

        coVerifyOrder {
            notificationCoordinator.cancelScheduled()
            notificationCoordinator.showPaused(paused, now)
        }
        coVerify(exactly = 1) { syncedWrite.sync(any()) }
    }

    @Test
    fun `pause on an already-paused session is a no-op`() = runTest {
        val input = session(pausedAt = now.minusSeconds(60))

        controller.pause(input)

        coVerify(exactly = 0) { pauseSessionUseCase(any()) }
        coVerify(exactly = 0) { notificationCoordinator.cancelScheduled() }
        coVerify(exactly = 0) { notificationCoordinator.showPaused(any(), any()) }
        coVerify(exactly = 0) { syncedWrite.sync(any()) }
    }

    @Test
    fun `resume reschedules alarms before showing running with the coordinator-returned paused total`() = runTest {
        val input = session(pausedAt = now.minusSeconds(120))
        val resumed = input.copy(pausedAt = null, pausedDurationMs = 120_000L)
        coEvery { resumeSessionUseCase(input) } returns resumed
        // Distinct from resumed.pausedDurationMs to prove the controller forwards the coordinator's
        // return value instead of recomputing it.
        coEvery { notificationCoordinator.rescheduleAfterResume(input, resumed, now) } returns 300_000L

        controller.resume(input)

        coVerifyOrder {
            notificationCoordinator.rescheduleAfterResume(input, resumed, now)
            notificationCoordinator.showRunning(resumed, pausedDurationMs = 300_000L)
        }
        coVerify(exactly = 1) { syncedWrite.sync(any()) }
    }

    @Test
    fun `resume on a session that is not paused is a no-op`() = runTest {
        val input = session()

        controller.resume(input)

        coVerify(exactly = 0) { resumeSessionUseCase(any()) }
        coVerify(exactly = 0) { notificationCoordinator.rescheduleAfterResume(any(), any(), any()) }
        coVerify(exactly = 0) { notificationCoordinator.showRunning(any(), any()) }
        coVerify(exactly = 0) { syncedWrite.sync(any()) }
    }
}
