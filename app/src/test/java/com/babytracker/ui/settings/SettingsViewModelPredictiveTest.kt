package com.babytracker.ui.settings

import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.breastfeeding.CountRecentValidIntervalsUseCase
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.sharing.domain.model.AppMode
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelPredictiveTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var getBabyProfile: GetBabyProfileUseCase
    private lateinit var countRecentValidIntervals: CountRecentValidIntervalsUseCase

    private val predictiveEnabledFlow = MutableStateFlow(false)
    private val predictiveLeadMinutesFlow = MutableStateFlow(15)
    private val quietHoursStartFlow = MutableStateFlow(0)
    private val quietHoursEndFlow = MutableStateFlow(480)
    private val validIntervalCountFlow = MutableStateFlow(0)
    private var notificationsEnabledStub: Boolean = true

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        getBabyProfile = mockk()
        countRecentValidIntervals = mockk()

        every { getBabyProfile() } returns flowOf(null)
        every { settingsRepository.getMaxPerBreastMinutes() } returns flowOf(0)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns flowOf(0)
        every { settingsRepository.getThemeConfig() } returns flowOf(ThemeConfig.SYSTEM)
        every { settingsRepository.getAutoUpdateEnabled() } returns flowOf(true)
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(true)
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.NONE)
        every { settingsRepository.getPredictiveEnabled() } returns predictiveEnabledFlow
        every { settingsRepository.getPredictiveLeadMinutes() } returns predictiveLeadMinutesFlow
        every { settingsRepository.getQuietHoursStartMinute() } returns quietHoursStartFlow
        every { settingsRepository.getQuietHoursEndMinute() } returns quietHoursEndFlow
        every { countRecentValidIntervals() } returns validIntervalCountFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): SettingsViewModel {
        return SettingsViewModel(
            getBabyProfile = getBabyProfile,
            settingsRepository = settingsRepository,
            saveBabyProfile = mockk(),
            countRecentValidIntervals = countRecentValidIntervals,
            notificationPermissionChecker = NotificationPermissionChecker { notificationsEnabledStub },
        )
    }

    @Test
    fun `warning row hidden when toggle off`() = runTest {
        notificationsEnabledStub = false
        predictiveEnabledFlow.value = false

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.predictiveEnabled)
        assertFalse(viewModel.uiState.value.notificationsPermissionGranted)
        assertFalse(viewModel.uiState.value.showPermissionWarning)
    }

    @Test
    fun `warning row visible when enabled and permission denied`() = runTest {
        notificationsEnabledStub = false
        predictiveEnabledFlow.value = true

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.predictiveEnabled)
        assertFalse(viewModel.uiState.value.notificationsPermissionGranted)
        assertTrue(viewModel.uiState.value.showPermissionWarning)
    }

    @Test
    fun `warning row hidden when enabled and permission granted`() = runTest {
        notificationsEnabledStub = true
        predictiveEnabledFlow.value = true

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.predictiveEnabled)
        assertTrue(viewModel.uiState.value.notificationsPermissionGranted)
        assertFalse(viewModel.uiState.value.showPermissionWarning)
    }

    @Test
    fun `toggle on persists predictiveEnabled true even when permission denied`() = runTest {
        notificationsEnabledStub = false
        predictiveEnabledFlow.value = false
        coEvery { settingsRepository.setPredictiveEnabled(true) } coAnswers {
            predictiveEnabledFlow.value = true
        }

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPredictiveToggleChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setPredictiveEnabled(true) }
        // Permission denial does NOT auto-flip enabled off
        assertTrue(viewModel.uiState.value.predictiveEnabled)
        assertFalse(viewModel.uiState.value.notificationsPermissionGranted)
        assertTrue(viewModel.uiState.value.showPermissionWarning)
    }

    @Test
    fun `setting lead minutes persists`() = runTest {
        coEvery { settingsRepository.setPredictiveLeadMinutes(30) } coAnswers {
            predictiveLeadMinutesFlow.value = 30
        }

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onLeadMinutesChanged(30)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setPredictiveLeadMinutes(30) }
        assertEquals(30, viewModel.uiState.value.predictiveLeadMinutes)
    }

    @Test
    fun `setting quiet hours start equal to end is reflected in uiState`() = runTest {
        coEvery { settingsRepository.setQuietHoursStartMinute(120) } coAnswers {
            quietHoursStartFlow.value = 120
        }
        coEvery { settingsRepository.setQuietHoursEndMinute(120) } coAnswers {
            quietHoursEndFlow.value = 120
        }

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onQuietHoursStartChanged(120)
        viewModel.onQuietHoursEndChanged(120)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(120, state.quietHoursStartMinute)
        assertEquals(120, state.quietHoursEndMinute)
        assertEquals(state.quietHoursStartMinute, state.quietHoursEndMinute)
    }

    @Test
    fun `validIntervalCount mirrors CountRecentValidIntervalsUseCase output`() = runTest {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.validIntervalCount)

        validIntervalCountFlow.value = 2
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.validIntervalCount)

        validIntervalCountFlow.value = 5
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.validIntervalCount)
    }

    @Test
    fun `hint is gated only on validIntervalCount not on prediction null`() = runTest {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        validIntervalCountFlow.value = 5
        testDispatcher.scheduler.advanceUntilIdle()

        // When CountRecentValidIntervalsUseCase emits 5, uiState reflects that count;
        // gating any "need more feeds" hint solely on this value means no hint at 5.
        assertEquals(5, viewModel.uiState.value.validIntervalCount)
    }
}
