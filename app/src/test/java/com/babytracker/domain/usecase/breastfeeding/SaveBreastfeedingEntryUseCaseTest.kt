package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SaveBreastfeedingEntryUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: SaveBreastfeedingEntryUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = SaveBreastfeedingEntryUseCase(repository)
    }

    @Test
    fun invokeBuildsCompletedSessionAndReturnsInsertedId() = runTest {
        val start = Instant.ofEpochSecond(1744538400L)
        val end = Instant.ofEpochSecond(1744540200L)
        val sessionSlot = slot<BreastfeedingSession>()
        coEvery { repository.insertSession(capture(sessionSlot)) } returns 42L

        val result = useCase(start, end, BreastSide.RIGHT)

        assertEquals(42L, result)
        val captured = sessionSlot.captured
        assertEquals(start, captured.startTime)
        assertEquals(end, captured.endTime)
        assertEquals(BreastSide.RIGHT, captured.startingSide)
        assertEquals(null, captured.switchTime)
        assertEquals(0L, captured.pausedDurationMs)
    }

    @Test
    fun invokeCallsRepositoryInsertSessionOnce() = runTest {
        coEvery { repository.insertSession(any()) } returns 1L

        useCase(Instant.ofEpochSecond(1L), Instant.ofEpochSecond(2L), BreastSide.LEFT)

        coVerify(exactly = 1) { repository.insertSession(any()) }
    }
}
