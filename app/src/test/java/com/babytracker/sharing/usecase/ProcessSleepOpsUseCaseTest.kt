package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.manager.PartnerSleepNotifier
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import com.babytracker.sharing.domain.repository.SharingRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessSleepOpsUseCaseTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val sharingRepository: SharingRepository = mockk()
    private val applySleepOp: ApplySleepOpUseCase = mockk()
    private val syncToFirestore: SyncToFirestoreUseCase = mockk()
    private val partnerSleepNotifier: PartnerSleepNotifier = mockk()
    private val shareCode = ShareCode("ABCD1234")
    private val sleepSync = SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS
    private lateinit var useCase: ProcessSleepOpsUseCase

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        useCase = ProcessSleepOpsUseCase(
            settingsRepository,
            sharingRepository,
            applySleepOp,
            syncToFirestore,
            partnerSleepNotifier,
        )
        coEvery { applySleepOp(any()) } returns SleepOpApplyResult(roomChanged = true)
        coEvery { syncToFirestore(any()) } just Runs
        coEvery { sharingRepository.deleteSleepOps(any(), any()) } just Runs
        coEvery { partnerSleepNotifier.notifySleepChanges(any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `ops are applied in created order`() = runTest {
        val opsFlow = MutableSharedFlow<List<SleepOp>>(replay = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeSleepOps(shareCode) } returns opsFlow
        val order = mutableListOf<String>()
        coEvery { applySleepOp(any()) } coAnswers {
            order += firstArg<SleepOp>().opId
            SleepOpApplyResult(roomChanged = true)
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-late", 200), op("op-early", 100)))
        advanceUntilIdle()

        assertEquals(listOf("op-early", "op-late"), order)
    }

    @Test
    fun `snapshot push happens before op delete`() = runTest {
        val opsFlow = MutableSharedFlow<List<SleepOp>>(replay = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeSleepOps(shareCode) } returns opsFlow

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()

        coVerifyOrder {
            syncToFirestore(sleepSync)
            sharingRepository.deleteSleepOps(shareCode, listOf("op-1"))
        }
    }

    @Test
    fun `all-dropped batch skips sync but still deletes ops`() = runTest {
        val opsFlow = MutableSharedFlow<List<SleepOp>>(replay = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeSleepOps(shareCode) } returns opsFlow
        coEvery { applySleepOp(any()) } returns SleepOpApplyResult(roomChanged = false)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()

        coVerify(exactly = 0) { syncToFirestore(any()) }
        coVerify { sharingRepository.deleteSleepOps(shareCode, listOf("op-1")) }
    }

    @Test
    fun `partner mode never observes sleep ops`() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PARTNER)
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)

        useCase()

        coVerify(exactly = 0) { applySleepOp(any()) }
        io.mockk.verify(exactly = 0) { sharingRepository.observeSleepOps(any()) }
    }

    @Test
    fun `missing share code never observes sleep ops`() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns flowOf(null)

        useCase()

        io.mockk.verify(exactly = 0) { sharingRepository.observeSleepOps(any()) }
    }

    @Test
    fun `listener error retries and recovers`() = runTest {
        var attempts = 0
        everyPrimaryWithCode()
        every { sharingRepository.observeSleepOps(shareCode) } returns flow {
            attempts += 1
            if (attempts == 1) error("offline")
            emit(listOf(op("op-1", 100)))
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        advanceUntilIdle()

        coVerify { applySleepOp(match { it.opId == "op-1" }) }
        coVerify { sharingRepository.deleteSleepOps(shareCode, listOf("op-1")) }
    }

    @Test
    fun `multi-change batch posts one coalesced notification of the latest change`() = runTest {
        val opsFlow = MutableSharedFlow<List<SleepOp>>(replay = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeSleepOps(shareCode) } returns opsFlow
        coEvery { applySleepOp(any()) } coAnswers {
            val kind = if (firstArg<SleepOp>().opId == "op-1") SleepNotifyKind.STARTED else SleepNotifyKind.STOPPED
            SleepOpApplyResult(true, PartnerSleepNotification(kind, SleepType.NAP))
        }
        val slot = slot<List<PartnerSleepNotification>>()
        coEvery { partnerSleepNotifier.notifySleepChanges(capture(slot)) } just Runs

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100), op("op-2", 200)))
        advanceUntilIdle()

        coVerify(exactly = 1) { partnerSleepNotifier.notifySleepChanges(any()) }
        assertEquals(
            listOf(SleepNotifyKind.STARTED, SleepNotifyKind.STOPPED),
            slot.captured.map { it.kind },
        )
    }

    @Test
    fun `batch with no notifications does not notify`() = runTest {
        val opsFlow = MutableSharedFlow<List<SleepOp>>(replay = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeSleepOps(shareCode) } returns opsFlow
        coEvery { applySleepOp(any()) } returns SleepOpApplyResult(roomChanged = false)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()

        coVerify(exactly = 0) { partnerSleepNotifier.notifySleepChanges(any()) }
    }

    private fun everyPrimaryWithCode() {
        every { settingsRepository.getAppMode() } returns MutableStateFlow(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns MutableStateFlow(shareCode.value)
    }

    private fun op(opId: String, createdAtMs: Long) = SleepOp(
        opId = opId,
        action = SleepOpAction.STOP,
        entryClientId = "client-$opId",
        authorUid = "partner-uid",
        createdAtMs = createdAtMs,
        endTimeMs = 2_000L,
    )
}
