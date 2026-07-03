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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PauseBreastfeedingSessionUseCaseTest {

    private val now = Instant.parse("2026-01-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: PauseBreastfeedingSessionUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = PauseBreastfeedingSessionUseCase(repository, clock)
    }

    @Test
    fun `invoke sets pausedAt to now on a running session`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = now.minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        val result = useCase(session)

        assertEquals(now, slot.captured.pausedAt)
        assertEquals(now, result.pausedAt)
        coVerify(exactly = 1) { repository.updateSession(any()) }
    }

    @Test
    fun `invoke does nothing when session is already paused`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = now.minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = now.minusSeconds(60)
        )

        val result = useCase(session)

        assertSame(session, result)
        coVerify(exactly = 0) { repository.updateSession(any()) }
    }

    @Test
    fun `invoke preserves existing pausedDurationMs when pausing`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = now.minusSeconds(600),
            startingSide = BreastSide.LEFT,
            pausedDurationMs = 30_000L
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        assertEquals(30_000L, slot.captured.pausedDurationMs)
    }
}
