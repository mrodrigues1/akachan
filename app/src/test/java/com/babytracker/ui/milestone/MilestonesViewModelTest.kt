package com.babytracker.ui.milestone

import app.cash.turbine.test
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.repository.MilestoneRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import org.junit.jupiter.api.extension.RegisterExtension

class MilestonesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)
    private lateinit var milestoneRepository: MilestoneRepository
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
        milestoneRepository = mockk(relaxed = true)
        photoCleaner = mockk(relaxed = true)
        syncToFirestore = mockk(relaxed = true)
        every { milestoneRepository.getMilestones() } returns flowOf(listOf(existing))
    }

    private fun viewModel() = MilestonesViewModel(
        milestoneRepository, photoCleaner, SyncedWrite(syncToFirestore),
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
        coVerify { milestoneRepository.addMilestone(moment) }
        coVerify(exactly = 0) { milestoneRepository.updateMilestone(any()) }
    }

    @Test
    fun `onSave with existing id updates`() = runTest {
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle() // let the eager moments flow populate
        val updated = existing.copy(title = "Renamed", photoUri = photoUri)
        vm.onSave(updated)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { milestoneRepository.updateMilestone(updated) }
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
        coVerify { milestoneRepository.deleteMilestone(1) }
        coVerify { photoCleaner.delete(photoUri) }
    }
}
