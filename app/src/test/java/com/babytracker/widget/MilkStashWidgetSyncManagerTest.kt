package com.babytracker.widget

import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.repository.InventoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MilkStashWidgetSyncManagerTest {

    private fun summary(totalMl: Int, bagCount: Int) =
        InventorySummary(totalMl = totalMl, bagCount = bagCount, oldestBagDate = null)

    private fun buildManager(
        summaryFlow: MutableSharedFlow<InventorySummary>,
        updater: MilkStashWidgetUpdater,
        scope: CoroutineScope,
    ): MilkStashWidgetSyncManager {
        val repo: InventoryRepository = mockk { every { getSummary() } returns summaryFlow }
        return MilkStashWidgetSyncManager(repo, updater, scope)
    }

    @Test
    fun `emits updateAll after debounce on a single summary change`() = runTest {
        val summaryFlow = MutableSharedFlow<InventorySummary>(replay = 1)
        val updater: MilkStashWidgetUpdater = mockk { coEvery { updateAll() } returns Unit }

        buildManager(summaryFlow, updater, backgroundScope).start()
        summaryFlow.emit(summary(840, 6))
        advanceTimeBy(600)

        coVerify(exactly = 1) { updater.updateAll() }
    }

    @Test
    fun `debounce coalesces back-to-back emissions to one updateAll`() = runTest {
        val summaryFlow = MutableSharedFlow<InventorySummary>(replay = 1)
        val updater: MilkStashWidgetUpdater = mockk { coEvery { updateAll() } returns Unit }

        buildManager(summaryFlow, updater, backgroundScope).start()
        summaryFlow.emit(summary(100, 1))
        advanceTimeBy(100)
        summaryFlow.emit(summary(200, 2))
        advanceTimeBy(100)
        summaryFlow.emit(summary(300, 3))
        advanceTimeBy(600)

        coVerify(exactly = 1) { updater.updateAll() }
    }

    @Test
    fun `first updateAll exception does not stop later emissions from refreshing`() = runTest {
        val summaryFlow = MutableSharedFlow<InventorySummary>(replay = 1)
        val updater: MilkStashWidgetUpdater = mockk {
            coEvery { updateAll() } throws IllegalStateException("glance host gone") andThen Unit
        }

        buildManager(summaryFlow, updater, backgroundScope).start()
        summaryFlow.emit(summary(840, 6))
        advanceTimeBy(600)
        summaryFlow.emit(summary(0, 0))
        advanceTimeBy(600)

        coVerify(exactly = 2) { updater.updateAll() }
    }
}
