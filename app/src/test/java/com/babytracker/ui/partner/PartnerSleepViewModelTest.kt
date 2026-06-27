package com.babytracker.ui.partner

import android.content.Context
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.StartPartnerSleepUseCase
import com.babytracker.sharing.usecase.StopPartnerSleepUseCase
import com.babytracker.sharing.usecase.UpdatePartnerSleepUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerSleepViewModelTest {
    private val startSleep: StartPartnerSleepUseCase = mockk()
    private val stopSleep: StopPartnerSleepUseCase = mockk()
    private val updateSleep: UpdatePartnerSleepUseCase = mockk()
    private val sharingRepository: SharingRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val appContext: Context = mockk()
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val testDispatcher = StandardTestDispatcher()

    private fun buildViewModel() = PartnerSleepViewModel(
        startSleep, stopSleep, updateSleep, sharingRepository, settingsRepository, appContext, { now },
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // No share code -> the observe loop returns early; pending ops stay empty in these tests.
        every { settingsRepository.getShareCode() } returns flowOf(null)
        every { appContext.getString(any()) } returns "error"
        coEvery { startSleep(any()) } returns "new-cid"
        coEvery { stopSleep(any()) } returns Unit
        coEvery { updateSleep(any(), any(), any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun partnerActive(clientId: String = "p-cid") = SleepSnapshot(
        id = 0, startTime = now.minusSeconds(600).toEpochMilli(), endTime = null,
        sleepType = "NAP", notes = null, clientId = clientId, startedBy = SleepAuthor.PARTNER.name,
    )

    private fun ownerActive() = SleepSnapshot(
        id = 1, startTime = now.minusSeconds(600).toEpochMilli(), endTime = null,
        sleepType = "NAP", notes = null, clientId = "o-cid", startedBy = SleepAuthor.OWNER.name,
    )

    @Test
    fun `onStartNap starts a nap when idle`() = runTest {
        val vm = buildViewModel()
        vm.onSleepRecordsAvailable(emptyList())

        vm.onStartNap()
        advanceUntilIdle()

        coVerify(exactly = 1) { startSleep(SleepType.NAP) }
    }

    @Test
    fun `onStartNightSleep starts night sleep when idle`() = runTest {
        val vm = buildViewModel()
        vm.onStartNightSleep()
        advanceUntilIdle()

        coVerify(exactly = 1) { startSleep(SleepType.NIGHT_SLEEP) }
    }

    @Test
    fun `start is ignored when a session is already active`() = runTest {
        val vm = buildViewModel()
        vm.onSleepRecordsAvailable(listOf(ownerActive()))

        vm.onStartNap()
        advanceUntilIdle()

        coVerify(exactly = 0) { startSleep(any()) }
    }

    @Test
    fun `onStop stops the active session by its clientId`() = runTest {
        val vm = buildViewModel()
        vm.onSleepRecordsAvailable(listOf(partnerActive("active-cid")))

        vm.onStop()
        advanceUntilIdle()

        coVerify(exactly = 1) { stopSleep("active-cid") }
    }

    @Test
    fun `canEditActive is true for a partner session, false for an owner session`() = runTest {
        val vm = buildViewModel()

        vm.onSleepRecordsAvailable(listOf(partnerActive()))
        assertTrue(vm.uiState.value.canEditActive)

        vm.onSleepRecordsAvailable(listOf(ownerActive()))
        assertFalse(vm.uiState.value.canEditActive)
    }

    @Test
    fun `onEditActive opens the editor only for a partner session`() = runTest {
        val vm = buildViewModel()

        vm.onSleepRecordsAvailable(listOf(ownerActive()))
        vm.onEditActive()
        assertNull(vm.uiState.value.editor)

        vm.onSleepRecordsAvailable(listOf(partnerActive("edit-cid")))
        vm.onEditActive()
        assertNotNull(vm.uiState.value.editor)
        assertEquals("edit-cid", vm.uiState.value.editor?.clientId)
    }

    @Test
    fun `onConfirmEdit submits the edited fields and closes the editor`() = runTest {
        val vm = buildViewModel()
        vm.onSleepRecordsAvailable(listOf(partnerActive("edit-cid")))
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
    fun `snapshotRefreshTick bumps when a submitted op is applied by the primary`() = runTest {
        // A pending op that the primary then applies (it disappears from the op listener).
        val op = SleepOp(
            opId = "op-1", action = SleepOpAction.STOP, entryClientId = "active-cid",
            authorUid = "uid", createdAtMs = now.toEpochMilli(), endTimeMs = now.toEpochMilli(),
        )
        every { settingsRepository.getShareCode() } returns flowOf("CODE")
        coEvery { sharingRepository.signInAnonymously() } returns "uid"
        every { sharingRepository.observeOwnSleepOps(ShareCode("CODE"), "uid") } returns
            flowOf(listOf(op), emptyList())

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.snapshotRefreshTick)
    }

    @Test
    fun `op listener resubscribes after a transient error`() = runTest {
        val op = SleepOp(
            opId = "op-1", action = SleepOpAction.STOP, entryClientId = "active-cid",
            authorUid = "uid", createdAtMs = now.toEpochMilli(), endTimeMs = now.toEpochMilli(),
        )
        var attempts = 0
        every { settingsRepository.getShareCode() } returns flowOf("CODE")
        coEvery { sharingRepository.signInAnonymously() } returns "uid"
        // First subscription blows up; retryWhen must re-subscribe and pick up the applied op.
        every { sharingRepository.observeOwnSleepOps(ShareCode("CODE"), "uid") } returns flow {
            attempts++
            if (attempts == 1) throw IOException("listener boom")
            emit(listOf(op))
            emit(emptyList())
        }

        val vm = buildViewModel()
        advanceUntilIdle()

        assertTrue(attempts >= 2)
        assertEquals(1, vm.uiState.value.snapshotRefreshTick)
    }

    @Test
    fun `revoked access surfaces an accessRevoked event`() = runTest {
        coEvery { startSleep(any()) } throws PartnerAccessRevokedException("revoked")
        val vm = buildViewModel()

        vm.onStartNap()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.accessRevoked)
    }
}
