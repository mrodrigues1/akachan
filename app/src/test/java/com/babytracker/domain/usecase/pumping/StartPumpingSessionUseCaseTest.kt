package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class StartPumpingSessionUseCaseTest {

    private lateinit var repository: PumpingRepository
    private lateinit var useCase: StartPumpingSessionUseCase
    private val fixedNow = Instant.parse("2026-05-16T10:00:00Z")

    @BeforeEach
    fun setup() {
        repository = mockk()
        coEvery { repository.getActiveSessionOnce() } returns null
        useCase = StartPumpingSessionUseCase(repository) { fixedNow }
    }

    @Test
    fun invokeInsertSessionWithFixedNow() = runTest {
        val sessionSlot = slot<PumpingSession>()
        coEvery { repository.insert(capture(sessionSlot)) } returns 1L

        val result = useCase(PumpingBreast.LEFT)

        assertEquals(1L, result)
        assertEquals(fixedNow, sessionSlot.captured.startTime)
        assertNull(sessionSlot.captured.endTime)
    }

    @Test
    fun invokePassesBreastToSession() = runTest {
        val sessionSlot = slot<PumpingSession>()
        coEvery { repository.insert(capture(sessionSlot)) } returns 2L

        useCase(PumpingBreast.RIGHT)

        assertEquals(PumpingBreast.RIGHT, sessionSlot.captured.breast)
    }

    @Test
    fun invokeCallsRepositoryInsertExactlyOnce() = runTest {
        coEvery { repository.insert(any()) } returns 1L

        useCase(PumpingBreast.BOTH)

        coVerify(exactly = 1) { repository.insert(any()) }
    }

    @Test
    fun invokeIsNoOpWhenActiveSessionExists() = runTest {
        val activeSession = PumpingSession(id = 1L, startTime = fixedNow, breast = PumpingBreast.LEFT)
        coEvery { repository.getActiveSessionOnce() } returns activeSession

        val result = useCase(PumpingBreast.RIGHT)

        assertEquals(0L, result)
        coVerify(exactly = 0) { repository.insert(any()) }
    }
}
