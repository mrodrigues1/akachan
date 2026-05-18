package com.babytracker.domain.usecase.inventory

import app.cash.turbine.test
import com.babytracker.domain.model.MilkBag
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

class GetInventoryUseCaseTest {

    private lateinit var repository: InventoryRepository
    private lateinit var useCase: GetInventoryUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = GetInventoryUseCase(repository)
    }

    @Test
    fun delegatesToRepositoryGetActiveBags() = runTest {
        every { repository.getActiveBags() } returns flowOf(emptyList())

        useCase().test { cancelAndIgnoreRemainingEvents() }

        verify(exactly = 1) { repository.getActiveBags() }
    }

    @Test
    fun emitsBagsFromRepository() = runTest {
        val bag = MilkBag(
            id = 1L,
            collectionDate = Instant.parse("2026-05-16T08:00:00Z"),
            volumeMl = 100,
            createdAt = Instant.parse("2026-05-16T08:00:00Z"),
        )
        every { repository.getActiveBags() } returns flowOf(listOf(bag))

        useCase().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(1L, items[0].id)
            awaitComplete()
        }
    }

    @Test
    fun emptyFlowEmitsEmptyList() = runTest {
        every { repository.getActiveBags() } returns flowOf(emptyList())

        useCase().test {
            assertEquals(0, awaitItem().size)
            awaitComplete()
        }
    }
}
