package com.babytracker.sharing.usecase

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    private val inventoryRepository: InventoryRepository = mockk()
    private val bottleFeedRepository: BottleFeedRepository = mockk()
    private val predictSleepWindow: PredictSleepWindowUseCase = mockk()
    private val fixedNow = Instant.parse("2026-05-16T10:00:00Z")

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
    private val mockBottleFeed = BottleFeed(
        id = 1L,
        timestamp = Instant.parse("2026-05-16T09:00:00Z"),
        volumeMl = 120,
        type = FeedType.FORMULA,
        createdAt = Instant.parse("2026-05-16T09:00:00Z"),
    )

    @BeforeEach
    fun setUp() {
        useCase = SyncToFirestoreUseCase(
            sharingRepository,
            settingsRepository,
            SnapshotSources(
                babyRepository,
                breastfeedingRepository,
                sleepRepository,
                inventoryRepository,
                bottleFeedRepository,
                predictSleepWindow,
            ),
        ) { fixedNow }
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        every { settingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)
        every { babyRepository.getBabyProfile() } returns flowOf(mockBaby)
        coEvery { breastfeedingRepository.getRecentSessions(any()) } returns listOf(mockSession)
        coEvery { sleepRepository.getRecentRecords(any()) } returns listOf(mockSleep)
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary.Empty
        every { bottleFeedRepository.getAll() } returns flowOf(listOf(mockBottleFeed))
        coEvery { sharingRepository.syncFullSnapshot(any(), any()) } just Runs
        coEvery { sharingRepository.syncSessions(any(), any()) } just Runs
        coEvery { sharingRepository.syncSleepRecords(any(), any()) } just Runs
        coEvery { sharingRepository.syncBaby(any(), any()) } just Runs
        coEvery { sharingRepository.syncInventory(any(), any()) } just Runs
        coEvery { sharingRepository.syncBottleFeeds(any(), any()) } just Runs
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

    @Test
    fun bottleFeedsSyncCallsSyncBottleFeeds() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        val feeds = slot<List<com.babytracker.sharing.domain.model.BottleFeedSnapshot>>()
        coEvery { sharingRepository.syncBottleFeeds(any(), capture(feeds)) } just Runs

        useCase(SyncType.BOTTLE_FEEDS)

        coVerify { sharingRepository.syncBottleFeeds(shareCode, any()) }
        coVerify(exactly = 0) { sharingRepository.syncFullSnapshot(any(), any()) }
        assertEquals(120, feeds.captured.single().volumeMl)
        assertEquals("FORMULA", feeds.captured.single().type)
    }

    @Test
    fun fullSyncIncludesBottleFeeds() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        val snapshot = slot<ShareSnapshot>()
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshot)) } just Runs

        useCase(SyncType.FULL)

        assertEquals(120, snapshot.captured.bottleFeeds.single().volumeMl)
    }

    @Test
    fun fullSyncIncludesPredictionWhenPredictiveSleepEnabled() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
        every { predictSleepWindow() } returns flowOf(
            SleepPredictionState.Window(
                SleepWindow(
                    windowStart = Instant.parse("2026-05-16T11:00:00Z"),
                    windowEnd = Instant.parse("2026-05-16T11:30:00Z"),
                    bestEstimate = Instant.parse("2026-05-16T11:15:00Z"),
                    confidence = Confidence.HIGH,
                    reasons = listOf("Awake 2h"),
                    feedPrompt = null,
                    safetyPrompt = "Back to sleep",
                ),
            ),
        )
        val snapshot = slot<ShareSnapshot>()
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshot)) } just Runs

        useCase(SyncType.FULL)

        val prediction = snapshot.captured.sleepPrediction
        assertEquals("WINDOW", prediction?.stateLabel)
        assertEquals("HIGH", prediction?.confidence)
        assertEquals(fixedNow.toEpochMilli(), prediction?.generatedAt)
    }

    @Test
    fun fullSyncOmitsPredictionWhenPredictiveSleepDisabled() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)
        val snapshot = slot<ShareSnapshot>()
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshot)) } just Runs

        useCase(SyncType.FULL)

        assertNull(snapshot.captured.sleepPrediction)
        coVerify(exactly = 0) { predictSleepWindow() }
    }

    @Test
    fun fullSyncOmitsPredictionWhenUnavailable() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
        every { predictSleepWindow() } returns flowOf(SleepPredictionState.Unavailable("no baby profile"))
        val snapshot = slot<ShareSnapshot>()
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshot)) } just Runs

        useCase(SyncType.FULL)

        assertNull(snapshot.captured.sleepPrediction)
    }
}
