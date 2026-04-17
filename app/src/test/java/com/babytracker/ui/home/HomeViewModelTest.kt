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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneId
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
        val zone = ZoneId.systemDefault()
        val todayAtNoon = LocalDate.now().atTime(12, 0).atZone(zone).toInstant()
        val yesterdayAtNoon = LocalDate.now().minusDays(1).atTime(12, 0).atZone(zone).toInstant()

        val todaySession = BreastfeedingSession(
            id = 3L,
            startTime = todayAtNoon,
            endTime = todayAtNoon.plusSeconds(600),
            startingSide = BreastSide.LEFT
        )
        val yesterdaySession = BreastfeedingSession(
            id = 4L,
            startTime = yesterdayAtNoon,
            endTime = yesterdayAtNoon.plusSeconds(600),
            startingSide = BreastSide.RIGHT
        )

        every { getBreastfeedingHistory() } returns flowOf(listOf(todaySession, yesterdaySession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.sessionsTodayCount)
        assertTrue(viewModel.uiState.value.recentFeedings.any { it.id == todaySession.id })
    }

    @Test
    fun `nextRecommendedSide_returnsOppositeOfLastCompletedSessionStartingSide_whenNoSwitch`() = runTest {
        // completedSession started on RIGHT with no switch → recommend LEFT (the less-used side)
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(BreastSide.LEFT, viewModel.uiState.value.nextRecommendedSide)
    }

    @Test
    fun `nextRecommendedSide_isNull_whenNoCompletedSession`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.nextRecommendedSide)
    }

    @Test
    fun `nextRecommendedSide_recommendsLessUsedSide_whenSessionHadSwitch`() = runTest {
        val now = Instant.now()
        // RIGHT 2m10s (130s), LEFT 19s — LEFT was used less → recommend LEFT (opposite of starting)
        val sessionWithSwitch = BreastfeedingSession(
            id = 3L,
            startTime = now.minusSeconds(300),
            endTime = now.minusSeconds(151),  // session ended
            startingSide = BreastSide.RIGHT,
            switchTime = now.minusSeconds(170) // 19s on RIGHT before switch, then 19s on LEFT
        )
        every { getBreastfeedingHistory() } returns flowOf(listOf(sessionWithSwitch))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        // RIGHT used 130s, LEFT used 19s → LEFT was used less → recommend LEFT
        assertEquals(BreastSide.LEFT, viewModel.uiState.value.nextRecommendedSide)
    }

    @Test
    fun `lastSessionStartTime_isNull_whenHistoryIsEmpty`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(emptyList())
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.lastSessionStartTime)
    }

    @Test
    fun `lastSessionStartTime_equalsActiveSessionStart_whenActiveSessionExists`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(inProgressSession.startTime, viewModel.uiState.value.lastSessionStartTime)
    }

    @Test
    fun `lastSessionStartTime_prefersActiveSession_evenWhenNotFirstInList`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(completedSession, inProgressSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(inProgressSession.startTime, viewModel.uiState.value.lastSessionStartTime)
    }

    @Test
    fun `lastSessionStartTime_equalsMostRecentCompletedStart_whenNoActiveSession`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(completedSession.startTime, viewModel.uiState.value.lastSessionStartTime)
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
