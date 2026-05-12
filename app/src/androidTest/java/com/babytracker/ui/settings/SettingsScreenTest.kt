package com.babytracker.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.sharing.domain.model.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildPartnerViewModel(): SettingsViewModel {
        val babyRepository = FakeBabyRepository()
        val settingsRepository = FakeSettingsRepository()
        return SettingsViewModel(
            getBabyProfile = GetBabyProfileUseCase(babyRepository),
            settingsRepository = settingsRepository,
            saveBabyProfile = SaveBabyProfileUseCase(babyRepository),
        )
    }

    @Test
    fun partnerModeHidesBabyProfileSection() {
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Baby Profile").assertDoesNotExist()
    }

    @Test
    fun partnerModeHidesFeedingLimitsSection() {
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Feeding Limits").assertDoesNotExist()
    }

    @Test
    fun partnerModeHidesNotificationsSection() {
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NOTIFICATIONS").assertDoesNotExist()
    }

    @Test
    fun partnerModeShowsDisconnectRow() {
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Disconnect")[0].assertIsDisplayed()
    }

    @Test
    fun partnerModeDisconnectRowButtonIsLabelledDisconnect() {
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel())
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Disconnect").assertCountEquals(2)
    }

    private class FakeBabyRepository : BabyRepository {
        override fun getBabyProfile(): Flow<Baby?> = flowOf(null)

        override suspend fun saveBabyProfile(baby: Baby) = Unit

        override fun isOnboardingComplete(): Flow<Boolean> = flowOf(true)
    }

    private class FakeSettingsRepository : SettingsRepository {
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
    }
}
