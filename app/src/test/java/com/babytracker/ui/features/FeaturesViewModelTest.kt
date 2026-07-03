package com.babytracker.ui.features

import app.cash.turbine.test
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.FeatureDomain
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.usecase.features.SetDomainEnabledUseCase
import com.babytracker.domain.usecase.features.SetFeatureEnabledUseCase
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class FeaturesViewModelTest {

    private val featuresFlow = MutableStateFlow(AppFeature.ALL)
    private val featureToggleRepository = mockk<FeatureToggleRepository>()
    private val setFeature = mockk<SetFeatureEnabledUseCase>()
    private val setDomain = mockk<SetDomainEnabledUseCase>(relaxed = true)

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

    @BeforeEach
    fun setup() {
        every { featureToggleRepository.getEnabledFeatures() } returns featuresFlow
    }

    // uiState is now WhileSubscribed, so it only collects its upstream while it has a subscriber.
    // The real screen always subscribes while shown; mirror that with a background collector so the
    // tests that read uiState.value (rather than collecting via Turbine) observe live state.
    private fun TestScope.activeViewModel(): FeaturesViewModel {
        val vm = FeaturesViewModel(featureToggleRepository, setFeature, setDomain)
        // Collect on an unconfined dispatcher so the subscription is live immediately (backgroundScope's
        // default StandardTestDispatcher would not start collecting until the scheduler advances, and
        // these tests assert uiState.value synchronously after a toggle).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `uiState reflects the enabled features flow`() = runTest {
        val vm = activeViewModel()
        vm.uiState.test {
            assertEquals(AppFeature.ALL, expectMostRecentItem().enabledFeatures)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `applied toggle does not raise the hint`() = runTest {
        coEvery { setFeature(AppFeature.SLEEP, false) } returns true
        val vm = activeViewModel()

        vm.onFeatureToggled(AppFeature.SLEEP, enabled = false)

        assertFalse(vm.uiState.value.showLastFeatureHint)
        coVerify { setFeature(AppFeature.SLEEP, false) }
    }

    @Test
    fun `blocked toggle raises the last-feature hint`() = runTest {
        coEvery { setFeature(AppFeature.SLEEP, false) } returns false
        val vm = activeViewModel()

        vm.onFeatureToggled(AppFeature.SLEEP, enabled = false)

        assertTrue(vm.uiState.value.showLastFeatureHint)
        coVerify { setFeature(AppFeature.SLEEP, false) }
    }

    @Test
    fun `blocked domain toggle raises the hint`() = runTest {
        coEvery { setDomain(FeatureDomain.FEEDING, false) } returns false
        val vm = activeViewModel()

        vm.onDomainToggled(FeatureDomain.FEEDING, enabled = false)

        assertTrue(vm.uiState.value.showLastFeatureHint)
    }

    @Test
    fun `onHintShown clears the hint`() = runTest {
        coEvery { setFeature(AppFeature.SLEEP, false) } returns false
        val vm = activeViewModel()
        vm.onFeatureToggled(AppFeature.SLEEP, enabled = false)
        assertTrue(vm.uiState.value.showLastFeatureHint)

        vm.onHintShown()

        assertFalse(vm.uiState.value.showLastFeatureHint)
    }
}
