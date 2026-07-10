package com.babytracker.ui.sleep

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.baby.LogBabyEventUseCase
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.SharedSleepPredictionStream
import com.babytracker.domain.usecase.sleep.UpdateSleepEntryUseCase
import com.babytracker.manager.SleepSessionController
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.testutil.MainDispatcherExtension
import com.babytracker.util.formatTime12h
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import app.cash.turbine.test
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class SleepViewModelTest {

    private lateinit var saveSleepEntry: SaveSleepEntryUseCase
    private lateinit var updateSleepEntry: UpdateSleepEntryUseCase
    private lateinit var sleepRepository: SleepRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sleepSessionController: SleepSessionController
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var sharedSleepPrediction: SharedSleepPredictionStream
    private lateinit var logBabyEvent: LogBabyEventUseCase
    private lateinit var appContext: Context
    private lateinit var viewModel: SleepViewModel
    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)

    @BeforeEach
    fun setUp() {
        saveSleepEntry = mockk()
        updateSleepEntry = mockk()
        sleepRepository = mockk()
        settingsRepository = mockk()
        sleepSessionController = mockk()
        syncToFirestore = mockk()
        sharedSleepPrediction = mockk()
        logBabyEvent = mockk()
        coJustRun { logBabyEvent(any()) }
        appContext = mockk()
        every { appContext.getString(eq(R.string.sleep_awake_for), *anyVararg()) } answers {
            "Awake for ${secondArg<Array<Any?>>()[0]}"
        }
        every { appContext.getString(eq(R.string.sleep_ended_at), *anyVararg()) } answers {
            "Ended at ${secondArg<Array<Any?>>()[0]}"
        }
        every { appContext.getString(R.string.error_sleep_end_after_start) } returns
            "End time needs to be after start time. Adjust one time to save this sleep."
        every { appContext.getString(R.string.error_sleep_duration_too_long) } returns
            "Sleep duration is too long for this sleep type. Adjust one time to save this sleep."
        every { appContext.getString(R.string.error_sleep_overlap) } returns
            "This sleep overlaps with an existing record. Adjust the times to save this sleep."

        // getAllRecords() backs onSaveEntry's overlap validation; getRecentOrActiveRecordsFlow() backs
        // the subscription-scoped `history` observer that drives activeSleepSession/todayStats/lastSleepSummary.
        every { sleepRepository.getAllRecords() } returns flowOf(emptyList())
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(emptyList())
        every { settingsRepository.getWakeTime() } returns flowOf(null)
        every { sharedSleepPrediction.observe() } returns flowOf(SleepPredictionState.Unavailable("test"))
        coJustRun { syncToFirestore(any()) }
    }


    @AfterEach
    fun tearDown() {
        // Cancel the ViewModel's scope while Dispatchers.Main is still the test dispatcher: stateIn
        // producers stubbed with never-completing flows otherwise outlive the test and dispatch into
        // Main after resetMain(), failing a LATER test with UncaughtExceptionsBeforeTest (#788).
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
    }

    private fun createViewModel() = SleepViewModel(
        saveSleepEntry,
        updateSleepEntry,
        sleepRepository,
        settingsRepository,
        sleepSessionController,
        SyncedWrite(syncToFirestore),
        sharedSleepPrediction,
        logBabyEvent,
        appContext,
    )

    @Test
    fun `does not observe sleep records until a consumer subscribes`() = runTest {
        val attachCount = AtomicInteger(0)
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flow {
            attachCount.incrementAndGet()
            emit(emptyList())
        }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, attachCount.get())

        val job = launch { viewModel.activeSleepSession.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(attachCount.get() >= 1)
        job.cancel()
    }

    @Test
    fun `onStartRecord delegates to sleepSessionController start with the given sleep type`() = runTest {
        coEvery { sleepSessionController.start(SleepType.NAP) } returns
            SleepRecord(id = 1L, startTime = Instant.now(), sleepType = SleepType.NAP)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartRecord(SleepType.NAP)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { sleepSessionController.start(SleepType.NAP) }
    }

    @Test
    fun `onStopRecord delegates to sleepSessionController stop with the active session id`() = runTest {
        val inProgress = SleepRecord(
            id = 1L, startTime = Instant.now().minusSeconds(300), sleepType = SleepType.NAP
        )
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(inProgress))
        coEvery { sleepSessionController.stop(1L) } returns null
        viewModel = createViewModel()

        val collectJob = launch { viewModel.activeSleepSession.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { sleepSessionController.stop(1L) }
        collectJob.cancel()
    }

    @Test
    fun `onSaveEntry triggers sleep records sync on valid times`() = runTest {
        coEvery { saveSleepEntry(any(), any(), any()) } returns 1L
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(20, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(22, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
    }

    @Test
    fun `onSaveEntry surfaces the use case's own validation failure instead of crashing`() = runTest {
        // Simulates the use case's defense-in-depth guard tripping on a fresh read (e.g. a
        // concurrent write) after the VM's own pre-check already passed.
        coEvery { saveSleepEntry(any(), any(), any()) } throws
            IllegalArgumentException("OVERLAP")
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(20, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(22, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "This sleep overlaps with an existing record. Adjust the times to save this sleep.",
            viewModel.uiState.value.entryError,
        )
        assertEquals(true, viewModel.uiState.value.showEntrySheet)
        coVerify(exactly = 0) { syncToFirestore(any()) }
        coVerify(exactly = 0) { settingsRepository.setWakeTime(any()) }
    }

    @Test
    fun `onStopRecord does nothing when no active session`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { sleepSessionController.stop(any()) }
    }

    @Test
    fun `onSaveEntry does not sync when end time is not after start time`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(20, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(20, 0))
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
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(20, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(20, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

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
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(completed))
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
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(inProgress))
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
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(completed, inProgress))
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
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns historyFlow
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
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(older, latest))
        viewModel = createViewModel()

        val job = launch { viewModel.lastSleepSummary.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val summary = viewModel.lastSleepSummary.value

        assertTrue(summary is LastSleepSummaryState.Populated)
        summary as LastSleepSummaryState.Populated
        assertEquals(latest, summary.record)
        assertTrue(summary.awakeForLabel.startsWith("Awake for "))
        assertEquals("Ended at ${latestEnd.formatTime12h()}", summary.endedAtLabel)
        job.cancel()
    }

    @Test
    fun `activeSleepSession surfaces an open record that started before the bounded window`() = runTest {
        // A stuck/forgotten active sleep whose start predates the since-yesterday window: its start is
        // out of range, but getRecentOrActiveRecordsFlow's `end_time IS NULL` clause still returns it,
        // so it must remain visible and stoppable. Regression guard for the bounded-query change (#750).
        val oldActive = SleepRecord(
            id = 42L,
            startTime = Instant.now().minusSeconds(3 * 24 * 3600),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(oldActive))
        coEvery { sleepSessionController.stop(42L) } returns null
        viewModel = createViewModel()

        val job = launch { viewModel.activeSleepSession.collect {} }
        val summaryJob = launch { viewModel.lastSleepSummary.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(42L, viewModel.activeSleepSession.value?.id)
        // A completed summary must not show while a session is genuinely in progress.
        assertEquals(LastSleepSummaryState.Empty, viewModel.lastSleepSummary.value)

        viewModel.onStopRecord()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { sleepSessionController.stop(42L) }

        job.cancel()
        summaryJob.cancel()
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
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(completed, inProgress))
        viewModel = createViewModel()

        val job = launch { viewModel.lastSleepSummary.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LastSleepSummaryState.Empty, viewModel.lastSleepSummary.value)
        job.cancel()
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
        coJustRun { sleepRepository.deleteRecord(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(id = 7L, startTime = Instant.now(), sleepType = SleepType.NAP)

        viewModel.onDeleteRequest(record)
        viewModel.onConfirmDelete()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sleepRepository.deleteRecord(7L) }
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
            timezoneId = "UTC",
        )

        viewModel.onEditRecord(record)

        val state = viewModel.uiState.value
        assertEquals(true, state.showEntrySheet)
        assertEquals(record, state.editingRecord)
        assertEquals(SleepType.NIGHT_SLEEP, state.entryType)
        assertNotNull(state.entryDurationPreview)
        assertEquals(LocalDate.of(2024, 1, 15), state.entryDate)
    }

    @Test
    fun `onEntryDateChanged updates entryDate`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        val newDate = LocalDate.of(2024, 3, 10)
        viewModel.onEntryDateChanged(newDate)

        assertEquals(newDate, viewModel.uiState.value.entryDate)
    }

    @Test
    fun `onSaveEntry uses entryDate as start date`() = runTest {
        val originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            coEvery { saveSleepEntry(any(), any(), any()) } returns 1L
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAddEntryClick()
            viewModel.onEntryDateChanged(LocalDate.of(2024, 6, 5))
            viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
            viewModel.onConfirmTimePicker(LocalTime.of(14, 0))
            viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
            viewModel.onConfirmTimePicker(LocalTime.of(15, 30))
            viewModel.onSaveEntry()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify {
                saveSleepEntry(
                    startTime = Instant.parse("2024-06-05T14:00:00Z"),
                    endTime = Instant.parse("2024-06-05T15:30:00Z"),
                    type = SleepType.NAP,
                )
            }
        } finally {
            TimeZone.setDefault(originalZone)
        }
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
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(10, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(11, 0))
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

            viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
            viewModel.onConfirmTimePicker(LocalTime.of(21, 0))
            viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
            viewModel.onConfirmTimePicker(LocalTime.of(22, 0))
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
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(9, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(9, 0))
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
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(9, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(13, 1))
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
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(12, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(7, 1))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { updateSleepEntry(any(), any(), any(), any(), any()) }
        assertNotNull(viewModel.uiState.value.entryError)
    }

    @Test
    fun `onStartRecord with NAP type calls sleepSessionController start with NAP`() = runTest {
        coEvery { sleepSessionController.start(any()) } returns null
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartRecord(SleepType.NAP)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { sleepSessionController.start(SleepType.NAP) }
        coVerify(exactly = 0) { sleepSessionController.start(SleepType.NIGHT_SLEEP) }
    }

    @Test
    fun `onStartRecord with NIGHT_SLEEP type calls sleepSessionController start with NIGHT_SLEEP`() = runTest {
        coEvery { sleepSessionController.start(any()) } returns null
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartRecord(SleepType.NIGHT_SLEEP)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { sleepSessionController.start(SleepType.NIGHT_SLEEP) }
        coVerify(exactly = 0) { sleepSessionController.start(SleepType.NAP) }
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
        coVerify(exactly = 0) { sleepSessionController.start(any()) }
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
    fun `sleepPrediction flows through the shared stream`() = runTest {
        val state = SleepPredictionState.CurrentlySleeping
        every { sharedSleepPrediction.observe() } returns flowOf(state)
        viewModel = createViewModel()

        val job = launch { viewModel.sleepPrediction.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(state, viewModel.sleepPrediction.value)
        job.cancel()
    }

    @Test
    fun `onSaveEntry sets wake time when NIGHT_SLEEP ends today`() = runTest {
        coEvery { saveSleepEntry(any(), any(), any()) } returns 1L
        coJustRun { settingsRepository.setWakeTime(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onEntryTypeChanged(SleepType.NIGHT_SLEEP)
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(2, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(8, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setWakeTime(LocalTime.of(8, 0)) }
    }

    @Test
    fun `onSaveEntry does not set wake time when NIGHT_SLEEP ends on past day`() = runTest {
        coEvery { saveSleepEntry(any(), any(), any()) } returns 1L
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val twoDaysAgo = LocalDate.now().minusDays(2)
        viewModel.onAddEntryClick()
        viewModel.onEntryTypeChanged(SleepType.NIGHT_SLEEP)
        viewModel.onEntryDateChanged(twoDaysAgo)
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(22, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(7, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { settingsRepository.setWakeTime(any()) }
    }

    @Test
    fun `onSaveEntry does not set wake time when entry type is NAP`() = runTest {
        coEvery { saveSleepEntry(any(), any(), any()) } returns 1L
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(13, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(14, 30))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { settingsRepository.setWakeTime(any()) }
    }

    @Test
    fun `onSaveEntry blocks NIGHT_SLEEP save when it overlaps an existing record`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val existing = SleepRecord(
            id = 5L,
            startTime = today.minusDays(1).atTime(LocalTime.of(22, 0)).atZone(zone).toInstant(),
            endTime = today.atTime(LocalTime.of(6, 0)).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { sleepRepository.getAllRecords() } returns flowOf(listOf(existing))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onEntryTypeChanged(SleepType.NIGHT_SLEEP)
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(5, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(8, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "This sleep overlaps with an existing record. Adjust the times to save this sleep.",
            viewModel.uiState.value.entryError,
        )
        coVerify(exactly = 0) { saveSleepEntry(any(), any(), any()) }
    }

    @Test
    fun `onSaveEntry allows NIGHT_SLEEP save when it does not overlap existing record`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val existing = SleepRecord(
            id = 5L,
            startTime = today.minusDays(1).atTime(LocalTime.of(22, 0)).atZone(zone).toInstant(),
            endTime = today.atTime(LocalTime.of(4, 0)).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { sleepRepository.getAllRecords() } returns flowOf(listOf(existing))
        coEvery { saveSleepEntry(any(), any(), any()) } returns 2L
        coJustRun { settingsRepository.setWakeTime(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onEntryTypeChanged(SleepType.NIGHT_SLEEP)
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(5, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(8, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.entryError)
        coVerify { saveSleepEntry(any(), any(), eq(SleepType.NIGHT_SLEEP)) }
    }

    @Test
    fun `onSaveEntry does not flag overlap when editing the record itself`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val record = SleepRecord(
            id = 7L,
            startTime = today.minusDays(1).atTime(LocalTime.of(22, 0)).atZone(zone).toInstant(),
            endTime = today.atTime(LocalTime.of(6, 0)).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { sleepRepository.getAllRecords() } returns flowOf(listOf(record))
        coJustRun { updateSleepEntry(any(), any(), any(), any(), any()) }
        coJustRun { settingsRepository.setWakeTime(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEditRecord(record)
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(8, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.entryError)
        coVerify { updateSleepEntry(eq(7L), any(), any(), any(), any()) }
    }

    @Test
    fun `onSaveEntry does not update wake time when a later night sleep already exists today`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val laterRecord = SleepRecord(
            id = 8L,
            startTime = today.atTime(LocalTime.of(5, 0)).atZone(zone).toInstant(),
            endTime = today.atTime(LocalTime.of(8, 0)).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { sleepRepository.getAllRecords() } returns flowOf(listOf(laterRecord))
        coEvery { saveSleepEntry(any(), any(), any()) } returns 9L
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onEntryTypeChanged(SleepType.NIGHT_SLEEP)
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(0, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(4, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { settingsRepository.setWakeTime(any()) }
    }

    @Test
    fun `onSaveEntry updates wake time when new night sleep is the most recent ending today`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val earlierRecord = SleepRecord(
            id = 10L,
            startTime = today.atTime(LocalTime.of(0, 0)).atZone(zone).toInstant(),
            endTime = today.atTime(LocalTime.of(4, 0)).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { sleepRepository.getAllRecords() } returns flowOf(listOf(earlierRecord))
        coEvery { saveSleepEntry(any(), any(), any()) } returns 11L
        coJustRun { settingsRepository.setWakeTime(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()
        viewModel.onEntryTypeChanged(SleepType.NIGHT_SLEEP)
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(5, 0))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(8, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setWakeTime(LocalTime.of(8, 0)) }
    }

    // --- Bounded-window / day-rollover correctness for sleepTodayStats (issue #750) ---
    // The tracking screen feeds off a since-yesterday window, then narrows to "today" per emission via
    // this pure function. These pin that the narrowing stays correct for an arbitrary `today` — i.e. it
    // keeps producing exactly today's stats after a midnight rollover without depending on the window's
    // fixed lower bound.

    @Test
    fun `sleepTodayStats narrows a since-yesterday window to today only`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 7, 10)
        val yesterday = today.minusDays(1)
        // A yesterday nap that sits inside the bounded window but must NOT count toward today.
        val yesterdayNap = SleepRecord(
            id = 1L,
            startTime = yesterday.atTime(14, 0).atZone(zone).toInstant(),
            endTime = yesterday.atTime(15, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NAP,
        )
        val todayNapA = SleepRecord(
            id = 2L,
            startTime = today.atTime(9, 0).atZone(zone).toInstant(),
            endTime = today.atTime(10, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NAP,
        )
        val todayNapB = SleepRecord(
            id = 3L,
            startTime = today.atTime(13, 0).atZone(zone).toInstant(),
            endTime = today.atTime(13, 30).atZone(zone).toInstant(),
            sleepType = SleepType.NAP,
        )

        val stats = sleepTodayStats(listOf(todayNapB, todayNapA, yesterdayNap), today, zone)

        assertEquals(listOf(3L, 2L), stats.entries.map { it.id })
        assertEquals(2, stats.napCount)
        assertEquals(java.time.Duration.ofMinutes(90), stats.totalSleep)
    }

    @Test
    fun `sleepTodayStats counts a cross-midnight night sleep that started yesterday and ended today`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 7, 10)
        val yesterday = today.minusDays(1)
        // Started before yesterday-midnight, ended today: within the since-yesterday window, and its
        // night-sleep total must land on today via the end-date filter even though its start is yesterday.
        val crossMidnight = SleepRecord(
            id = 1L,
            startTime = yesterday.atTime(23, 0).atZone(zone).toInstant(),
            endTime = today.atTime(6, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )

        val stats = sleepTodayStats(listOf(crossMidnight), today, zone)

        // It started yesterday, so it is not one of "today's entries"...
        assertTrue(stats.entries.isEmpty())
        // ...but its 7h span counts toward last night's sleep, which ended today.
        assertEquals(java.time.Duration.ofHours(7), stats.nightSleep)
    }
}
