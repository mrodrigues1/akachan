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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class DeleteBreastfeedingSessionUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: DeleteBreastfeedingSessionUseCase

    private val session = BreastfeedingSession(
        id = 13L,
        startTime = Instant.parse("2026-05-15T09:00:00Z"),
        endTime = Instant.parse("2026-05-15T09:30:00Z"),
        startingSide = BreastSide.RIGHT,
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = DeleteBreastfeedingSessionUseCase(repository)
    }

    @Test
    fun invokeCallsDeleteSessionOnce() = runTest {
        coJustRun { repository.deleteSession(any()) }

        useCase(session)

        coVerify(exactly = 1) { repository.deleteSession(any()) }
    }

    @Test
    fun invokePassesSameSessionToRepository() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.deleteSession(capture(slot)) }

        useCase(session)

        assertEquals(13L, slot.captured.id)
    }
}
