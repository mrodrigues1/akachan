package com.babytracker.ui.sleep

import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.sleep.DeleteSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.StartSleepRecordUseCase
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.domain.usecase.sleep.UpdateSleepEntryUseCase
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.SleepNotificationScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.util.formatTime12h
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class SleepViewModelTest {

    private lateinit var saveSleepEntry: SaveSleepEntryUseCase
    private lateinit var updateSleepEntry: UpdateSleepEntryUseCase
    private lateinit var deleteSleepEntry: DeleteSleepEntryUseCase
    private lateinit var getSleepHistory: GetSleepHistoryUseCase
    private lateinit var generateSchedule: GenerateSleepScheduleUseCase
    private lateinit var getBabyProfile: GetBabyProfileUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var startRecord: StartSleepRecordUseCase
    private lateinit var stopRecord: StopSleepRecordUseCase
    private lateinit var sleepNotificationScheduler: SleepNotificationScheduler
    private lateinit var napReminderScheduler: NapReminderScheduler
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var predictSleepWindow: PredictSleepWindowUseCase
    private lateinit var viewModel: SleepViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        saveSleepEntry = mockk()
        updateSleepEntry = mockk()
        deleteSleepEntry = mockk()
        getSleepHistory = mockk()
        generateSchedule = mockk()
        getBabyProfile = mockk()
        settingsRepository = mockk()
        startRecord = mockk()
        stopRecord = mockk()
        sleepNotificationScheduler = mockk()
        napReminderScheduler = mockk(relaxed = true)
        syncToFirestore = mockk()
        predictSleepWindow = mockk()

        every { getSleepHistory() } returns flowOf(emptyList())
        every { settingsRepository.getWakeTime() } returns flowOf(null)
        every { getBabyProfile() } returns flowOf(null)
        every { settingsRepository.getNapReminderEnabled() } returns flowOf(false)
        every { settingsRepository.getNapReminderDelayMinutes() } returns flowOf(60)
        every { predictSleepWindow() } returns flowOf(SleepPredictionState.Unavailable("test"))
        coJustRun { syncToFirestore(any()) }
        coJustRun { sleepNotificationScheduler.show(any(), any(), any()) }
        every { sleepNotificationScheduler.cancel() } returns Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SleepViewModel(
        saveSleepEntry,
        updateSleepEntry,
        deleteSleepEntry,
        getSleepHistory,
        generateSchedule,
        getBabyProfile,
        settingsRepository,
        startRecord,
        stopRecord,
        sleepNotificationScheduler,
        napReminderScheduler,
        syncToFirestore,
        predictSleepWindow,
    )

    @Test
    fun `onStartRecord triggers sleep records sync`() = runTest {
        val record = SleepRecord(id = 1L, startTime = Instant.now(), sleepType = SleepType.NAP)
        coEvery { startRecord(SleepType.NAP) } returns record
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartRecord(SleepType.NAP)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
    }

    @Test
    fun `onStopRecord triggers sleep records sync`() = runTest {
        val inProgress = SleepRecord(
            id = 1L, startTime = Instant.now().minusSeconds(300), sleepType = SleepType.NAP
        )
        every { getSleepHistory() } returns flowOf(listOf(inProgress))
        viewModel = createViewModel()

        val collectJob = launch { viewModel.activeSleepSession.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { stopRecord(any()) } returns null
        viewModel.onStopRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
        collectJob.cancel()
    }

    @Test
    fun `onSaveEntry triggers sleep records sync on valid times`() = runTest {
        coEvery { saveSleepEntry(any(), any(), any()) } returns 1L
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onEntryStartTimeChanged(LocalTime.of(20, 0))
        viewModel.onEntryEndTimeChanged(LocalTime.of(22, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
    }

    @Test
    fun `onStopRecord does nothing when no active session`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { stopRecord(any()) }
        coVerify(exactly = 0) { syncToFirestore(any()) }
    }

    @Test
    fun `onSaveEntry does not sync when end time is not after start time`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onEntryStartTimeChanged(LocalTime.of(20, 0))
        viewModel.onEntryEndTimeChanged(LocalTime.of(20, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { saveSleepEntry(any(), any(), any()) }
        coVerify(exactly = 0) { syncToFirestore(any()) }
    }

    @Test
    fun `onSaveEntry explains how to fix matching start and end times`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onEntryStartTimeChanged(LocalTime.of(20, 0))
        viewModel.onEntryEndTimeChanged(LocalTime.of(20, 0))
        viewModel.onSaveEntry()

        assertEquals(
            "End time needs to be after start time. Adjust one time to save this sleep.",
            viewModel.uiState.value.entryError
        )
    }

    @Test
    fun `onAddEntryClick starts with a one hour sleep duration ready to save`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()

        assertEquals(java.time.Duration.ofHours(1), viewModel.uiState.value.entryDurationPreview)
    }

    @Test
    fun `activeSleepSession is null when all records are completed`() = runTest {
        val completed = SleepRecord(
            id = 1L,
            startTime = Instant.now().minusSeconds(3600),
            endTime = Instant.now(),
            sleepType = SleepType.NAP,
        )
        every { getSleepHistory() } returns flowOf(listOf(completed))
        viewModel = createViewModel()

        viewModel.activeSleepSession.test {
            testDispatcher.scheduler.advanceUntilIdle()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activeSleepSession returns in-progress record when endTime is null`() = runTest {
        val inProgress = SleepRecord(
            id = 2L,
            startTime = Instant.now().minusSeconds(1800),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { getSleepHistory() } returns flowOf(listOf(inProgress))
        viewModel = createViewModel()

        viewModel.activeSleepSession.test {
            assertNull(awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val session = awaitItem()
            assertEquals(2L, session?.id)
            assertEquals(SleepType.NIGHT_SLEEP, session?.sleepType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activeSleepSession returns in-progress record when mixed with completed records`() = runTest {
        val completed = SleepRecord(
            id = 1L,
            startTime = Instant.now().minusSeconds(7200),
            endTime = Instant.now().minusSeconds(3600),
            sleepType = SleepType.NAP,
        )
        val inProgress = SleepRecord(
            id = 2L,
            startTime = Instant.now().minusSeconds(600),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { getSleepHistory() } returns flowOf(listOf(completed, inProgress))
        viewModel = createViewModel()

        viewModel.activeSleepSession.test {
            assertNull(awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(2L, awaitItem()?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activeSleepSession updates reactively when session transitions from in-progress to complete`() = runTest {
        val inProgress = SleepRecord(
            id = 3L,
            startTime = Instant.now().minusSeconds(1800),
            endTime = null,
            sleepType = SleepType.NAP,
        )
        val completed = inProgress.copy(endTime = Instant.now())
        val historyFlow = MutableStateFlow(listOf(inProgress))
        every { getSleepHistory() } returns historyFlow
        viewModel = createViewModel()

        viewModel.activeSleepSession.test {
            assertNull(awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(3L, awaitItem()?.id)

            historyFlow.value = listOf(completed)
            testDispatcher.scheduler.advanceUntilIdle()
            assertNull(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lastSleepSummary shows latest completed sleep end context`() = runTest {
        val latestEnd = Instant.now().minusSeconds(3660)
        val older = SleepRecord(
            id = 1L,
            startTime = latestEnd.minusSeconds(7200),
            endTime = latestEnd.minusSeconds(3600),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        val latest = SleepRecord(
            id = 2L,
            startTime = latestEnd.minusSeconds(1800),
            endTime = latestEnd,
            sleepType = SleepType.NAP,
        )
        every { getSleepHistory() } returns flowOf(listOf(older, latest))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val summary = viewModel.uiState.value.lastSleepSummary

        assertTrue(summary is LastSleepSummaryState.Populated)
        summary as LastSleepSummaryState.Populated
        assertEquals(latest, summary.record)
        assertTrue(summary.awakeForLabel.startsWith("Awake for "))
        assertEquals("Ended at ${latestEnd.formatTime12h()}", summary.endedAtLabel)
    }

    @Test
    fun `lastSleepSummary is empty while a sleep session is active`() = runTest {
        val completed = SleepRecord(
            id = 1L,
            startTime = Instant.now().minusSeconds(7200),
            endTime = Instant.now().minusSeconds(3600),
            sleepType = SleepType.NAP,
        )
        val inProgress = SleepRecord(
            id = 2L,
            startTime = Instant.now().minusSeconds(600),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { getSleepHistory() } returns flowOf(listOf(completed, inProgress))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LastSleepSummaryState.Empty, viewModel.uiState.value.lastSleepSummary)
    }

    @Test
    fun `onDeleteRequest sets pendingDeleteRecord`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(id = 5L, startTime = Instant.now(), sleepType = SleepType.NAP)

        viewModel.onDeleteRequest(record)

        assertEquals(record, viewModel.uiState.value.pendingDeleteRecord)
    }

    @Test
    fun `onDismissDelete clears pendingDeleteRecord`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(id = 5L, startTime = Instant.now(), sleepType = SleepType.NAP)

        viewModel.onDeleteRequest(record)
        viewModel.onDismissDelete()

        assertNull(viewModel.uiState.value.pendingDeleteRecord)
    }

    @Test
    fun `onConfirmDelete calls deleteSleepEntry and syncs`() = runTest {
        coJustRun { deleteSleepEntry(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(id = 7L, startTime = Instant.now(), sleepType = SleepType.NAP)

        viewModel.onDeleteRequest(record)
        viewModel.onConfirmDelete()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { deleteSleepEntry(7L) }
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
        assertNull(viewModel.uiState.value.pendingDeleteRecord)
    }

    @Test
    fun `onEditRecord opens sheet pre-populated with record values`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(
            id = 9L,
            startTime = Instant.parse("2024-01-15T20:00:00Z"),
            endTime = Instant.parse("2024-01-15T22:00:00Z"),
            sleepType = SleepType.NIGHT_SLEEP,
        )

        viewModel.onEditRecord(record)

        val state = viewModel.uiState.value
        assertEquals(true, state.showEntrySheet)
        assertEquals(record, state.editingRecord)
        assertEquals(SleepType.NIGHT_SLEEP, state.entryType)
        assertNotNull(state.entryDurationPreview)
    }

    @Test
    fun `onSaveEntry calls updateSleepEntry when editing`() = runTest {
        coJustRun { updateSleepEntry(any(), any(), any(), any(), any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(
            id = 11L,
            startTime = Instant.now().minusSeconds(7200),
            endTime = Instant.now().minusSeconds(3600),
            sleepType = SleepType.NAP,
        )

        viewModel.onEditRecord(record)
        viewModel.onEntryStartTimeChanged(LocalTime.of(10, 0))
        viewModel.onEntryEndTimeChanged(LocalTime.of(11, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { updateSleepEntry(eq(11L), any(), any(), any(), any()) }
        coVerify(exactly = 0) { saveSleepEntry(any(), any(), any()) }
        assertNull(viewModel.uiState.value.editingRecord)
    }

    @Test
    fun `onSaveEntry edits in record timezone when it differs from device timezone`() = runTest {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        try {
            coJustRun { updateSleepEntry(any(), any(), any(), any(), any()) }
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val record = SleepRecord(
                id = 11L,
                startTime = Instant.parse("2024-01-15T20:00:00Z"),
                endTime = Instant.parse("2024-01-15T22:00:00Z"),
                sleepType = SleepType.NAP,
                timezoneId = "UTC",
            )

            viewModel.onEditRecord(record)
            assertEquals(LocalTime.of(20, 0), viewModel.uiState.value.entryStartTime)

            viewModel.onEntryStartTimeChanged(LocalTime.of(21, 0))
            viewModel.onEntryEndTimeChanged(LocalTime.of(22, 0))
            viewModel.onSaveEntry()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify {
                updateSleepEntry(
                    id = 11L,
                    startTime = Instant.parse("2024-01-15T21:00:00Z"),
                    endTime = Instant.parse("2024-01-15T22:00:00Z"),
                    type = SleepType.NAP,
                    timezoneId = "UTC",
                )
            }
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `onSaveEntry preserves overnight date span when editing`() = runTest {
        coJustRun { updateSleepEntry(any(), any(), any(), any(), any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(
            id = 12L,
            startTime = Instant.parse("2024-01-15T23:00:00Z"),
            endTime = Instant.parse("2024-01-16T01:00:00Z"),
            sleepType = SleepType.NIGHT_SLEEP,
            timezoneId = "UTC",
        )

        viewModel.onEditRecord(record)
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            updateSleepEntry(
                id = 12L,
                startTime = Instant.parse("2024-01-15T23:00:00Z"),
                endTime = Instant.parse("2024-01-16T01:00:00Z"),
                type = SleepType.NIGHT_SLEEP,
                timezoneId = "UTC",
            )
        }
    }

    @Test
    fun `onSaveEntry rejects equal start and end when editing`() = runTest {
        coJustRun { updateSleepEntry(any(), any(), any(), any(), any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(
            id = 13L,
            startTime = Instant.parse("2024-01-15T09:00:00Z"),
            endTime = Instant.parse("2024-01-15T10:00:00Z"),
            sleepType = SleepType.NAP,
            timezoneId = "UTC",
        )

        viewModel.onEditRecord(record)
        viewModel.onEntryStartTimeChanged(LocalTime.of(9, 0))
        viewModel.onEntryEndTimeChanged(LocalTime.of(9, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { updateSleepEntry(any(), any(), any(), any(), any()) }
        assertNotNull(viewModel.uiState.value.entryError)
    }

    @Test
    fun `onSaveEntry rejects overlong edited nap`() = runTest {
        coJustRun { updateSleepEntry(any(), any(), any(), any(), any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(
            id = 14L,
            startTime = Instant.parse("2024-01-15T09:00:00Z"),
            endTime = Instant.parse("2024-01-15T10:00:00Z"),
            sleepType = SleepType.NAP,
            timezoneId = "UTC",
        )

        viewModel.onEditRecord(record)
        viewModel.onEntryStartTimeChanged(LocalTime.of(9, 0))
        viewModel.onEntryEndTimeChanged(LocalTime.of(13, 1))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { updateSleepEntry(any(), any(), any(), any(), any()) }
        assertNotNull(viewModel.uiState.value.entryError)
    }

    @Test
    fun `onSaveEntry rejects overlong edited night sleep`() = runTest {
        coJustRun { updateSleepEntry(any(), any(), any(), any(), any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(
            id = 15L,
            startTime = Instant.parse("2024-01-15T18:00:00Z"),
            endTime = Instant.parse("2024-01-16T06:00:00Z"),
            sleepType = SleepType.NIGHT_SLEEP,
            timezoneId = "UTC",
        )

        viewModel.onEditRecord(record)
        viewModel.onEntryStartTimeChanged(LocalTime.of(12, 0))
        viewModel.onEntryEndTimeChanged(LocalTime.of(7, 1))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { updateSleepEntry(any(), any(), any(), any(), any()) }
        assertNotNull(viewModel.uiState.value.entryError)
    }

    @Test
    fun `onStartRecord with NAP type calls startRecord with NAP`() = runTest {
        val record = SleepRecord(id = 1L, startTime = Instant.now(), sleepType = SleepType.NAP)
        coEvery { startRecord(SleepType.NAP) } returns record
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartRecord(SleepType.NAP)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { startRecord(SleepType.NAP) }
        coVerify(exactly = 0) { startRecord(SleepType.NIGHT_SLEEP) }
    }

    @Test
    fun `onStartRecord with NIGHT_SLEEP type calls startRecord with NIGHT_SLEEP`() = runTest {
        val record = SleepRecord(id = 2L, startTime = Instant.now(), sleepType = SleepType.NIGHT_SLEEP)
        coEvery { startRecord(SleepType.NIGHT_SLEEP) } returns record
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartRecord(SleepType.NIGHT_SLEEP)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { startRecord(SleepType.NIGHT_SLEEP) }
        coVerify(exactly = 0) { startRecord(SleepType.NAP) }
    }

    @Test
    fun `onStartRecord schedules notification with record data`() = runTest {
        val record = SleepRecord(id = 3L, startTime = Instant.now(), sleepType = SleepType.NAP)
        coEvery { startRecord(SleepType.NAP) } returns record
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartRecord(SleepType.NAP)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sleepNotificationScheduler.show(record.id, record.sleepType, record.startTime) }
    }

    @Test
    fun `onStartRecord NIGHT_SLEEP triggers sleep records sync`() = runTest {
        val record = SleepRecord(id = 4L, startTime = Instant.now(), sleepType = SleepType.NIGHT_SLEEP)
        coEvery { startRecord(SleepType.NIGHT_SLEEP) } returns record
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartRecord(SleepType.NIGHT_SLEEP)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
    }

    @Test
    fun `onAddEntryClick opens sheet with no editing record and does not start a live session`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.showEntrySheet)
        assertNull(state.editingRecord)
        coVerify(exactly = 0) { startRecord(any()) }
    }

    @Test
    fun `isRegressionExpanded defaults to true`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isRegressionExpanded)
    }

    @Test
    fun `onToggleRegression flips isRegressionExpanded from true to false`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onToggleRegression()

        assertEquals(false, viewModel.uiState.value.isRegressionExpanded)
    }

    @Test
    fun `onToggleRegression twice restores isRegressionExpanded to true`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onToggleRegression()
        viewModel.onToggleRegression()

        assertEquals(true, viewModel.uiState.value.isRegressionExpanded)
    }

    @Test
    fun `historyByDateDesc emits empty list when history is empty`() = runTest {
        viewModel = createViewModel()

        viewModel.historyByDateDesc.test {
            assertEquals(emptyList<Pair<LocalDate, List<SleepRecord>>>(), awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `historyByDateDesc groups records by local date sorted descending`() = runTest {
        val zone = ZoneId.systemDefault()
        val day1Morning = LocalDate.of(2024, 1, 10).atTime(8, 0).atZone(zone).toInstant()
        val day1Evening = LocalDate.of(2024, 1, 10).atTime(20, 0).atZone(zone).toInstant()
        val day2 = LocalDate.of(2024, 1, 11).atTime(9, 0).atZone(zone).toInstant()
        val day3 = LocalDate.of(2024, 1, 12).atTime(10, 0).atZone(zone).toInstant()

        val r1 = SleepRecord(id = 1L, startTime = day1Morning, endTime = day1Morning.plusSeconds(1800), sleepType = SleepType.NAP)
        val r2 = SleepRecord(id = 2L, startTime = day1Evening, endTime = day1Evening.plusSeconds(1800), sleepType = SleepType.NAP)
        val r3 = SleepRecord(id = 3L, startTime = day2, endTime = day2.plusSeconds(1800), sleepType = SleepType.NAP)
        val r4 = SleepRecord(id = 4L, startTime = day3, endTime = day3.plusSeconds(1800), sleepType = SleepType.NIGHT_SLEEP)

        every { getSleepHistory() } returns flowOf(listOf(r1, r2, r3, r4))
        viewModel = createViewModel()

        viewModel.historyByDateDesc.test {
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()
            val grouped = awaitItem()

            assertEquals(3, grouped.size)
            assertEquals(LocalDate.of(2024, 1, 12), grouped[0].first)
            assertEquals(LocalDate.of(2024, 1, 11), grouped[1].first)
            assertEquals(LocalDate.of(2024, 1, 10), grouped[2].first)
            assertEquals(2, grouped[2].second.size)
            assertEquals(listOf(1L, 2L), grouped[2].second.map { it.id }.sorted())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `historyByDateDesc updates reactively when history flow emits`() = runTest {
        val zone = ZoneId.systemDefault()
        val initial = SleepRecord(
            id = 1L,
            startTime = LocalDate.of(2024, 2, 1).atTime(10, 0).atZone(zone).toInstant(),
            endTime = LocalDate.of(2024, 2, 1).atTime(11, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NAP,
        )
        val added = SleepRecord(
            id = 2L,
            startTime = LocalDate.of(2024, 2, 2).atTime(10, 0).atZone(zone).toInstant(),
            endTime = LocalDate.of(2024, 2, 2).atTime(11, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NAP,
        )
        val historyFlow = MutableStateFlow(listOf(initial))
        every { getSleepHistory() } returns historyFlow
        viewModel = createViewModel()

        viewModel.historyByDateDesc.test {
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals(LocalDate.of(2024, 2, 1), first[0].first)

            historyFlow.value = listOf(initial, added)
            testDispatcher.scheduler.advanceUntilIdle()
            val second = awaitItem()
            assertEquals(2, second.size)
            assertEquals(LocalDate.of(2024, 2, 2), second[0].first)
            assertEquals(LocalDate.of(2024, 2, 1), second[1].first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activeTimePicker defaults to null`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeTimePicker)
    }

    @Test
    fun `onShowTimePicker WAKE sets activeTimePicker to WAKE`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onShowTimePicker(SleepTimePickerTarget.WAKE)

        assertEquals(SleepTimePickerTarget.WAKE, viewModel.uiState.value.activeTimePicker)
    }

    @Test
    fun `onDismissTimePicker clears activeTimePicker`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onDismissTimePicker()

        assertNull(viewModel.uiState.value.activeTimePicker)
    }

    @Test
    fun `onConfirmTimePicker WAKE applies wake time and dismisses`() = runTest {
        coJustRun { settingsRepository.setWakeTime(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onShowTimePicker(SleepTimePickerTarget.WAKE)
        viewModel.onConfirmTimePicker(LocalTime.of(7, 30))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeTimePicker)
        coVerify { settingsRepository.setWakeTime(LocalTime.of(7, 30)) }
    }

    @Test
    fun `onConfirmTimePicker ENTRY_START updates entryStartTime and dismisses`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(13, 15))

        val state = viewModel.uiState.value
        assertNull(state.activeTimePicker)
        assertEquals(LocalTime.of(13, 15), state.entryStartTime)
    }

    @Test
    fun `onConfirmTimePicker ENTRY_END updates entryEndTime and dismisses`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(14, 45))

        val state = viewModel.uiState.value
        assertNull(state.activeTimePicker)
        assertEquals(LocalTime.of(14, 45), state.entryEndTime)
    }

    @Test
    fun `onConfirmTimePicker is a no-op when no picker is active`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val initialStart = viewModel.uiState.value.entryStartTime
        val initialEnd = viewModel.uiState.value.entryEndTime

        viewModel.onConfirmTimePicker(LocalTime.of(3, 0))

        val state = viewModel.uiState.value
        assertEquals(initialStart, state.entryStartTime)
        assertEquals(initialEnd, state.entryEndTime)
        coVerify(exactly = 0) { settingsRepository.setWakeTime(any()) }
    }

    @Test
    fun `onDismissSheet clears editingRecord`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(id = 13L, startTime = Instant.now(), sleepType = SleepType.NAP)

        viewModel.onEditRecord(record)
        viewModel.onDismissSheet()

        assertNull(viewModel.uiState.value.editingRecord)
        assertEquals(false, viewModel.uiState.value.showEntrySheet)
    }

    @Test
    fun `onStopRecord schedules nap reminder when NAP type and reminder enabled`() = runTest {
        val inProgress = SleepRecord(id = 1L, startTime = Instant.now().minusSeconds(1800), sleepType = SleepType.NAP)
        every { getSleepHistory() } returns flowOf(listOf(inProgress))
        coEvery { stopRecord(1L) } returns inProgress
        every { settingsRepository.getNapReminderEnabled() } returns flowOf(true)
        every { settingsRepository.getNapReminderDelayMinutes() } returns flowOf(10)
        viewModel = createViewModel()

        val collectJob = launch { viewModel.activeSleepSession.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { napReminderScheduler.schedule(any(), 10) }
        collectJob.cancel()
    }

    @Test
    fun `onStopRecord does not schedule nap reminder when NAP type but reminder disabled`() = runTest {
        val inProgress = SleepRecord(id = 1L, startTime = Instant.now().minusSeconds(1800), sleepType = SleepType.NAP)
        every { getSleepHistory() } returns flowOf(listOf(inProgress))
        coEvery { stopRecord(1L) } returns inProgress
        every { settingsRepository.getNapReminderEnabled() } returns flowOf(false)
        viewModel = createViewModel()

        val collectJob = launch { viewModel.activeSleepSession.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { napReminderScheduler.schedule(any(), any()) }
        collectJob.cancel()
    }

    @Test
    fun `onStopRecord does not schedule nap reminder when NIGHT_SLEEP type`() = runTest {
        val inProgress = SleepRecord(id = 2L, startTime = Instant.now().minusSeconds(28800), sleepType = SleepType.NIGHT_SLEEP)
        every { getSleepHistory() } returns flowOf(listOf(inProgress))
        coEvery { stopRecord(2L) } returns inProgress
        viewModel = createViewModel()

        val collectJob = launch { viewModel.activeSleepSession.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { napReminderScheduler.schedule(any(), any()) }
        collectJob.cancel()
    }

    @Test
    fun `onStopRecord does not schedule nap reminder when stopRecord returns null`() = runTest {
        val inProgress = SleepRecord(id = 1L, startTime = Instant.now().minusSeconds(1800), sleepType = SleepType.NAP)
        every { getSleepHistory() } returns flowOf(listOf(inProgress))
        coEvery { stopRecord(1L) } returns null
        every { settingsRepository.getNapReminderEnabled() } returns flowOf(true)
        viewModel = createViewModel()

        val collectJob = launch { viewModel.activeSleepSession.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { napReminderScheduler.schedule(any(), any()) }
        collectJob.cancel()
    }

    @Test
    fun `onStartRecord always cancels pending nap reminder regardless of sleep type`() = runTest {
        val record = SleepRecord(id = 1L, startTime = Instant.now(), sleepType = SleepType.NIGHT_SLEEP)
        coEvery { startRecord(SleepType.NIGHT_SLEEP) } returns record
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartRecord(SleepType.NIGHT_SLEEP)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { napReminderScheduler.cancel() }
    }

    @Test
    fun `sleepPrediction flowsThroughToUiState`() = runTest {
        val state = SleepPredictionState.CurrentlySleeping
        every { predictSleepWindow() } returns flowOf(state)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(state, viewModel.uiState.value.sleepPrediction)
    }
}
