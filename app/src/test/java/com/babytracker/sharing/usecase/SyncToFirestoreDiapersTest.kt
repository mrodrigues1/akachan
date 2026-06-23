package com.babytracker.sharing.usecase

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.DiaperSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class SyncToFirestoreDiapersTest {
    @Test
    fun `DIAPERS sync pushes mapped diapers`() = runTest {
        val sharingRepository = mockk<SharingRepository>(relaxed = true)
        val diaperRepo = mockk<DiaperRepository>()
        val sources = mockk<SnapshotSources>()
        every { sources.diaper } returns diaperRepo
        coEvery { diaperRepo.getRecent(any()) } returns listOf(
            DiaperChange(
                id = 1,
                timestamp = Instant.ofEpochMilli(10),
                type = DiaperType.WET,
                createdAt = Instant.ofEpochMilli(10),
            ),
        )
        val settings = mockk<SettingsRepository>(relaxed = true)
        every { settings.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settings.getShareCode() } returns flowOf("ABCD")
        val sleepSettings = mockk<SleepSettingsRepository>(relaxed = true)

        val useCase = SyncToFirestoreUseCase(
            sharingRepository,
            settings,
            sleepSettings,
            sources,
            appContext = mockk(relaxed = true),
        ) {
            Instant.ofEpochMilli(99)
        }

        val captured = slot<List<DiaperSnapshot>>()
        coEvery { sharingRepository.syncDiapers(any(), capture(captured)) } returns Unit

        useCase(SyncToFirestoreUseCase.SyncType.DIAPERS)

        assertEquals(1, captured.captured.size)
        assertEquals("WET", captured.captured.first().type)
        coVerify { sharingRepository.syncDiapers(any(), any()) }
    }
}
