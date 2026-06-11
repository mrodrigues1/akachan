package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class EditBottleFeedUseCaseTest {

    private lateinit var repository: BottleFeedRepository
    private lateinit var sync: SyncToFirestoreUseCase
    private val now = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: EditBottleFeedUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        sync = mockk(relaxed = true)
        useCase = EditBottleFeedUseCase(repository, sync) { now }
    }

    @Test
    fun `updates details through transactional inventory boundary`() = runTest {
        coEvery {
            repository.updateDetailsWithInventory(any(), any(), any(), any(), any(), any(), any())
        } returns true

        useCase(
            id = 3,
            timestamp = Instant.ofEpochMilli(9_000),
            volumeMl = 150,
            type = FeedType.FORMULA,
            linkedMilkBagId = 7L,
            notes = "edited",
        )

        coVerify {
            repository.updateDetailsWithInventory(
                id = 3,
                timestamp = Instant.ofEpochMilli(9_000),
                volumeMl = 150,
                type = FeedType.FORMULA,
                linkedMilkBagId = 7L,
                notes = "edited",
                usedAt = now,
            )
        }
        coVerify { sync(SyncToFirestoreUseCase.SyncType.INVENTORY) }
        coVerify { sync(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) }
    }

    @Test
    fun `throws when no row updated`() = runTest {
        coEvery { repository.updateDetailsWithInventory(any(), any(), any(), any(), any(), any(), any()) } returns false

        assertThrows<IllegalStateException> {
            runBlocking {
                useCase(99, Instant.ofEpochMilli(9_000), 100, FeedType.FORMULA, null, null)
            }
        }
    }

    @Test
    fun `rejects non-positive volume`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase(3, Instant.ofEpochMilli(9_000), 0, FeedType.FORMULA, null, null) }
        }
    }
}
