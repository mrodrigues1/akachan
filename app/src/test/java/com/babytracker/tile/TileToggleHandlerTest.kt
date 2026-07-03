package com.babytracker.tile

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.manager.BreastfeedingSessionController
import com.babytracker.manager.SleepSessionController
import com.babytracker.widget.WidgetUpdater
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class TileToggleHandlerTest {

    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var sleepRepository: SleepRepository
    private lateinit var sessionController: BreastfeedingSessionController
    private lateinit var sleepSessionController: SleepSessionController
    private lateinit var widgetUpdater: WidgetUpdater
    private lateinit var handler: TileToggleHandler

    private val zone = ZoneOffset.UTC
    private val fixedNow: Instant = LocalDate.of(2024, 6, 15)
        .atTime(LocalTime.of(12, 0))
        .atZone(zone)
        .toInstant()

    private val activeSession = BreastfeedingSession(
        id = 1L,
        startTime = fixedNow.minusSeconds(300),
        startingSide = BreastSide.LEFT,
    )

    private val napRecord = SleepRecord(
        id = 10L,
        startTime = fixedNow.minusSeconds(1800),
        sleepType = SleepType.NAP,
    )

    private val nightRecord = SleepRecord(
        id = 11L,
        startTime = fixedNow.minusSeconds(1800),
        sleepType = SleepType.NIGHT_SLEEP,
    )

    @BeforeEach
    fun setup() {
        breastfeedingRepository = mockk()
        sleepRepository = mockk()
        sessionController = mockk()
        sleepSessionController = mockk()
        widgetUpdater = mockk(relaxed = true)
        handler = buildHandler(Clock.fixed(fixedNow, zone))
    }

    private fun buildHandler(clock: Clock) = TileToggleHandler(
        breastfeedingRepository = breastfeedingRepository,
        sleepRepository = sleepRepository,
        sessionController = sessionController,
        sleepSessionController = sleepSessionController,
        widgetUpdater = widgetUpdater,
        clock = clock,
    )

    // ── Feed stop ──────────────────────────────────────────────────────────────

    @Test
    fun feedStopReturnsChanged() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(activeSession)
        coEvery { sessionController.stop() } returns true

        val result = handler.toggleFeed()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    @Test
    fun feedStopDelegatesToSessionController() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(activeSession)
        coEvery { sessionController.stop() } returns true

        handler.toggleFeed()

        // controller.stop() ends the session atomically (the DAO re-reads the active row and
        // folds any open pause) and cancels both scheduled and posted notifications — the tile
        // no longer needs to replicate that choreography itself.
        coVerify(exactly = 1) { sessionController.stop() }
    }

    @Test
    fun feedStopUpdatesWidget() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(activeSession)
        coEvery { sessionController.stop() } returns true

        handler.toggleFeed()

        coVerify(exactly = 1) { widgetUpdater.updateAll() }
    }

    @Test
    fun feedStopReturnsNoOpWhenControllerReportsFailure() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(activeSession)
        coEvery { sessionController.stop() } returns false

        val result = handler.toggleFeed()

        assertEquals(TileToggleResult.NO_OP, result)
        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    // ── Feed start ─────────────────────────────────────────────────────────────

    @Test
    fun feedStartWithNoLastSessionUsesLeftSide() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        val captured = slot<BreastSide>()
        coEvery { sessionController.start(capture(captured)) } returns activeSession

        handler.toggleFeed()

        assertEquals(BreastSide.LEFT, captured.captured)
    }

    @Test
    fun feedStartAlternatesSideFromLastSession() = runTest {
        val lastSession = BreastfeedingSession(
            id = 99L,
            startTime = fixedNow.minusSeconds(3600),
            startingSide = BreastSide.LEFT,
        )
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns lastSession
        val captured = slot<BreastSide>()
        coEvery { sessionController.start(capture(captured)) } returns activeSession

        handler.toggleFeed()

        assertEquals(BreastSide.RIGHT, captured.captured)
    }

    @Test
    fun feedStartReturnsChanged() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { sessionController.start(any()) } returns activeSession

        val result = handler.toggleFeed()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    @Test
    fun feedStartDelegatesToSessionController() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { sessionController.start(any()) } returns activeSession

        handler.toggleFeed()

        // controller.start() owns scheduling the limit alarms, showing the running notification,
        // and syncing to Firestore — the tile only decides which side to start and updates the
        // widget on success.
        coVerify(exactly = 1) { sessionController.start(any()) }
    }

    @Test
    fun feedStartUpdatesWidget() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { sessionController.start(any()) } returns activeSession

        handler.toggleFeed()

        coVerify(exactly = 1) { widgetUpdater.updateAll() }
    }

    // ── Feed no-op ─────────────────────────────────────────────────────────────

    @Test
    fun feedNoOpWhenControllerStartReturnsNull() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { sessionController.start(any()) } returns null

        val result = handler.toggleFeed()

        assertEquals(TileToggleResult.NO_OP, result)
    }

    @Test
    fun feedNoOpDoesNotCallSideEffects() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { sessionController.start(any()) } returns null

        handler.toggleFeed()

        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    // ── Feed failure ───────────────────────────────────────────────────────────

    @Test
    fun feedReturnsFailedWhenRepositoryThrows() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } throws RuntimeException("db error")

        val result = handler.toggleFeed()

        assertEquals(TileToggleResult.FAILED, result)
    }

    @Test
    fun feedFailureDoesNotCallSideEffects() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } throws RuntimeException("db error")

        handler.toggleFeed()

        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    // ── Feed widget failure after successful mutation ───────────────────────────

    @Test
    fun feedWidgetFailureAfterMutationStillReturnsChanged() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { sessionController.start(any()) } returns activeSession
        coEvery { widgetUpdater.updateAll() } throws RuntimeException("widget unavailable")

        val result = handler.toggleFeed()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    // ── Sleep stop ─────────────────────────────────────────────────────────────

    @Test
    fun sleepStopReturnsChanged() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns napRecord
        coEvery { sleepSessionController.stop(napRecord.id) } returns napRecord.copy(endTime = fixedNow)

        val result = handler.toggleSleep()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    @Test
    fun sleepStopDelegatesToSleepSessionController() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns napRecord
        coEvery { sleepSessionController.stop(napRecord.id) } returns napRecord.copy(endTime = fixedNow)

        handler.toggleSleep()

        // controller.stop() owns cancelling the sleep notification, re-arming the nap reminder
        // (subject to the feature gate and the enabled/delay settings), propagating a night-sleep
        // end into the wake-time setting, and syncing to Firestore — the tile only decides which
        // record to stop and updates the widget on success.
        coVerify(exactly = 1) { sleepSessionController.stop(napRecord.id) }
    }

    @Test
    fun sleepStopUpdatesWidget() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns napRecord
        coEvery { sleepSessionController.stop(napRecord.id) } returns napRecord.copy(endTime = fixedNow)

        handler.toggleSleep()

        coVerify(exactly = 1) { widgetUpdater.updateAll() }
    }

    @Test
    fun sleepStopReturnsNoOpWhenControllerReturnsNull() = runTest {
        // The id-guarded use case inside the controller re-reads the active record; a race that
        // clears it between the tile's getLatestRecord() check and the controller call surfaces
        // here as a null return, which the tile treats as a no-op rather than a failure.
        coEvery { sleepRepository.getLatestRecord() } returns napRecord
        coEvery { sleepSessionController.stop(napRecord.id) } returns null

        val result = handler.toggleSleep()

        assertEquals(TileToggleResult.NO_OP, result)
        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    @Test
    fun sleepStopWithNightSleepDelegatesToSleepSessionController() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns nightRecord
        coEvery { sleepSessionController.stop(nightRecord.id) } returns nightRecord.copy(endTime = fixedNow)

        handler.toggleSleep()

        coVerify(exactly = 1) { sleepSessionController.stop(nightRecord.id) }
    }

    // ── Sleep start at evening (NIGHT_SLEEP) ───────────────────────────────────

    @Test
    fun sleepStartAt19ReceivesNightSleepType() = runTest {
        val eveningInstant = LocalDate.of(2024, 6, 15)
            .atTime(LocalTime.of(19, 0))
            .atZone(zone)
            .toInstant()
        val eveningHandler = buildHandler(Clock.fixed(eveningInstant, zone))
        coEvery { sleepRepository.getLatestRecord() } returns null
        val captured = slot<SleepType>()
        coEvery { sleepSessionController.start(capture(captured)) } returns
            nightRecord.copy(startTime = eveningInstant)

        eveningHandler.toggleSleep()

        assertEquals(SleepType.NIGHT_SLEEP, captured.captured)
    }

    @Test
    fun sleepStartAt19ReturnsChanged() = runTest {
        val eveningInstant = LocalDate.of(2024, 6, 15)
            .atTime(LocalTime.of(19, 0))
            .atZone(zone)
            .toInstant()
        val eveningHandler = buildHandler(Clock.fixed(eveningInstant, zone))
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepSessionController.start(any()) } returns nightRecord.copy(startTime = eveningInstant)

        val result = eveningHandler.toggleSleep()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    @Test
    fun sleepStartAt19DelegatesToSleepSessionController() = runTest {
        val eveningInstant = LocalDate.of(2024, 6, 15)
            .atTime(LocalTime.of(19, 0))
            .atZone(zone)
            .toInstant()
        val eveningHandler = buildHandler(Clock.fixed(eveningInstant, zone))
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepSessionController.start(any()) } returns nightRecord.copy(startTime = eveningInstant)

        eveningHandler.toggleSleep()

        // controller.start() owns cancelling the pending nap reminder, persisting the record,
        // showing the sleep notification, and syncing to Firestore.
        coVerify(exactly = 1) { sleepSessionController.start(SleepType.NIGHT_SLEEP) }
    }

    // ── Sleep start at midday (NAP) ────────────────────────────────────────────

    @Test
    fun sleepStartAtMiddayReceivesNapType() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns null
        val captured = slot<SleepType>()
        coEvery { sleepSessionController.start(capture(captured)) } returns napRecord.copy(startTime = fixedNow)

        handler.toggleSleep()

        assertEquals(SleepType.NAP, captured.captured)
    }

    // ── Sleep no-op ────────────────────────────────────────────────────────────

    @Test
    fun sleepNoOpWhenControllerStartReturnsNull() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepSessionController.start(any()) } returns null

        val result = handler.toggleSleep()

        assertEquals(TileToggleResult.NO_OP, result)
    }

    @Test
    fun sleepNoOpDoesNotCallSideEffects() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepSessionController.start(any()) } returns null

        handler.toggleSleep()

        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    // ── Sleep failure ──────────────────────────────────────────────────────────

    @Test
    fun sleepReturnsFailedWhenRepositoryThrows() = runTest {
        coEvery { sleepRepository.getLatestRecord() } throws RuntimeException("db error")

        val result = handler.toggleSleep()

        assertEquals(TileToggleResult.FAILED, result)
    }

    @Test
    fun sleepFailureDoesNotCallSideEffects() = runTest {
        coEvery { sleepRepository.getLatestRecord() } throws RuntimeException("db error")

        handler.toggleSleep()

        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    // ── Sleep widget failure after successful mutation ─────────────────────────

    @Test
    fun sleepWidgetFailureAfterMutationStillReturnsChanged() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepSessionController.start(any()) } returns napRecord.copy(startTime = fixedNow)
        coEvery { widgetUpdater.updateAll() } throws RuntimeException("widget unavailable")

        val result = handler.toggleSleep()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    // ── Mutex serialization ────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun twoConcurrentToggleFeedCallsAreSerializedByMutex() = runTest {
        val gate = CompletableDeferred<Unit>()
        val callOrder = mutableListOf<String>()
        var callCount = 0

        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { sessionController.start(any()) } coAnswers {
            val index = ++callCount
            if (index == 1) {
                callOrder.add("first-entered")
                gate.await()
                callOrder.add("first-exited")
            } else {
                callOrder.add("second-entered")
            }
            activeSession.copy(id = index.toLong())
        }

        val job1 = launch { handler.toggleFeed() }
        val job2 = launch { handler.toggleFeed() }

        advanceUntilIdle()

        gate.complete(Unit)

        job1.join()
        job2.join()

        assertEquals(listOf("first-entered", "first-exited", "second-entered"), callOrder)
    }
}
