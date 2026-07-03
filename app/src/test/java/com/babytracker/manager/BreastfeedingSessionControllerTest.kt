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

class BreastfeedingSessionControllerTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var notificationCoordinator: BreastfeedingSessionNotificationCoordinator
    private lateinit var syncedWrite: SyncedWrite
    private lateinit var controller: BreastfeedingSessionController

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        notificationCoordinator = mockk(relaxed = true)
        syncedWrite = mockk(relaxed = true)
        controller = BreastfeedingSessionController(
            repository = repository,
            switchSideUseCase = mockk<SwitchBreastfeedingSideUseCase>(relaxed = true),
            pauseSessionUseCase = mockk<PauseBreastfeedingSessionUseCase>(relaxed = true),
            resumeSessionUseCase = mockk<ResumeBreastfeedingSessionUseCase>(relaxed = true),
            notificationCoordinator = notificationCoordinator,
            syncedWrite = syncedWrite,
        )
    }

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
}
