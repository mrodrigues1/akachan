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
}
