package com.babytracker.sharing.usecase

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import io.mockk.Runs
import io.mockk.coEvery
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
import java.time.LocalDate

class GenerateShareCodeUseCaseInventoryTest {

    private val sharingRepository: SharingRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val babyRepository: BabyRepository = mockk()
    private val breastfeedingRepository: BreastfeedingRepository = mockk()
    private val sleepRepository: SleepRepository = mockk()
    private val inventoryRepository: InventoryRepository = mockk()
    private val bottleFeedRepository: BottleFeedRepository = mockk()
    private val predictSleepWindow: PredictSleepWindowUseCase = mockk()
    private val fixedNow = Instant.parse("2026-05-16T10:00:00Z")

    private lateinit var useCase: GenerateShareCodeUseCase

    @BeforeEach
    fun setUp() {
        useCase = GenerateShareCodeUseCase(
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
        every { babyRepository.getBabyProfile() } returns flowOf(Baby("Test", LocalDate.now()))
        coEvery { breastfeedingRepository.getRecentSessions(any()) } returns emptyList()
        coEvery { sleepRepository.getRecentRecords(any()) } returns emptyList()
        every { inventoryRepository.getActiveBags() } returns flowOf(emptyList())
        every { bottleFeedRepository.getAll() } returns flowOf(emptyList())
        coEvery { sharingRepository.signInAnonymously() } returns "uid123"
        coEvery { sharingRepository.isShareCodeValid(any()) } returns false
        coEvery { sharingRepository.createShareDocument(any(), any()) } just Runs
        coEvery { sharingRepository.syncFullSnapshot(any(), any()) } just Runs
        coEvery { settingsRepository.setShareCode(any()) } just Runs
        coEvery { settingsRepository.setAppMode(any()) } just Runs
    }

    @Test
    fun initialSnapshotCarriesCurrentInventorySummary() = runTest {
        val snapshotSlot = slot<ShareSnapshot>()
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 480,
            bagCount = 4,
            oldestBagDate = Instant.parse("2026-05-15T10:00:00Z"),
        )
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshotSlot)) } just Runs

        useCase()

        assertEquals(480, snapshotSlot.captured.inventoryTotalMl)
        assertEquals(4, snapshotSlot.captured.inventoryBagCount)
        assertEquals(fixedNow.toEpochMilli(), snapshotSlot.captured.inventoryUpdatedAt)
    }

    @Test
    fun initialSnapshotWithEmptyInventoryHasZeroFields() = runTest {
        val snapshotSlot = slot<ShareSnapshot>()
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary.Empty
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshotSlot)) } just Runs

        useCase()

        assertEquals(0, snapshotSlot.captured.inventoryTotalMl)
        assertEquals(0, snapshotSlot.captured.inventoryBagCount)
        assertEquals(fixedNow.toEpochMilli(), snapshotSlot.captured.inventoryUpdatedAt)
    }

    @Test
    fun initialSnapshotCarriesActiveMilkBags() = runTest {
        val snapshotSlot = slot<ShareSnapshot>()
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 150,
            bagCount = 1,
            oldestBagDate = null,
        )
        every { inventoryRepository.getActiveBags() } returns flowOf(
            listOf(
                MilkBag(
                    id = 7,
                    collectionDate = Instant.ofEpochMilli(5_000L),
                    volumeMl = 150,
                    notes = "freezer",
                    createdAt = Instant.ofEpochMilli(4_000L),
                ),
            ),
        )
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshotSlot)) } just Runs

        useCase()

        assertEquals(
            listOf(MilkBagSnapshot(id = 7, collectionDateMs = 5_000L, volumeMl = 150, notes = "freezer")),
            snapshotSlot.captured.milkBags,
        )
    }
}
