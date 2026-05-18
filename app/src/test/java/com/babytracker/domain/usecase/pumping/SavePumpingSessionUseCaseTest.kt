package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.repository.PumpingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class SavePumpingSessionUseCaseTest {

    private lateinit var repository: PumpingRepository
    private lateinit var useCase: SavePumpingSessionUseCase

    private val start = Instant.parse("2026-05-16T10:00:00Z")
    private val end = Instant.parse("2026-05-16T10:30:00Z")

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = SavePumpingSessionUseCase(repository)
    }

    @Test
    fun persistsManualEntryAndReturnsWithId() = runTest {
        coEvery { repository.insert(any()) } returns 42L

        val result = useCase(
            startTime = start,
            endTime = end,
            breast = PumpingBreast.LEFT,
            volumeMl = 120,
            notes = null,
        )

        assertEquals(42L, result.id)
        assertEquals(start, result.startTime)
        assertEquals(end, result.endTime)
        assertNotNull(result.endTime)
        coVerify(exactly = 1) { repository.insert(any()) }
    }

    @Test
    fun rejectsEndTimeBeforeStart() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(
                    startTime = end,
                    endTime = start,
                    breast = PumpingBreast.RIGHT,
                    volumeMl = 50,
                    notes = null,
                )
            }
        }
    }

    @Test
    fun rejectsEndTimeEqualToStart() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(
                    startTime = start,
                    endTime = start,
                    breast = PumpingBreast.BOTH,
                    volumeMl = 50,
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
    fun rejectsNegativeVolume() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(
                    startTime = start,
                    endTime = end,
                    breast = PumpingBreast.LEFT,
                    volumeMl = -5,
                    notes = null,
                )
            }
        }
    }

    @Test
    fun preservesNotes() = runTest {
        coEvery { repository.insert(any()) } returns 1L

        val result = useCase(
            startTime = start,
            endTime = end,
            breast = PumpingBreast.BOTH,
            volumeMl = 80,
            notes = "morning session",
        )

        assertEquals("morning session", result.notes)
    }
}
