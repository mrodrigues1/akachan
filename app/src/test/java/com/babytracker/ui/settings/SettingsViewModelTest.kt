package com.babytracker.ui.settings

import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.domain.usecase.breastfeeding.CountRecentValidIntervalsUseCase
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.sharing.domain.model.AppMode
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: SettingsViewModel
    private val richNotificationsFlow = MutableStateFlow(true)
    private val appModeFlow = MutableStateFlow(AppMode.NONE)
    private lateinit var getBabyProfile: GetBabyProfileUseCase
    private lateinit var countRecentValidIntervals: CountRecentValidIntervalsUseCase
    private lateinit var permissionChecker: NotificationPermissionChecker

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk()
        getBabyProfile = mockk()
        every { getBabyProfile() } returns flowOf(null)
        every { settingsRepository.getMaxPerBreastMinutes() } returns flowOf(0)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns flowOf(0)
        every { settingsRepository.getThemeConfig() } returns flowOf(ThemeConfig.SYSTEM)
        every { settingsRepository.getAutoUpdateEnabled() } returns flowOf(true)
        every { settingsRepository.getRichNotificationsEnabled() } returns richNotificationsFlow
        every { settingsRepository.getAppMode() } returns appModeFlow
        every { settingsRepository.getPredictiveEnabled() } returns flowOf(false)
        every { settingsRepository.getPredictiveLeadMinutes() } returns flowOf(15)
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(480)
        countRecentValidIntervals = mockk()
        every { countRecentValidIntervals() } returns flowOf(0)
        permissionChecker = mockk { every { areNotificationsEnabled() } returns true }
        every { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
        every { settingsRepository.getMeasurementSystem() } returns flowOf(MeasurementSystem.METRIC)

        viewModel = SettingsViewModel(
            getBabyProfile,
            settingsRepository,
            mockk(),
            countRecentValidIntervals,
            permissionChecker,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has richNotificationsEnabled true`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.richNotificationsEnabled)
    }

    @Test
    fun `onRichNotificationsToggled false calls repository and updates state`() = runTest {
        coEvery { settingsRepository.setRichNotificationsEnabled(false) } coAnswers {
            richNotificationsFlow.value = false
        }

        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onRichNotificationsToggled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setRichNotificationsEnabled(false) }
        assertFalse(viewModel.uiState.value.richNotificationsEnabled)
    }

    @Test
    fun `appMode is null before combine flow emits`() {
        // ViewModel is created in setup(); before advancing the dispatcher the init
        // coroutine has not yet collected, so appMode stays at its default null.
        assertNull(viewModel.uiState.value.appMode)
    }

    @Test
    fun `appMode emits NONE after combine flow fires`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppMode.NONE, viewModel.uiState.value.appMode)
    }

    @Test
    fun `disconnect calls setAppMode then clearShareCode in order and sets isDisconnected`() = runTest {
        coJustRun { settingsRepository.setAppMode(AppMode.NONE) }
        coJustRun { settingsRepository.clearShareCode() }

        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.disconnect()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerifyOrder {
            settingsRepository.setAppMode(AppMode.NONE)
            settingsRepository.clearShareCode()
        }
        assertTrue(viewModel.uiState.value.isDisconnected)
    }

    @Test
    fun `new combine emission after disconnect preserves isDisconnected flag`() = runTest {
        coJustRun { settingsRepository.setAppMode(AppMode.NONE) }
        coJustRun { settingsRepository.clearShareCode() }

        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.disconnect()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDisconnected)

        // Simulate a new DataStore emission arriving after disconnect completes;
        // without the update { current -> next.copy(isDisconnected = ...) } guard
        // in the collect block, this would reset isDisconnected to false.
        appModeFlow.value = AppMode.NONE
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDisconnected)
    }

    @Test
    fun `initial state has volumeUnit ML`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(VolumeUnit.ML, viewModel.uiState.value.volumeUnit)
    }

    @Test
    fun `onVolumeUnitChanged persists selection`() = runTest {
        coJustRun { settingsRepository.setVolumeUnit(any()) }

        viewModel.onVolumeUnitChanged(VolumeUnit.OZ)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setVolumeUnit(VolumeUnit.OZ) }
    }

    @Test
    fun `showPermissionWarning true when predictive feeding enabled and notifications denied`() = runTest {
        every { settingsRepository.getPredictiveEnabled() } returns flowOf(true)
        val deniedChecker = mockk<NotificationPermissionChecker> {
            every { areNotificationsEnabled() } returns false
        }
        val vm = SettingsViewModel(
            getBabyProfile,
            settingsRepository,
            mockk(),
            countRecentValidIntervals,
            deniedChecker,
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.showPermissionWarning)
    }
}
