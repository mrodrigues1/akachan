package com.babytracker.ui.bottlefeed

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.bottlefeed.EditBottleFeedUseCase
import com.babytracker.domain.usecase.bottlefeed.LogBottleFeedUseCase
import com.babytracker.domain.usecase.inventory.GetInventoryUseCase
import io.mockk.coVerify
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BottleFeedViewModelTest {

    private val log = mockk<LogBottleFeedUseCase>(relaxed = true)
    private val edit = mockk<EditBottleFeedUseCase>(relaxed = true)
    private val getInventory = mockk<GetInventoryUseCase>()
    private val settings = mockk<SettingsRepository>()
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { getInventory() } returns flowOf(emptyList())
        every { settings.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = BottleFeedViewModel(log, edit, getInventory, settings) { Instant.ofEpochMilli(10_000) }

    @Test
    fun `blank volume sets validation error and does not save`() = runTest {
        val viewModel = vm()
        viewModel.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Enter a volume", viewModel.uiState.value.validationError)
        coVerify(exactly = 0) { log(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `valid add calls LogBottleFeedUseCase and marks saved`() = runTest {
        val viewModel = vm()
        viewModel.onVolumeChange("120")
        viewModel.onTypeChange(FeedType.FORMULA)
        viewModel.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { log(Instant.ofEpochMilli(10_000), 120, FeedType.FORMULA, null, null) }
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `two rapid saves only log once`() = runTest {
        val viewModel = vm()
        viewModel.onVolumeChange("120")
        viewModel.onSave()
        viewModel.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { log(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `loadForEdit populates state and save calls EditBottleFeedUseCase`() = runTest {
        val viewModel = vm()
        viewModel.loadForEdit(
            id = 3,
            timestamp = Instant.ofEpochMilli(8_000),
            volumeMl = 90,
            type = FeedType.BREAST_MILK,
            linkedMilkBagId = 7,
            notes = "am",
        )
        viewModel.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { edit(3, Instant.ofEpochMilli(8_000), 90, FeedType.BREAST_MILK, 7, "am") }
    }
}
