package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ResumeBreastfeedingSessionUseCaseTest {

    private val now = Instant.parse("2026-01-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: ResumeBreastfeedingSessionUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = ResumeBreastfeedingSessionUseCase(repository, clock)
    }

    @Test
    fun `invoke does nothing when session is not paused`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = now.minusSeconds(300),
            startingSide = BreastSide.LEFT
        )

        val result = useCase(session)

        assertSame(session, result)
        coVerify(exactly = 0) { repository.updateSession(any()) }
    }

    @Test
    fun `invoke clears pausedAt on resume`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = now.minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = now.minusSeconds(30)
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        assertNull(slot.captured.pausedAt)
    }

    @Test
    fun `invoke accumulates pause duration into pausedDurationMs`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = now.minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = now.minusSeconds(30),
            pausedDurationMs = 10_000L
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        val result = useCase(session)

        assertEquals(40_000L, slot.captured.pausedDurationMs)
        assertEquals(40_000L, result.pausedDurationMs)
    }

    @Test
    fun `invoke clamps negative delta when clock moved backward past pausedAt`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = now.minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = now.plusSeconds(60), // clock was adjusted backward after pausing
            pausedDurationMs = 10_000L
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        val result = useCase(session)

        assertEquals(10_000L, slot.captured.pausedDurationMs)
        assertNull(slot.captured.pausedAt)
        assertEquals(10_000L, result.pausedDurationMs)
        assertNull(result.pausedAt)
    }
}
