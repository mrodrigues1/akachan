package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class StopPumpingSessionUseCaseTest {

    private lateinit var repository: PumpingRepository
    private lateinit var useCase: StopPumpingSessionUseCase
    private val fixedNow = Instant.parse("2026-05-16T10:30:00Z")

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        useCase = StopPumpingSessionUseCase(repository) { fixedNow }
    }

    @Test
    fun throwsWhenSessionAlreadyCompleted() = runTest {
        val completed = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            endTime = Instant.parse("2026-05-16T10:15:00Z"),
            breast = PumpingBreast.LEFT,
        )
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase(completed, volumeMl = 100) }
        }
    }

    @Test
    fun rejectsNonPositiveVolume() = runTest {
        val active = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.LEFT,
        )
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase(active, volumeMl = 0) }
        }
    }

    @Test
    fun rejectsNegativeVolume() = runTest {
        val active = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.LEFT,
        )
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase(active, volumeMl = -10) }
        }
    }

    @Test
    fun foldsWindowIntoPausedDurationMsWhenStoppingWhilePaused() = runTest {
        val start = Instant.parse("2026-05-16T10:00:00Z")
        val pausedAt = Instant.parse("2026-05-16T10:20:00Z")
        val active = PumpingSession(
            startTime = start,
            breast = PumpingBreast.LEFT,
            pausedAt = pausedAt,
            pausedDurationMs = 0,
        )
        val result = useCase(active, volumeMl = 90)

        assertEquals(fixedNow, result.endTime)
        assertEquals(90, result.volumeMl)
        assertNull(result.pausedAt)
        assertEquals(10 * 60 * 1000L, result.pausedDurationMs)
        coVerify { repository.update(result) }
    }

    @Test
    fun stopsRunningSessionWithoutPause() = runTest {
        val active = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.RIGHT,
            pausedDurationMs = 5000L,
        )
        val result = useCase(active, volumeMl = null)

        assertEquals(fixedNow, result.endTime)
        assertEquals(5000L, result.pausedDurationMs)
        assertNull(result.pausedAt)
        coVerify { repository.update(result) }
    }
}
