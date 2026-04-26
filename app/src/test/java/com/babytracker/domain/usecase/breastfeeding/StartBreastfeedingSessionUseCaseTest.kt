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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartBreastfeedingSessionUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: StartBreastfeedingSessionUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = StartBreastfeedingSessionUseCase(repository)
    }

    @Test
    fun invokeWithLeftSideCreatesSessionWithLeftSide() = runTest {
        val sessionSlot = slot<BreastfeedingSession>()
        coEvery { repository.insertSession(capture(sessionSlot)) } returns 1L

        val result = useCase(BreastSide.LEFT)

        assertEquals(1L, result)
        assertEquals(BreastSide.LEFT, sessionSlot.captured.startingSide)
        assertNull(sessionSlot.captured.endTime)
    }

    @Test
    fun invokeWithRightSideCreatesSessionWithRightSide() = runTest {
        val sessionSlot = slot<BreastfeedingSession>()
        coEvery { repository.insertSession(capture(sessionSlot)) } returns 2L

        val result = useCase(BreastSide.RIGHT)

        assertEquals(2L, result)
        assertEquals(BreastSide.RIGHT, sessionSlot.captured.startingSide)
    }

    @Test
    fun invokeCallsRepositoryInsertSession() = runTest {
        coEvery { repository.insertSession(any()) } returns 1L

        useCase(BreastSide.LEFT)

        coVerify(exactly = 1) { repository.insertSession(any()) }
    }
}
