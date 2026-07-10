package com.babytracker.ui.sleep

import android.content.Context
import app.cash.turbine.test
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.UpdateSleepEntryUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SleepHistoryViewModelTest {

    private lateinit var saveSleepEntry: SaveSleepEntryUseCase
    private lateinit var updateSleepEntry: UpdateSleepEntryUseCase
    private lateinit var sleepRepository: SleepRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var appContext: Context
    private lateinit var viewModel: SleepHistoryViewModel
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
        syncToFirestore = mockk()
        appContext = mockk()
        every { appContext.getString(R.string.error_sleep_overlap) } returns
            "This sleep overlaps with an existing record. Adjust the times to save this sleep."
        every { sleepRepository.getAllRecords() } returns flowOf(emptyList())
        every { sleepRepository.getRecentRecordsFlow(any()) } returns flowOf(emptyList())
        coJustRun { syncToFirestore(any()) }
    }


    @AfterEach
    fun tearDown() {
        // Cancel the ViewModel's scope while Dispatchers.Main is still the test dispatcher: stateIn
        // producers stubbed with never-completing flows otherwise outlive the test and dispatch into
        // Main after resetMain(), failing a LATER test with UncaughtExceptionsBeforeTest (#788).
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
    }

    private fun createViewModel() = SleepHistoryViewModel(
        saveSleepEntry,
        updateSleepEntry,
        sleepRepository,
        settingsRepository,
        SyncedWrite(syncToFirestore),
        appContext,
    )

    @Test
    fun `does not observe sleep records until historyByDateDesc is subscribed`() = runTest {
        val attachCount = AtomicInteger(0)
        val record = SleepRecord(
            id = 1L,
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(60),
            sleepType = SleepType.NAP,
        )
        every { sleepRepository.getRecentRecordsFlow(any()) } returns flow {
            attachCount.incrementAndGet()
            emit(listOf(record))
        }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, attachCount.get())

        // Draining until the grouped record surfaces guarantees the flowOn(Default) upstream has run,
        // synchronising the attach counter across the background dispatcher.
        viewModel.historyByDateDesc.test {
            var latest = awaitItem()
            while (latest.days.isEmpty()) {
                latest = awaitItem()
            }
            assertTrue(attachCount.get() >= 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `historyByDateDesc emits empty window when history is empty`() = runTest {
        viewModel = createViewModel()

        viewModel.historyByDateDesc.test {
            assertEquals(SleepHistoryWindow(), awaitItem())
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

        // getRecentRecordsFlow() is ordered start_time DESC; feed the fake in that same order.
        every { sleepRepository.getRecentRecordsFlow(any()) } returns flowOf(listOf(r4, r3, r2, r1))
        viewModel = createViewModel()

        viewModel.historyByDateDesc.test {
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()
            val grouped = awaitItem().days

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
        every { sleepRepository.getRecentRecordsFlow(any()) } returns historyFlow
        viewModel = createViewModel()

        viewModel.historyByDateDesc.test {
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()
            val first = awaitItem().days
            assertEquals(1, first.size)
            assertEquals(LocalDate.of(2024, 2, 1), first[0].first)

            // Newer record first, matching the start_time DESC ordering of getRecentRecordsFlow().
            historyFlow.value = listOf(added, initial)
            testDispatcher.scheduler.advanceUntilIdle()
            val second = awaitItem().days
            assertEquals(2, second.size)
            assertEquals(LocalDate.of(2024, 2, 2), second[0].first)
            assertEquals(LocalDate.of(2024, 2, 1), second[1].first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `historyByDateDesc groups a cross-midnight night sleep under its start date`() = runTest {
        val zone = ZoneId.systemDefault()
        // Night sleep starts on day 1 and ends after midnight on day 2.
        val nightSleep = SleepRecord(
            id = 1L,
            startTime = LocalDate.of(2024, 3, 1).atTime(23, 0).atZone(zone).toInstant(),
            endTime = LocalDate.of(2024, 3, 2).atTime(1, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        val nextDayNap = SleepRecord(
            id = 2L,
            startTime = LocalDate.of(2024, 3, 2).atTime(8, 0).atZone(zone).toInstant(),
            endTime = LocalDate.of(2024, 3, 2).atTime(9, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NAP,
        )
        // start_time DESC: the day-2 nap precedes the day-1 night sleep.
        every { sleepRepository.getRecentRecordsFlow(any()) } returns flowOf(listOf(nextDayNap, nightSleep))
        viewModel = createViewModel()

        viewModel.historyByDateDesc.test {
            awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()
            val grouped = awaitItem().days

            assertEquals(2, grouped.size)
            assertEquals(LocalDate.of(2024, 3, 2), grouped[0].first)
            assertEquals(listOf(2L), grouped[0].second.map { it.id })
            // The cross-midnight night sleep groups under its START date (day 1), not its end date.
            assertEquals(LocalDate.of(2024, 3, 1), grouped[1].first)
            assertEquals(listOf(1L), grouped[1].second.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `history window flags hasMore and grows on load more`() = runTest {
        val zone = ZoneId.systemDefault()
        // 51 rows: one past the first page of 50, so hasMore is derived without a count query.
        val records = List(51) { i ->
            SleepRecord(
                id = i + 1L,
                startTime = LocalDate.of(2024, 4, 1).atTime(10, 0).atZone(zone).toInstant().minusSeconds(3_600L * i),
                endTime = LocalDate.of(2024, 4, 1).atTime(10, 30).atZone(zone).toInstant().minusSeconds(3_600L * i),
                sleepType = SleepType.NAP,
            )
        }
        every { sleepRepository.getRecentRecordsFlow(51) } returns flowOf(records)
        every { sleepRepository.getRecentRecordsFlow(101) } returns flowOf(records)
        viewModel = createViewModel()

        viewModel.historyByDateDesc.test {
            var window = awaitItem()
            while (window.recordCount < 50) {
                window = awaitItem()
            }
            assertTrue(window.hasMore)

            // Second call arrives before the grown window emits: it must be ignored, otherwise the
            // limit reaches 150 and the unstubbed getRecentRecordsFlow(151) fails this test.
            viewModel.onLoadMoreHistory()
            viewModel.onLoadMoreHistory()

            while (window.recordCount != 51) {
                window = awaitItem()
            }
            assertFalse(window.hasMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onLoadMoreHistory is a no-op when the full history is already loaded`() = runTest {
        val zone = ZoneId.systemDefault()
        val records = List(3) { i ->
            SleepRecord(
                id = i + 1L,
                startTime = LocalDate.of(2024, 4, 1).atTime(10, 0).atZone(zone).toInstant().minusSeconds(3_600L * i),
                endTime = LocalDate.of(2024, 4, 1).atTime(10, 30).atZone(zone).toInstant().minusSeconds(3_600L * i),
                sleepType = SleepType.NAP,
            )
        }
        every { sleepRepository.getRecentRecordsFlow(51) } returns flowOf(records)
        viewModel = createViewModel()

        viewModel.historyByDateDesc.test {
            var window = awaitItem()
            while (window.recordCount == 0) {
                window = awaitItem()
            }
            assertEquals(3, window.recordCount)
            assertFalse(window.hasMore)

            // No-op: an unstubbed getRecentRecordsFlow(101) would fail this test if the limit grew.
            viewModel.onLoadMoreHistory()
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirmDelete deletes the record and syncs`() = runTest {
        coJustRun { sleepRepository.deleteRecord(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val record = SleepRecord(id = 7L, startTime = Instant.now(), sleepType = SleepType.NAP)

        viewModel.onDeleteRequest(record)
        assertEquals(record, viewModel.uiState.value.pendingDeleteRecord)
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
    fun `onSaveEntry updates the edited record and syncs`() = runTest {
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
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(23, 0))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { updateSleepEntry(eq(11L), any(), any(), any(), any()) }
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
        assertNull(viewModel.uiState.value.editingRecord)
        assertEquals(false, viewModel.uiState.value.showEntrySheet)
    }

    @Test
    fun `onSaveEntry rejects an edit that overlaps another record`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val editing = SleepRecord(
            id = 1L,
            startTime = today.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant(),
            endTime = today.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant(),
            sleepType = SleepType.NAP,
        )
        val other = SleepRecord(
            id = 2L,
            startTime = today.atTime(LocalTime.of(12, 0)).atZone(zone).toInstant(),
            endTime = today.atTime(LocalTime.of(13, 0)).atZone(zone).toInstant(),
            sleepType = SleepType.NAP,
        )
        every { sleepRepository.getAllRecords() } returns flowOf(listOf(editing, other))
        coJustRun { updateSleepEntry(any(), any(), any(), any(), any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEditRecord(editing)
        viewModel.onEntryDateChanged(today)
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START)
        viewModel.onConfirmTimePicker(LocalTime.of(12, 30))
        viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END)
        viewModel.onConfirmTimePicker(LocalTime.of(12, 45))
        viewModel.onSaveEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "This sleep overlaps with an existing record. Adjust the times to save this sleep.",
            viewModel.uiState.value.entryError,
        )
        coVerify(exactly = 0) { updateSleepEntry(any(), any(), any(), any(), any()) }
    }
}
