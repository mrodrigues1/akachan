package com.babytracker.ui.diaper

import android.content.Context
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.usecase.diaper.EditDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class DiaperViewModelTest {
    private val log = mockk<LogDiaperChangeUseCase>()
    private val edit = mockk<EditDiaperChangeUseCase>(relaxed = true)
    private val appContext = mockk<Context>(relaxed = true)
    private val fixedNow = Instant.ofEpochMilli(50_000)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

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
    fun `failure surfaces validation error and clears saving`() = runTest {
        coEvery { log(any(), any(), any()) } throws IllegalArgumentException("future")
        val vm = viewModel()
        vm.onSave()
        assertEquals("future", vm.uiState.value.validationError)
        assertTrue(!vm.uiState.value.isSaving)
    }
}
