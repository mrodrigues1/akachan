package com.babytracker.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.domain.model.BackupData
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildFakeDataExportViewModel(): DataExportViewModel = mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(DataExportUiState())
    }

    private fun buildPartnerViewModel(): SettingsViewModel {
        val babyRepository = FakeBabyRepository()
        val settingsRepository = FakeSettingsRepository()
        return SettingsViewModel(
            getBabyProfile = GetBabyProfileUseCase(babyRepository),
            settingsRepository = settingsRepository,
            saveBabyProfile = SaveBabyProfileUseCase(babyRepository, mockk(relaxed = true)),
        )
    }

    @Test
    fun partnerModeHidesBabyProfileSection() {
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel(), dataVm = buildFakeDataExportViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Baby Profile").assertDoesNotExist()
    }

    @Test
    fun feedingLimitsSectionRemovedFromGlobalSettings() {
        // Feeding Limits moved to the dedicated Feed Settings screen (AKA-106).
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel(), dataVm = buildFakeDataExportViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Feeding Limits").assertDoesNotExist()
    }

    @Test
    fun partnerModeHidesNotificationsSection() {
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel(), dataVm = buildFakeDataExportViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NOTIFICATIONS").assertDoesNotExist()
    }

    @Test
    fun partnerModeShowsDisconnectRow() {
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel(), dataVm = buildFakeDataExportViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Disconnect")[0].assertIsDisplayed()
    }

    @Test
    fun partnerModeDisconnectRowButtonIsLabelledDisconnect() {
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel(), dataVm = buildFakeDataExportViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Disconnect").assertCountEquals(2)
    }

    // Regression test: clicking Edit Allergies crashed with nested verticalScroll giving
    // AllergiesStepContent infinite height constraints (IllegalStateException in ScrollNode).
    @Test
    fun clickingEditAllergiesOpensSheetWithoutCrash() {
        val babyRepo = object : FakeBabyRepository() {
            override fun getBabyProfile(): Flow<Baby?> = flowOf(
                Baby(name = "Leo", birthDate = LocalDate.of(2025, 1, 1))
            )
        }
        val settingsRepo = object : FakeSettingsRepository() {
            override fun getAppMode(): Flow<com.babytracker.sharing.domain.model.AppMode> =
                flowOf(com.babytracker.sharing.domain.model.AppMode.NONE)
        }
        val viewModel = SettingsViewModel(
            getBabyProfile = GetBabyProfileUseCase(babyRepo),
            settingsRepository = settingsRepo,
            saveBabyProfile = SaveBabyProfileUseCase(babyRepo, mockk(relaxed = true)),
        )

        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = viewModel, dataVm = buildFakeDataExportViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Allergies").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit allergies").assertIsDisplayed()
    }

    @Test
    fun volumeUnitSelectorRendersAndPersistsSelection() {
        val settingsRepo = FakeSettingsRepository()
        val babyRepo = FakeBabyRepository()
        val viewModel = SettingsViewModel(
            getBabyProfile = GetBabyProfileUseCase(babyRepo),
            settingsRepository = settingsRepo,
            saveBabyProfile = SaveBabyProfileUseCase(babyRepo, mockk(relaxed = true)),
        )

        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = viewModel, dataVm = buildFakeDataExportViewModel())
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Volume unit").assertIsDisplayed()
        composeRule.onNodeWithText("mL").assertIsDisplayed()
        composeRule.onNodeWithText("oz").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(VolumeUnit.OZ, settingsRepo.volumeUnitState.value)
        }
    }

    private open class FakeBabyRepository : BabyRepository {
        override fun getBabyProfile(): Flow<Baby?> = flowOf(null)

        override suspend fun saveBabyProfile(baby: Baby) = Unit

        override fun isOnboardingComplete(): Flow<Boolean> = flowOf(true)
    }

    private open class FakeSettingsRepository : SettingsRepository {
        override fun getThemeConfig(): Flow<ThemeConfig> = flowOf(ThemeConfig.SYSTEM)

        override suspend fun setThemeConfig(themeConfig: ThemeConfig) = Unit

        val volumeUnitState = MutableStateFlow(VolumeUnit.ML)

        override fun getVolumeUnit(): Flow<VolumeUnit> = volumeUnitState

        override suspend fun setVolumeUnit(unit: VolumeUnit) {
            volumeUnitState.value = unit
        }

        val measurementSystemState = MutableStateFlow(MeasurementSystem.METRIC)

        override fun getMeasurementSystem(): Flow<MeasurementSystem> = measurementSystemState

        override suspend fun setMeasurementSystem(system: MeasurementSystem) {
            measurementSystemState.value = system
        }

        override fun getHomeTileOrder(): Flow<List<HomeTile>> = flowOf(HomeTile.DEFAULT_ORDER)

        override suspend fun setHomeTileOrder(order: List<HomeTile>) = Unit

        override suspend fun clearHomeTileOrder() = Unit

        override fun isOnboardingComplete(): Flow<Boolean> = flowOf(true)

        override suspend fun setOnboardingComplete(complete: Boolean) = Unit

        override fun getWakeTime(): Flow<LocalTime?> = flowOf(null)

        override suspend fun setWakeTime(time: LocalTime) = Unit

        override fun getAutoUpdateEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setAutoUpdateEnabled(enabled: Boolean) = Unit

        override fun getRichNotificationsEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setRichNotificationsEnabled(enabled: Boolean) = Unit

        override fun getPartnerFeedStashNotificationsEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setPartnerFeedStashNotificationsEnabled(enabled: Boolean) = Unit

        override fun getAppMode(): Flow<com.babytracker.sharing.domain.model.AppMode> =
            flowOf(com.babytracker.sharing.domain.model.AppMode.PARTNER)

        override suspend fun setAppMode(mode: com.babytracker.sharing.domain.model.AppMode) = Unit

        override fun getShareCode(): Flow<String?> = flowOf("ABC12345")

        override suspend fun setShareCode(code: String) = Unit

        override suspend fun clearShareCode() = Unit

        override suspend fun clearPartnerStateIfShareCodeMatches(code: String): Boolean = false

        override fun getQuietHoursStartMinute(): Flow<Int> = flowOf(0)

        override suspend fun setQuietHoursStartMinute(minuteOfDay: Int) = Unit

        override fun getQuietHoursEndMinute(): Flow<Int> = flowOf(480)

        override suspend fun setQuietHoursEndMinute(minuteOfDay: Int) = Unit

        override fun isImportInProgress(): Flow<Boolean> = flowOf(false)

        override suspend fun markImportInProgress(startedAt: Long) = Unit

        override suspend fun restoreFromBackup(data: BackupData) = Unit
    }
}
