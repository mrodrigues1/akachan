package com.babytracker.ui.milestone

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.usecase.milestone.DeleteMilestoneUseCase
import com.babytracker.domain.usecase.milestone.GetMilestoneUseCase
import com.babytracker.domain.usecase.milestone.UpdateMilestoneUseCase
import com.babytracker.navigation.Routes
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MilestoneDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var getMilestone: GetMilestoneUseCase
    private lateinit var updateMilestone: UpdateMilestoneUseCase
    private lateinit var deleteMilestone: DeleteMilestoneUseCase
    private lateinit var photoCleaner: MilestonePhotoCleaner
    private lateinit var syncToFirestore: SyncToFirestoreUseCase

    private val photoUri = "file:///data/milestone_photos/moment_1.jpg"
    private val moment = Milestone(id = 1, title = "Smile", date = LocalDate.of(2026, 6, 1), photoUri = photoUri)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getMilestone = mockk()
        updateMilestone = mockk(relaxed = true)
        deleteMilestone = mockk(relaxed = true)
        photoCleaner = mockk(relaxed = true)
        syncToFirestore = mockk(relaxed = true)
        every { getMilestone(1) } returns flowOf(moment)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MilestoneDetailViewModel(
        SavedStateHandle(mapOf(Routes.MILESTONE_DETAIL_ARG to "1")),
        getMilestone, updateMilestone, deleteMilestone, photoCleaner, syncToFirestore,
    )

    @Test
    fun `loads the moment by id`() = runTest {
        viewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals("Smile", state.milestone?.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDelete removes the moment, its photo, and signals completion`() = runTest {
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        var deleted = false
        vm.onDelete(onDeleted = { deleted = true })
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { deleteMilestone(1) }
        coVerify { photoCleaner.delete(photoUri) }
        assertTrue(deleted)
    }

    @Test
    fun `onSave cleans up the replaced photo`() = runTest {
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val updated = moment.copy(photoUri = "content://new")
        vm.onSave(updated)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { updateMilestone(updated) }
        coVerify { photoCleaner.delete(photoUri) }
    }
}
