package com.babytracker.manager

import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.InventorySettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureSuppressionCoordinatorTest {

    private val features = MutableStateFlow(AppFeature.ALL)
    private val featureRepo = mockk<FeatureToggleRepository> { every { getEnabledFeatures() } returns features }
    private val napScheduler = mockk<NapReminderScheduler>(relaxed = true)
    private val stashScheduler = mockk<StashExpirationScheduler>(relaxed = true)
    private val inventorySettings = mockk<InventorySettingsRepository>(relaxed = true)

    @Test
    fun `cancels nap and stash alarms when sleep and inventory turn off`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = FeatureSuppressionCoordinator(featureRepo, napScheduler, stashScheduler, inventorySettings, scope)

        coordinator.start()
        advanceUntilIdle()

        features.value = AppFeature.ALL - AppFeature.SLEEP - AppFeature.INVENTORY
        advanceUntilIdle()

        verify { napScheduler.cancel() }
        verify { stashScheduler.cancel() }
    }

    @Test
    fun `does not cancel on the initial emission`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = FeatureSuppressionCoordinator(featureRepo, napScheduler, stashScheduler, inventorySettings, scope)

        coordinator.start()
        advanceUntilIdle()

        verify(exactly = 0) { napScheduler.cancel() }
        verify(exactly = 0) { stashScheduler.cancel() }
    }

    @Test
    fun `reschedules stash when inventory re-enabled and its settings allow`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifEnabled() } returns flowOf(true)
        every { inventorySettings.getExpirationNotifTimeMinutes() } returns flowOf(480)
        features.value = AppFeature.ALL - AppFeature.INVENTORY
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = FeatureSuppressionCoordinator(featureRepo, napScheduler, stashScheduler, inventorySettings, scope)

        coordinator.start()
        advanceUntilIdle()

        features.value = AppFeature.ALL
        advanceUntilIdle()

        verify { stashScheduler.scheduleDaily(480) }
    }

    @Test
    fun `does not reschedule stash when inventory re-enabled but settings disabled`() = runTest {
        every { inventorySettings.getExpirationEnabled() } returns flowOf(false)
        every { inventorySettings.getExpirationNotifEnabled() } returns flowOf(false)
        features.value = AppFeature.ALL - AppFeature.INVENTORY
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = FeatureSuppressionCoordinator(featureRepo, napScheduler, stashScheduler, inventorySettings, scope)

        coordinator.start()
        advanceUntilIdle()

        features.value = AppFeature.ALL
        advanceUntilIdle()

        verify(exactly = 0) { stashScheduler.scheduleDaily(any()) }
    }
}
