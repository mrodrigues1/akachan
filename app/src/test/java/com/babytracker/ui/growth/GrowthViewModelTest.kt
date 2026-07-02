package com.babytracker.ui.growth

import app.cash.turbine.test
import com.babytracker.domain.growth.GrowthChartData
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.repository.GrowthRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.growth.AddGrowthMeasurementUseCase
import com.babytracker.domain.usecase.growth.UpdateGrowthMeasurementUseCase
import com.babytracker.domain.usecase.growth.GetGrowthChartDataUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GrowthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var getGrowthChartData: GetGrowthChartDataUseCase
    private lateinit var addGrowthMeasurement: AddGrowthMeasurementUseCase
    private lateinit var updateGrowthMeasurement: UpdateGrowthMeasurementUseCase
    private lateinit var growthRepository: GrowthRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var syncToFirestore: SyncToFirestoreUseCase

    private fun chart(type: GrowthType, isSexSpecified: Boolean = true) = GrowthChartData(
        type = type,
        measurements = listOf(
            GrowthMeasurement(id = 1, takenAt = Instant.ofEpochMilli(1000), type = type, valueCanonical = 5000),
        ),
        plotted = emptyList(),
        curves = emptyList(),
        latestPercentile = if (isSexSpecified) 50.0 else null,
        isSexSpecified = isSexSpecified,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getGrowthChartData = mockk()
        addGrowthMeasurement = mockk(relaxed = true)
        updateGrowthMeasurement = mockk(relaxed = true)
        growthRepository = mockk(relaxed = true)
        settingsRepository = mockk()
        syncToFirestore = mockk(relaxed = true)
        every { settingsRepository.getMeasurementSystem() } returns flowOf(MeasurementSystem.METRIC)
        every { getGrowthChartData(GrowthType.WEIGHT) } returns flowOf(chart(GrowthType.WEIGHT))
        every { getGrowthChartData(GrowthType.LENGTH) } returns flowOf(chart(GrowthType.LENGTH))
        every { getGrowthChartData(GrowthType.HEAD_CIRC) } returns flowOf(chart(GrowthType.HEAD_CIRC, isSexSpecified = false))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = GrowthViewModel(
        getGrowthChartData,
        addGrowthMeasurement,
        updateGrowthMeasurement,
        growthRepository,
        settingsRepository,
        SyncedWrite(syncToFirestore),
    )

    @Test
    fun `initial state loads weight chart`() = runTest {
        viewModel().uiState.test {
            // skip the initial loading emission
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(GrowthType.WEIGHT, state.selectedType)
            assertEquals(GrowthType.WEIGHT, state.chart?.type)
            assertEquals(MeasurementSystem.METRIC, state.measurementSystem)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting a type switches the chart`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            vm.onTypeSelected(GrowthType.HEAD_CIRC)
            var next = awaitItem()
            while (next.chart?.type != GrowthType.HEAD_CIRC) next = awaitItem()
            assertEquals(GrowthType.HEAD_CIRC, next.selectedType)
            assertFalse(next.chart!!.isSexSpecified)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onAddMeasurement delegates to the use case with canonical value`() = runTest {
        val vm = viewModel()
        val takenAt = Instant.ofEpochMilli(2000)
        vm.onAddMeasurement(GrowthType.WEIGHT, valueCanonical = 5200, takenAt = takenAt, notes = "  ")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify {
            addGrowthMeasurement(
                match { it.type == GrowthType.WEIGHT && it.valueCanonical == 5200L && it.notes == null },
            )
        }
        coVerify { syncToFirestore() } // partner snapshot is refreshed after the edit
    }

    @Test
    fun `onUpdateMeasurement delegates to the use case preserving the id`() = runTest {
        val vm = viewModel()
        val takenAt = Instant.ofEpochMilli(3000)
        vm.onUpdateMeasurement(id = 9, type = GrowthType.LENGTH, valueCanonical = 640, takenAt = takenAt, notes = "  ")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify {
            updateGrowthMeasurement(
                match { it.id == 9L && it.type == GrowthType.LENGTH && it.valueCanonical == 640L && it.notes == null },
            )
        }
        coVerify { syncToFirestore() }
    }

    @Test
    fun `onDeleteMeasurement delegates to the use case`() = runTest {
        val vm = viewModel()
        vm.onDeleteMeasurement(7)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { growthRepository.deleteMeasurement(7) }
    }
}
