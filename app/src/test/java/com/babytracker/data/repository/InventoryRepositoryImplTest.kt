package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.InventorySummaryRow
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.entity.MilkBagEntity
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
}
