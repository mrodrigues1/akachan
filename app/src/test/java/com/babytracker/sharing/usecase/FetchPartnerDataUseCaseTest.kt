package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class FetchPartnerDataUseCaseTest {

    private val sharingRepository: SharingRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private lateinit var useCase: FetchPartnerDataUseCase

    private val shareCode = ShareCode("ABCD1234")
    private val snapshot = ShareSnapshot(
        lastSyncAt = Instant.now(),
        baby = BabySnapshot("Baby", 0L, emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    @BeforeEach
    fun setUp() {
        useCase = FetchPartnerDataUseCase(sharingRepository, settingsRepository)
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        coEvery { sharingRepository.signInAnonymously() } returns "uid123"
    }

    @Test
    fun returnsSnapshotWhenPartnerIsConnected() = runTest {
        coEvery { sharingRepository.isPartnerConnected(shareCode, "uid123") } returns true
        coEvery { sharingRepository.fetchSnapshot(shareCode) } returns snapshot

        val result = useCase()

        assertEquals(snapshot, result)
    }

    @Test
    fun throwsAndClearsStateWhenPartnerIsDisconnected() = runTest {
        coEvery { sharingRepository.isPartnerConnected(shareCode, "uid123") } returns false
        coEvery { settingsRepository.clearShareCode() } just Runs
        coEvery { settingsRepository.setAppMode(AppMode.NONE) } just Runs

        var caught: IllegalStateException? = null
        try {
            useCase()
        } catch (e: IllegalStateException) {
            caught = e
        }

        assertNotNull(caught)
        coVerify { settingsRepository.clearShareCode() }
        coVerify { settingsRepository.setAppMode(AppMode.NONE) }
    }

    @Test
    fun throwsWhenNoShareCodeStored() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(null)

        var caught: IllegalStateException? = null
        try {
            useCase()
        } catch (e: IllegalStateException) {
            caught = e
        }

        assertNotNull(caught)
    }
}
