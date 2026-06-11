package com.babytracker.widget

import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MilkStashWidgetDataLoaderTest {

    private fun settingsRepository(unit: VolumeUnit = VolumeUnit.ML): SettingsRepository = mockk {
        every { getVolumeUnit() } returns flowOf(unit)
    }

    @Test
    fun `maps repository summary to widget data`() = runTest {
        val repository: InventoryRepository = mockk {
            coEvery { currentSummary() } returns InventorySummary(totalMl = 840, bagCount = 6, oldestBagDate = null)
        }

        val result = loadMilkStashWidgetData(repository, settingsRepository())

        assertEquals(MilkStashWidgetData(totalMl = 840, bagCount = 6), result)
    }

    @Test
    fun `applies volume unit preference to widget data`() = runTest {
        val repository: InventoryRepository = mockk {
            coEvery { currentSummary() } returns InventorySummary(totalMl = 840, bagCount = 6, oldestBagDate = null)
        }

        val result = loadMilkStashWidgetData(repository, settingsRepository(VolumeUnit.OZ))

        assertEquals(VolumeUnit.OZ, result.volumeUnit)
    }

    @Test
    fun `repository failure falls back to EMPTY`() = runTest {
        val repository: InventoryRepository = mockk {
            coEvery { currentSummary() } throws IllegalStateException("room busted")
        }

        val result = loadMilkStashWidgetData(repository, settingsRepository())

        assertEquals(MilkStashWidgetData.EMPTY, result)
    }

    @Test
    fun `cancellation propagates instead of mapping to EMPTY`() = runTest {
        val repository: InventoryRepository = mockk {
            coEvery { currentSummary() } throws CancellationException("scope gone")
        }

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { loadMilkStashWidgetData(repository, settingsRepository()) }
        }
    }
}
