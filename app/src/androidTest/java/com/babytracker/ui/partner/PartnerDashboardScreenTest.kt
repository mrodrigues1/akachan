package com.babytracker.ui.partner

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PartnerDashboardScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildViewModel(): PartnerDashboardViewModel {
        val fetchUseCase = mockk<FetchPartnerDataUseCase>()
        coEvery { fetchUseCase.invoke() } throws RuntimeException("offline")
        return PartnerDashboardViewModel(fetchUseCase)
    }

    @Test
    fun settingsNavigationBarItemIsDisplayed() {
        composeRule.setContent {
            PartnerDashboardScreen(
                onNavigateToSettings = {},
                viewModel = buildViewModel(),
            )
        }
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun tappingSettingsItemTriggersOnNavigateToSettings() {
        var called = false
        composeRule.setContent {
            PartnerDashboardScreen(
                onNavigateToSettings = { called = true },
                viewModel = buildViewModel(),
            )
        }
        composeRule.onNodeWithText("Settings").performClick()
        assertTrue(called)
    }
}
