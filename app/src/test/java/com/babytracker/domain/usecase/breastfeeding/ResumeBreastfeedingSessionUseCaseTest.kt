package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ResumeBreastfeedingSessionUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: ResumeBreastfeedingSessionUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = ResumeBreastfeedingSessionUseCase(repository)
    }

    @Test
    fun `invoke does nothing when session is not paused`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )

        useCase(session)

        coVerify(exactly = 0) { repository.updateSession(any()) }
    }

    @Test
    fun `invoke clears pausedAt on resume`() = runTest {
        val pausedAt = Instant.now().minusSeconds(30)
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = pausedAt
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        assertNull(slot.captured.pausedAt)
    }

    @Test
    fun `invoke accumulates pause duration into pausedDurationMs`() = runTest {
        val pausedAt = Instant.now().minusSeconds(30)
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = pausedAt,
            pausedDurationMs = 10_000L
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        // new pausedDurationMs must be >= 10_000 + ~30_000 (30 seconds paused)
        assertTrue(slot.captured.pausedDurationMs >= 40_000L)
    }
}
