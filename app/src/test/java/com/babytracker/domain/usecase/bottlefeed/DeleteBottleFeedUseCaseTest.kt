package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class DeleteBottleFeedUseCaseTest {

    @Test
    fun `deletes through transactional inventory boundary and syncs inventory`() = runTest {
        val repository = mockk<BottleFeedRepository>(relaxed = true)
        val sync = mockk<SyncToFirestoreUseCase>(relaxed = true)
        val feed = feed(linkedMilkBagId = null)
        coEvery { repository.deleteWithInventoryRestore(feed) } returns true

        DeleteBottleFeedUseCase(repository, sync)(feed)

        coVerify { repository.deleteWithInventoryRestore(feed) }
        coVerify { sync(SyncToFirestoreUseCase.SyncType.INVENTORY) }
        coVerify { sync(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) }
    }

    @Test
    fun `syncs inventory when deleting linked feed restores bag`() = runTest {
        val repository = mockk<BottleFeedRepository>(relaxed = true)
        val sync = mockk<SyncToFirestoreUseCase>(relaxed = true)
        val feed = feed(linkedMilkBagId = 8L)
        coEvery { repository.deleteWithInventoryRestore(feed) } returns true

        DeleteBottleFeedUseCase(repository, sync)(feed)

        coVerify { sync(SyncToFirestoreUseCase.SyncType.INVENTORY) }
    }

    @Test
    fun `throws and does not sync inventory when delete did not remove a row`() = runTest {
        val repository = mockk<BottleFeedRepository>(relaxed = true)
        val sync = mockk<SyncToFirestoreUseCase>(relaxed = true)
        val feed = feed(linkedMilkBagId = 8L)
        coEvery { repository.deleteWithInventoryRestore(feed) } returns false

        assertThrows<IllegalStateException> {
            runBlocking { DeleteBottleFeedUseCase(repository, sync)(feed) }
        }

        coVerify(exactly = 0) { sync(any()) }
    }

    private fun feed(linkedMilkBagId: Long?) = BottleFeed(
        id = 4L,
        timestamp = Instant.ofEpochMilli(1_000),
        volumeMl = 90,
        type = FeedType.BREAST_MILK,
        linkedMilkBagId = linkedMilkBagId,
        createdAt = Instant.ofEpochMilli(1_000),
    )
}
