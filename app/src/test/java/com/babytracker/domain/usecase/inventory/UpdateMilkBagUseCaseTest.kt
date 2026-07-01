package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.time.Instant

class UpdateMilkBagUseCaseTest {

    private lateinit var repository: InventoryRepository
    private lateinit var sync: SyncToFirestoreUseCase
    private lateinit var useCase: UpdateMilkBagUseCase

    private val fixedNow = Instant.parse("2026-05-16T10:00:00Z")
    private val originalBag = MilkBag(
        id = 5L,
        collectionDate = Instant.parse("2026-05-16T08:00:00Z"),
        volumeMl = 120,
        sourceSessionId = 9L,
        usedAt = Instant.parse("2026-05-16T09:00:00Z"),
        notes = "Old note",
        createdAt = Instant.parse("2026-05-16T07:55:00Z"),
    )

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        sync = mockk(relaxed = true)
        useCase = UpdateMilkBagUseCase(repository, SyncedWrite(sync)) { fixedNow }
    }

    @Test
    fun updatesEditableFieldsAndSyncsInventory() = runTest {
        val updatedCollectionDate = Instant.parse("2026-05-16T08:30:00Z")
        coEvery {
            repository.updateDetails(
                id = originalBag.id,
                collectionDate = updatedCollectionDate,
                volumeMl = 150,
                notes = "Top shelf",
            )
        } returns true

        useCase(
            bagId = originalBag.id,
            collectionDate = updatedCollectionDate,
            volumeMl = 150,
            notes = "Top shelf",
        )

        coVerifyOrder {
            repository.updateDetails(
                id = originalBag.id,
                collectionDate = updatedCollectionDate,
                volumeMl = 150,
                notes = "Top shelf",
            )
            sync(SyncToFirestoreUseCase.SyncType.INVENTORY)
        }
    }

    @Test
    fun throwsWhenBagIsNoLongerEditable() = runTest {
        coEvery {
            repository.updateDetails(
                id = originalBag.id,
                collectionDate = originalBag.collectionDate,
                volumeMl = originalBag.volumeMl,
                notes = null,
            )
        } returns false

        assertThrows<IllegalStateException> {
            runBlocking {
                useCase(
                    bagId = originalBag.id,
                    collectionDate = originalBag.collectionDate,
                    volumeMl = originalBag.volumeMl,
                )
            }
        }
        coVerify(exactly = 0) { sync(any()) }
    }

    @Test
    fun rejectsZeroVolume() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(
                    bagId = originalBag.id,
                    collectionDate = originalBag.collectionDate,
                    volumeMl = 0,
                )
            }
        }
    }

    @Test
    fun rejectsFutureCollectionDate() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(
                    bagId = originalBag.id,
                    collectionDate = fixedNow.plusSeconds(1),
                    volumeMl = 100,
                )
            }
        }
    }

    @Test
    fun returnsFalseAfterLocalUpdateWhenSyncFails() = runTest {
        coEvery { repository.updateDetails(any(), any(), any(), any()) } returns true
        coEvery { sync(any()) } throws IOException("offline")

        val syncSucceeded = useCase(
            bagId = originalBag.id,
            collectionDate = originalBag.collectionDate,
            volumeMl = originalBag.volumeMl,
        )

        assertEquals(false, syncSucceeded)
    }
}
