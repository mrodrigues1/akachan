package com.babytracker.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.babytracker.domain.model.AppFeature
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class HomeFeatureGatingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val noopCallbacks = HomeTileCallbacks(
        onBreastfeeding = {},
        onSleep = {},
        onPumping = {},
        onInventory = {},
        onBottleFeed = {},
        onDiaper = {},
        onVaccine = {},
        onDoctorVisit = {},
        onFeedingHistory = {},
        onConnectPartner = {},
        onGrowth = {},
        onMilestones = {},
        onTrends = {},
    )

    @Test
    fun breastfeedingTile_isHidden_whenFeatureDisabled_butSleepTileShown() {
        composeRule.setContent {
            BabyTrackerTheme {
                HomeContent(
                    uiState = HomeUiState(enabledFeatures = setOf(AppFeature.SLEEP)),
                    callbacks = noopCallbacks,
                    onReorder = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Breastfeeding. Open feeding screen.")
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Sleep. Open sleep screen.")
            .assertIsDisplayed()
    }
}
