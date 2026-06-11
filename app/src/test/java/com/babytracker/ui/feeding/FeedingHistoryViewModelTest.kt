package com.babytracker.ui.feeding

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.bottlefeed.DeleteBottleFeedUseCase
import com.babytracker.domain.usecase.feeding.ObserveFeedingHistoryUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class FeedingHistoryViewModelTest {

    private val observe = mockk<ObserveFeedingHistoryUseCase>()
    private val delete = mockk<DeleteBottleFeedUseCase>(relaxed = true)
    private val settings = mockk<SettingsRepository>()
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `maps entries into day groups`() = runTest {
        val bottle = BottleFeed(
            id = 1L,
            timestamp = Instant.parse("2026-06-01T08:00:00Z"),
            volumeMl = 120,
            type = FeedType.FORMULA,
            createdAt = Instant.EPOCH,
        )
        every { observe() } returns flowOf(listOf(FeedEntry.Bottle(bottle)))
        every { settings.getVolumeUnit() } returns flowOf(VolumeUnit.ML)

        val vm = FeedingHistoryViewModel(observe, delete, settings, ZoneId.of("UTC"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.days.size)
        assertEquals(120, vm.uiState.value.days.first().totals.bottleVolumeMl)
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `onDeleteBottle delegates to use case`() = runTest {
        val bottle = BottleFeed(
            id = 1L,
            timestamp = Instant.parse("2026-06-01T08:00:00Z"),
            volumeMl = 120,
            type = FeedType.FORMULA,
            createdAt = Instant.EPOCH,
        )
        every { observe() } returns flowOf(emptyList())
        every { settings.getVolumeUnit() } returns flowOf(VolumeUnit.ML)

        val vm = FeedingHistoryViewModel(observe, delete, settings, ZoneId.of("UTC"))
        vm.onDeleteBottle(bottle)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { delete.invoke(bottle) }
    }

    @Test
    fun `onDeleteBottle records error when delete fails`() = runTest {
        val bottle = BottleFeed(
            id = 1L,
            timestamp = Instant.parse("2026-06-01T08:00:00Z"),
            volumeMl = 120,
            type = FeedType.FORMULA,
            createdAt = Instant.EPOCH,
        )
        every { observe() } returns flowOf(emptyList())
        every { settings.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
        val bottleFeedRepository = mockk<BottleFeedRepository>()
        val sync = mockk<SyncToFirestoreUseCase>(relaxed = true)
        coEvery { bottleFeedRepository.deleteWithInventoryRestore(bottle) } throws
            IllegalStateException("Delete failed")

        val vm = FeedingHistoryViewModel(
            observe,
            DeleteBottleFeedUseCase(bottleFeedRepository, sync),
            settings,
            ZoneId.of("UTC"),
        )
        vm.onDeleteBottle(bottle)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isDeleting)
        assertEquals("Delete failed", vm.uiState.value.deleteError)
    }
}
