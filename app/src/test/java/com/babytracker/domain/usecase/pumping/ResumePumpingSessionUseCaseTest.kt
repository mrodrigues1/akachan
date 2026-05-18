package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ResumePumpingSessionUseCaseTest {

    private lateinit var repository: PumpingRepository
    private lateinit var useCase: ResumePumpingSessionUseCase
    private val fixedNow = Instant.parse("2026-05-16T10:25:00Z")

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        useCase = ResumePumpingSessionUseCase(repository) { fixedNow }
    }

    @Test
    fun clearsPausedAtAndAccumulatesMs() = runTest {
        val pausedAt = Instant.parse("2026-05-16T10:20:00Z")
        val session = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.LEFT,
            pausedAt = pausedAt,
            pausedDurationMs = 0L,
        )
        useCase(session)

        val expectedAddedMs = 5 * 60 * 1000L
        coVerify {
            repository.update(match {
                it.pausedAt == null && it.pausedDurationMs == expectedAddedMs
            })
        }
    }

    @Test
    fun accumulatesPausedMsOnTopOfExisting() = runTest {
        val pausedAt = Instant.parse("2026-05-16T10:20:00Z")
        val session = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.LEFT,
            pausedAt = pausedAt,
            pausedDurationMs = 60_000L,
        )
        useCase(session)

        val expectedTotal = 60_000L + 5 * 60 * 1000L
        coVerify { repository.update(match { it.pausedDurationMs == expectedTotal }) }
    }

    @Test
    fun noOpWhenNotPaused() = runTest {
        val session = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.LEFT,
        )
        useCase(session)

        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun throwsWhenSessionCompleted() = runTest {
        val completed = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            endTime = Instant.parse("2026-05-16T10:20:00Z"),
            breast = PumpingBreast.LEFT,
        )
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase(completed) }
        }
    }

    @Test
    fun resumeReturnsCorrectAccumulatedMs() = runTest {
        val pausedAt = Instant.parse("2026-05-16T10:20:00Z")
        val session = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.RIGHT,
            pausedAt = pausedAt,
            pausedDurationMs = 0L,
        )
        useCase(session)

        val expectedMs = fixedNow.toEpochMilli() - pausedAt.toEpochMilli()
        assertEquals(5 * 60 * 1000L, expectedMs)
    }
}
