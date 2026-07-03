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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

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
    fun `stop while paused folds trailing pause and clears pausedAt`() = runTest {
        val pausedAt = Instant.now().minusSeconds(120)
        val session = BreastfeedingSession(
            id = 1L,
            startTime = pausedAt.minusSeconds(600),
            startingSide = BreastSide.LEFT,
            pausedAt = pausedAt,
            pausedDurationMs = 30_000L,
        )
        val saved = slot<BreastfeedingSession>()
        coEvery { repository.updateSession(capture(saved)) } returns Unit

        val stopped = controller.stop(session)

        assertTrue(stopped)
        assertNull(saved.captured.pausedAt)
        val trailingMs = saved.captured.pausedDurationMs - 30_000L
        val elapsedSincePauseMs = Instant.now().toEpochMilli() - pausedAt.toEpochMilli()
        assertTrue(trailingMs in 120_000L..elapsedSincePauseMs) {
            "expected trailing pause >= 120s, was ${trailingMs}ms"
        }
        assertEquals(saved.captured.endTime!!.toEpochMilli() - pausedAt.toEpochMilli(), trailingMs)
    }

    @Test
    fun `stop while running keeps pausedDurationMs untouched`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            startingSide = BreastSide.LEFT,
            pausedDurationMs = 30_000L,
        )
        val saved = slot<BreastfeedingSession>()
        coEvery { repository.updateSession(capture(saved)) } returns Unit

        val stopped = controller.stop(session)

        assertTrue(stopped)
        assertNull(saved.captured.pausedAt)
        assertEquals(30_000L, saved.captured.pausedDurationMs)
    }

    @Test
    fun `stop returns false and skips side effects when persist fails`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            startingSide = BreastSide.LEFT,
        )
        coEvery { repository.updateSession(any()) } throws RuntimeException("db gone")

        val stopped = controller.stop(session)

        assertFalse(stopped)
        coVerify(exactly = 0) { notificationCoordinator.cancelAllSessionNotifications() }
        coVerify(exactly = 0) { syncedWrite.sync(any()) }
    }
}
