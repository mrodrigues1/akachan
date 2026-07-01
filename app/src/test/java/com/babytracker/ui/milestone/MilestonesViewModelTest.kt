package com.babytracker.ui.milestone

import app.cash.turbine.test
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.usecase.milestone.AddMilestoneUseCase
import com.babytracker.domain.usecase.milestone.DeleteMilestoneUseCase
import com.babytracker.domain.usecase.milestone.GetMilestonesUseCase
import com.babytracker.domain.usecase.milestone.UpdateMilestoneUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
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
    private lateinit var getMilestones: GetMilestonesUseCase
    private lateinit var addMilestone: AddMilestoneUseCase
    private lateinit var updateMilestone: UpdateMilestoneUseCase
    private lateinit var deleteMilestone: DeleteMilestoneUseCase
    private lateinit var photoCleaner: MilestonePhotoCleaner
    private lateinit var syncToFirestore: SyncToFirestoreUseCase

    private val photoUri = "file:///data/milestone_photos/moment_1.jpg"
    private val existing = Milestone(
        id = 1,
        title = "First steps",
        date = LocalDate.of(2026, 6, 1),
        photoUri = photoUri,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getMilestones = mockk()
        addMilestone = mockk(relaxed = true)
        updateMilestone = mockk(relaxed = true)
        deleteMilestone = mockk(relaxed = true)
        photoCleaner = mockk(relaxed = true)
        syncToFirestore = mockk(relaxed = true)
        every { getMilestones() } returns flowOf(listOf(existing))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MilestonesViewModel(
        getMilestones, addMilestone, updateMilestone, deleteMilestone, photoCleaner, SyncedWrite(syncToFirestore),
    )

    @Test
    fun `loads the moments`() = runTest {
        viewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(1, state.moments.size)
            assertEquals("First steps", state.moments.first().title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSave with id 0 adds a new moment`() = runTest {
        val vm = viewModel()
        val moment = Milestone(id = 0, title = "New", date = LocalDate.of(2026, 6, 2))
        vm.onSave(moment)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { addMilestone(moment) }
        coVerify(exactly = 0) { updateMilestone(any()) }
    }

    @Test
    fun `onSave with existing id updates`() = runTest {
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle() // let the eager moments flow populate
        val updated = existing.copy(title = "Renamed", photoUri = photoUri)
        vm.onSave(updated)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { updateMilestone(updated) }
        // Photo unchanged, so no cleanup.
        coVerify(exactly = 0) { photoCleaner.delete(photoUri) }
    }

    @Test
    fun `onSave cleans up the replaced photo`() = runTest {
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val updated = existing.copy(photoUri = "content://new")
        vm.onSave(updated)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { photoCleaner.delete(photoUri) }
    }

    @Test
    fun `onDelete removes the moment and its photo`() = runTest {
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onDelete(1)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { deleteMilestone(1) }
        coVerify { photoCleaner.delete(photoUri) }
    }
}
