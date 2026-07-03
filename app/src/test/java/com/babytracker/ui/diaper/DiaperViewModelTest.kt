package com.babytracker.ui.diaper

import android.content.Context
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.usecase.diaper.EditDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCase
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import org.junit.jupiter.api.extension.RegisterExtension

class DiaperViewModelTest {
    private val log = mockk<LogDiaperChangeUseCase>()
    private val edit = mockk<EditDiaperChangeUseCase>(relaxed = true)
    private val appContext = mockk<Context>(relaxed = true)
    private val fixedNow = Instant.ofEpochMilli(50_000)

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

    private fun viewModel() = DiaperViewModel(log, edit, appContext) { fixedNow }

    @Test
    fun `save logs a new change and flags saved`() = runTest {
        coEvery { log(any(), any(), any()) } returns 1
        val vm = viewModel()
        vm.onTypeChange(DiaperType.DIRTY)
        vm.onSave()
        coVerify { log(DiaperType.DIRTY, any(), any()) }
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `save edits when editingId is set, preserving createdAt`() = runTest {
        val created = Instant.ofEpochMilli(10_000)
        val captured = slot<DiaperChange>()
        coEvery { edit(capture(captured)) } returns Unit
        val vm = viewModel()
        vm.loadForEdit(
            id = 9,
            timestamp = Instant.ofEpochMilli(20_000),
            type = DiaperType.BOTH,
            notes = "x",
            createdAt = created,
        )
        vm.onSave()
        assertEquals(9, captured.captured.id)
        assertEquals(created, captured.captured.createdAt)
    }

    @Test
    fun `failure surfaces a localized save error and never leaks the exception message`() = runTest {
        every { appContext.getString(any()) } returns "save_failed"
        coEvery { log(any(), any(), any()) } throws IllegalStateException("raw db detail")
        val vm = viewModel()
        vm.onSave()
        assertEquals("save_failed", vm.uiState.value.saveError)
        assertTrue(!vm.uiState.value.isSaving)
    }

    @Test
    fun `future timestamp is rejected on the time field without calling the use case`() = runTest {
        every { appContext.getString(any()) } returns "future_error"
        val vm = viewModel()
        vm.onTimeChange(fixedNow.plusSeconds(60))
        vm.onSave()
        assertEquals("future_error", vm.uiState.value.timeError)
        assertTrue(!vm.uiState.value.isSaving)
        coVerify(exactly = 0) { log(any(), any(), any()) }
    }

    @Test
    fun `notes longer than the cap are truncated`() = runTest {
        val vm = viewModel()
        vm.onNotesChange("x".repeat(DIAPER_NOTES_MAX + 50))
        assertEquals(DIAPER_NOTES_MAX, vm.uiState.value.notes.length)
    }
}
