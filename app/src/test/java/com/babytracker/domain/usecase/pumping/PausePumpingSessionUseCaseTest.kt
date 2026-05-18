package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class PausePumpingSessionUseCaseTest {

    private lateinit var repository: PumpingRepository
    private lateinit var useCase: PausePumpingSessionUseCase
    private val fixedNow = Instant.parse("2026-05-16T10:15:00Z")

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        useCase = PausePumpingSessionUseCase(repository) { fixedNow }
    }

    @Test
    fun setsFixedNowAsPausedAt() = runTest {
        val session = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.LEFT,
        )
        useCase(session)

        coVerify { repository.update(match { it.pausedAt == fixedNow }) }
    }

    @Test
    fun noOpWhenAlreadyPaused() = runTest {
        val session = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.LEFT,
            pausedAt = Instant.parse("2026-05-16T10:10:00Z"),
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
}
