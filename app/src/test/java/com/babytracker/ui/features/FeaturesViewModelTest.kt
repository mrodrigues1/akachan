package com.babytracker.ui.features

import app.cash.turbine.test
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.FeatureDomain
import com.babytracker.domain.usecase.features.GetEnabledFeaturesUseCase
import com.babytracker.domain.usecase.features.SetDomainEnabledUseCase
import com.babytracker.domain.usecase.features.SetFeatureEnabledUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeaturesViewModelTest {

    private val featuresFlow = MutableStateFlow(AppFeature.ALL)
    private val getEnabled = mockk<GetEnabledFeaturesUseCase>()
    private val setFeature = mockk<SetFeatureEnabledUseCase>()
    private val setDomain = mockk<SetDomainEnabledUseCase>(relaxed = true)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { getEnabled() } returns featuresFlow
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `uiState reflects the enabled features flow`() = runTest {
        val vm = FeaturesViewModel(getEnabled, setFeature, setDomain)
        vm.uiState.test {
            assertEquals(AppFeature.ALL, expectMostRecentItem().enabledFeatures)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `applied toggle does not raise the hint`() = runTest {
        coEvery { setFeature(AppFeature.SLEEP, false) } returns true
        val vm = FeaturesViewModel(getEnabled, setFeature, setDomain)

        vm.onFeatureToggled(AppFeature.SLEEP, enabled = false)

        assertFalse(vm.uiState.value.showLastFeatureHint)
        coVerify { setFeature(AppFeature.SLEEP, false) }
    }

    @Test
    fun `blocked toggle raises the last-feature hint`() = runTest {
        coEvery { setFeature(AppFeature.SLEEP, false) } returns false
        val vm = FeaturesViewModel(getEnabled, setFeature, setDomain)

        vm.onFeatureToggled(AppFeature.SLEEP, enabled = false)

        assertTrue(vm.uiState.value.showLastFeatureHint)
        coVerify { setFeature(AppFeature.SLEEP, false) }
    }

    @Test
    fun `blocked domain toggle raises the hint`() = runTest {
        coEvery { setDomain(FeatureDomain.FEEDING, false) } returns false
        val vm = FeaturesViewModel(getEnabled, setFeature, setDomain)

        vm.onDomainToggled(FeatureDomain.FEEDING, enabled = false)

        assertTrue(vm.uiState.value.showLastFeatureHint)
    }

    @Test
    fun `onHintShown clears the hint`() = runTest {
        coEvery { setFeature(AppFeature.SLEEP, false) } returns false
        val vm = FeaturesViewModel(getEnabled, setFeature, setDomain)
        vm.onFeatureToggled(AppFeature.SLEEP, enabled = false)
        assertTrue(vm.uiState.value.showLastFeatureHint)

        vm.onHintShown()

        assertFalse(vm.uiState.value.showLastFeatureHint)
    }
}
