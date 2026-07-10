package com.babytracker.ui.partner

import android.content.Context
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.usecase.ObservePartnerActiveSleepUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.PartnerActiveSleep
import com.babytracker.sharing.usecase.StartPartnerSleepUseCase
import com.babytracker.sharing.usecase.StopPartnerSleepUseCase
import com.babytracker.sharing.usecase.UpdatePartnerSleepUseCase
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerSleepViewModelTest {
    private val startSleep: StartPartnerSleepUseCase = mockk()
    private val stopSleep: StopPartnerSleepUseCase = mockk()
    private val updateSleep: UpdatePartnerSleepUseCase = mockk()
    private val observeActiveSleep: ObservePartnerActiveSleepUseCase = mockk()
    private val appContext: Context = mockk()
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val testDispatcher = StandardTestDispatcher()

    // The reconcile/merge pipeline now lives in ObservePartnerActiveSleepUseCase (tested separately);
    // the view model just mirrors its emissions, so tests drive the active state through this flow.
    private val activeFlow = MutableStateFlow(PartnerActiveSleep())

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)

    private fun buildViewModel() = PartnerSleepViewModel(
        startSleep, stopSleep, updateSleep, observeActiveSleep, appContext,
    )

    @BeforeEach
    fun setUp() {
        coEvery { observeActiveSleep(any()) } returns activeFlow
        every { appContext.getString(any()) } returns "error"
        coEvery { startSleep(any()) } returns "new-cid"
        coEvery { stopSleep(any()) } returns Unit
        coEvery { updateSleep(any(), any(), any(), any(), any()) } returns Unit
    }

    private fun partnerActive(clientId: String = "p-cid") = SleepSnapshot(
        id = 0, startTime = now.minusSeconds(600).toEpochMilli(), endTime = null,
        sleepType = SleepType.NAP, notes = null, clientId = clientId, startedBy = SleepAuthor.PARTNER,
    )

    private fun ownerActive() = SleepSnapshot(
        id = 1, startTime = now.minusSeconds(600).toEpochMilli(), endTime = null,
        sleepType = SleepType.NAP, notes = null, clientId = "o-cid", startedBy = SleepAuthor.OWNER,
    )

    @Test
    fun `onStartNap starts a nap when idle`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onStartNap()
        advanceUntilIdle()

        coVerify(exactly = 1) { startSleep(SleepType.NAP) }
    }

    @Test
    fun `onStartNightSleep starts night sleep when idle`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onStartNightSleep()
        advanceUntilIdle()

        coVerify(exactly = 1) { startSleep(SleepType.NIGHT_SLEEP) }
    }

    @Test
    fun `start is ignored when a session is already active`() = runTest {
        activeFlow.value = PartnerActiveSleep(active = ownerActive())
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onStartNap()
        advanceUntilIdle()

        coVerify(exactly = 0) { startSleep(any()) }
    }

    @Test
    fun `onStop stops the active session by its clientId`() = runTest {
        activeFlow.value = PartnerActiveSleep(active = partnerActive("active-cid"))
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onStop()
        advanceUntilIdle()

        coVerify(exactly = 1) { stopSleep("active-cid") }
    }

    @Test
    fun `canEditActive mirrors the use case output`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        activeFlow.value = PartnerActiveSleep(active = partnerActive(), canEditActive = true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canEditActive)

        activeFlow.value = PartnerActiveSleep(active = ownerActive(), canEditActive = false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canEditActive)
    }

    @Test
    fun `active, lastCompleted and mostRecent mirror the use case output`() = runTest {
        val active = partnerActive("p-cid")
        val completed = partnerActive("c-cid").copy(endTime = now.toEpochMilli())
        activeFlow.value = PartnerActiveSleep(active = active, lastCompleted = completed, mostRecent = active, stopping = true)
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(active, vm.uiState.value.active)
        assertEquals(completed, vm.uiState.value.lastCompleted)
        assertEquals(active, vm.uiState.value.mostRecent)
        assertTrue(vm.uiState.value.stopping)
    }

    @Test
    fun `onEditActive opens the editor only for a partner session`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        activeFlow.value = PartnerActiveSleep(active = ownerActive())
        advanceUntilIdle()
        vm.onEditActive()
        assertNull(vm.uiState.value.editor)

        activeFlow.value = PartnerActiveSleep(active = partnerActive("edit-cid"))
        advanceUntilIdle()
        vm.onEditActive()
        assertNotNull(vm.uiState.value.editor)
        assertEquals("edit-cid", vm.uiState.value.editor?.clientId)
    }

    @Test
    fun `onConfirmEdit submits the edited fields and closes the editor`() = runTest {
        activeFlow.value = PartnerActiveSleep(active = partnerActive("edit-cid"))
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onEditActive()
        vm.onEditorTypeChange(SleepType.NIGHT_SLEEP)

        vm.onConfirmEdit()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateSleep("edit-cid", any(), any(), SleepType.NIGHT_SLEEP, any())
        }
        assertNull(vm.uiState.value.editor)
    }

    @Test
    fun `revoked access surfaces an accessRevoked event`() = runTest {
        coEvery { startSleep(any()) } throws PartnerAccessRevokedException("revoked")
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onStartNap()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.accessRevoked)
    }

    @Test
    fun `failed start surfaces a startStopError and clears busy`() = runTest {
        coEvery { startSleep(any()) } throws IOException("offline")
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onStartNap()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isBusy)
        assertNotNull(vm.uiState.value.startStopError)
        assertFalse(vm.uiState.value.accessRevoked)
    }

    @Test
    fun `failed stop surfaces a startStopError and clears busy`() = runTest {
        coEvery { stopSleep(any()) } throws IOException("offline")
        activeFlow.value = PartnerActiveSleep(active = partnerActive("active-cid"))
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onStop()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isBusy)
        assertNotNull(vm.uiState.value.startStopError)
    }

    @Test
    fun `startStopError clears on the next start attempt`() = runTest {
        coEvery { startSleep(any()) } throws IOException("offline") andThen "new-cid"
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onStartNap()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.startStopError)

        vm.onStartNap()
        advanceUntilIdle()

        assertNull(vm.uiState.value.startStopError)
    }
}
