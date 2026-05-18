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

class UpdatePumpingSessionUseCaseTest {

    private lateinit var repository: PumpingRepository
    private lateinit var useCase: UpdatePumpingSessionUseCase

    private val start = Instant.parse("2026-05-16T10:00:00Z")
    private val end = Instant.parse("2026-05-16T10:30:00Z")
    private val original = PumpingSession(
        id = 1L,
        startTime = start,
        endTime = end,
        breast = PumpingBreast.LEFT,
        volumeMl = 100,
    )

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        useCase = UpdatePumpingSessionUseCase(repository)
    }

    @Test
    fun happyPathUpdatesAndReturns() = runTest {
        val newEnd = end.plusSeconds(600)
        val result = useCase(
            original = original,
            startTime = start,
            endTime = newEnd,
            breast = PumpingBreast.RIGHT,
            volumeMl = 150,
            notes = "updated",
        )

        assertEquals(newEnd, result.endTime)
        assertEquals(PumpingBreast.RIGHT, result.breast)
        assertEquals(150, result.volumeMl)
        assertEquals("updated", result.notes)
        assertEquals(1L, result.id)
        coVerify { repository.update(result) }
    }

    @Test
    fun rejectsEndBeforeStart() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(
                    original = original,
                    startTime = end,
                    endTime = start,
                    breast = PumpingBreast.LEFT,
                    volumeMl = null,
                    notes = null,
                )
            }
        }
    }

    @Test
    fun rejectsZeroVolume() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(
                    original = original,
                    startTime = start,
                    endTime = end,
                    breast = PumpingBreast.LEFT,
                    volumeMl = 0,
                    notes = null,
                )
            }
        }
    }

    @Test
    fun allowsNullEndTime() = runTest {
        val result = useCase(
            original = original,
            startTime = start,
            endTime = null,
            breast = PumpingBreast.BOTH,
            volumeMl = null,
            notes = null,
        )

        assertEquals(null, result.endTime)
        coVerify { repository.update(result) }
    }

    @Test
    fun allowsNullVolume() = runTest {
        val result = useCase(
            original = original,
            startTime = start,
            endTime = end,
            breast = PumpingBreast.LEFT,
            volumeMl = null,
            notes = null,
        )

        assertEquals(null, result.volumeMl)
        coVerify { repository.update(result) }
    }
}
