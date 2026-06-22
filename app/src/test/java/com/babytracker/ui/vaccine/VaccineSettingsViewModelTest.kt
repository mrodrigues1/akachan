package com.babytracker.ui.vaccine

import app.cash.turbine.test
import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.manager.VaccineReminderScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VaccineSettingsViewModelTest {
    private val settings = mockk<VaccineSettingsRepository>(relaxed = true)
    private val scheduler = mockk<VaccineReminderScheduler>(relaxed = true)
    private val permissionChecker = mockk<NotificationPermissionChecker>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settings.getReminderEnabled() } returns flowOf(false)
        every { settings.getReminderLeadDays() } returns flowOf(7)
        every { settings.getToScheduleLeadDays() } returns flowOf(14)
        every { permissionChecker.areNotificationsEnabled() } returns true
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = VaccineSettingsViewModel(settings, scheduler, permissionChecker)

    @Test
    fun `toggle persists and reschedules`() = runTest {
        vm().onReminderToggle(true)
        coVerify { settings.setReminderEnabled(true) }
        coVerify { scheduler.rescheduleAll() }
    }

    @Test
    fun `lead-days change persists and reschedules`() = runTest {
        vm().onLeadDaysChange(3)
        coVerify { settings.setReminderLeadDays(3) }
        coVerify { scheduler.rescheduleAll() }
    }

    @Test
    fun `exposes the to-schedule lead from settings`() = runTest {
        every { settings.getToScheduleLeadDays() } returns flowOf(30)
        vm().uiState.test {
            var s = awaitItem()
            if (s.isLoading) s = awaitItem()
            org.junit.jupiter.api.Assertions.assertEquals(30, s.toScheduleLeadDays)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `to-schedule lead change persists and reschedules`() = runTest {
        vm().onToScheduleLeadDaysChange(7)
        coVerify { settings.setToScheduleLeadDays(7) }
        coVerify { scheduler.rescheduleAll() }
    }

    @Test
    fun `permission warning shows when reminders on but notifications blocked`() = runTest {
        every { settings.getReminderEnabled() } returns flowOf(true)
        every { permissionChecker.areNotificationsEnabled() } returns false

        vm().uiState.test {
            var s = awaitItem()
            if (s.isLoading) s = awaitItem()
            assertTrue(s.showPermissionWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no permission warning when reminders are off`() = runTest {
        every { settings.getReminderEnabled() } returns flowOf(false)
        every { permissionChecker.areNotificationsEnabled() } returns false

        vm().uiState.test {
            var s = awaitItem()
            if (s.isLoading) s = awaitItem()
            assertFalse(s.showPermissionWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
