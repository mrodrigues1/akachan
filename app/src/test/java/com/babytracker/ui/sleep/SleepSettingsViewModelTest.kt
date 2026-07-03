package com.babytracker.ui.sleep

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class SleepSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)
    private lateinit var sleepSettingsRepository: SleepSettingsRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var napReminderScheduler: NapReminderScheduler
    private lateinit var permissionChecker: NotificationPermissionChecker

    private val napEnabledFlow = MutableStateFlow(false)
    private val predictiveSleepEnabledFlow = MutableStateFlow(false)
    private var notificationsEnabledStub = true

    @BeforeEach
    fun setup() {
        sleepSettingsRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        napReminderScheduler = mockk(relaxed = true)
        every { sleepSettingsRepository.getNapReminderEnabled() } returns napEnabledFlow
        every { sleepSettingsRepository.getNapReminderDelayMinutes() } returns flowOf(60)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns predictiveSleepEnabledFlow
        every { sleepSettingsRepository.getPredictiveSleepLeadMinutes() } returns flowOf(15)
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(480)
        permissionChecker = NotificationPermissionChecker { notificationsEnabledStub }
    }

    private fun buildViewModel() = SleepSettingsViewModel(
        sleepSettingsRepository,
        settingsRepository,
        napReminderScheduler,
        permissionChecker,
    )

    @Test
    fun `initial state mirrors repository defaults`() = runTest {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.napReminderEnabled)
        assertEquals(60, state.napReminderDelayMinutes)
        assertFalse(state.predictiveSleepEnabled)
        assertEquals(15, state.predictiveSleepLeadMinutes)
        assertEquals(0, state.quietHoursStartMinute)
        assertEquals(480, state.quietHoursEndMinute)
    }

    @Test
    fun `onNapReminderToggleChanged true persists and does not cancel scheduler`() = runTest {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNapReminderToggleChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sleepSettingsRepository.setNapReminderEnabled(true) }
        verify(exactly = 0) { napReminderScheduler.cancel() }
    }

    @Test
    fun `onNapReminderToggleChanged false cancels pending nap reminder alarm`() = runTest {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNapReminderToggleChanged(false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sleepSettingsRepository.setNapReminderEnabled(false) }
        verify { napReminderScheduler.cancel() }
    }

    @Test
    fun `onNapReminderDelayChanged persists value`() = runTest {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNapReminderDelayChanged(90)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sleepSettingsRepository.setNapReminderDelayMinutes(90) }
    }

    @Test
    fun `onPredictiveSleepToggleChanged persists value`() = runTest {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPredictiveSleepToggleChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sleepSettingsRepository.setPredictiveSleepEnabled(true) }
    }

    @Test
    fun `onSleepLeadMinutesChanged persists value`() = runTest {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSleepLeadMinutesChanged(30)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sleepSettingsRepository.setPredictiveSleepLeadMinutes(30) }
    }

    @Test
    fun `quiet hours changes route to SettingsRepository`() = runTest {
        coJustRun { settingsRepository.setQuietHoursStartMinute(any()) }
        coJustRun { settingsRepository.setQuietHoursEndMinute(any()) }
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onQuietHoursStartChanged(120)
        viewModel.onQuietHoursEndChanged(420)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setQuietHoursStartMinute(120) }
        coVerify { settingsRepository.setQuietHoursEndMinute(420) }
    }

    @Test
    fun `showPermissionWarning true when a toggle is enabled and notifications denied`() = runTest {
        notificationsEnabledStub = false
        napEnabledFlow.value = true

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.notificationsPermissionGranted)
        assertTrue(viewModel.uiState.value.showPermissionWarning)
    }

    @Test
    fun `showPermissionWarning false when toggles off even if notifications denied`() = runTest {
        notificationsEnabledStub = false

        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showPermissionWarning)
    }
}
