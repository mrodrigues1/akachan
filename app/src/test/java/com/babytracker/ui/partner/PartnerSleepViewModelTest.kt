package com.babytracker.ui.partner

import android.content.Context
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.observeSleepOps
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.StartPartnerSleepUseCase
import com.babytracker.sharing.usecase.StopPartnerSleepUseCase
import com.babytracker.sharing.usecase.UpdatePartnerSleepUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
    private val service: FirestoreSharingService = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val appContext: Context = mockk()
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val testDispatcher = StandardTestDispatcher()

    private fun buildViewModel() = PartnerSleepViewModel(
        startSleep, stopSleep, updateSleep, service, settingsRepository, appContext, { now },
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // observeSleepOps is a top-level extension function on the service.
        mockkStatic("com.babytracker.sharing.data.firebase.FirestoreSharingServiceKt")
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
        unmockkAll()
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
    fun `op listener resubscribes after a transient error`() = runTest {
        val startOp = SleepOp(
            opId = "op-1", action = SleepOpAction.START, entryClientId = "active-cid",
            authorUid = "uid", createdAtMs = now.toEpochMilli(), startTimeMs = now.toEpochMilli(), sleepType = "NAP",
        )
        var attempts = 0
        every { settingsRepository.getShareCode() } returns flowOf("CODE")
        coEvery { service.signInAnonymously() } returns "uid"
        // First subscription blows up; retryWhen must re-subscribe and pick up the op.
        every { service.observeSleepOps("CODE", "uid") } returns flow {
            attempts++
            if (attempts == 1) throw IOException("listener boom")
            emit(listOf(startOp))
        }

        val vm = buildViewModel()
        vm.onSleepRecordsAvailable(emptyList())
        advanceUntilIdle()

        assertTrue(attempts >= 2)
        assertNotNull(vm.uiState.value.active) // the optimistic START surfaced after re-subscribe
    }

    @Test
    fun `a consumed START overlay is retained until the snapshot shows the session`() = runTest {
        val startOp = SleepOp(
            opId = "op-1", action = SleepOpAction.START, entryClientId = "p-cid",
            authorUid = "uid", createdAtMs = now.toEpochMilli(), startTimeMs = now.toEpochMilli(), sleepType = "NAP",
        )
        every { settingsRepository.getShareCode() } returns flowOf("CODE")
        coEvery { service.signInAnonymously() } returns "uid"
        // START op present, then consumed (empty) — BEFORE the snapshot has the session.
        every { service.observeSleepOps("CODE", "uid") } returns flowOf(listOf(startOp), emptyList())

        val vm = buildViewModel()
        vm.onSleepRecordsAvailable(emptyList()) // snapshot still has no active session
        advanceUntilIdle()

        // The just-started session is retained optimistically instead of vanishing on op-consume.
        assertNotNull(vm.uiState.value.active)
        assertEquals("p-cid", vm.uiState.value.active?.clientId)
    }

    @Test
    fun `a stopped session the primary has not yet published shows as the last completed sleep`() = runTest {
        val t = now.toEpochMilli()
        val startOp = SleepOp(
            opId = "op-start", action = SleepOpAction.START, entryClientId = "c",
            authorUid = "uid", createdAtMs = t - 1_000, startTimeMs = t - 1_000, sleepType = "NAP",
        )
        val stopOp = SleepOp(
            opId = "op-stop", action = SleepOpAction.STOP, entryClientId = "c",
            authorUid = "uid", createdAtMs = t, endTimeMs = t,
        )
        every { settingsRepository.getShareCode() } returns flowOf("CODE")
        coEvery { service.signInAnonymously() } returns "uid"
        every { service.observeSleepOps("CODE", "uid") } returns flowOf(listOf(startOp, stopOp))

        val vm = buildViewModel()
        vm.onSleepRecordsAvailable(emptyList()) // primary hasn't published the session yet
        advanceUntilIdle()

        assertNull(vm.uiState.value.active) // no longer active (start then stop)
        val completed = vm.uiState.value.lastCompleted
        assertNotNull(completed)
        assertEquals("c", completed?.clientId)
        assertEquals(t, completed?.endTime)
        assertEquals("c", vm.uiState.value.mostRecent?.clientId)
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
