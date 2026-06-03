package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.InventorySummaryRow
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.entity.MilkBagEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class InventoryRepositoryImplTest {
    private lateinit var dao: MilkBagDao
    private lateinit var repository: InventoryRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = InventoryRepositoryImpl(dao)
    }

    @Test
    fun `getActiveBags maps rows oldest first`() = runTest {
        every { dao.getActiveBags() } returns flowOf(
            listOf(
                MilkBagEntity(id = 1, collectionDate = 100L, volumeMl = 60, createdAt = 100L),
                MilkBagEntity(id = 2, collectionDate = 200L, volumeMl = 100, createdAt = 200L),
            ),
        )
        repository.getActiveBags().test {
            val bags = awaitItem()
            assertEquals(2, bags.size)
            assertEquals(1L, bags[0].id)
            awaitComplete()
        }
    }

    @Test
    fun `getSummary converts row, mapping null oldestBagDateMs to null Instant`() = runTest {
        every { dao.getActiveSummary() } returns flowOf(
            InventorySummaryRow(totalMl = 0, bagCount = 0, oldestBagDateMs = null),
        )
        repository.getSummary().test {
            val summary = awaitItem()
            assertEquals(0, summary.totalMl)
            assertEquals(0, summary.bagCount)
            assertNull(summary.oldestBagDate)
            awaitComplete()
        }
    }

    @Test
    fun `getSummary converts non-null oldestBagDateMs to Instant`() = runTest {
        every { dao.getActiveSummary() } returns flowOf(
            InventorySummaryRow(totalMl = 240, bagCount = 3, oldestBagDateMs = 100L),
        )
        repository.getSummary().test {
            val summary = awaitItem()
            assertNotNull(summary.oldestBagDate)
            assertEquals(Instant.ofEpochMilli(100L), summary.oldestBagDate)
            awaitComplete()
        }
    }

    @Test
    fun `updateDetails forwards editable fields and returns whether row was updated`() = runTest {
        val collectionDate = Instant.ofEpochMilli(500L)
        coEvery {
            dao.updateActiveDetails(id = 7L, collectionDate = 500L, volumeMl = 120, notes = "Top shelf")
        } returns 1

        val updated = repository.updateDetails(
            id = 7L,
            collectionDate = collectionDate,
            volumeMl = 120,
            notes = "Top shelf",
        )

        assertEquals(true, updated)
        coVerify(exactly = 1) {
            dao.updateActiveDetails(id = 7L, collectionDate = 500L, volumeMl = 120, notes = "Top shelf")
        }
    }

    @Test
    fun `updateDetails returns false when dao updates no rows`() = runTest {
        coEvery { dao.updateActiveDetails(any(), any(), any(), any()) } returns 0

        val updated = repository.updateDetails(
            id = 7L,
            collectionDate = Instant.ofEpochMilli(500L),
            volumeMl = 120,
            notes = null,
        )

        assertEquals(false, updated)
    }
}
