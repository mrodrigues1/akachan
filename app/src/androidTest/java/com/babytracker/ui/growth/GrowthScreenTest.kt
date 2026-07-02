package com.babytracker.ui.growth

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.babytracker.domain.growth.LmsPoint
import com.babytracker.domain.growth.WhoReferenceData
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabySex
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.GrowthRepository
import com.babytracker.domain.usecase.growth.AddGrowthMeasurementUseCase
import com.babytracker.domain.usecase.growth.UpdateGrowthMeasurementUseCase
import com.babytracker.domain.usecase.growth.GetGrowthChartDataUseCase
import com.babytracker.ui.theme.BabyTrackerTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class GrowthScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val measurements = MutableStateFlow<List<GrowthMeasurement>>(emptyList())

    private val growthRepository = object : GrowthRepository {
        override suspend fun addMeasurement(measurement: GrowthMeasurement): Long {
            measurements.value = measurements.value + measurement.copy(id = measurements.value.size + 1L)
            return measurements.value.size.toLong()
        }

        override fun getAllMeasurements(): Flow<List<GrowthMeasurement>> = measurements

        override fun getMeasurementsByType(type: GrowthType): Flow<List<GrowthMeasurement>> =
            measurements.map { list -> list.filter { it.type == type } }

        override suspend fun deleteMeasurement(id: Long) {
            measurements.value = measurements.value.filterNot { it.id == id }
        }
    }

    private var baby = Baby(name = "Leo", birthDate = LocalDate.now().minusMonths(3), sex = BabySex.MALE)
    private var lmsTable = listOf(
        LmsPoint(3, 0.1738, 6.3762, 0.11727),
        LmsPoint(4, 0.1553, 7.0023, 0.11316),
    )

    private val babyRepository = mockk<BabyRepository> {
        every { getBabyProfile() } answers { flowOf(baby) }
    }

    private val whoReferenceData = mockk<WhoReferenceData> {
        coEvery { lmsTable(any(), any()) } answers { lmsTable }
    }

    private val settingsRepository = mockk<com.babytracker.domain.repository.SettingsRepository> {
        every { getMeasurementSystem() } returns flowOf(MeasurementSystem.METRIC)
    }

    private fun viewModel() = GrowthViewModel(
        GetGrowthChartDataUseCase(growthRepository, babyRepository, whoReferenceData),
        AddGrowthMeasurementUseCase(growthRepository),
        UpdateGrowthMeasurementUseCase(growthRepository),
        growthRepository,
        settingsRepository,
        mockk(relaxed = true),
    )

    @Test
    fun addingAMeasurementShowsItInHistory() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                GrowthScreen(
                    onNavigateBack = {},
                    onNavigateToSettings = {},
                    viewModel = viewModel(),
                )
            }
        }

        composeRule.onNodeWithText("No measurements yet").assertIsDisplayed()

        composeRule.onNodeWithTag("growth_add_fab").performClick()
        composeRule.onNodeWithTag("growth_value_input").performTextInput("6.5")
        composeRule.onNodeWithTag("growth_save_measurement").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("growth_chart").fetchSemanticsNodes().isNotEmpty()
        }
        // The value appears in exactly two places: the summary card and the history row.
        // Asserting the count proves the history row rendered, not just the summary.
        composeRule.onAllNodesWithText("6.50 kg").assertCountEquals(2)
    }

    /**
     * Regression for AKA-210: a brand-new user keeps the default unspecified sex (onboarding does not
     * require it), so the WHO table is empty and the first measurement is the chart's only point. The
     * chart must render that lone point — with a flat Y range and no curves — without crashing.
     */
    @Test
    fun addingFirstMeasurementWithoutSexDoesNotCrash() {
        baby = baby.copy(sex = BabySex.UNSPECIFIED)
        lmsTable = emptyList()

        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                GrowthScreen(
                    onNavigateBack = {},
                    onNavigateToSettings = {},
                    viewModel = viewModel(),
                )
            }
        }

        composeRule.onNodeWithTag("growth_add_fab").performClick()
        composeRule.onNodeWithTag("growth_value_input").performTextInput("6.5")
        composeRule.onNodeWithTag("growth_save_measurement").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("growth_chart").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("growth_chart").assertIsDisplayed()
    }
}
