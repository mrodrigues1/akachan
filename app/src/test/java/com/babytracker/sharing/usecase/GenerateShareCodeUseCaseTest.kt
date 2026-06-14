package com.babytracker.sharing.usecase

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.SleepPredictionState
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class GenerateShareCodeUseCaseTest {

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
                mockk { every { getAllMeasurements() } returns flowOf(emptyList()) },
                mockk { every { getAchievements() } returns flowOf(emptyList()) },
            ),
        ) { fixedNow }
        every { babyRepository.getBabyProfile() } returns flowOf(Baby("Test", LocalDate.now()))
        coEvery { breastfeedingRepository.getRecentSessions(any()) } returns emptyList()
        coEvery { sleepRepository.getRecentRecords(any()) } returns emptyList()
        coEvery { inventoryRepository.currentSummary() } returns InventorySummary.Empty
        every { inventoryRepository.getActiveBags() } returns flowOf(emptyList())
        every { bottleFeedRepository.getAll() } returns flowOf(emptyList())
        coEvery { sharingRepository.signInAnonymously() } returns "uid123"
        coEvery { sharingRepository.isShareCodeValid(any()) } returns false
        coEvery { sharingRepository.createShareDocument(any(), any()) } just Runs
        coEvery { sharingRepository.syncFullSnapshot(any(), any()) } just Runs
        coEvery { settingsRepository.setShareCode(any()) } just Runs
        coEvery { settingsRepository.setAppMode(any()) } just Runs
        every { settingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)
        every { predictSleepWindow() } returns flowOf(SleepPredictionState.Unavailable("disabled"))
    }

    @Test
    fun generatedCodeIsEightUppercaseAlphanumericCharacters() = runTest {
        val codeSlot = slot<ShareCode>()
        coEvery { sharingRepository.createShareDocument(capture(codeSlot), any()) } just Runs

        useCase()

        val code = codeSlot.captured.value
        assertEquals(8, code.length)
        assertTrue(code.all { it.isUpperCase() || it.isDigit() })
    }

    @Test
    fun consecutiveCallsGenerateDifferentCodes() = runTest {
        val capturedCodes = mutableListOf<ShareCode>()
        coEvery { sharingRepository.createShareDocument(capture(capturedCodes), any()) } just Runs

        repeat(2) { useCase() }

        assertEquals(2, capturedCodes.size)
        assertNotEquals(capturedCodes[0].value, capturedCodes[1].value)
    }

    @Test
    fun collidingCodeIsRegeneratedSilently() = runTest {
        coEvery { sharingRepository.isShareCodeValid(any()) } returnsMany listOf(true, true, false)

        useCase()

        coVerify(exactly = 3) { sharingRepository.isShareCodeValid(any()) }
    }

    @Test
    fun setsAppModePrimaryAndSavesShareCode() = runTest {
        useCase()

        coVerify { settingsRepository.setAppMode(AppMode.PRIMARY) }
        coVerify { settingsRepository.setShareCode(any()) }
    }

    @Test
    fun initialSnapshotHasNoPredictionWhenPredictiveSleepDisabled() = runTest {
        val snapshotSlot = slot<ShareSnapshot>()
        every { settingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshotSlot)) } just Runs

        useCase()

        assertNull(snapshotSlot.captured.sleepPrediction)
    }

    @Test
    fun initialSnapshotIncludesPredictionWhenPredictiveSleepEnabled() = runTest {
        val snapshotSlot = slot<ShareSnapshot>()
        every { settingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
        every { predictSleepWindow() } returns flowOf(SleepPredictionState.CurrentlySleeping)
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshotSlot)) } just Runs

        useCase()

        assertNotNull(snapshotSlot.captured.sleepPrediction)
    }

    @Test
    fun initialSnapshotCarriesExistingBottleFeeds() = runTest {
        val snapshotSlot = slot<ShareSnapshot>()
        every { bottleFeedRepository.getAll() } returns flowOf(
            listOf(
                BottleFeed(
                    id = 1L,
                    clientId = "client-1",
                    timestamp = Instant.parse("2026-05-16T09:00:00Z"),
                    volumeMl = 120,
                    type = FeedType.FORMULA,
                    createdAt = Instant.parse("2026-05-16T09:00:00Z"),
                ),
            ),
        )
        coEvery { sharingRepository.syncFullSnapshot(any(), capture(snapshotSlot)) } just Runs

        useCase()

        val feed = snapshotSlot.captured.bottleFeeds.single()
        assertEquals(120, feed.volumeMl)
        assertEquals("FORMULA", feed.type)
    }
}
