package com.babytracker.domain.usecase.breastfeeding

import app.cash.turbine.test
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GetBreastfeedingHistoryUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: GetBreastfeedingHistoryUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = GetBreastfeedingHistoryUseCase(repository)
    }

    @Test
    fun invokeDelegatesToRepositoryGetAllSessions() = runTest {
        every { repository.getAllSessions() } returns flowOf(emptyList())

        useCase().test { cancelAndIgnoreRemainingEvents() }

        verify(exactly = 1) { repository.getAllSessions() }
    }

    @Test
    fun invokeEmitsSessionsFromRepository() = runTest {
        val session = BreastfeedingSession(id = 1L, startTime = Instant.now(), startingSide = BreastSide.LEFT)
        every { repository.getAllSessions() } returns flowOf(listOf(session))

        useCase().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(1L, items[0].id)
            awaitComplete()
        }
    }

    @Test
    fun invokeEmptyFlowEmitsEmpty() = runTest {
        every { repository.getAllSessions() } returns flowOf(emptyList())

        useCase().test {
            val items = awaitItem()
            assertEquals(0, items.size)
            awaitComplete()
        }
    }

    @Test
    fun invokeMultipleEmissionsAllPropagated() = runTest {
        val session1 = BreastfeedingSession(id = 1L, startTime = Instant.now(), startingSide = BreastSide.LEFT)
        val session2 = BreastfeedingSession(id = 2L, startTime = Instant.now(), startingSide = BreastSide.RIGHT)
        val mutableFlow = MutableStateFlow(listOf(session1))
        every { repository.getAllSessions() } returns mutableFlow

        useCase().test {
            assertEquals(1, awaitItem().size)
            mutableFlow.value = listOf(session1, session2)
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
