package com.babytracker.ui.breastfeeding

import com.babytracker.domain.repository.FeedSettingsRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.CountRecentValidIntervalsUseCase
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class FeedSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)
    private lateinit var feedSettingsRepository: FeedSettingsRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var countRecentValidIntervals: CountRecentValidIntervalsUseCase

    private val maxPerBreastFlow = MutableStateFlow(0)
    private val maxTotalFeedFlow = MutableStateFlow(0)
    private val predictiveEnabledFlow = MutableStateFlow(false)
    private val predictiveLeadMinutesFlow = MutableStateFlow(15)
    private val quietHoursStartFlow = MutableStateFlow(0)
    private val quietHoursEndFlow = MutableStateFlow(480)
    private val validIntervalCountFlow = MutableStateFlow(0)
    private var notificationsEnabledStub: Boolean = true

    @BeforeEach
    fun setup() {
        feedSettingsRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        countRecentValidIntervals = mockk()

        every { feedSettingsRepository.getMaxPerBreastMinutes() } returns maxPerBreastFlow
        every { feedSettingsRepository.getMaxTotalFeedMinutes() } returns maxTotalFeedFlow
        every { feedSettingsRepository.getPredictiveEnabled() } returns predictiveEnabledFlow
        every { feedSettingsRepository.getPredictiveLeadMinutes() } returns predictiveLeadMinutesFlow
        every { settingsRepository.getQuietHoursStartMinute() } returns quietHoursStartFlow
        every { settingsRepository.getQuietHoursEndMinute() } returns quietHoursEndFlow
        every { countRecentValidIntervals() } returns validIntervalCountFlow
    }

    private fun buildViewModel(): FeedSettingsViewModel =
        FeedSettingsViewModel(
            feedSettingsRepository = feedSettingsRepository,
            settingsRepository = settingsRepository,
            countRecentValidIntervals = countRecentValidIntervals,
            notificationPermissionChecker = NotificationPermissionChecker { notificationsEnabledStub },
        )

    @Test
    fun `state maps repository flows`() = runTest {
        maxPerBreastFlow.value = 12
        maxTotalFeedFlow.value = 30
        predictiveLeadMinutesFlow.value = 10

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(12, state.maxPerBreastMinutes)
        assertEquals(30, state.maxTotalFeedMinutes)
        assertEquals(10, state.predictiveLeadMinutes)
    }

    @Test
    fun `onMaxPerBreastChanged persists value`() = runTest {
        coEvery { feedSettingsRepository.setMaxPerBreastMinutes(20) } coAnswers {
            maxPerBreastFlow.value = 20
        }

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onMaxPerBreastChanged(20)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { feedSettingsRepository.setMaxPerBreastMinutes(20) }
        assertEquals(20, viewModel.uiState.value.maxPerBreastMinutes)
    }

    @Test
    fun `onMaxTotalFeedChanged persists value`() = runTest {
        coEvery { feedSettingsRepository.setMaxTotalFeedMinutes(45) } coAnswers {
            maxTotalFeedFlow.value = 45
        }

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onMaxTotalFeedChanged(45)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { feedSettingsRepository.setMaxTotalFeedMinutes(45) }
        assertEquals(45, viewModel.uiState.value.maxTotalFeedMinutes)
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
        coEvery { feedSettingsRepository.setPredictiveEnabled(true) } coAnswers {
            predictiveEnabledFlow.value = true
        }

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onPredictiveToggleChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { feedSettingsRepository.setPredictiveEnabled(true) }
        assertTrue(viewModel.uiState.value.predictiveEnabled)
        assertTrue(viewModel.uiState.value.showPermissionWarning)
    }

    @Test
    fun `setting lead minutes persists`() = runTest {
        coEvery { feedSettingsRepository.setPredictiveLeadMinutes(30) } coAnswers {
            predictiveLeadMinutesFlow.value = 30
        }

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onLeadMinutesChanged(30)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { feedSettingsRepository.setPredictiveLeadMinutes(30) }
        assertEquals(30, viewModel.uiState.value.predictiveLeadMinutes)
    }

    @Test
    fun `quiet hours read from and written to shared SettingsRepository`() = runTest {
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

        coVerify { settingsRepository.setQuietHoursStartMinute(120) }
        coVerify { settingsRepository.setQuietHoursEndMinute(120) }
        val state = viewModel.uiState.value
        assertEquals(120, state.quietHoursStartMinute)
        assertEquals(120, state.quietHoursEndMinute)
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
}
