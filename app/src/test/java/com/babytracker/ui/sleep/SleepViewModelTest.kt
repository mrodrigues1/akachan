package com.babytracker.ui.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.StartSleepRecordUseCase
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.manager.SleepNotificationScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SleepViewModelTest {

    private lateinit var saveSleepEntry: SaveSleepEntryUseCase
    private lateinit var getSleepHistory: GetSleepHistoryUseCase
    private lateinit var generateSchedule: GenerateSleepScheduleUseCase
    private lateinit var getBabyProfile: GetBabyProfileUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var startRecord: StartSleepRecordUseCase
    private lateinit var stopRecord: StopSleepRecordUseCase
    private lateinit var sleepNotificationScheduler: SleepNotificationScheduler
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var viewModel: SleepViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        saveSleepEntry = mockk()
        getSleepHistory = mockk()
        generateSchedule = mockk()
        getBabyProfile = mockk()
        settingsRepository = mockk()
        startRecord = mockk()
        stopRecord = mockk()
        sleepNotificationScheduler = mockk()
        syncToFirestore = mockk()

        every { getSleepHistory() } returns flowOf(emptyList())
        every { settingsRepository.getWakeTime() } returns flowOf(null)
        every { getBabyProfile() } returns flowOf(null)
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
        getSleepHistory,
        generateSchedule,
        getBabyProfile,
        settingsRepository,
        startRecord,
        stopRecord,
        sleepNotificationScheduler,
        syncToFirestore,
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

        coJustRun { stopRecord(any()) }
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
}
