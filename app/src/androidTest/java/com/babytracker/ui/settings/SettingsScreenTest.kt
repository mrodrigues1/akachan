package com.babytracker.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.sharing.domain.model.AppMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildPartnerViewModel(): SettingsViewModel {
        val getBabyProfile = mockk<GetBabyProfileUseCase>()
        every { getBabyProfile() } returns flowOf(null)

        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.getMaxPerBreastMinutes() } returns flowOf(0)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns flowOf(0)
        every { settingsRepository.getThemeConfig() } returns flowOf(ThemeConfig.SYSTEM)
        every { settingsRepository.getAutoUpdateEnabled() } returns flowOf(true)
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(true)
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PARTNER)

        return SettingsViewModel(getBabyProfile, settingsRepository, mockk<SaveBabyProfileUseCase>())
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
        // The Disconnect SettingsRow renders both a label Text("Disconnect")
        // and a button Text("Disconnect") — asserting the first is enough.
        composeRule.onAllNodesWithText("Disconnect")[0].assertIsDisplayed()
    }

    @Test
    fun partnerModeDisconnectRowButtonIsLabelledDisconnect() {
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = buildPartnerViewModel())
        }
        composeRule.waitForIdle()
        // Both the label Text and the TextButton Text render "Disconnect".
        // assertCountEquals(2) verifies actionLabel = "Disconnect" was applied — if it
        // defaulted to "Edit", the button would show "Edit" and this count would be 1.
        composeRule.onAllNodesWithText("Disconnect").assertCountEquals(2)
    }
}
