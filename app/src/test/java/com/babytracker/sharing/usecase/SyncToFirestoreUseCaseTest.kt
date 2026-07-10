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
import com.babytracker.domain.model.SleepReason
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.SharedSleepPredictionStream
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.PredictionStateLabel
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SyncToFirestoreUseCaseTest {

    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val sleepSettingsRepository: SleepSettingsRepository = mockk()
    private val babyRepository: BabyRepository = mockk()
    private val breastfeedingRepository: BreastfeedingRepository = mockk()
    private val sleepRepository: SleepRepository = mockk()
    private val inventoryRepository: InventoryRepository = mockk()
    private val bottleFeedRepository: BottleFeedRepository = mockk()
    private val sharedSleepPrediction: SharedSleepPredictionStream = mockk()
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
        clientId = "client-1",
        timestamp = Instant.parse("2026-05-16T09:00:00Z"),
        volumeMl = 120,
        type = FeedType.FORMULA,
        createdAt = Instant.parse("2026-05-16T09:00:00Z"),
    )

    @BeforeEach
    fun setUp() {
        useCase = SyncToFirestoreUseCase(
            service,
            settingsRepository,
            sleepSettingsRepository,
            SnapshotSources(
                babyRepository,
                breastfeedingRepository,
                sleepRepository,
                inventoryRepository,
                bottleFeedRepository,
                mockk { coEvery { getRecent(any()) } returns emptyList() },
                sharedSleepPrediction,
                mockk { every { getAllMeasurements() } returns flowOf(emptyList()) },
                mockk { every { getMilestones() } returns flowOf(emptyList()) },
                mockk { coEvery { getRecentVisits(any()) } returns emptyList() },
            ),
        ) { fixedNow }
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)
        every { babyRepository.getBabyProfile() } returns flowOf(mockBaby)
        coEvery { breastfeedingRepository.getRecentSessions(any()) } returns listOf(mockSession)
        coEvery { sleepRepository.getRecentRecords(any()) } returns listOf(mockSleep)
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary.Empty
        every { inventoryRepository.getActiveBags() } returns flowOf(emptyList())
        coEvery { bottleFeedRepository.getRecent(any()) } returns listOf(mockBottleFeed)
        coEvery { service.syncFullSnapshot(any(), any()) } just Runs
        coEvery { service.syncSessions(any(), any(), any()) } just Runs
        coEvery { service.syncSleepRecords(any(), any(), any()) } just Runs
        coEvery { service.syncInventory(any(), any(), any()) } just Runs
        coEvery { service.syncBottleFeeds(any(), any()) } just Runs
    }

    @Test
    fun noOpWhenAppModeIsNotPrimary() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.NONE)

        useCase()

        coVerify(exactly = 0) { service.syncFullSnapshot(any(), any()) }
    }

    @Test
    fun noOpWhenShareCodeIsNull() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns flowOf(null)

        useCase()

        coVerify(exactly = 0) { service.syncFullSnapshot(any(), any()) }
    }

    @Test
    fun fullSyncCallsSyncFullSnapshot() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)

        useCase(SyncType.FULL)

        coVerify { service.syncFullSnapshot(shareCode.value, any()) }
    }

    @Test
    fun sessionsSyncCallsSyncSessions() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)

        useCase(SyncType.SESSIONS)

        coVerify { service.syncSessions(shareCode.value, any(), any()) }
        coVerify(exactly = 0) { service.syncFullSnapshot(any(), any()) }
    }

    @Test
    fun sleepRecordsSyncCallsSyncSleepRecords() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)

        useCase(SyncType.SLEEP_RECORDS)

        coVerify { service.syncSleepRecords(shareCode.value, any(), any()) }
    }

    @Test
    fun bottleFeedsSyncCallsSyncBottleFeeds() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        val feeds = slot<List<com.babytracker.sharing.domain.model.BottleFeedSnapshot>>()
        coEvery { service.syncBottleFeeds(any(), capture(feeds)) } just Runs

        useCase(SyncType.BOTTLE_FEEDS)

        coVerify { service.syncBottleFeeds(shareCode.value, any()) }
        coVerify(exactly = 0) { service.syncFullSnapshot(any(), any()) }
        assertEquals(120, feeds.captured.single().volumeMl)
        assertEquals("FORMULA", feeds.captured.single().type)
    }

    @Test
    fun fullSyncIncludesBottleFeeds() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        val snapshot = slot<ShareSnapshot>()
        coEvery { service.syncFullSnapshot(any(), capture(snapshot)) } just Runs

        useCase(SyncType.FULL)

        assertEquals(120, snapshot.captured.bottleFeeds.single().volumeMl)
    }

    @Test
    fun fullSyncIncludesPredictionWhenPredictiveSleepEnabled() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
        every { sharedSleepPrediction.observe() } returns flowOf(
            SleepPredictionState.Window(
                SleepWindow(
                    windowStart = Instant.parse("2026-05-16T11:00:00Z"),
                    windowEnd = Instant.parse("2026-05-16T11:30:00Z"),
                    bestEstimate = Instant.parse("2026-05-16T11:15:00Z"),
                    sleepType = SleepType.NAP,
                    confidence = Confidence.HIGH,
                    reasons = listOf(SleepReason.Disruption),
                    feedDue = false,
                ),
            ),
        )
        val snapshot = slot<ShareSnapshot>()
        coEvery { service.syncFullSnapshot(any(), capture(snapshot)) } just Runs

        useCase(SyncType.FULL)

        val prediction = snapshot.captured.sleepPrediction
        assertEquals(PredictionStateLabel.WINDOW, prediction?.stateLabel)
        assertEquals("HIGH", prediction?.confidence)
        assertEquals(fixedNow.toEpochMilli(), prediction?.generatedAt)
    }

    @Test
    fun fullSyncOmitsPredictionWhenPredictiveSleepDisabled() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)
        val snapshot = slot<ShareSnapshot>()
        coEvery { service.syncFullSnapshot(any(), capture(snapshot)) } just Runs

        useCase(SyncType.FULL)

        assertNull(snapshot.captured.sleepPrediction)
        coVerify(exactly = 0) { sharedSleepPrediction.observe() }
    }

    @Test
    fun sessionsSyncRefreshesPredictionWhenPredictiveSleepEnabled() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
        every { sharedSleepPrediction.observe() } returns flowOf(SleepPredictionState.AfterActiveFeed)
        val prediction = slot<SleepPredictionSnapshot>()
        coEvery { service.syncSessions(any(), any(), capture(prediction)) } just Runs

        useCase(SyncType.SESSIONS)

        assertEquals(PredictionStateLabel.AFTER_ACTIVE_FEED, prediction.captured.stateLabel)
        assertEquals(fixedNow.toEpochMilli(), prediction.captured.generatedAt)
    }

    @Test
    fun sessionsSyncPassesNullPredictionWhenPredictiveSleepDisabled() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)

        useCase(SyncType.SESSIONS)

        coVerify { service.syncSessions(shareCode.value, any(), null) }
        coVerify(exactly = 0) { sharedSleepPrediction.observe() }
    }

    @Test
    fun sleepRecordsSyncRefreshesPredictionWhenPredictiveSleepEnabled() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
        every { sharedSleepPrediction.observe() } returns flowOf(SleepPredictionState.CurrentlySleeping)
        val prediction = slot<SleepPredictionSnapshot>()
        coEvery {
            service.syncSleepRecords(any(), any(), capture(prediction))
        } just Runs

        useCase(SyncType.SLEEP_RECORDS)

        assertEquals(PredictionStateLabel.CURRENTLY_SLEEPING, prediction.captured.stateLabel)
        // The prediction must come from the shared, already-hot stream — not a freshly cold-started
        // PredictSleepWindowUseCase pipeline — on every sleep-record sync (issue #764).
        coVerify(exactly = 1) { sharedSleepPrediction.observe() }
    }

    @Test
    fun sleepRecordsSyncReadsLiveSharedPredictionSoAStaleValueSelfHealsOnTheNextSync() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
        // Model the shared replay=1 cache: a sync reads whatever the hot pipeline currently holds. The
        // pipeline reacts to a write's Room invalidation asynchronously, so a sync can momentarily read
        // a pre-write value — but once the pipeline pushes the recomputed prediction, the next sync
        // reflects it. This is the bounded, self-healing staleness accepted in place of a per-write
        // cold start (issue #764).
        val shared = MutableStateFlow<SleepPredictionState>(SleepPredictionState.CurrentlySleeping)
        every { sharedSleepPrediction.observe() } returns shared
        val prediction = slot<SleepPredictionSnapshot>()
        coEvery { service.syncSleepRecords(any(), any(), capture(prediction)) } just Runs

        useCase(SyncType.SLEEP_RECORDS)
        assertEquals(PredictionStateLabel.CURRENTLY_SLEEPING, prediction.captured.stateLabel)

        // The shared pipeline recomputes off the write and updates its cache; the next sync picks it up.
        shared.value = SleepPredictionState.AfterActiveFeed
        useCase(SyncType.SLEEP_RECORDS)
        assertEquals(PredictionStateLabel.AFTER_ACTIVE_FEED, prediction.captured.stateLabel)
    }

    @Test
    fun fullSyncOmitsPredictionWhenUnavailable() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
        every { sharedSleepPrediction.observe() } returns flowOf(SleepPredictionState.Unavailable("no baby profile"))
        val snapshot = slot<ShareSnapshot>()
        coEvery { service.syncFullSnapshot(any(), capture(snapshot)) } just Runs

        useCase(SyncType.FULL)

        assertNull(snapshot.captured.sleepPrediction)
    }
}
