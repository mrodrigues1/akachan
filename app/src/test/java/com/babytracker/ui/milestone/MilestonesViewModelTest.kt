package com.babytracker.ui.milestone

import app.cash.turbine.test
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.MilestoneAchievement
import com.babytracker.domain.model.MilestoneProgress
import com.babytracker.domain.usecase.milestone.DeleteMilestoneUseCase
import com.babytracker.domain.usecase.milestone.GetMilestoneProgressUseCase
import com.babytracker.domain.usecase.milestone.LogMilestoneUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MilestonesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var getMilestoneProgress: GetMilestoneProgressUseCase
    private lateinit var logMilestone: LogMilestoneUseCase
    private lateinit var deleteMilestone: DeleteMilestoneUseCase
    private lateinit var photoCleaner: MilestonePhotoCleaner
    private lateinit var syncToFirestore: SyncToFirestoreUseCase

    private val photoUri = "file:///data/milestone_photos/WALKING_ALONE_1.jpg"
    private val catalog = Milestone.entries.map { milestone ->
        if (milestone == Milestone.WALKING_ALONE) {
            MilestoneProgress(
                milestone,
                MilestoneAchievement(id = 1, milestone = milestone, achievedOn = LocalDate.of(2026, 6, 1), photoUri = photoUri),
            )
        } else {
            MilestoneProgress(milestone, achievement = null)
        }
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getMilestoneProgress = mockk()
        logMilestone = mockk(relaxed = true)
        deleteMilestone = mockk(relaxed = true)
        photoCleaner = mockk(relaxed = true)
        syncToFirestore = mockk(relaxed = true)
        every { getMilestoneProgress() } returns flowOf(catalog)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        MilestonesViewModel(getMilestoneProgress, logMilestone, deleteMilestone, photoCleaner, syncToFirestore)

    @Test
    fun `loads the milestone catalog`() = runTest {
        viewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(Milestone.entries.size, state.progress.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onLog delegates to the use case`() = runTest {
        val vm = viewModel()
        val date = LocalDate.of(2026, 6, 1)
        vm.onLog(Milestone.WALKING_ALONE, date, photoUri = "content://x", notes = "  ")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify {
            logMilestone(
                match {
                    it.milestone == Milestone.WALKING_ALONE &&
                        it.achievedOn == date &&
                        it.photoUri == "content://x" &&
                        it.notes == null
                },
            )
        }
    }

    @Test
    fun `onDelete delegates to the use case`() = runTest {
        val vm = viewModel()
        vm.onDelete(Milestone.STANDING_ALONE)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { deleteMilestone(Milestone.STANDING_ALONE) }
    }

    @Test
    fun `onDelete cleans up the achievement photo file`() = runTest {
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle() // let the eager progress flow populate
        vm.onDelete(Milestone.WALKING_ALONE)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { deleteMilestone(Milestone.WALKING_ALONE) }
        coVerify { photoCleaner.delete(photoUri) }
    }
}
