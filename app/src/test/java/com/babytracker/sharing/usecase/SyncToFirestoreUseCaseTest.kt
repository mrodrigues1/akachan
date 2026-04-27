package com.babytracker.sharing.usecase

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SyncToFirestoreUseCaseTest {

    private val sharingRepository: SharingRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val babyRepository: BabyRepository = mockk()
    private val breastfeedingRepository: BreastfeedingRepository = mockk()
    private val sleepRepository: SleepRepository = mockk()

    private lateinit var useCase: SyncToFirestoreUseCase

    private val shareCode = ShareCode("ABCD1234")
    private val mockBaby = Baby("Test", LocalDate.now())
    private val mockSession = BreastfeedingSession(
        id = 1L,
        startTime = Instant.now(),
        startingSide = BreastSide.LEFT,
    )
    private val mockSleep = SleepRecord(
        id = 1L,
        startTime = Instant.now(),
        sleepType = SleepType.NAP,
    )

    @BeforeEach
    fun setUp() {
        useCase = SyncToFirestoreUseCase(
            sharingRepository,
            settingsRepository,
            babyRepository,
            breastfeedingRepository,
            sleepRepository,
        )
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        every { babyRepository.getBabyProfile() } returns flowOf(mockBaby)
        coEvery { breastfeedingRepository.getRecentSessions(any()) } returns listOf(mockSession)
        coEvery { sleepRepository.getRecentRecords(any()) } returns listOf(mockSleep)
        coEvery { sharingRepository.syncFullSnapshot(any(), any()) } just Runs
        coEvery { sharingRepository.syncSessions(any(), any()) } just Runs
        coEvery { sharingRepository.syncSleepRecords(any(), any()) } just Runs
        coEvery { sharingRepository.syncBaby(any(), any()) } just Runs
    }

    @Test
    fun noOpWhenAppModeIsNotPrimary() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.NONE)

        useCase()

        coVerify(exactly = 0) { sharingRepository.syncFullSnapshot(any(), any()) }
    }

    @Test
    fun noOpWhenShareCodeIsNull() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns flowOf(null)

        useCase()

        coVerify(exactly = 0) { sharingRepository.syncFullSnapshot(any(), any()) }
    }

    @Test
    fun fullSyncCallsSyncFullSnapshot() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)

        useCase(SyncType.FULL)

        coVerify { sharingRepository.syncFullSnapshot(shareCode, any()) }
    }

    @Test
    fun sessionsSyncCallsSyncSessions() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)

        useCase(SyncType.SESSIONS)

        coVerify { sharingRepository.syncSessions(shareCode, any()) }
        coVerify(exactly = 0) { sharingRepository.syncFullSnapshot(any(), any()) }
    }

    @Test
    fun sleepRecordsSyncCallsSyncSleepRecords() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)

        useCase(SyncType.SLEEP_RECORDS)

        coVerify { sharingRepository.syncSleepRecords(shareCode, any()) }
    }

    @Test
    fun babySyncCallsSyncBaby() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)

        useCase(SyncType.BABY)

        coVerify { sharingRepository.syncBaby(shareCode, any()) }
    }
}
