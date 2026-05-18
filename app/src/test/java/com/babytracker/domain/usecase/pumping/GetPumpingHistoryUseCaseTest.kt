package com.babytracker.domain.usecase.pumping

import app.cash.turbine.test
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
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

class GetPumpingHistoryUseCaseTest {

    private lateinit var repository: PumpingRepository
    private lateinit var useCase: GetPumpingHistoryUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = GetPumpingHistoryUseCase(repository)
    }

    @Test
    fun delegatesToRepositoryGetAllSessions() = runTest {
        every { repository.getAllSessions() } returns flowOf(emptyList())

        useCase().test { cancelAndIgnoreRemainingEvents() }

        verify(exactly = 1) { repository.getAllSessions() }
    }

    @Test
    fun emitsSessionsFromRepository() = runTest {
        val session = PumpingSession(
            id = 1L,
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.LEFT,
        )
        every { repository.getAllSessions() } returns flowOf(listOf(session))

        useCase().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(1L, items[0].id)
            awaitComplete()
        }
    }

    @Test
    fun emptyFlowEmitsEmptyList() = runTest {
        every { repository.getAllSessions() } returns flowOf(emptyList())

        useCase().test {
            assertEquals(0, awaitItem().size)
            awaitComplete()
        }
    }

    @Test
    fun multipleEmissionsAllPropagated() = runTest {
        val s1 = PumpingSession(id = 1L, startTime = Instant.parse("2026-05-16T10:00:00Z"), breast = PumpingBreast.LEFT)
        val s2 = PumpingSession(id = 2L, startTime = Instant.parse("2026-05-16T11:00:00Z"), breast = PumpingBreast.RIGHT)
        val mutableFlow = MutableStateFlow(listOf(s1))
        every { repository.getAllSessions() } returns mutableFlow

        useCase().test {
            assertEquals(1, awaitItem().size)
            mutableFlow.value = listOf(s1, s2)
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
