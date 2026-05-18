package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class AddMilkBagUseCaseTest {

    private lateinit var repository: InventoryRepository
    private lateinit var sync: SyncToFirestoreUseCase
    private lateinit var useCase: AddMilkBagUseCase
    private val fixedNow = Instant.parse("2026-05-16T10:00:00Z")

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        sync = mockk(relaxed = true)
        useCase = AddMilkBagUseCase(repository, sync) { fixedNow }
    }

    @Test
    fun persistsBagWithCreatedAtNowAndSyncsInventory() = runTest {
        val captured = slot<MilkBag>()
        coEvery { repository.insert(capture(captured)) } returns 9L

        val id = useCase(
            collectionDate = Instant.parse("2026-05-16T09:30:00Z"),
            volumeMl = 120,
            sourceSessionId = 4L,
        )

        assertEquals(9L, id)
        assertEquals(fixedNow, captured.captured.createdAt)
        assertNotNull(captured.captured.sourceSessionId)
        assertEquals(4L, captured.captured.sourceSessionId)
        coVerifyOrder {
            repository.insert(any())
            sync(SyncToFirestoreUseCase.SyncType.INVENTORY)
        }
    }

    @Test
    fun rejectsZeroVolume() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase(fixedNow, volumeMl = 0) }
        }
    }

    @Test
    fun rejectsNegativeVolume() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase(fixedNow, volumeMl = -10) }
        }
    }

    @Test
    fun swallowsSyncFailures() = runTest {
        coEvery { sync(any()) } throws RuntimeException("network down")
        useCase(fixedNow, volumeMl = 50)
    }

    @Test
    fun returnsRepositoryId() = runTest {
        coEvery { repository.insert(any()) } returns 42L

        val id = useCase(fixedNow, volumeMl = 100)

        assertEquals(42L, id)
    }
}
