package com.babytracker.sharing.usecase

import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.InventorySnapshotFields
import com.babytracker.sharing.domain.model.ShareCode
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SyncToFirestoreUseCaseInventoryTest {

    private val sharingRepository: SharingRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val babyRepository: BabyRepository = mockk()
    private val breastfeedingRepository: BreastfeedingRepository = mockk()
    private val sleepRepository: SleepRepository = mockk()
    private val inventoryRepository: InventoryRepository = mockk()
    private val fixedNow = Instant.parse("2026-05-16T10:00:00Z")

    private val shareCode = ShareCode("ABCD1234")
    private lateinit var useCase: SyncToFirestoreUseCase

    @BeforeEach
    fun setUp() {
        useCase = SyncToFirestoreUseCase(
            sharingRepository,
            settingsRepository,
            babyRepository,
            breastfeedingRepository,
            sleepRepository,
            inventoryRepository,
        ) { fixedNow }
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        coEvery { sharingRepository.syncInventory(any(), any()) } just Runs
        coEvery { sharingRepository.syncFullSnapshot(any(), any()) } just Runs
        every { babyRepository.getBabyProfile() } returns flowOf(null)
        coEvery { breastfeedingRepository.getRecentSessions(any()) } returns emptyList()
        coEvery { sleepRepository.getRecentRecords(any()) } returns emptyList()
    }

    @Test
    fun inventorySyncCallsSyncInventoryOnce() = runTest {
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 240,
            bagCount = 3,
            oldestBagDate = null,
        )

        useCase(SyncType.INVENTORY)

        coVerify(exactly = 1) { sharingRepository.syncInventory(shareCode, any()) }
        coVerify(exactly = 0) { sharingRepository.syncFullSnapshot(any(), any()) }
    }

    @Test
    fun inventorySyncPassesCorrectFields() = runTest {
        val fieldsSlot = slot<InventorySnapshotFields>()
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 240,
            bagCount = 3,
            oldestBagDate = null,
        )
        coEvery { sharingRepository.syncInventory(any(), capture(fieldsSlot)) } just Runs

        useCase(SyncType.INVENTORY)

        assertEquals(240, fieldsSlot.captured.totalMl)
        assertEquals(3, fieldsSlot.captured.bagCount)
        assertEquals(fixedNow.toEpochMilli(), fieldsSlot.captured.updatedAtMs)
    }

    @Test
    fun inventorySyncNoOpWhenNotPrimary() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.NONE)

        useCase(SyncType.INVENTORY)

        coVerify(exactly = 0) { sharingRepository.syncInventory(any(), any()) }
    }

    @Test
    fun inventorySyncNoOpWhenShareCodeNull() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(null)

        useCase(SyncType.INVENTORY)

        coVerify(exactly = 0) { sharingRepository.syncInventory(any(), any()) }
    }

    @Test
    fun fullSyncPopulatesInventoryFields() = runTest {
        val snapshotSlot = slot<com.babytracker.sharing.domain.model.ShareSnapshot>()
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary(
            totalMl = 360,
            bagCount = 5,
            oldestBagDate = null,
        )
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshotSlot)) } just Runs

        useCase(SyncType.FULL)

        assertEquals(360, snapshotSlot.captured.inventoryTotalMl)
        assertEquals(5, snapshotSlot.captured.inventoryBagCount)
        assertEquals(fixedNow.toEpochMilli(), snapshotSlot.captured.inventoryUpdatedAt)
    }
}
