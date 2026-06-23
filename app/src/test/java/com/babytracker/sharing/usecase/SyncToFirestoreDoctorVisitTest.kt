package com.babytracker.sharing.usecase

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.repository.GrowthRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.MilestoneRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class SyncToFirestoreDoctorVisitTest {
    @Test
    fun `FULL sync includes mapped doctor visits`() = runTest {
        val sharingRepository = mockk<SharingRepository>(relaxed = true)
        val settings = mockk<SettingsRepository>(relaxed = true)
        every { settings.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settings.getShareCode() } returns flowOf("ABCD")
        val sleepSettings = mockk<SleepSettingsRepository>(relaxed = true)
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(false)

        val babyRepo = mockk<BabyRepository>()
        every { babyRepo.getBabyProfile() } returns flowOf(null)
        val bfRepo = mockk<BreastfeedingRepository>()
        coEvery { bfRepo.getRecentSessions(any()) } returns emptyList()
        val sleepRepo = mockk<SleepRepository>()
        coEvery { sleepRepo.getRecentRecords(any()) } returns emptyList()
        val inventoryRepo = mockk<InventoryRepository>()
        coEvery { inventoryRepo.currentSummary() } returns InventorySummary.Empty
        every { inventoryRepo.getActiveBags() } returns flowOf(emptyList())
        val bottleRepo = mockk<BottleFeedRepository>()
        coEvery { bottleRepo.getRecent(any()) } returns emptyList()
        val diaperRepo = mockk<DiaperRepository>()
        coEvery { diaperRepo.getRecent(any()) } returns emptyList()
        val growthRepo = mockk<GrowthRepository>()
        every { growthRepo.getAllMeasurements() } returns flowOf(emptyList())
        val milestoneRepo = mockk<MilestoneRepository>()
        every { milestoneRepo.getMilestones() } returns flowOf(emptyList())
        val doctorRepo = mockk<DoctorVisitRepository>()
        coEvery { doctorRepo.getRecentVisits(any()) } returns listOf(
            DoctorVisit(
                id = 1,
                date = Instant.ofEpochMilli(5_000),
                providerName = "Dr. A",
                notes = "n",
                createdAt = Instant.ofEpochMilli(1_000),
            ),
        )

        val sources = SnapshotSources(
            babyRepo, bfRepo, sleepRepo, inventoryRepo, bottleRepo, diaperRepo,
            mockk<PredictSleepWindowUseCase>(), growthRepo, milestoneRepo, doctorRepo,
        )
        val useCase = SyncToFirestoreUseCase(
            sharingRepository, settings, sleepSettings, sources, appContext = mockk(relaxed = true),
        ) { Instant.ofEpochMilli(99) }

        val captured = slot<ShareSnapshot>()
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(captured)) } returns Unit

        useCase(SyncToFirestoreUseCase.SyncType.FULL)

        assertEquals(1, captured.captured.doctorVisits.size)
        assertEquals(5_000L, captured.captured.doctorVisits.first().date)
        assertEquals("Dr. A", captured.captured.doctorVisits.first().providerName)
    }
}
