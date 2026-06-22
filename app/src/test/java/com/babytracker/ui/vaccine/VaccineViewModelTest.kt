package com.babytracker.ui.vaccine

import android.content.Context
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.usecase.vaccine.AddVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.EditVaccineRecordUseCase
import com.babytracker.R
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class VaccineViewModelTest {
    private val add = mockk<AddVaccineRecordUseCase>()
    private val edit = mockk<EditVaccineRecordUseCase>(relaxed = true)
    private val appContext = mockk<Context>(relaxed = true)
    private val fixedNow = Instant.ofEpochMilli(50_000)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = VaccineViewModel(add, edit, appContext) { fixedNow }

    @Test
    fun `save adds an administered vaccine and flags saved`() = runTest {
        coEvery { add(any(), any(), any(), any(), any()) } returns 1
        val vm = viewModel()
        vm.onNameChange("MMR")
        vm.onSave()
        coVerify { add("MMR", any(), VaccineStatus.ADMINISTERED, any(), any()) }
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `switching to schedule mode saves with scheduled status and future date`() = runTest {
        val statusSlot = slot<VaccineStatus>()
        val dateSlot = slot<Instant>()
        coEvery { add(any(), any(), capture(statusSlot), capture(dateSlot), any()) } returns 2
        val vm = viewModel()
        vm.onNameChange("BCG")
        vm.onModeChange(VaccineStatus.SCHEDULED)
        vm.onSave()
        assertEquals(VaccineStatus.SCHEDULED, statusSlot.captured)
        assertTrue(dateSlot.captured.isAfter(fixedNow)) // defaulted to tomorrow on mode switch
    }

    @Test
    fun `switching to to-schedule saves with to-schedule status and a future target date`() = runTest {
        val statusSlot = slot<VaccineStatus>()
        val dateSlot = slot<Instant>()
        coEvery { add(any(), any(), capture(statusSlot), capture(dateSlot), any()) } returns 3
        val vm = viewModel()
        vm.onNameChange("MMR")
        vm.onModeChange(VaccineStatus.TO_SCHEDULE)
        vm.onSave()
        assertEquals(VaccineStatus.TO_SCHEDULE, statusSlot.captured)
        assertTrue(dateSlot.captured.isAfter(fixedNow)) // defaulted to tomorrow on mode switch
    }

    @Test
    fun `to-schedule save failure maps to the future-date message on the date field`() = runTest {
        every { appContext.getString(R.string.vaccine_scheduled_future_error) } returns "needs a future date"
        coEvery {
            add(any(), any(), any(), any(), any())
        } throws IllegalArgumentException("Scheduled date must be in the future")
        val vm = viewModel()
        vm.onNameChange("MMR")
        vm.onModeChange(VaccineStatus.TO_SCHEDULE)
        vm.onSave()
        assertEquals("needs a future date", vm.uiState.value.validationError)
        assertEquals(VaccineField.DATE, vm.uiState.value.errorField)
    }

    @Test
    fun `edit reconstructs to-schedule target date into the scheduled field`() = runTest {
        val captured = slot<VaccineRecord>()
        coEvery { edit(capture(captured)) } returns Unit
        val vm = viewModel()
        vm.loadForEdit(
            VaccineRecord(
                id = 6,
                name = "MMR",
                status = VaccineStatus.TO_SCHEDULE,
                scheduledDate = Instant.ofEpochMilli(99_000),
                createdAt = Instant.ofEpochMilli(1),
            ),
        )
        vm.onSave()
        assertEquals(VaccineStatus.TO_SCHEDULE, captured.captured.status)
        assertEquals(Instant.ofEpochMilli(99_000), captured.captured.scheduledDate)
        assertNull(captured.captured.administeredDate)
    }

    @Test
    fun `edit reconstructs administered date field and preserves createdAt`() = runTest {
        val created = Instant.ofEpochMilli(10_000)
        val captured = slot<VaccineRecord>()
        coEvery { edit(capture(captured)) } returns Unit
        val vm = viewModel()
        vm.loadForEdit(
            VaccineRecord(
                id = 9,
                name = "DTaP",
                status = VaccineStatus.ADMINISTERED,
                administeredDate = Instant.ofEpochMilli(20_000),
                createdAt = created,
            ),
        )
        vm.onSave()
        assertEquals(9, captured.captured.id)
        assertEquals(Instant.ofEpochMilli(20_000), captured.captured.administeredDate)
        assertNull(captured.captured.scheduledDate)
        assertEquals(created, captured.captured.createdAt)
    }

    @Test
    fun `edit reconstructs scheduled date field`() = runTest {
        val captured = slot<VaccineRecord>()
        coEvery { edit(capture(captured)) } returns Unit
        val vm = viewModel()
        vm.loadForEdit(
            VaccineRecord(
                id = 4,
                name = "Polio",
                status = VaccineStatus.SCHEDULED,
                scheduledDate = Instant.ofEpochMilli(99_000),
                createdAt = Instant.ofEpochMilli(1),
            ),
        )
        vm.onSave()
        assertEquals(Instant.ofEpochMilli(99_000), captured.captured.scheduledDate)
        assertNull(captured.captured.administeredDate)
    }

    @Test
    fun `failure surfaces curated validation error, never the raw exception, and clears saving`() = runTest {
        // The use case throws developer-phrased require() text; the VM must map it to a curated,
        // localized resource rather than surfacing the raw message to the parent.
        every { appContext.getString(R.string.vaccine_administered_past_error) } returns "given date can't be future"
        coEvery {
            add(any(), any(), any(), any(), any())
        } throws IllegalArgumentException("Administered date cannot be in the future")
        val vm = viewModel()
        vm.onNameChange("MMR")
        vm.onSave()
        assertEquals("given date can't be future", vm.uiState.value.validationError)
        assertTrue(!vm.uiState.value.isSaving)
    }

    @Test
    fun `scheduled save failure maps to the future-date message`() = runTest {
        every { appContext.getString(R.string.vaccine_scheduled_future_error) } returns "needs a future date"
        coEvery {
            add(any(), any(), any(), any(), any())
        } throws IllegalArgumentException("Scheduled date must be in the future")
        val vm = viewModel()
        vm.onNameChange("BCG")
        vm.onModeChange(VaccineStatus.SCHEDULED)
        vm.onSave()
        assertEquals("needs a future date", vm.uiState.value.validationError)
    }

    @Test
    fun `onStartAdd clears edit handles and the saved flag for a fresh form`() = runTest {
        val vm = viewModel()
        vm.loadForEdit(
            VaccineRecord(
                id = 9,
                name = "DTaP",
                status = VaccineStatus.ADMINISTERED,
                administeredDate = Instant.ofEpochMilli(20_000),
                createdAt = Instant.ofEpochMilli(10_000),
            ),
        )

        vm.onStartAdd()

        val state = vm.uiState.value
        assertNull(state.editingId)
        assertTrue(!state.isEditing)
        assertTrue(!state.saved)
        assertEquals("", state.name)
        assertEquals(fixedNow, state.date)
    }

    @Test
    fun `future administered date raises the warning flag but still saves`() = runTest {
        coEvery { add(any(), any(), any(), any(), any()) } returns 1
        val vm = viewModel()
        vm.onNameChange("MMR")
        vm.onDateChange(fixedNow.plusSeconds(86_400)) // tomorrow, administered mode (the default)
        assertTrue(vm.uiState.value.isFutureAdministered)

        vm.onSave()
        assertTrue(vm.uiState.value.saved) // not blocked
    }

    @Test
    fun `scheduled future date does not raise the administered warning`() = runTest {
        val vm = viewModel()
        // Switching to schedule defaults the date to tomorrow (future), but the warning is
        // administered-only, so it must stay off.
        vm.onModeChange(VaccineStatus.SCHEDULED)
        assertTrue(!vm.uiState.value.isFutureAdministered)
    }

    @Test
    fun `editing an administered dose dated ahead of now raises the warning`() = runTest {
        val vm = viewModel()
        vm.loadForEdit(
            VaccineRecord(
                id = 5,
                name = "MMR",
                status = VaccineStatus.ADMINISTERED,
                administeredDate = fixedNow.plusSeconds(86_400),
                createdAt = fixedNow,
            ),
        )
        assertTrue(vm.uiState.value.isFutureAdministered)
    }

    @Test
    fun `second rapid save is ignored while the first is in flight`() = runTest {
        val gate = CompletableDeferred<Long>()
        coEvery { add(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val vm = viewModel()
        vm.onNameChange("MMR")
        vm.onSave()
        vm.onSave() // dropped by the isSaving guard
        gate.complete(1)
        coVerify(exactly = 1) { add(any(), any(), any(), any(), any()) }
    }
}
