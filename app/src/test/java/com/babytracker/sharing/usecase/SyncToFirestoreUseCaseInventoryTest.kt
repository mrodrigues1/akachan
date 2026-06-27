package com.babytracker.sharing.usecase

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.InventorySnapshotFields
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.ShareCode
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SyncToFirestoreUseCaseInventoryTest {

    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val sleepSettingsRepository: SleepSettingsRepository = mockk()
    private val babyRepository: BabyRepository = mockk()
    private val breastfeedingRepository: BreastfeedingRepository = mockk()
    private val sleepRepository: SleepRepository = mockk()
    private val inventoryRepository: InventoryRepository = mockk()
    private val bottleFeedRepository: BottleFeedRepository = mockk()
    private val predictSleepWindow: PredictSleepWindowUseCase = mockk()
    private val fixedNow = Instant.parse("2026-05-16T10:00:00Z")

    private val shareCode = ShareCode("ABCD1234")
    private lateinit var useCase: SyncToFirestoreUseCase

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
                predictSleepWindow,
                mockk { every { getAllMeasurements() } returns flowOf(emptyList()) },
                mockk { every { getMilestones() } returns flowOf(emptyList()) },
                mockk { coEvery { getRecentVisits(any()) } returns emptyList() },
            ),
            appContext = mockk(relaxed = true),
        ) { fixedNow }
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)
        coEvery { service.syncInventory(any(), any(), any()) } just Runs
        coEvery { service.syncFullSnapshot(any(), any()) } just Runs
        every { babyRepository.getBabyProfile() } returns flowOf(null)
        coEvery { breastfeedingRepository.getRecentSessions(any()) } returns emptyList()
        coEvery { sleepRepository.getRecentRecords(any()) } returns emptyList()
        every { inventoryRepository.getActiveBags() } returns flowOf(emptyList())
        coEvery { bottleFeedRepository.getRecent(any()) } returns emptyList()
    }

    @Test
    fun inventorySyncCallsSyncInventoryOnce() = runTest {
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 240,
            bagCount = 3,
            oldestBagDate = null,
        )

        useCase(SyncType.INVENTORY)

        coVerify(exactly = 1) { service.syncInventory(shareCode.value, any(), any()) }
        coVerify(exactly = 0) { service.syncFullSnapshot(any(), any()) }
    }

    @Test
    fun inventorySyncPassesCorrectFields() = runTest {
        val fieldsSlot = slot<InventorySnapshotFields>()
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 240,
            bagCount = 3,
            oldestBagDate = null,
        )
        coEvery { service.syncInventory(any(), capture(fieldsSlot), any()) } just Runs

        useCase(SyncType.INVENTORY)

        assertEquals(240, fieldsSlot.captured.totalMl)
        assertEquals(3, fieldsSlot.captured.bagCount)
        assertEquals(fixedNow.toEpochMilli(), fieldsSlot.captured.updatedAtMs)
    }

    @Test
    fun inventorySyncNoOpWhenNotPrimary() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.NONE)

        useCase(SyncType.INVENTORY)

        coVerify(exactly = 0) { service.syncInventory(any(), any(), any()) }
    }

    @Test
    fun inventorySyncNoOpWhenShareCodeNull() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(null)

        useCase(SyncType.INVENTORY)

        coVerify(exactly = 0) { service.syncInventory(any(), any(), any()) }
    }

    @Test
    fun fullSyncPopulatesInventoryFields() = runTest {
        val snapshotSlot = slot<com.babytracker.sharing.domain.model.ShareSnapshot>()
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 360,
            bagCount = 5,
            oldestBagDate = null,
        )
        coEvery { service.syncFullSnapshot(any(), capture(snapshotSlot)) } just Runs

        useCase(SyncType.FULL)

        assertEquals(360, snapshotSlot.captured.inventoryTotalMl)
        assertEquals(5, snapshotSlot.captured.inventoryBagCount)
        assertEquals(fixedNow.toEpochMilli(), snapshotSlot.captured.inventoryUpdatedAt)
    }

    @Test
    fun inventorySyncPassesActiveBagSnapshots() = runTest {
        val bagsSlot = slot<List<MilkBagSnapshot>>()
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 150,
            bagCount = 1,
            oldestBagDate = null,
        )
        every { inventoryRepository.getActiveBags() } returns flowOf(listOf(activeBag))
        coEvery { service.syncInventory(any(), any(), capture(bagsSlot)) } just Runs

        useCase(SyncType.INVENTORY)

        assertEquals(
            listOf(MilkBagSnapshot(id = 7, collectionDateMs = 5_000L, volumeMl = 150, notes = "freezer")),
            bagsSlot.captured,
        )
    }

    @Test
    fun fullSyncPopulatesMilkBags() = runTest {
        val snapshotSlot = slot<com.babytracker.sharing.domain.model.ShareSnapshot>()
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 150,
            bagCount = 1,
            oldestBagDate = null,
        )
        every { inventoryRepository.getActiveBags() } returns flowOf(listOf(activeBag))
        coEvery { service.syncFullSnapshot(any(), capture(snapshotSlot)) } just Runs

        useCase(SyncType.FULL)

        assertEquals(
            listOf(MilkBagSnapshot(id = 7, collectionDateMs = 5_000L, volumeMl = 150, notes = "freezer")),
            snapshotSlot.captured.milkBags,
        )
    }

    @Test
    fun bottleFeedsAndInventorySyncCallsCombinedRepoMethodOnce() = runTest {
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 240,
            bagCount = 3,
            oldestBagDate = null,
        )
        every { inventoryRepository.getActiveBags() } returns flowOf(listOf(activeBag))
        coEvery { bottleFeedRepository.getRecent(any()) } returns listOf(bottleFeed)
        val feedsSlot = slot<List<BottleFeedSnapshot>>()
        val fieldsSlot = slot<InventorySnapshotFields>()
        val bagsSlot = slot<List<MilkBagSnapshot>>()
        coEvery {
            service.syncBottleFeedsAndInventory(
                any(),
                capture(feedsSlot),
                capture(fieldsSlot),
                capture(bagsSlot),
            )
        } just Runs

        useCase(SyncType.BOTTLE_FEEDS_AND_INVENTORY)

        // One combined call; neither single-tracker sync is issued.
        coVerify(exactly = 1) { service.syncBottleFeedsAndInventory(shareCode.value, any(), any(), any()) }
        coVerify(exactly = 0) { service.syncInventory(any(), any(), any()) }
        coVerify(exactly = 0) { service.syncBottleFeeds(any(), any()) }
        assertEquals(1, feedsSlot.captured.size)
        assertEquals(240, fieldsSlot.captured.totalMl)
        assertEquals(3, fieldsSlot.captured.bagCount)
        assertEquals(fixedNow.toEpochMilli(), fieldsSlot.captured.updatedAtMs)
        assertEquals(
            listOf(MilkBagSnapshot(id = 7, collectionDateMs = 5_000L, volumeMl = 150, notes = "freezer")),
            bagsSlot.captured,
        )
    }

    private val activeBag = MilkBag(
        id = 7,
        collectionDate = Instant.ofEpochMilli(5_000L),
        volumeMl = 150,
        notes = "freezer",
        createdAt = Instant.ofEpochMilli(4_000L),
    )

    private val bottleFeed = BottleFeed(
        id = 4L,
        clientId = "client-4",
        timestamp = Instant.ofEpochMilli(1_000L),
        volumeMl = 90,
        type = FeedType.BREAST_MILK,
        linkedMilkBagId = null,
        createdAt = Instant.ofEpochMilli(1_000L),
    )
}
