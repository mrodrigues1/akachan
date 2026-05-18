package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class DeleteMilkBagUseCaseTest {

    private lateinit var repository: InventoryRepository
    private lateinit var sync: SyncToFirestoreUseCase
    private lateinit var useCase: DeleteMilkBagUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        sync = mockk(relaxed = true)
        useCase = DeleteMilkBagUseCase(repository, sync)
    }

    @Test
    fun deletesAndSyncsInventory() = runTest {
        val bag = MilkBag(
            id = 5L,
            collectionDate = Instant.parse("2026-05-16T08:00:00Z"),
            volumeMl = 120,
            createdAt = Instant.parse("2026-05-16T08:00:00Z"),
        )
        useCase(bag)

        coVerifyOrder {
            repository.delete(bag)
            sync(SyncToFirestoreUseCase.SyncType.INVENTORY)
        }
    }

    @Test
    fun swallowsSyncFailures() = runTest {
        val bag = MilkBag(
            id = 6L,
            collectionDate = Instant.parse("2026-05-16T08:00:00Z"),
            volumeMl = 60,
            createdAt = Instant.parse("2026-05-16T08:00:00Z"),
        )
        coEvery { sync(any()) } throws RuntimeException("offline")

        useCase(bag)
    }

    @Test
    fun forwardsExactBagToRepository() = runTest {
        val bag = MilkBag(
            id = 7L,
            collectionDate = Instant.parse("2026-05-16T08:00:00Z"),
            volumeMl = 90,
            createdAt = Instant.parse("2026-05-16T08:00:00Z"),
        )
        useCase(bag)

        coVerify(exactly = 1) { repository.delete(bag) }
    }
}
