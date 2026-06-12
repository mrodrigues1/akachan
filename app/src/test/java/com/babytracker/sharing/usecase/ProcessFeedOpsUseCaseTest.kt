package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
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
    private val sharingRepository: SharingRepository = mockk()
    private val applyFeedOp: ApplyFeedOpUseCase = mockk()
    private val syncToFirestore: SyncToFirestoreUseCase = mockk()
    private val shareCode = ShareCode("ABCD1234")
    private lateinit var useCase: ProcessFeedOpsUseCase

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        useCase = ProcessFeedOpsUseCase(
            settingsRepository,
            sharingRepository,
            applyFeedOp,
            syncToFirestore,
        )
        coEvery { applyFeedOp(any()) } returns true
        coEvery { syncToFirestore(any()) } just Runs
        coEvery { sharingRepository.deleteFeedOps(any(), any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `ops are applied in created order`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeFeedOps(shareCode) } returns opsFlow
        val order = mutableListOf<String>()
        coEvery { applyFeedOp(any()) } coAnswers {
            order += firstArg<FeedOp>().opId
            true
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-late", 200), op("op-early", 100)))
        advanceUntilIdle()

        assertEquals(listOf("op-early", "op-late"), order)
    }

    @Test
    fun `snapshot pushes happen before op delete`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeFeedOps(shareCode) } returns opsFlow

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()

        coVerifyOrder {
            syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS)
            syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY)
            sharingRepository.deleteFeedOps(shareCode, listOf("op-1"))
        }
    }

    @Test
    fun `push still happens when every op was dropped`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeFeedOps(shareCode) } returns opsFlow
        coEvery { applyFeedOp(any()) } returns false

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) }
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY) }
        coVerify { sharingRepository.deleteFeedOps(shareCode, listOf("op-1")) }
    }

    @Test
    fun `partner mode never observes feed ops`() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PARTNER)
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)

        useCase()

        coVerify(exactly = 0) { applyFeedOp(any()) }
        coVerify(exactly = 0) { sharingRepository.deleteFeedOps(any(), any()) }
        io.mockk.verify(exactly = 0) { sharingRepository.observeFeedOps(any()) }
    }

    @Test
    fun `missing share code never observes feed ops`() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns flowOf(null)

        useCase()

        io.mockk.verify(exactly = 0) { sharingRepository.observeFeedOps(any()) }
    }

    @Test
    fun `failed batch retries in place and succeeds without new emission`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeFeedOps(shareCode) } returns opsFlow
        var bottleSyncCalls = 0
        coEvery { syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) } coAnswers {
            bottleSyncCalls += 1
            if (bottleSyncCalls == 1) error("boom")
            Unit
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        advanceUntilIdle()
        advanceTimeBy(5_001)
        runCurrent()

        assertEquals(2, bottleSyncCalls)
        coVerify { sharingRepository.deleteFeedOps(shareCode, listOf("op-1")) }
    }

    @Test
    fun `batch gives up after max attempts and later emission is processed`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(replay = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeFeedOps(shareCode) } returns opsFlow
        var bottleSyncCalls = 0
        var failSync = true
        coEvery { syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) } coAnswers {
            bottleSyncCalls += 1
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

        assertEquals(3, bottleSyncCalls)
        coVerify(exactly = 0) { sharingRepository.deleteFeedOps(any(), any()) }

        failSync = false
        opsFlow.emit(listOf(op("op-2", 200)))
        advanceUntilIdle()

        coVerify { sharingRepository.deleteFeedOps(shareCode, listOf("op-2")) }
    }

    @Test
    fun `listener error retries and recovers`() = runTest {
        var collectionAttempts = 0
        everyPrimaryWithCode()
        every { sharingRepository.observeFeedOps(shareCode) } returns flow {
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
        coVerify { sharingRepository.deleteFeedOps(shareCode, listOf("op-1")) }
    }

    @Test
    fun `new emission does not cancel in flight batch`() = runTest {
        val opsFlow = MutableSharedFlow<List<FeedOp>>(extraBufferCapacity = 1)
        everyPrimaryWithCode()
        every { sharingRepository.observeFeedOps(shareCode) } returns opsFlow
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
            true
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }
        advanceUntilIdle()
        opsFlow.emit(listOf(op("op-1", 100)))
        started.await()
        opsFlow.emit(listOf(op("op-2", 200)))
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("start-op-1", "end-op-1", "start-op-2", "end-op-2"), order)
        coVerify { sharingRepository.deleteFeedOps(shareCode, listOf("op-1")) }
        coVerify { sharingRepository.deleteFeedOps(shareCode, listOf("op-2")) }
    }

    private fun everyPrimaryWithCode() {
        every { settingsRepository.getAppMode() } returns MutableStateFlow(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns MutableStateFlow(shareCode.value)
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
