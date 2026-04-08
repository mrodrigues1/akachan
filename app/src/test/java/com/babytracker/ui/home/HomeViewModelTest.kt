package com.babytracker.ui.home

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var getBabyProfile: GetBabyProfileUseCase
    private lateinit var getBreastfeedingHistory: GetBreastfeedingHistoryUseCase
    private lateinit var getSleepHistory: GetSleepHistoryUseCase
    private lateinit var stopBreastfeedingSession: StopBreastfeedingSessionUseCase
    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testBaby = Baby(name = "Emma", birthDate = LocalDate.of(2026, 3, 15))
    private val inProgressSession = BreastfeedingSession(
        id = 1L,
        startTime = Instant.now().minusSeconds(300),
        endTime = null,
        startingSide = BreastSide.LEFT
    )
    private val completedSession = BreastfeedingSession(
        id = 2L,
        startTime = Instant.now().minusSeconds(7200),
        endTime = Instant.now().minusSeconds(6900),
        startingSide = BreastSide.RIGHT
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getBabyProfile = mockk()
        getBreastfeedingHistory = mockk()
        getSleepHistory = mockk()
        stopBreastfeedingSession = mockk()

        every { getBabyProfile() } returns flowOf(testBaby)
        every { getBreastfeedingHistory() } returns flowOf(emptyList())
        every { getSleepHistory() } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(
        getBabyProfile,
        getBreastfeedingHistory,
        getSleepHistory,
        stopBreastfeedingSession
    )

    @Test
    fun `activeSession_isNull_whenNoInProgressFeeding`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.activeSession)
    }

    @Test
    fun `activeSession_isSet_whenInProgressFeedingExists`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.activeSession)
        assertEquals(inProgressSession.id, viewModel.uiState.value.activeSession!!.id)
    }

    @Test
    fun `sessionsTodayCount_countsOnlyTodaySessions`() = runTest {
        val todaySession = BreastfeedingSession(
            id = 3L,
            startTime = Instant.now().minusSeconds(3600),
            endTime = Instant.now().minusSeconds(3000),
            startingSide = BreastSide.LEFT
        )
        every { getBreastfeedingHistory() } returns flowOf(listOf(todaySession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assert(viewModel.uiState.value.sessionsTodayCount >= 1)
    }

    @Test
    fun `lastFeedSide_returnsFirstSessionStartingSide`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(BreastSide.LEFT, viewModel.uiState.value.lastFeedSide)
    }

    @Test
    fun `onStopActiveSession_callsStopUseCase`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession))
        coJustRun { stopBreastfeedingSession(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopActiveSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { stopBreastfeedingSession(inProgressSession) }
    }
}
