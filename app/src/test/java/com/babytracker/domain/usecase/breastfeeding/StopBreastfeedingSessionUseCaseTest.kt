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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class StopBreastfeedingSessionUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: StopBreastfeedingSessionUseCase

    private val session = BreastfeedingSession(
        id = 5L,
        startTime = Instant.now().minusSeconds(300),
        startingSide = BreastSide.LEFT,
        pausedDurationMs = 10_000L
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = StopBreastfeedingSessionUseCase(repository)
    }

    @Test
    fun invokeCallsUpdateSessionExactlyOnce() = runTest {
        coJustRun { repository.updateSession(any()) }

        useCase(session)

        coVerify(exactly = 1) { repository.updateSession(any()) }
    }

    @Test
    fun invokeSetsNonNullEndTime() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        assertNotNull(slot.captured.endTime)
    }

    @Test
    fun invokePreservesSessionId() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        assertEquals(5L, slot.captured.id)
    }

    @Test
    fun invokePreservesStartingSide() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        assertEquals(BreastSide.LEFT, slot.captured.startingSide)
    }

    @Test
    fun invokePreservesPausedDurationMs() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session)

        assertEquals(10_000L, slot.captured.pausedDurationMs)
    }

    @Test
    fun invokeAlreadyStoppedSessionStillUpdates() = runTest {
        val stopped = session.copy(endTime = Instant.now().minusSeconds(60))
        coJustRun { repository.updateSession(any()) }

        useCase(stopped)

        coVerify(exactly = 1) { repository.updateSession(any()) }
    }
}
