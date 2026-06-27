package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.manager.PartnerFeedNotificationManager
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
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
import kotlinx.coroutines.CompletableDeferred
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessFeedOpsUseCaseTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val service: FirestoreSharingService = mockk()
    private val applyFeedOp: ApplyFeedOpUseCase = mockk()
    private val syncToFirestore: SyncToFirestoreUseCase = mockk()
    private val partnerFeedNotifier: PartnerFeedNotificationManager = mockk()
    private val shareCode = "ABCD1234"
    private val coalescedSync = SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS_AND_INVENTORY
    private lateinit var useCase: ProcessFeedOpsUseCase

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        useCase = ProcessFeedOpsUseCase(
            settingsRepository,
            service,
            applyFeedOp,
            syncToFirestore,
            partnerFeedNotifier,
        )
        coEvery { applyFeedOp(any()) } returns FeedOpApplyResult(roomChanged = true)
        coEvery { syncToFirestore(any()) } just Runs
        coEvery { service.deleteFeedOps(any(), any()) } just Runs
        coEvery { partnerFeedNotifier.notifyStashConsumed(any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `ops are applied in created order`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow
        val order = mutableListOf<String>()
        coEvery { applyFeedOp(any()) } coAnswers {
            order += firstArg<FeedOp>().opId
            FeedOpApplyResult(roomChanged = true)
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-late", 200), op("op-early", 100)))
        advanceUntilIdle()

        assertEquals(listOf("op-early", "op-late"), order)
    }

    @Test
    fun `snapshot push happens before op delete`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()

        coVerifyOrder {
            syncToFirestore(coalescedSync)
            service.deleteFeedOps(shareCode, listOf("op-1"))
        }
    }

    @Test
    fun `feed-and-inventory changes push one coalesced write, not two`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()

        coVerify(exactly = 1) { syncToFirestore(coalescedSync) }
        coVerify(exactly = 0) { syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) }
        coVerify(exactly = 0) { syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY) }
    }

    @Test
    fun `all-dropped batch skips sync but still deletes ops`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow
        coEvery { applyFeedOp(any()) } returns FeedOpApplyResult(roomChanged = false)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()

        coVerify(exactly = 0) { syncToFirestore(any()) }
        coVerify { service.deleteFeedOps(shareCode, listOf("op-1")) }
    }

    @Test
    fun `mixed batch applies every op and pushes snapshot once`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow
        val applied = mutableListOf<String>()
        coEvery { applyFeedOp(any()) } coAnswers {
            val id = firstArg<FeedOp>().opId
            applied += id
            FeedOpApplyResult(roomChanged = id == "op-changed")
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-changed", 100), op("op-dropped", 200)))
        advanceUntilIdle()

        assertEquals(listOf("op-changed", "op-dropped"), applied)
        coVerify(exactly = 1) { syncToFirestore(coalescedSync) }
        coVerify { service.deleteFeedOps(shareCode, listOf("op-changed", "op-dropped")) }
    }

    @Test
    fun `partner mode never observes feed ops`() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PARTNER)
        every { settingsRepository.getShareCode() } returns flowOf(shareCode)

        useCase()

        coVerify(exactly = 0) { applyFeedOp(any()) }
        coVerify(exactly = 0) { service.deleteFeedOps(any(), any()) }
        io.mockk.verify(exactly = 0) { service.observeFeedOps(any()) }
    }

    @Test
    fun `missing share code never observes feed ops`() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns flowOf(null)

        useCase()

        io.mockk.verify(exactly = 0) { service.observeFeedOps(any()) }
    }

    @Test
    fun `failed batch retries in place and succeeds without new emission`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow
        var syncCalls = 0
        coEvery { syncToFirestore(coalescedSync) } coAnswers {
            syncCalls += 1
            if (syncCalls == 1) error("boom")
            Unit
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()
        advanceTimeBy(5_001)
        runCurrent()

        assertEquals(2, syncCalls)
        coVerify { service.deleteFeedOps(shareCode, listOf("op-1")) }
    }

    @Test
    fun `retry still pushes snapshot when reapply is an idempotent no-op`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow
        var applyCalls = 0
        coEvery { applyFeedOp(any()) } coAnswers {
            applyCalls += 1
            // Room changed on first attempt; retry re-apply is a no-op
            FeedOpApplyResult(roomChanged = applyCalls == 1)
        }
        var syncCalls = 0
        coEvery { syncToFirestore(coalescedSync) } coAnswers {
            syncCalls += 1
            if (syncCalls == 1) error("boom")
            Unit
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()
        advanceTimeBy(5_001)
        runCurrent()

        // syncPending survives the no-op reapply, so the coalesced push is retried until it succeeds.
        assertEquals(2, syncCalls)
        coVerify(exactly = 1) { service.deleteFeedOps(shareCode, listOf("op-1")) }
    }

    @Test
    fun `batch gives up after max attempts and later emission is processed`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow
        var syncCalls = 0
        var failSync = true
        coEvery { syncToFirestore(coalescedSync) } coAnswers {
            syncCalls += 1
            if (failSync) error("boom")
            Unit
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()
        advanceTimeBy(5_001)
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(3, syncCalls)
        coVerify(exactly = 0) { service.deleteFeedOps(any(), any()) }

        failSync = false
        opsFlow.emit(listOf(op("op-2", 200)))
        advanceUntilIdle()

        coVerify { service.deleteFeedOps(shareCode, listOf("op-2")) }
    }

    @Test
    fun `sync owed after give-up survives to a later idempotent re-emission`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow
        var applyCalls = 0
        coEvery { applyFeedOp(any()) } coAnswers {
            applyCalls += 1
            // Room changed once; every re-apply is an idempotent no-op
            FeedOpApplyResult(roomChanged = applyCalls == 1)
        }
        var syncCalls = 0
        var failSync = true
        coEvery { syncToFirestore(coalescedSync) } coAnswers {
            syncCalls += 1
            if (failSync) error("boom")
            Unit
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()
        advanceTimeBy(5_001)
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()

        // All attempts exhausted; op must not be deleted while the snapshot push is still owed.
        assertEquals(3, syncCalls)
        coVerify(exactly = 0) { service.deleteFeedOps(any(), any()) }

        failSync = false
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()

        assertEquals(4, syncCalls)
        coVerify(exactly = 1) { service.deleteFeedOps(shareCode, listOf("op-1")) }
    }

    @Test
    fun `listener error retries and recovers`() = runTest {
        var collectionAttempts = 0
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns flow {
            collectionAttempts += 1
            if (collectionAttempts == 1) error("offline")
            emit(listOf(op("op-1", 100)))
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        advanceUntilIdle()

        coVerify { applyFeedOp(match { it.opId == "op-1" }) }
        coVerify { service.deleteFeedOps(shareCode, listOf("op-1")) }
    }

    @Test
    fun `new emission does not cancel in flight batch`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(extraBufferCapacity = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        coEvery { applyFeedOp(any()) } coAnswers {
            val id = firstArg<FeedOp>().opId
            order += "start-$id"
            if (id == "op-1") {
                started.complete(Unit)
                release.await()
            }
            order += "end-$id"
            FeedOpApplyResult(roomChanged = true)
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        started.await()
        opsFlow.emit(listOf(op("op-2", 200)))
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("start-op-1", "end-op-1", "start-op-2", "end-op-2"), order)
        coVerify { service.deleteFeedOps(shareCode, listOf("op-1")) }
        coVerify { service.deleteFeedOps(shareCode, listOf("op-2")) }
    }

    @Test
    fun `consuming feeds trigger a single coalesced stash notification`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow
        coEvery { applyFeedOp(any()) } coAnswers {
            val bagId = if (firstArg<FeedOp>().opId == "op-1") 11L else 22L
            FeedOpApplyResult(roomChanged = true, consumedBagId = bagId)
        }
        val bagSlot = slot<List<Long>>()
        coEvery { partnerFeedNotifier.notifyStashConsumed(capture(bagSlot)) } just Runs

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100), op("op-2", 200)))
        advanceUntilIdle()

        coVerify(exactly = 1) { partnerFeedNotifier.notifyStashConsumed(any()) }
        assertEquals(listOf(11L, 22L), bagSlot.captured)
    }

    @Test
    fun `batch without consumed bags does not notify`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()

        coVerify(exactly = 0) { partnerFeedNotifier.notifyStashConsumed(any()) }
    }

    @Test
    fun `replayed consuming op notifies only once across retry`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { service.observeFeedOps(shareCode) } returns opsFlow
        var applyCalls = 0
        coEvery { applyFeedOp(any()) } coAnswers {
            applyCalls += 1
            // Fresh create consumes the bag once; the retry re-apply reports no consumed bag.
            FeedOpApplyResult(
                roomChanged = applyCalls == 1,
                consumedBagId = if (applyCalls == 1) 11L else null,
            )
        }
        var syncCalls = 0
        coEvery { syncToFirestore(coalescedSync) } coAnswers {
            syncCalls += 1
            if (syncCalls == 1) error("boom")
            Unit
        }
        val bagSlot = slot<List<Long>>()
        coEvery { partnerFeedNotifier.notifyStashConsumed(capture(bagSlot)) } just Runs

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()
        advanceTimeBy(5_001)
        runCurrent()

        coVerify(exactly = 1) { partnerFeedNotifier.notifyStashConsumed(any()) }
        assertEquals(listOf(11L), bagSlot.captured)
    }

    private fun everyPrimaryWithCode() {
        every { settingsRepository.getAppMode() } returns MutableStateFlow(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns MutableStateFlow(shareCode)
    }

    private fun op(opId: String, createdAtMs: Long) = FeedOp(
        opId = opId,
        action = FeedOpAction.DELETE,
        entryClientId = "client-$opId",
        authorUid = "partner-uid",
        createdAtMs = createdAtMs,
        timestampMs = Instant.parse("2026-06-01T09:00:00Z").toEpochMilli(),
        volumeMl = 120,
        type = "FORMULA",
    )
}
