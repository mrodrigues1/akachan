package com.babytracker.ui.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCase
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class SleepScheduleViewModelTest {

    private lateinit var generateSchedule: GenerateSleepScheduleUseCase
    private lateinit var babyRepository: BabyRepository
    private lateinit var viewModel: SleepScheduleViewModel
    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)

    @BeforeEach
    fun setUp() {
        generateSchedule = mockk()
        babyRepository = mockk()
        every { babyRepository.getBabyProfile() } returns flowOf(null)
    }

    private fun createViewModel() = SleepScheduleViewModel(generateSchedule, babyRepository)

    @Test
    fun `isRegressionExpanded defaults to true`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isRegressionExpanded)
    }

    @Test
    fun `onToggleRegression flips isRegressionExpanded from true to false`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onToggleRegression()

        assertEquals(false, viewModel.uiState.value.isRegressionExpanded)
    }

    @Test
    fun `onToggleRegression twice restores isRegressionExpanded to true`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onToggleRegression()
        viewModel.onToggleRegression()

        assertEquals(true, viewModel.uiState.value.isRegressionExpanded)
    }

    @Test
    fun `init loads the schedule when a baby profile exists`() = runTest {
        val baby = mockk<Baby>()
        val schedule = mockk<SleepSchedule>()
        every { babyRepository.getBabyProfile() } returns flowOf(baby)
        coEvery { generateSchedule(baby) } returns schedule
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(schedule, state.schedule)
        assertFalse(state.isLoading)
    }

    @Test
    fun `refreshSchedule regenerates the schedule`() = runTest {
        val baby = mockk<Baby>()
        val schedule = mockk<SleepSchedule>()
        every { babyRepository.getBabyProfile() } returns flowOf(baby)
        coEvery { generateSchedule(baby) } returns schedule
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refreshSchedule()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(schedule, viewModel.uiState.value.schedule)
        coVerify(atLeast = 2) { generateSchedule(baby) }
    }

    @Test
    fun `schedule stays null when no baby profile exists`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.schedule)
        assertFalse(state.isLoading)
    }
}
