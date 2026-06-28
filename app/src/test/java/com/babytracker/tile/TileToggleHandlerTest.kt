package com.babytracker.tile

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.SleepNotificationScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.widget.WidgetUpdater
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
    private lateinit var breastfeedingNotifications: BreastfeedingSessionNotificationCoordinator
    private lateinit var sleepNotificationScheduler: SleepNotificationScheduler
    private lateinit var napReminderScheduler: NapReminderScheduler
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
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
        breastfeedingNotifications = mockk(relaxed = true)
        sleepNotificationScheduler = mockk(relaxed = true)
        napReminderScheduler = mockk(relaxed = true)
        syncToFirestore = mockk(relaxed = true)
        widgetUpdater = mockk(relaxed = true)
        handler = buildHandler(Clock.fixed(fixedNow, zone))
    }

    private fun buildHandler(clock: Clock) = TileToggleHandler(
        breastfeedingRepository = breastfeedingRepository,
        sleepRepository = sleepRepository,
        breastfeedingNotifications = breastfeedingNotifications,
        sleepNotificationScheduler = sleepNotificationScheduler,
        napReminderScheduler = napReminderScheduler,
        syncToFirestore = syncToFirestore,
        widgetUpdater = widgetUpdater,
        clock = clock,
    )

    // ── Feed stop ──────────────────────────────────────────────────────────────

    @Test
    fun feedStopReturnsChanged() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(activeSession)
        coEvery { breastfeedingRepository.stopActiveSession(fixedNow) } returns true

        val result = handler.toggleFeed()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    @Test
    fun feedStopCancelsScheduledAndPostedNotifications() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(activeSession)
        coEvery { breastfeedingRepository.stopActiveSession(fixedNow) } returns true

        handler.toggleFeed()

        verify { breastfeedingNotifications.cancelScheduled() }
        verify { breastfeedingNotifications.cancelPostedSessionNotifications() }
    }

    @Test
    fun feedStopRequestsFirestoreSessionSync() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(activeSession)
        coEvery { breastfeedingRepository.stopActiveSession(fixedNow) } returns true

        handler.toggleFeed()

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun feedStopUpdatesWidget() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(activeSession)
        coEvery { breastfeedingRepository.stopActiveSession(fixedNow) } returns true

        handler.toggleFeed()

        coVerify(exactly = 1) { widgetUpdater.updateAll() }
    }

    // ── Feed start ─────────────────────────────────────────────────────────────

    @Test
    fun feedStartWithNoLastSessionUsesLeftSide() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        val captured = slot<BreastfeedingSession>()
        coEvery { breastfeedingRepository.startSessionIfNone(capture(captured)) } returns 1L

        handler.toggleFeed()

        assertEquals(BreastSide.LEFT, captured.captured.startingSide)
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
        val captured = slot<BreastfeedingSession>()
        coEvery { breastfeedingRepository.startSessionIfNone(capture(captured)) } returns 2L

        handler.toggleFeed()

        assertEquals(BreastSide.RIGHT, captured.captured.startingSide)
    }

    @Test
    fun feedStartReturnsChanged() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { breastfeedingRepository.startSessionIfNone(any()) } returns 1L

        val result = handler.toggleFeed()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    @Test
    fun feedStartSchedulesAndShowsNotifications() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { breastfeedingRepository.startSessionIfNone(any()) } returns 1L

        handler.toggleFeed()

        coVerify { breastfeedingNotifications.scheduleInitial(any()) }
        coVerify { breastfeedingNotifications.showRunning(any()) }
    }

    @Test
    fun feedStartRequestsFirestoreSessionSync() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { breastfeedingRepository.startSessionIfNone(any()) } returns 1L

        handler.toggleFeed()

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun feedStartUpdatesWidget() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { breastfeedingRepository.startSessionIfNone(any()) } returns 1L

        handler.toggleFeed()

        coVerify(exactly = 1) { widgetUpdater.updateAll() }
    }

    // ── Feed no-op ─────────────────────────────────────────────────────────────

    @Test
    fun feedNoOpWhenStartSessionIfNoneReturnsNull() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { breastfeedingRepository.startSessionIfNone(any()) } returns null

        val result = handler.toggleFeed()

        assertEquals(TileToggleResult.NO_OP, result)
    }

    @Test
    fun feedNoOpDoesNotCallSideEffects() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { breastfeedingRepository.startSessionIfNone(any()) } returns null

        handler.toggleFeed()

        coVerify(exactly = 0) { breastfeedingNotifications.scheduleInitial(any()) }
        coVerify(exactly = 0) { breastfeedingNotifications.showRunning(any()) }
        coVerify(exactly = 0) { syncToFirestore(any()) }
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

        coVerify(exactly = 0) { breastfeedingNotifications.scheduleInitial(any()) }
        coVerify(exactly = 0) { syncToFirestore(any()) }
        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    // ── Feed widget failure after successful mutation ───────────────────────────

    @Test
    fun feedWidgetFailureAfterMutationStillReturnsChanged() = runTest {
        every { breastfeedingRepository.getActiveSession() } returns flowOf(null)
        coEvery { breastfeedingRepository.getLastSession() } returns null
        coEvery { breastfeedingRepository.startSessionIfNone(any()) } returns 1L
        coEvery { widgetUpdater.updateAll() } throws RuntimeException("widget unavailable")

        val result = handler.toggleFeed()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    // ── Sleep stop NAP ─────────────────────────────────────────────────────────

    @Test
    fun sleepStopNapReturnsChanged() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns napRecord
        coEvery { sleepRepository.stopActiveRecord(fixedNow) } returns true

        val result = handler.toggleSleep()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    @Test
    fun sleepStopCancelsSleepNotification() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns napRecord
        coEvery { sleepRepository.stopActiveRecord(fixedNow) } returns true

        handler.toggleSleep()

        verify { sleepNotificationScheduler.cancel() }
    }

    @Test
    fun sleepStopNapCallsScheduleNapReminderIfEnabled() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns napRecord
        coEvery { sleepRepository.stopActiveRecord(fixedNow) } returns true

        handler.toggleSleep()

        coVerify { napReminderScheduler.scheduleIfEnabled(fixedNow) }
    }

    @Test
    fun sleepStopRequestsFirestoreSleepSync() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns napRecord
        coEvery { sleepRepository.stopActiveRecord(fixedNow) } returns true

        handler.toggleSleep()

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
    }

    @Test
    fun sleepStopUpdatesWidget() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns napRecord
        coEvery { sleepRepository.stopActiveRecord(fixedNow) } returns true

        handler.toggleSleep()

        coVerify(exactly = 1) { widgetUpdater.updateAll() }
    }

    // ── Sleep stop NIGHT_SLEEP ─────────────────────────────────────────────────

    @Test
    fun sleepStopNightSleepDoesNotCallScheduleNapReminderIfEnabled() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns nightRecord
        coEvery { sleepRepository.stopActiveRecord(fixedNow) } returns true

        handler.toggleSleep()

        coVerify(exactly = 0) { napReminderScheduler.scheduleIfEnabled(any()) }
        verify { sleepNotificationScheduler.cancel() }
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
        val captured = slot<SleepRecord>()
        coEvery { sleepRepository.startRecordIfNone(capture(captured)) } returns 1L

        eveningHandler.toggleSleep()

        assertEquals(SleepType.NIGHT_SLEEP, captured.captured.sleepType)
    }

    @Test
    fun sleepStartAt19ReturnsChanged() = runTest {
        val eveningInstant = LocalDate.of(2024, 6, 15)
            .atTime(LocalTime.of(19, 0))
            .atZone(zone)
            .toInstant()
        val eveningHandler = buildHandler(Clock.fixed(eveningInstant, zone))
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepRepository.startRecordIfNone(any()) } returns 1L

        val result = eveningHandler.toggleSleep()

        assertEquals(TileToggleResult.CHANGED, result)
    }

    @Test
    fun sleepStartAt19CancelsNapReminderAndShowsSleepNotification() = runTest {
        val eveningInstant = LocalDate.of(2024, 6, 15)
            .atTime(LocalTime.of(19, 0))
            .atZone(zone)
            .toInstant()
        val eveningHandler = buildHandler(Clock.fixed(eveningInstant, zone))
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepRepository.startRecordIfNone(any()) } returns 1L

        eveningHandler.toggleSleep()

        verify { napReminderScheduler.cancel() }
        coVerify { sleepNotificationScheduler.show(any(), any(), any()) }
    }

    @Test
    fun sleepStartAt19RequestsFirestoreSleepSync() = runTest {
        val eveningInstant = LocalDate.of(2024, 6, 15)
            .atTime(LocalTime.of(19, 0))
            .atZone(zone)
            .toInstant()
        val eveningHandler = buildHandler(Clock.fixed(eveningInstant, zone))
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepRepository.startRecordIfNone(any()) } returns 1L

        eveningHandler.toggleSleep()

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
    }

    // ── Sleep start at midday (NAP) ────────────────────────────────────────────

    @Test
    fun sleepStartAtMiddayReceivesNapType() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns null
        val captured = slot<SleepRecord>()
        coEvery { sleepRepository.startRecordIfNone(capture(captured)) } returns 1L

        handler.toggleSleep()

        assertEquals(SleepType.NAP, captured.captured.sleepType)
    }

    // ── Sleep no-op ────────────────────────────────────────────────────────────

    @Test
    fun sleepNoOpWhenStartRecordIfNoneReturnsNull() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepRepository.startRecordIfNone(any()) } returns null

        val result = handler.toggleSleep()

        assertEquals(TileToggleResult.NO_OP, result)
    }

    @Test
    fun sleepNoOpDoesNotCallSideEffects() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepRepository.startRecordIfNone(any()) } returns null

        handler.toggleSleep()

        coVerify(exactly = 0) { sleepNotificationScheduler.show(any(), any(), any()) }
        coVerify(exactly = 0) { syncToFirestore(any()) }
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

        coVerify(exactly = 0) { sleepNotificationScheduler.show(any(), any(), any()) }
        coVerify(exactly = 0) { syncToFirestore(any()) }
        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    // ── Sleep widget failure after successful mutation ─────────────────────────

    @Test
    fun sleepWidgetFailureAfterMutationStillReturnsChanged() = runTest {
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepRepository.startRecordIfNone(any()) } returns 1L
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
        coEvery { breastfeedingRepository.startSessionIfNone(any()) } coAnswers {
            val index = ++callCount
            if (index == 1) {
                callOrder.add("first-entered")
                gate.await()
                callOrder.add("first-exited")
            } else {
                callOrder.add("second-entered")
            }
            index.toLong()
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
