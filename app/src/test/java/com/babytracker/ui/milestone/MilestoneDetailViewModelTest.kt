package com.babytracker.ui.milestone

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.repository.MilestoneRepository
import com.babytracker.navigation.Routes
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import org.junit.jupiter.api.extension.RegisterExtension

class MilestoneDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)
    private lateinit var milestoneRepository: MilestoneRepository
    private lateinit var photoCleaner: MilestonePhotoCleaner
    private lateinit var syncToFirestore: SyncToFirestoreUseCase

    private val photoUri = "file:///data/milestone_photos/moment_1.jpg"
    private val moment = Milestone(id = 1, title = "Smile", date = LocalDate.of(2026, 6, 1), photoUri = photoUri)

    @BeforeEach
    fun setup() {
        milestoneRepository = mockk(relaxed = true)
        photoCleaner = mockk(relaxed = true)
        syncToFirestore = mockk(relaxed = true)
        every { milestoneRepository.getMilestone(1) } returns flowOf(moment)
    }

    private fun viewModel() = MilestoneDetailViewModel(
        SavedStateHandle(mapOf(Routes.MILESTONE_DETAIL_ARG to "1")),
        milestoneRepository, photoCleaner, SyncedWrite(syncToFirestore),
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
        coVerify { milestoneRepository.deleteMilestone(1) }
        coVerify { photoCleaner.delete(photoUri) }
        assertTrue(deleted)
    }

    @Test
    fun `onDelete still syncs and signals completion when photo cleanup fails`() = runTest {
        coEvery { photoCleaner.delete(photoUri) } throws RuntimeException("disk error")
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        var deleted = false
        vm.onDelete(onDeleted = { deleted = true })
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { milestoneRepository.deleteMilestone(1) }
        coVerify { syncToFirestore() }
        assertTrue(deleted)
    }

    @Test
    fun `onSave cleans up the replaced photo`() = runTest {
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val updated = moment.copy(photoUri = "content://new")
        vm.onSave(updated)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { milestoneRepository.updateMilestone(updated) }
        coVerify { photoCleaner.delete(photoUri) }
    }
}
