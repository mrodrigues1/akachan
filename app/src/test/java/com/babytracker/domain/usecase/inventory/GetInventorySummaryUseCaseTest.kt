package com.babytracker.domain.usecase.inventory

import app.cash.turbine.test
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.repository.InventoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GetInventorySummaryUseCaseTest {

    private lateinit var repository: InventoryRepository
    private lateinit var useCase: GetInventorySummaryUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = GetInventorySummaryUseCase(repository)
    }

    @Test
    fun delegatesToRepositoryGetSummary() = runTest {
        every { repository.getSummary() } returns flowOf(InventorySummary.Empty)

        useCase().test { cancelAndIgnoreRemainingEvents() }

        verify(exactly = 1) { repository.getSummary() }
    }

    @Test
    fun emitsSummaryFromRepository() = runTest {
        val summary = InventorySummary(
            totalMl = 480,
            bagCount = 4,
            oldestBagDate = Instant.parse("2026-05-15T10:00:00Z"),
        )
        every { repository.getSummary() } returns flowOf(summary)

        useCase().test {
            val result = awaitItem()
            assertEquals(480, result.totalMl)
            assertEquals(4, result.bagCount)
            awaitComplete()
        }
    }

    @Test
    fun emptyInventoryEmitsZeros() = runTest {
        every { repository.getSummary() } returns flowOf(InventorySummary.Empty)

        useCase().test {
            val result = awaitItem()
            assertEquals(0, result.totalMl)
            assertEquals(0, result.bagCount)
            awaitComplete()
        }
    }
}
