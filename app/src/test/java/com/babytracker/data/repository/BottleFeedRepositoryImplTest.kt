package com.babytracker.data.repository

import com.babytracker.data.local.dao.BottleFeedDao
import com.babytracker.data.local.entity.BottleFeedEntity
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BottleFeedRepositoryImplTest {

    private val dao = mockk<BottleFeedDao>(relaxed = true)
    private val repository = BottleFeedRepositoryImpl(dao)

    @Test
    fun `getAll maps entities to domain`() = runTest {
        coEvery { dao.getAll() } returns flowOf(
            listOf(
                BottleFeedEntity(
                    id = 1, timestamp = 1_000, volumeMl = 100,
                    type = "FORMULA", linkedMilkBagId = null, notes = null, createdAt = 2_000,
                ),
            ),
        )

        val result = repository.getAll().first()

        assertEquals(1, result.size)
        assertEquals(FeedType.FORMULA, result.first().type)
    }

    @Test
    fun `updateDetails returns true when a row is updated`() = runTest {
        coEvery { dao.updateDetails(any(), any(), any(), any(), any(), any()) } returns 1

        val updated = repository.updateDetails(
            id = 5,
            timestamp = Instant.ofEpochMilli(3_000),
            volumeMl = 150,
            type = FeedType.BREAST_MILK,
            linkedMilkBagId = 9,
            notes = "note",
        )

        assertTrue(updated)
        coVerify { dao.updateDetails(5, 3_000, 150, "BREAST_MILK", 9, "note") }
    }

    @Test
    fun `updateDetailsWithInventory delegates transactional update`() = runTest {
        coEvery { dao.updateDetailsWithInventory(any(), any(), any(), any(), any(), any(), any()) } returns true

        val updated = repository.updateDetailsWithInventory(
            id = 5,
            timestamp = Instant.ofEpochMilli(3_000),
            volumeMl = 150,
            type = FeedType.BREAST_MILK,
            linkedMilkBagId = 9,
            notes = "note",
            usedAt = Instant.ofEpochMilli(4_000),
        )

        assertTrue(updated)
        coVerify { dao.updateDetailsWithInventory(5, 3_000, 150, "BREAST_MILK", 9, "note", 4_000) }
    }

    @Test
    fun `insert delegates to dao and returns id`() = runTest {
        val feed = BottleFeed(
            timestamp = Instant.ofEpochMilli(1_000),
            volumeMl = 90,
            type = FeedType.BREAST_MILK,
            createdAt = Instant.ofEpochMilli(1_000),
        )
        coEvery { dao.insert(any()) } returns 11

        assertEquals(11, repository.insert(feed))
    }

    @Test
    fun `getById maps entity to domain`() = runTest {
        coEvery { dao.getById(7L) } returns BottleFeedEntity(
            id = 7L,
            timestamp = 1_000,
            volumeMl = 100,
            type = "BREAST_MILK",
            linkedMilkBagId = 3L,
            notes = "note",
            createdAt = 1_000,
        )

        val result = repository.getById(7L)

        assertEquals(7L, result?.id)
        assertEquals(3L, result?.linkedMilkBagId)
    }

    @Test
    fun `deleteWithInventoryRestore delegates transactional delete`() = runTest {
        val feed = BottleFeed(
            id = 7L,
            timestamp = Instant.ofEpochMilli(1_000),
            volumeMl = 90,
            type = FeedType.BREAST_MILK,
            createdAt = Instant.ofEpochMilli(1_000),
        )
        coEvery { dao.deleteWithInventoryRestore(7L) } returns true

        val deleted = repository.deleteWithInventoryRestore(feed)

        assertTrue(deleted)
        coVerify { dao.deleteWithInventoryRestore(7L) }
    }
}
