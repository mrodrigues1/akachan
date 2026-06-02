package com.babytracker.ui.inventory

import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventorySettingsViewModelTest {

    private lateinit var settings: InventorySettingsRepository
    private lateinit var scheduler: StashExpirationScheduler

    private val testDispatcher = StandardTestDispatcher()
    private val enabled = MutableStateFlow(false)
    private val days = MutableStateFlow(4)
    private val notifEnabled = MutableStateFlow(false)
    private val notifTime = MutableStateFlow(480)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settings = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        every { settings.getExpirationEnabled() } returns enabled
        every { settings.getExpirationDays() } returns days
        every { settings.getExpirationNotifEnabled() } returns notifEnabled
        every { settings.getExpirationNotifTimeMinutes() } returns notifTime
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun viewModel(): InventorySettingsViewModel = InventorySettingsViewModel(settings, scheduler)

    @Test
    fun `master toggle off cancels scheduler`() = runTest {
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onExpirationEnabledChanged(false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { settings.setExpirationEnabled(false) }
        verify(exactly = 1) { scheduler.cancel() }
    }

    @Test
    fun `master off then on restores alarm when notif was enabled`() = runTest {
        notifEnabled.value = true
        notifTime.value = 510
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onExpirationEnabledChanged(false)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onExpirationEnabledChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { scheduler.cancel() }
        verify(exactly = 1) { scheduler.scheduleDaily(510) }
    }

    @Test
    fun `master on does not schedule when notif disabled`() = runTest {
        notifEnabled.value = false
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onExpirationEnabledChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { settings.setExpirationEnabled(true) }
        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `notif toggle on schedules at current time`() = runTest {
        notifTime.value = 540
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNotifEnabledChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { settings.setExpirationNotifEnabled(true) }
        verify(exactly = 1) { scheduler.scheduleDaily(540) }
    }

    @Test
    fun `notif toggle off cancels scheduler`() = runTest {
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNotifEnabledChanged(false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { settings.setExpirationNotifEnabled(false) }
        verify(exactly = 1) { scheduler.cancel() }
    }

    @Test
    fun `notif time change reschedules when notif enabled`() = runTest {
        notifEnabled.value = true
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNotifTimeChanged(600)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { settings.setExpirationNotifTimeMinutes(600) }
        verify(exactly = 1) { scheduler.scheduleDaily(600) }
    }

    @Test
    fun `notif time change does not schedule when notif disabled`() = runTest {
        notifEnabled.value = false
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNotifTimeChanged(600)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { settings.setExpirationNotifTimeMinutes(600) }
        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `valid days input persists`() = runTest {
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDaysChanged("7")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { settings.setExpirationDays(7) }
        assertEquals("7", viewModel.uiState.value.expirationDays)
        assertNull(viewModel.uiState.value.validationError)
    }

    @Test
    fun `invalid days input shows error and does not persist`() = runTest {
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDaysChanged("0")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("0", viewModel.uiState.value.expirationDays)
        assertEquals("Must be at least 1 day", viewModel.uiState.value.validationError)
        coVerify(exactly = 0) { settings.setExpirationDays(any()) }
    }

    @Test
    fun `blank days input shows error and is not overwritten by store updates`() = runTest {
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDaysChanged("")
        days.value = 9
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.expirationDays)
        assertEquals("Must be at least 1 day", viewModel.uiState.value.validationError)
        coVerify(exactly = 0) { settings.setExpirationDays(any()) }
    }
}
