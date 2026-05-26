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
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.domain.model.BackupData
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.domain.usecase.breastfeeding.CountRecentValidIntervalsUseCase
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.sharing.domain.model.AppMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
        val countRecentValidIntervals = mockk<CountRecentValidIntervalsUseCase>()
        every { countRecentValidIntervals() } returns flowOf(0)
        return SettingsViewModel(
            getBabyProfile = GetBabyProfileUseCase(babyRepository),
            settingsRepository = settingsRepository,
            saveBabyProfile = SaveBabyProfileUseCase(babyRepository),
            countRecentValidIntervals = countRecentValidIntervals,
            notificationPermissionChecker = NotificationPermissionChecker { true },
            napReminderScheduler = mockk(relaxed = true),
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
    fun partnerModeHidesFeedingLimitsSection() {
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
            override fun getAppMode(): Flow<AppMode> = flowOf(AppMode.NONE)
        }
        val countRecentValidIntervals = mockk<CountRecentValidIntervalsUseCase>()
        every { countRecentValidIntervals() } returns flowOf(0)
        val viewModel = SettingsViewModel(
            getBabyProfile = GetBabyProfileUseCase(babyRepo),
            settingsRepository = settingsRepo,
            saveBabyProfile = SaveBabyProfileUseCase(babyRepo),
            countRecentValidIntervals = countRecentValidIntervals,
            notificationPermissionChecker = NotificationPermissionChecker { true },
            napReminderScheduler = mockk(relaxed = true),
        )

        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = viewModel, dataVm = buildFakeDataExportViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Allergies").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit allergies").assertIsDisplayed()
    }

    private open class FakeBabyRepository : BabyRepository {
        override fun getBabyProfile(): Flow<Baby?> = flowOf(null)

        override suspend fun saveBabyProfile(baby: Baby) = Unit

        override fun isOnboardingComplete(): Flow<Boolean> = flowOf(true)
    }

    private open class FakeSettingsRepository : SettingsRepository {
        override fun getThemeConfig(): Flow<ThemeConfig> = flowOf(ThemeConfig.SYSTEM)

        override suspend fun setThemeConfig(themeConfig: ThemeConfig) = Unit

        override fun isOnboardingComplete(): Flow<Boolean> = flowOf(true)

        override suspend fun setOnboardingComplete(complete: Boolean) = Unit

        override fun getMaxPerBreastMinutes(): Flow<Int> = flowOf(0)

        override suspend fun setMaxPerBreastMinutes(minutes: Int) = Unit

        override fun getMaxTotalFeedMinutes(): Flow<Int> = flowOf(0)

        override suspend fun setMaxTotalFeedMinutes(minutes: Int) = Unit

        override fun getWakeTime(): Flow<LocalTime?> = flowOf(null)

        override suspend fun setWakeTime(time: LocalTime) = Unit

        override fun getAutoUpdateEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setAutoUpdateEnabled(enabled: Boolean) = Unit

        override fun getRichNotificationsEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setRichNotificationsEnabled(enabled: Boolean) = Unit

        override fun getAppMode(): Flow<AppMode> = flowOf(AppMode.PARTNER)

        override suspend fun setAppMode(mode: AppMode) = Unit

        override fun getShareCode(): Flow<String?> = flowOf("ABC12345")

        override suspend fun setShareCode(code: String) = Unit

        override suspend fun clearShareCode() = Unit

        override fun getPredictiveEnabled(): Flow<Boolean> = flowOf(false)

        override suspend fun setPredictiveEnabled(enabled: Boolean) = Unit

        override fun getPredictiveLeadMinutes(): Flow<Int> = flowOf(15)

        override suspend fun setPredictiveLeadMinutes(minutes: Int) = Unit

        override fun getQuietHoursStartMinute(): Flow<Int> = flowOf(0)

        override suspend fun setQuietHoursStartMinute(minuteOfDay: Int) = Unit

        override fun getQuietHoursEndMinute(): Flow<Int> = flowOf(480)

        override suspend fun setQuietHoursEndMinute(minuteOfDay: Int) = Unit

        override fun getNapReminderEnabled(): Flow<Boolean> = flowOf(false)

        override suspend fun setNapReminderEnabled(enabled: Boolean) = Unit

        override fun getNapReminderDelayMinutes(): Flow<Int> = flowOf(60)

        override suspend fun setNapReminderDelayMinutes(minutes: Int) = Unit

        override fun isImportInProgress(): Flow<Boolean> = flowOf(false)

        override suspend fun markImportInProgress(startedAt: Long) = Unit

        override suspend fun restoreFromBackup(data: BackupData) = Unit
    }
}
