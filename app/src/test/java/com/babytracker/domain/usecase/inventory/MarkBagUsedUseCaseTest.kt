package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class MarkBagUsedUseCaseTest {

    private lateinit var repository: InventoryRepository
    private lateinit var sync: SyncToFirestoreUseCase
    private lateinit var useCase: MarkBagUsedUseCase
    private val fixedNow = Instant.parse("2026-05-16T10:00:00Z")

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        sync = mockk(relaxed = true)
        useCase = MarkBagUsedUseCase(repository, SyncedWrite(sync)) { fixedNow }
    }

    @Test
    fun updatesUsedAtAndSyncs() = runTest {
        val bag = MilkBag(
            id = 1L,
            collectionDate = Instant.parse("2026-05-16T08:00:00Z"),
            volumeMl = 100,
            createdAt = Instant.parse("2026-05-16T08:00:00Z"),
        )
        useCase(bag)

        coVerifyOrder {
            repository.update(match { it.usedAt == fixedNow })
            sync(SyncToFirestoreUseCase.SyncType.INVENTORY)
        }
    }

    @Test
    fun noOpWhenBagAlreadyUsed() = runTest {
        val bag = MilkBag(
            id = 1L,
            collectionDate = Instant.parse("2026-05-16T08:00:00Z"),
            volumeMl = 100,
            usedAt = Instant.parse("2026-05-16T09:00:00Z"),
            createdAt = Instant.parse("2026-05-16T08:00:00Z"),
        )
        useCase(bag)

        coVerify(exactly = 0) { repository.update(any()) }
        coVerify(exactly = 0) { sync(any()) }
    }

    @Test
    fun swallowsSyncFailures() = runTest {
        val bag = MilkBag(
            id = 2L,
            collectionDate = Instant.parse("2026-05-16T08:00:00Z"),
            volumeMl = 80,
            createdAt = Instant.parse("2026-05-16T08:00:00Z"),
        )
        coEvery { sync(any()) } throws RuntimeException("offline")

        useCase(bag)
    }
}
